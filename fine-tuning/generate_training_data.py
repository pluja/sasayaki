#!/usr/bin/env python3
"""Generate synthetic training data for dictation post-processing fine-tuning.

Calls any OpenAI-compatible API to produce (raw_dictation, cleaned_text) pairs
in ChatML/conversations format, ready for Unsloth SFT training.

Language-agnostic: specify any languages via --languages and the LLM handles
generating appropriate speech artifacts, fillers, and ASR confusions.
"""

import argparse
import json
import random
import time
from pathlib import Path

from openai import OpenAI
from tqdm import tqdm

APP_CONTEXTS = {
    "email": {
        "apps": ["Gmail", "Outlook", "Proton Mail"],
        "style": "Use a professional written tone with proper greetings and sign-offs if present.",
    },
    "chat": {
        "apps": ["Slack", "Discord", "Telegram", "WhatsApp", "Signal", "Messages"],
        "style": "Use a casual conversational tone. Keep it concise and natural for chat.",
    },
    "notes": {
        "apps": ["Google Docs", "Notion", "Obsidian", "Apple Notes", "Evernote"],
        "style": "Use a clear, structured writing style suitable for documents and notes.",
    },
    "social": {
        "apps": ["Twitter", "Mastodon", "Threads", "Bluesky"],
        "style": "Keep it very concise and punchy, suitable for social media posts.",
    },
}

# Universal terms for teaching the model to handle custom dictionary words.
# These are proper nouns / acronyms recognized worldwide, not language-specific.
DICTIONARY_TERMS = [
    "Kubernetes", "PostgreSQL", "GraphQL", "OAuth", "nginx", "Redis",
    "iPhone", "YouTube", "GitHub", "ChatGPT", "WhatsApp", "Spotify",
    "ibuprofen", "paracetamol", "MRI", "COVID", "UNESCO", "NATO",
]

LENGTH_PROFILES = [
    ("short", "1-2 sentences, like a quick chat message"),
    ("medium", "3-5 sentences, like a short email or note"),
    ("long", "6-10 sentences, like a detailed paragraph or longer message"),
]

BASE_SYSTEM_PROMPT = """You are a dictation post-processor. The input is raw speech-to-text output. Clean it into text that reads as if the person had typed it themselves. Make minimal changes:
- Remove filler words and verbal tics common in the speaker's language
- Remove thinking-aloud fragments and mid-sentence realizations that only happen in speech, not when typing (false starts, trailing-off, verbal pauses, self-addressed asides, sudden realizations)
- Apply self-corrections: when the speaker restates something, keep only the final version ("five no wait seven" -> "seven")
- Remove false starts and repeated words ("I I think" -> "I think")
- Fix punctuation, capitalization, and sentence boundaries
- Convert spoken forms to written forms ("dot com" -> ".com", "at sign" -> "@", "slash" -> "/", and their equivalents in the speaker's language)
- Fix ASR misrecognition errors: when the speech engine picked the wrong homophone or similar-sounding word, use context to pick the correct one
- Preserve the speaker's vocabulary, tone, and sentence structure. The result should sound like them, not like an editor
- Do not add, infer, or rephrase content beyond what was spoken"""


def build_system_prompt(language, app_context, dictionary_words, multilingual_langs=None):
    """Build a system prompt matching the app's TextProcessor.buildSystemPrompt logic."""
    parts = [BASE_SYSTEM_PROMPT]

    if multilingual_langs:
        lang_list = ", ".join(multilingual_langs)
        parts.append(f"- The user dictates in: {lang_list}. Handle speech disfluencies in all of these languages.")
    elif language:
        parts.append(f"- The user is dictating in: {language}. Handle speech disfluencies for this language.")

    if dictionary_words:
        parts.append(f"- Use these known terms with their exact spelling: {', '.join(dictionary_words)}")

    if app_context:
        app_name = random.choice(app_context["apps"])
        parts.append(f"- The user is dictating into {app_name}. {app_context['style']}")

    parts.append("Return ONLY the cleaned text, nothing else.")
    return "\n".join(parts)


def build_generation_prompt(language, app_context, dictionary_words, length_profile, multilingual_langs=None):
    """Build the prompt that asks the LLM to generate a training example."""
    _, length_desc = length_profile

    lang_context = language
    if multilingual_langs:
        others = [l for l in multilingual_langs if l != language]
        lang_context = f"primarily {language} but mixing in {', '.join(others)}"

    dict_instruction = ""
    if dictionary_words:
        dict_instruction = f"\n- Naturally incorporate some of these terms: {', '.join(dictionary_words)}"

    app_instruction = ""
    if app_context:
        app_name = random.choice(app_context["apps"])
        app_instruction = f"\n- The dictation is being typed into {app_name}, so the content should be appropriate for that context."

    return f"""Generate a realistic dictation training example in {lang_context}.

RAW text requirements:
- Length: {length_desc}
- Must contain realistic speech-to-text artifacts that are natural for {language} speakers:
  - Filler words and verbal tics common in spoken {language}
  - Thinking-aloud fragments (expressions people use when speaking but never when typing)
  - At least one self-correction (speaker restates something)
  - Missing/wrong punctuation and capitalization (STT output often lacks these)
  - Spoken forms of symbols and URLs (how people say "@", ".com", "/", etc. in {language})
  - 1-2 ASR/STT misrecognition errors: the speech engine picked the wrong homophone or similar-sounding word that exists in {language}
  - Mid-sentence realizations or thought pivots{dict_instruction}{app_instruction}

CLEAN text requirements:
- Must read as if the same person had typed it on a keyboard. Same vocabulary, same tone, same personality. Not more formal, not more polished, not reworded.
- Remove everything that only exists because they were speaking instead of typing:
  - Fillers, verbal tics, thinking-aloud fragments
  - Mid-sentence realizations -- when typing you just write the thought directly
  - Self-corrections: keep only the final version, seamlessly
  - False starts and word repetitions
- Fix what STT got wrong:
  - ASR misrecognitions (wrong homophone/similar word) -- pick the correct word from context
  - Spoken forms to written forms
  - Punctuation, capitalization, sentence boundaries
- Do NOT:
  - Rephrase or restructure sentences
  - Make the text more formal or "proper" than the speaker intended
  - Add greetings, sign-offs, or content that wasn't spoken
  - Remove casual language, slang, or informal constructions -- those are intentional

Respond in this exact JSON format, nothing else:
{{"raw": "the raw dictation text with all speech artifacts", "clean": "the ideal cleaned output"}}"""


def parse_weights(weight_str):
    """Parse a weight string like 'English:3,Spanish:3,French:1' into a dict."""
    weights = {}
    for pair in weight_str.split(","):
        pair = pair.strip()
        if ":" in pair:
            key, value = pair.rsplit(":", 1)
            weights[key.strip()] = float(value.strip())
    return weights


def pick_example_config(languages, language_weights, context_weights, include_multilingual=True):
    """Randomly pick a configuration for one training example."""
    multilingual_langs = None
    if include_multilingual and random.random() < 0.1 and len(languages) >= 2:
        multilingual_langs = random.sample(languages, k=random.randint(2, min(3, len(languages))))
        language = multilingual_langs[0]
    else:
        lang_weights = [language_weights.get(l, 1) for l in languages]
        language = random.choices(languages, weights=lang_weights, k=1)[0]

    app_context = None
    if random.random() < 0.6:
        context_keys = list(context_weights.keys())
        weights = [context_weights[k] for k in context_keys]
        chosen_key = random.choices(context_keys, weights=weights, k=1)[0]
        app_context = APP_CONTEXTS[chosen_key]

    dictionary_words = []
    if random.random() < 0.4:
        dictionary_words = random.sample(DICTIONARY_TERMS, k=random.randint(1, 3))

    length_profile = random.choice(LENGTH_PROFILES)

    return language, app_context, dictionary_words, length_profile, multilingual_langs


def generate_example(client, model, language, app_context, dictionary_words, length_profile, multilingual_langs):
    """Generate one training example via the LLM."""
    generation_prompt = build_generation_prompt(
        language, app_context, dictionary_words, length_profile, multilingual_langs
    )

    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": "You generate realistic dictation training data. Always respond with valid JSON only."},
            {"role": "user", "content": generation_prompt},
        ],
        temperature=0.9,
        max_tokens=2048,
    )

    content = response.choices[0].message.content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()

    pair = json.loads(content)

    system_prompt = build_system_prompt(language, app_context, dictionary_words, multilingual_langs)

    return {
        "conversations": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": pair["raw"]},
            {"role": "assistant", "content": pair["clean"]},
        ],
        "metadata": {
            "language": language,
            "multilingual": multilingual_langs,
            "app_context": list(APP_CONTEXTS.keys())[list(APP_CONTEXTS.values()).index(app_context)] if app_context else None,
            "has_dictionary": len(dictionary_words) > 0,
            "length": length_profile[0],
        },
    }


def run_sample(client, model, languages):
    """Generate 2 examples per language and print them for quality inspection."""
    print(f"Generating sample with model: {model}\n{'=' * 70}")
    contexts = [None, random.choice(list(APP_CONTEXTS.values()))]
    for language in languages:
        for i, app_ctx in enumerate(contexts):
            dict_words = random.sample(DICTIONARY_TERMS, 2) if i == 1 else []
            length = LENGTH_PROFILES[i % len(LENGTH_PROFILES)]
            try:
                example = generate_example(client, model, language, app_ctx, dict_words, length, multilingual_langs=None)
                meta = example["metadata"]
                ctx_label = meta["app_context"] or "none"
                print(f"\n--- {language} | context={ctx_label} | length={meta['length']} | dict={meta['has_dictionary']} ---")
                print(f"RAW:   {example['conversations'][1]['content']}")
                print(f"CLEAN: {example['conversations'][2]['content']}")
            except Exception as e:
                print(f"\n--- {language} | ERROR: {e} ---")
    print(f"\n{'=' * 70}")


def main():
    parser = argparse.ArgumentParser(description="Generate synthetic dictation post-processing training data")
    parser.add_argument("--base-url", default="https://api.openai.com/v1", help="OpenAI-compatible API base URL")
    parser.add_argument("--api-key", required=True, help="API key")
    parser.add_argument("--model", default="claude-sonnet-4-6", help="Model to use for generation")
    parser.add_argument("--num-examples", type=int, default=2000, help="Total examples to generate")
    parser.add_argument("--languages", nargs="+", required=True, help="Languages to generate for (e.g. English German Japanese)")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent / "data", help="Output directory")
    parser.add_argument("--train-split", type=float, default=0.85, help="Fraction for training (rest split between val/test)")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument(
        "--language-weights", type=str, default=None,
        help="Language sampling weights, e.g. 'English:3,German:2,Japanese:1'. Unspecified languages default to 1.",
    )
    parser.add_argument(
        "--context-weights", type=str, default=None,
        help="App context sampling weights, e.g. 'chat:3,email:1,notes:1,social:1'. Unspecified contexts default to 1.",
    )
    parser.add_argument("--resume", action="store_true", help="Resume from existing data file")
    parser.add_argument("--sample", action="store_true", help="Generate a small sample (2 per language) for quality inspection")
    args = parser.parse_args()

    random.seed(args.seed)

    language_weights = parse_weights(args.language_weights) if args.language_weights else {}
    context_weights = {k: 1.0 for k in APP_CONTEXTS}
    context_weights["chat"] = 3.0
    if args.context_weights:
        context_weights.update(parse_weights(args.context_weights))

    client = OpenAI(base_url=args.base_url, api_key=args.api_key)

    if args.sample:
        run_sample(client, args.model, args.languages)
        return

    args.output_dir.mkdir(parents=True, exist_ok=True)

    all_examples = []
    raw_path = args.output_dir / "all_generated.jsonl"
    if args.resume and raw_path.exists():
        with open(raw_path) as f:
            all_examples = [json.loads(line) for line in f if line.strip()]
        print(f"Resumed with {len(all_examples)} existing examples")

    remaining = args.num_examples - len(all_examples)
    if remaining <= 0:
        print(f"Already have {len(all_examples)} examples, nothing to generate.")
    else:
        print(f"Generating {remaining} examples across {args.languages}...")
        errors = 0
        with open(raw_path, "a") as f:
            for i in tqdm(range(remaining), desc="Generating"):
                language, app_ctx, dict_words, length, multi_langs = pick_example_config(args.languages, language_weights, context_weights)
                try:
                    example = generate_example(client, args.model, language, app_ctx, dict_words, length, multi_langs)
                    all_examples.append(example)
                    f.write(json.dumps(example, ensure_ascii=False) + "\n")
                    f.flush()
                except Exception as e:
                    errors += 1
                    tqdm.write(f"Error generating example {i}: {e}")
                    if errors > remaining * 0.2:
                        print(f"Too many errors ({errors}), stopping.")
                        break
                    time.sleep(1)
                    continue

    random.shuffle(all_examples)
    n = len(all_examples)
    train_end = int(n * args.train_split)
    val_end = train_end + int(n * (1 - args.train_split) / 2)

    splits = {
        "train": all_examples[:train_end],
        "val": all_examples[train_end:val_end],
        "test": all_examples[val_end:],
    }

    for split_name, examples in splits.items():
        path = args.output_dir / f"{split_name}.jsonl"
        with open(path, "w") as f:
            for ex in examples:
                f.write(json.dumps({"conversations": ex["conversations"]}, ensure_ascii=False) + "\n")
        print(f"{split_name}: {len(examples)} examples -> {path}")

    print(f"\nTotal: {n} examples")
    langs = {}
    for ex in all_examples:
        lang = ex["metadata"]["language"]
        langs[lang] = langs.get(lang, 0) + 1
    for lang, count in sorted(langs.items()):
        print(f"  {lang}: {count} ({count/n*100:.1f}%)")


if __name__ == "__main__":
    main()
