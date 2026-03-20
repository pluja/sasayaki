# Fine-tuning

This directory contains everything you need to fine-tune a small LLM for Sasayaki's post-processing step. The pipeline generates synthetic training data, trains a model with LoRA, and exports to GGUF for local inference.

It works with any language. You just set your languages in `.env` and the LLM generates realistic speech artifacts, fillers, and ASR confusions for those languages automatically. No linguistic expertise needed.

I personally fine-tuned Qwen3.5-2B and it works great with around 1500 examples. The whole process takes a few hours of generation time (depending on your API) and about 30 minutes of training on a consumer GPU.

## Requirements

- Python 3.13+ with [uv](https://github.com/astral-sh/uv)
- An OpenAI-compatible API for data generation (any LLM that can follow instructions well)
- A GPU with ~5GB VRAM for training (or use the Docker target for remote servers)
- [Unsloth](https://unsloth.ai/) (installed automatically during training)

## Quick start

```bash
cd fine-tuning
cp .env.example .env
# Edit .env: set your API key and languages
```

Generate a small sample first to check quality:

```bash
make sample
```

If the samples look good, generate the full dataset:

```bash
make generate
```

Optionally review and fix quality issues:

```bash
make review-dry    # preview flagged entries
make review        # fix them with LLM
```

Train the model:

```bash
make train         # local GPU
# or
make train-docker  # remote GPU via Docker
```

Evaluate:

```bash
make evaluate-quick  # edit distance only (fast)
make evaluate        # full eval with LLM judge
```

Or just run the full pipeline:

```bash
make all
```

## Configuration

Everything is configured via `.env`. See `.env.example` for all options.

| Variable | Description | Default |
|---|---|---|
| `LANGUAGES` | Space-separated list of languages | (required) |
| `LLM_API_KEY` | API key for data generation | (required) |
| `LLM_BASE_URL` | OpenAI-compatible API endpoint | `https://api.openai.com/v1` |
| `LLM_MODEL` | Model for generation and review | `claude-sonnet-4-6` |
| `NUM_EXAMPLES` | Training examples to generate | `2000` |
| `LANGUAGE_WEIGHTS` | Sampling weights, e.g. `English:3,German:1` | equal |
| `CONTEXT_WEIGHTS` | App context weights, e.g. `chat:3,email:1` | chat-heavy |
| `BASE_MODEL` | Base model for fine-tuning | `unsloth/Qwen3.5-2B` |
| `GGUF_QUANTIZATION` | GGUF export quantizations | `q4_k_m q8_0` |

## How it works

1. **Generate** (`generate_training_data.py`): Calls your LLM to produce realistic (raw_dictation, cleaned_text) pairs. The LLM generates speech artifacts appropriate for each language: filler words, thinking-aloud fragments, ASR misrecognitions, spoken forms, self-corrections. No hardcoded language data; the LLM handles it all.

2. **Review** (`review_and_fix.py`): Flags suspicious entries with heuristic checks (empty output, hallucinated content, missing punctuation). Optionally runs LLM-based checks with `--thorough` for language-specific quality issues. Fixes flagged entries by re-generating the clean output.

3. **Train** (`fine_tune.py`): LoRA fine-tunes the base model using Unsloth. Exports to GGUF for use with llama.cpp, koboldcpp, llamafile, or any other local inference server.

4. **Evaluate** (`evaluate.py`): Tests the model against held-out examples. Computes edit distance and optionally uses an LLM judge to score quality on filler removal, punctuation, intent preservation, and naturalness.

## Deploying the model

After training, you'll find GGUF files in `output/gguf-q4_k_m/` (and `gguf-q8_0/`). Load them into your inference server and point the Sasayaki app's LLM settings at it.

## Tips

- 1000 examples is a good starting point. More helps, but diminishing returns after ~2000.
- Use `LANGUAGE_WEIGHTS` to bias toward your primary language if you have several.
- The `q4_k_m` quantization is a good balance of size and quality for mobile/edge use.
- If generation quality is poor, try a different `LLM_MODEL`. Larger models produce better training data.
- Run `make sample` with different models to compare before committing to a full generation run.
