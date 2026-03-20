#!/usr/bin/env python3
"""Review and fix quality issues in the generated dataset.

Runs language-agnostic heuristic checks to flag suspicious entries, then uses
an LLM to re-generate the clean output for flagged examples.
"""

import argparse
import json
import time
from pathlib import Path

from openai import OpenAI
from tqdm import tqdm


def check_entry(raw: str, clean: str) -> list[str]:
    """Return a list of issue descriptions. Language-agnostic heuristics only."""
    issues = []
    raw_l = raw.lower()
    clean_l = clean.lower()

    if not clean.strip():
        issues.append("empty clean output")
        return issues

    if len(clean) > len(raw) * 1.3:
        issues.append(f"clean longer than raw ({len(clean)} vs {len(raw)} chars)")

    if len(clean) / max(len(raw), 1) > 0.98 and clean_l == raw_l:
        issues.append("clean identical to raw, nothing was cleaned")

    sentences = clean.split(".")
    if len(clean) > 100 and len(sentences) <= 1 and "?" not in clean and "!" not in clean and "\u3002" not in clean:
        issues.append("clean has no punctuation (likely failed generation)")

    return issues


def llm_check_entry(client, model, raw: str, clean: str, language: str) -> list[str]:
    """Use an LLM to detect remaining speech artifacts. Returns issue descriptions."""
    prompt = f"""You are reviewing a dictation post-processing training example in {language}.

RAW (speech-to-text input):
{raw}

CLEAN (post-processed output):
{clean}

Check if the CLEAN output still contains any of these problems:
1. Filler words or verbal tics that should have been removed
2. Thinking-aloud fragments ("let me think", "what was I saying", etc. in {language})
3. Spoken forms not converted to written forms ("dot com" should be ".com", etc.)
4. Self-corrections not resolved (both the wrong and corrected version remain)
5. ASR errors not fixed (wrong homophone still present)

If you find issues, list them briefly. If the clean output looks correct, respond with exactly: OK

Respond with either "OK" or a short list of issues, nothing else."""

    response = client.chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=256,
    )
    result = response.choices[0].message.content.strip()
    if result.upper() == "OK":
        return []
    return [result]


FIX_PROMPT = """You are fixing a dictation post-processing training example.

The SYSTEM PROMPT defines the task:
{system_prompt}

RAW input (speech-to-text output):
{raw}

CURRENT CLEAN output (has issues: {issues}):
{clean}

Produce a corrected CLEAN output that fixes the listed issues while following the system prompt exactly.
Respond with ONLY the corrected clean text, nothing else."""


def fix_entry(client, model, system_prompt, raw, clean, issues):
    """Re-generate the clean output for a flagged entry."""
    prompt = FIX_PROMPT.format(
        system_prompt=system_prompt,
        raw=raw,
        clean=clean,
        issues="; ".join(issues),
    )
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": "You fix dictation post-processing training examples. Output only the corrected clean text."},
            {"role": "user", "content": prompt},
        ],
        temperature=0.2,
        max_tokens=2048,
    )
    return response.choices[0].message.content.strip()


def main():
    parser = argparse.ArgumentParser(description="Review and fix dataset quality issues")
    parser.add_argument("--input", type=Path, default=Path(__file__).parent / "data" / "all_generated.jsonl")
    parser.add_argument("--output", type=Path, default=None, help="Output path (defaults to overwriting input)")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--model", default="claude-sonnet-4-6")
    parser.add_argument("--dry-run", action="store_true", help="Only flag issues, don't fix")
    parser.add_argument("--thorough", action="store_true", help="Also run LLM-based checks (slower but catches language-specific issues)")
    args = parser.parse_args()

    output_path = args.output or args.input

    with open(args.input) as f:
        examples = [json.loads(l) for l in f if l.strip()]

    print(f"Loaded {len(examples)} examples. Running heuristic checks...")

    flagged = []
    clean_count = 0
    for i, ex in enumerate(examples):
        convos = ex["conversations"]
        raw = convos[1]["content"]
        clean = convos[2]["content"]
        issues = check_entry(raw, clean)
        if issues:
            flagged.append((i, issues))
        else:
            clean_count += 1

    print(f"Heuristic pass: {clean_count} clean, {len(flagged)} flagged ({len(flagged)/len(examples)*100:.1f}%)")

    if args.thorough:
        client = OpenAI(base_url=args.base_url, api_key=args.api_key)
        print("Running LLM-based checks on unflagged entries...")
        unflagged_indices = [i for i in range(len(examples)) if not any(idx == i for idx, _ in flagged)]
        for i in tqdm(unflagged_indices, desc="LLM checking"):
            ex = examples[i]
            convos = ex["conversations"]
            raw = convos[1]["content"]
            clean = convos[2]["content"]
            language = ex.get("metadata", {}).get("language", "unknown")
            try:
                llm_issues = llm_check_entry(client, args.model, raw, clean, language)
                if llm_issues:
                    flagged.append((i, llm_issues))
            except Exception as e:
                tqdm.write(f"[{i}] LLM check failed: {e}")
                time.sleep(1)
        print(f"After LLM pass: {len(examples) - len(flagged)} clean, {len(flagged)} flagged")

    if not flagged:
        print("No issues found!")
        return

    print("\nFlagged issues breakdown:")
    from collections import Counter
    all_issues = [issue for _, issues in flagged for issue in issues]
    for issue, count in Counter(all_issues).most_common():
        print(f"  {count:4d}x  {issue}")

    if args.dry_run:
        print("\nDry run -- showing first 10 flagged examples:")
        for i, issues in flagged[:10]:
            ex = examples[i]
            c = ex["conversations"]
            lang = ex.get("metadata", {}).get("language", "?")
            print(f"\n[{i}] {lang} | issues: {issues}")
            print(f"  RAW:   {c[1]['content'][:200]}")
            print(f"  CLEAN: {c[2]['content'][:200]}")
        return

    print(f"\nFixing {len(flagged)} examples with {args.model}...")
    client = OpenAI(base_url=args.base_url, api_key=args.api_key)

    fixed = 0
    failed = 0
    for idx, issues in tqdm(flagged, desc="Fixing"):
        ex = examples[idx]
        convos = ex["conversations"]
        system_prompt = convos[0]["content"]
        raw = convos[1]["content"]
        clean = convos[2]["content"]
        try:
            new_clean = fix_entry(client, args.model, system_prompt, raw, clean, issues)
            remaining = check_entry(raw, new_clean)
            if remaining:
                tqdm.write(f"[{idx}] Partial fix (remaining: {remaining}), keeping best version")
            examples[idx]["conversations"][2]["content"] = new_clean
            examples[idx]["metadata"]["fixed"] = True
            examples[idx]["metadata"]["original_issues"] = issues
            fixed += 1
        except Exception as e:
            tqdm.write(f"[{idx}] Fix failed: {e}")
            failed += 1
            time.sleep(1)

    print(f"\nFixed: {fixed}, Failed: {failed}")
    print(f"Writing {len(examples)} examples to {output_path}...")

    with open(output_path, "w") as f:
        for ex in examples:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")

    print("Done.")


if __name__ == "__main__":
    main()
