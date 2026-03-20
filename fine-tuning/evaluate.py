#!/usr/bin/env python3
"""Evaluate fine-tuned dictation post-processing models.

Runs test examples through an OpenAI-compatible endpoint and compares against
ground truth using edit distance and LLM-as-judge scoring.
"""

import argparse
import json
import time
from pathlib import Path

from openai import OpenAI
from tqdm import tqdm


def edit_distance(a: str, b: str) -> int:
    """Levenshtein distance between two strings."""
    if len(a) < len(b):
        return edit_distance(b, a)
    if len(b) == 0:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a):
        curr = [i + 1]
        for j, cb in enumerate(b):
            cost = 0 if ca == cb else 1
            curr.append(min(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost))
        prev = curr
    return prev[len(b)]


def normalized_edit_distance(a: str, b: str) -> float:
    """Edit distance normalized by max length (0.0 = identical, 1.0 = completely different)."""
    max_len = max(len(a), len(b))
    if max_len == 0:
        return 0.0
    return edit_distance(a, b) / max_len


def run_inference(client, model, system_prompt, user_text):
    """Run a single inference through the model."""
    start = time.time()
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_text},
        ],
        temperature=0.3,
        max_tokens=2048,
    )
    latency = time.time() - start
    return response.choices[0].message.content.strip(), latency


def judge_quality(client, judge_model, raw_text, expected, actual):
    """Use an LLM to judge quality of the post-processing on a 1-5 scale."""
    prompt = f"""You are evaluating a dictation post-processor. Rate the output on a 1-5 scale for each criterion.

Raw dictation input:
{raw_text}

Expected output:
{expected}

Actual output:
{actual}

Rate each criterion (1=terrible, 5=perfect):
1. Filler removal: Were all filler words and verbal tics removed?
2. Punctuation: Is punctuation and capitalization correct?
3. Intent preservation: Does the output preserve the speaker's original meaning?
4. Naturalness: Does it read naturally as written text?

Respond in this exact JSON format:
{{"filler_removal": N, "punctuation": N, "intent_preservation": N, "naturalness": N}}"""

    response = client.chat.completions.create(
        model=judge_model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=256,
    )
    content = response.choices[0].message.content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()
    return json.loads(content)


def main():
    parser = argparse.ArgumentParser(description="Evaluate dictation post-processing models")
    parser.add_argument("--base-url", required=True, help="OpenAI-compatible API base URL for the model under test")
    parser.add_argument("--api-key", default="not-needed", help="API key for model under test")
    parser.add_argument("--model", required=True, help="Model name for the model under test")
    parser.add_argument("--judge-base-url", default=None, help="API base URL for the judge LLM (defaults to same as --base-url)")
    parser.add_argument("--judge-api-key", default=None, help="API key for judge LLM")
    parser.add_argument("--judge-model", default="claude-sonnet-4-6", help="Model for LLM-as-judge scoring")
    parser.add_argument("--test-file", type=Path, default=Path(__file__).parent / "data" / "test.jsonl")
    parser.add_argument("--max-examples", type=int, default=None, help="Limit number of test examples")
    parser.add_argument("--output", type=Path, default=None, help="Save detailed results to JSON")
    parser.add_argument("--skip-judge", action="store_true", help="Skip LLM-as-judge (only compute edit distance)")
    args = parser.parse_args()

    client = OpenAI(base_url=args.base_url, api_key=args.api_key)

    judge_client = None
    if not args.skip_judge:
        judge_client = OpenAI(
            base_url=args.judge_base_url or args.base_url,
            api_key=args.judge_api_key or args.api_key,
        )

    # Load test data
    examples = []
    with open(args.test_file) as f:
        for line in f:
            if line.strip():
                examples.append(json.loads(line))

    if args.max_examples:
        examples = examples[:args.max_examples]

    print(f"Evaluating {len(examples)} examples with model: {args.model}")

    results = []
    total_edit_dist = 0.0
    total_latency = 0.0
    judge_totals = {"filler_removal": 0, "punctuation": 0, "intent_preservation": 0, "naturalness": 0}
    judge_count = 0

    for ex in tqdm(examples, desc="Evaluating"):
        convos = ex["conversations"]
        system_prompt = convos[0]["content"]
        raw_text = convos[1]["content"]
        expected = convos[2]["content"]

        try:
            actual, latency = run_inference(client, args.model, system_prompt, raw_text)
        except Exception as e:
            tqdm.write(f"Inference error: {e}")
            continue

        ned = normalized_edit_distance(expected, actual)
        total_edit_dist += ned
        total_latency += latency

        result = {
            "raw": raw_text,
            "expected": expected,
            "actual": actual,
            "edit_distance": ned,
            "latency_s": latency,
        }

        if judge_client:
            try:
                scores = judge_quality(judge_client, args.judge_model, raw_text, expected, actual)
                result["judge_scores"] = scores
                for key in judge_totals:
                    judge_totals[key] += scores.get(key, 0)
                judge_count += 1
            except Exception as e:
                tqdm.write(f"Judge error: {e}")

        results.append(result)

    n = len(results)
    if n == 0:
        print("No results.")
        return

    print(f"\n{'='*60}")
    print(f"Model: {args.model}")
    print(f"Examples: {n}")
    print(f"Avg normalized edit distance: {total_edit_dist / n:.4f}")
    print(f"Avg latency: {total_latency / n:.3f}s")

    if judge_count > 0:
        print(f"\nLLM Judge scores (avg, 1-5 scale):")
        for key, total in judge_totals.items():
            print(f"  {key}: {total / judge_count:.2f}")
        overall = sum(judge_totals.values()) / (judge_count * len(judge_totals))
        print(f"  overall: {overall:.2f}")

    if args.output:
        summary = {
            "model": args.model,
            "num_examples": n,
            "avg_edit_distance": total_edit_dist / n,
            "avg_latency_s": total_latency / n,
            "judge_scores": {k: v / judge_count for k, v in judge_totals.items()} if judge_count else None,
            "results": results,
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with open(args.output, "w") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\nDetailed results saved to {args.output}")


if __name__ == "__main__":
    main()
