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
    ("minimal", "1-5 words, like a quick reply or acknowledgment (e.g. 'yes okay', 'sure thing', 'got it thanks')"),
    ("short", "1-2 sentences, like a quick chat message"),
    ("medium", "3-5 sentences, like a short email or note"),
    ("long", "6-10 sentences, like a detailed paragraph or longer message"),
]

BASE_SYSTEM_PROMPT = """You are a dictation post-processor. The input is raw speech-to-text output. Clean it into text that reads as if the person had typed it themselves. Make minimal changes:
- Remove filler words and verbal tics common in the speaker's language
- Remove discourse markers and conversational tics that only make sense in speech, not writing ("you know?", "right?", "sabes?", "verdad?", "no?", "saps?", "quoi", "hein", "n'est-ce pas?", "ne?")
- Remove thinking-aloud fragments and mid-sentence realizations that only happen in speech, not when typing (false starts, trailing-off, verbal pauses, self-addressed asides, sudden realizations)
- Apply self-corrections: when the speaker restates something, keep only the final version ("five no wait seven" -> "seven")
- Remove false starts and repeated words ("I I think" -> "I think")
- Fix punctuation, capitalization, and sentence boundaries
- Convert spoken forms to written forms ("dot com" -> ".com", "at sign" -> "@", "slash" -> "/", and their equivalents in the speaker's language)
- Fix ASR misrecognition errors: Whisper often produces a wrong but plausible word in the same language (e.g. "hay" instead of "ahí", "their" instead of "there", "afinaranies" instead of "al final aniràs"). Use sentence context to pick the correct word
- Preserve the speaker's vocabulary, tone, and sentence structure. The result should sound like them, not like an editor
- Do not add, infer, or rephrase content beyond what was spoken
- If the input is already clean and needs no changes, return it as-is"""


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


def build_generation_prompt(language, app_context, dictionary_words, length_profile, multilingual_langs=None, count=1):
    """Build the prompt that asks the LLM to generate a training example."""
    length_name, length_desc = length_profile

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

    batch_instruction = f"Generate {count} DIFFERENT and DIVERSE realistic dictation training examples" if count > 1 else "Generate a realistic dictation training example"

    if length_name == "minimal":
        json_format = '[{{"raw": "...", "clean": "..."}}, ...]' if count > 1 else '{{"raw": "the raw dictation text", "clean": "the ideal cleaned output"}}'
        return f"""{batch_instruction} in {lang_context}.

RAW text requirements:
- Length: {length_desc}
- This is a very short utterance. Apply only artifacts that realistically occur in short speech:
  - Maybe a filler word or verbal tic at the start ("um yes", "eh vale", "euh oui")
  - Missing or wrong capitalization/punctuation (STT often lowercases everything)
  - Possibly a discourse marker that doesn't belong in writing ("okay you know", "vale no?", "d'accord quoi")
  - Maybe a minor ASR error: Whisper v3 produces a wrong but plausible word in the SAME language, never gibberish or mixed-script (e.g. "hay" instead of "ahí", "their" instead of "there")
- Do NOT force artifacts that don't make sense for short utterances (no self-corrections, no thought pivots, no long thinking-aloud fragments){app_instruction}

CLEAN text requirements:
- The cleaned version should be equally short -- do NOT expand or elaborate
- Fix capitalization and punctuation only if needed
- Remove any filler or discourse marker
- If the raw text is already clean, the clean text should be identical or near-identical
- Do NOT make the text longer, more formal, or more detailed than what was spoken
{"- Each example must be DIFFERENT from the others -- vary the topic, wording, and artifacts" if count > 1 else ""}
Respond in this exact JSON format, nothing else:
{json_format}"""

    json_format = '[{{"raw": "...", "clean": "..."}}, ...]' if count > 1 else '{{"raw": "the raw dictation text with all speech artifacts", "clean": "the ideal cleaned output"}}'
    return f"""{batch_instruction} in {lang_context}.

RAW text requirements:
- Length: {length_desc}
- Must contain realistic speech-to-text artifacts that are natural for {language} speakers:
  - Filler words and verbal tics common in spoken {language}
  - Thinking-aloud fragments (expressions people use when speaking but never when typing)
  - At least one self-correction (speaker restates something)
  - Missing/wrong punctuation and capitalization (STT output often lacks these)
  - Spoken forms of symbols and URLs (how people say "@", ".com", "/", etc. in {language})
  - 1-2 ASR/STT misrecognition errors. IMPORTANT: Whisper v3 does NOT mix scripts or languages within a sentence. When it mishears a word, it produces a wrong but plausible-sounding word IN THE SAME LANGUAGE. Examples:
    - English: "their" instead of "there", "weather" instead of "whether", "affects" instead of "effects"
    - Spanish: "haber" instead of "a ver", "hay" instead of "ahí", "baya" instead of "vaya", "ves" instead of "vez"
    - Catalan: "afinaranies" instead of "al final aniràs", "hes" instead of "és", "cent" instead of "sent", "te" instead of "té"
    - French: "ses" instead of "ces", "ver" instead of "vers", "prêt" instead of "près"
    - The garbled word always looks like a real word or plausible word in the same language, never gibberish or mixed-script
  - Discourse markers and conversational tics that only make sense in speech ("you know?", "right?", "sabes?", "verdad?", "no?", "saps?", "quoi", "hein", "n'est-ce pas?")
  - Mid-sentence realizations or thought pivots{dict_instruction}{app_instruction}

CLEAN text requirements:
- Must read as if the same person had typed it on a keyboard. Same vocabulary, same tone, same personality. Not more formal, not more polished, not reworded.
- Remove everything that only exists because they were speaking instead of typing:
  - Fillers, verbal tics, thinking-aloud fragments
  - Discourse markers and conversational tics ("you know?", "right?", "sabes?", "verdad?", etc.)
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
{"- Each example must be DIFFERENT from the others -- vary the topic, wording, and artifacts" if count > 1 else ""}
Respond in this exact JSON format, nothing else:
{json_format}"""


def build_passthrough_prompt(language, app_context, length_profile, multilingual_langs=None, count=1):
    """Build a prompt for generating examples where no editing is needed."""
    length_name, length_desc = length_profile
    # For passthrough, cap at short length — long clean text needing no edits is rare
    if length_name in ("medium", "long"):
        length_desc = "1-2 sentences, like a quick chat message"

    lang_context = language
    if multilingual_langs:
        others = [l for l in multilingual_langs if l != language]
        lang_context = f"primarily {language} but mixing in {', '.join(others)}"

    app_instruction = ""
    if app_context:
        app_name = random.choice(app_context["apps"])
        app_instruction = f"\n- The text is being typed into {app_name}, so the content should be appropriate for that context."

    return f"""Generate a realistic text in {lang_context} that a speech-to-text engine transcribed PERFECTLY — no errors, no artifacts, nothing to fix.

Requirements:
- Length: {length_desc}
- The text should be something a person would naturally dictate
- It must already be clean: correct punctuation, capitalization, no fillers, no speech artifacts
- It should read as if typed on a keyboard — natural written text
- Both "raw" and "clean" must be IDENTICAL (the same exact string){app_instruction}

This teaches the model that sometimes the input needs no changes at all.
{"Each example must be DIFFERENT -- vary the topic and wording." if count > 1 else ""}
Respond in this exact JSON format, nothing else:
{('[{{"raw": "...", "clean": "..."}}, ...]' if count > 1 else '{{"raw": "the text", "clean": "the exact same text"}}')}"""


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

    # Weight: minimal 15%, short 30%, medium 35%, long 20%
    length_weights = [15, 30, 35, 20]
    length_profile = random.choices(LENGTH_PROFILES, weights=length_weights, k=1)[0]

    # ~10% of examples are passthrough (no editing needed)
    passthrough = random.random() < 0.10

    return language, app_context, dictionary_words, length_profile, multilingual_langs, passthrough


def generate_batch(client, model, language, app_context, dictionary_words, length_profile, multilingual_langs, passthrough=False, count=1):
    """Generate one or more training examples via a single LLM call."""
    if passthrough:
        generation_prompt = build_passthrough_prompt(language, app_context, length_profile, multilingual_langs, count=count)
    else:
        generation_prompt = build_generation_prompt(
            language, app_context, dictionary_words, length_profile, multilingual_langs, count=count
        )

    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": "You generate realistic dictation training data. Always respond with valid JSON only."},
            {"role": "user", "content": generation_prompt},
        ],
        temperature=0.9,
        max_tokens=min(2048 * count, 8192),
    )

    content = response.choices[0].message.content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()

    parsed = json.loads(content)
    pairs = parsed if isinstance(parsed, list) else [parsed]

    system_prompt = build_system_prompt(language, app_context, dictionary_words, multilingual_langs)
    app_context_key = list(APP_CONTEXTS.keys())[list(APP_CONTEXTS.values()).index(app_context)] if app_context else None

    return [
        {
            "conversations": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": pair["raw"]},
                {"role": "assistant", "content": pair["clean"]},
            ],
            "metadata": {
                "language": language,
                "multilingual": multilingual_langs,
                "app_context": app_context_key,
                "has_dictionary": len(dictionary_words) > 0,
                "length": length_profile[0],
                "passthrough": passthrough,
            },
        }
        for pair in pairs
    ]


def run_sample(client, model, languages, batch_size=4):
    """Generate batch_size examples per language per length profile for quality inspection."""
    print(f"Generating sample with model: {model}\n{'=' * 70}")
    for language in languages:
        for length in LENGTH_PROFILES:
            app_ctx = random.choice(list(APP_CONTEXTS.values())) if length[0] != "minimal" else None
            dict_words = random.sample(DICTIONARY_TERMS, 2) if app_ctx else []
            try:
                examples = generate_batch(client, model, language, app_ctx, dict_words, length, multilingual_langs=None, count=batch_size)
                for example in examples:
                    meta = example["metadata"]
                    ctx_label = meta["app_context"] or "none"
                    print(f"\n--- {language} | context={ctx_label} | length={meta['length']} | dict={meta['has_dictionary']} ---")
                    print(f"RAW:   {example['conversations'][1]['content']}")
                    print(f"CLEAN: {example['conversations'][2]['content']}")
            except Exception as e:
                print(f"\n--- {language} | length={length[0]} | ERROR: {e} ---")
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
    parser.add_argument("--batch-size", type=int, default=4, help="Examples to generate per API call (1-6)")
    parser.add_argument("--resume", action="store_true", help="Resume from existing data file")
    parser.add_argument("--sample", action="store_true", help="Generate a small sample (2 per language) for quality inspection")
    args = parser.parse_args()

    random.seed(args.seed)

    language_weights = parse_weights(args.language_weights) if args.language_weights else {}
    context_weights = {k: 1.0 for k in APP_CONTEXTS}
    context_weights["chat"] = 3.0
    if args.context_weights:
        context_weights.update(parse_weights(args.context_weights))

    client = OpenAI(base_url=args.base_url, api_key=args.api_key, timeout=120)

    if args.sample:
        run_sample(client, args.model, args.languages, batch_size=max(1, min(args.batch_size, 6)))
        return

    args.output_dir.mkdir(parents=True, exist_ok=True)

    all_examples = []
    raw_path = args.output_dir / "all_generated.jsonl"
    if args.resume and raw_path.exists():
        with open(raw_path) as f:
            all_examples = [json.loads(line) for line in f if line.strip()]
        print(f"Resumed with {len(all_examples)} existing examples")

    remaining = args.num_examples - len(all_examples)
    batch_size = max(1, min(args.batch_size, 6))
    if remaining <= 0:
        print(f"Already have {len(all_examples)} examples, nothing to generate.")
    else:
        num_calls = (remaining + batch_size - 1) // batch_size
        print(f"Generating {remaining} examples across {args.languages} ({num_calls} API calls, batch size {batch_size})...")
        errors = 0
        with open(raw_path, "a") as f:
            pbar = tqdm(total=remaining, desc="Generating")
            while len(all_examples) < args.num_examples:
                language, app_ctx, dict_words, length, multi_langs, is_passthrough = pick_example_config(args.languages, language_weights, context_weights)
                needed = args.num_examples - len(all_examples)
                current_batch = min(batch_size, needed)
                try:
                    examples = generate_batch(client, args.model, language, app_ctx, dict_words, length, multi_langs, passthrough=is_passthrough, count=current_batch)
                    added = 0
                    for example in examples:
                        if len(all_examples) >= args.num_examples:
                            break
                        all_examples.append(example)
                        f.write(json.dumps(example, ensure_ascii=False) + "\n")
                        added += 1
                    f.flush()
                    pbar.update(added)
                except Exception as e:
                    errors += 1
                    tqdm.write(f"Error generating batch: {e}")
                    if errors > num_calls * 0.2:
                        print(f"Too many errors ({errors}), stopping.")
                        break
                    time.sleep(1)
                    continue
            pbar.close()

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
