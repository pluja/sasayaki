-include .env
export

LLM_BASE_URL   ?= https://api.openai.com/v1
LLM_API_KEY     ?= $(error Set LLM_API_KEY in .env or environment)
LLM_MODEL       ?= claude-sonnet-4-6
NUM_EXAMPLES    ?= 2000
LANGUAGES       ?= $(error Set LANGUAGES in .env, e.g. LANGUAGES=English German Japanese)
BASE_MODEL      ?= unsloth/Qwen3.5-2B
GGUF_QUANTIZATION ?= q4_k_m q8_0
DOCKER_IMAGE      ?= unsloth/unsloth:latest
LANGUAGE_WEIGHTS ?=
CONTEXT_WEIGHTS  ?=

DATA_DIR  = data
OUTPUT_DIR = output

WEIGHT_FLAGS =
ifneq ($(LANGUAGE_WEIGHTS),)
  WEIGHT_FLAGS += --language-weights "$(LANGUAGE_WEIGHTS)"
endif
ifneq ($(CONTEXT_WEIGHTS),)
  WEIGHT_FLAGS += --context-weights "$(CONTEXT_WEIGHTS)"
endif

.PHONY: generate train evaluate all clean help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

sample: ## Generate a small sample to compare model quality
	uv run python generate_training_data.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--languages $(LANGUAGES) \
		$(WEIGHT_FLAGS) --sample

generate: ## Generate synthetic training data
	uv run python generate_training_data.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--num-examples $(NUM_EXAMPLES) \
		--languages $(LANGUAGES) \
		--output-dir $(DATA_DIR) \
		$(WEIGHT_FLAGS)

resume: ## Resume interrupted data generation
	uv run python generate_training_data.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--num-examples $(NUM_EXAMPLES) \
		--languages $(LANGUAGES) \
		--output-dir $(DATA_DIR) \
		$(WEIGHT_FLAGS) --resume

review: ## Review and fix quality issues in the dataset
	uv run python review_and_fix.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--input $(DATA_DIR)/all_generated.jsonl

review-thorough: ## Review with LLM-based checks (slower, catches more)
	uv run python review_and_fix.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--input $(DATA_DIR)/all_generated.jsonl \
		--thorough

review-dry: ## Dry run: flag issues without fixing
	uv run python review_and_fix.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--input $(DATA_DIR)/all_generated.jsonl \
		--dry-run

train: ## Fine-tune the model (local, requires uv + unsloth)
	uv run python fine_tune.py \
		--model "$(BASE_MODEL)" \
		--data-dir $(DATA_DIR) \
		--output-dir $(OUTPUT_DIR) \
		--gguf-quantization $(GGUF_QUANTIZATION)

train-docker: ## Fine-tune in Docker (remote GPU server)
	docker run --gpus all --rm \
		-v $(PWD)/$(DATA_DIR):/workspace/$(DATA_DIR) \
		-v $(PWD)/$(OUTPUT_DIR):/workspace/$(OUTPUT_DIR) \
		-v $(PWD)/fine_tune.py:/workspace/fine_tune.py \
		-w /workspace \
		--entrypoint bash \
		$(DOCKER_IMAGE) \
		-c 'pip install -q --upgrade --force-reinstall --no-cache-dir unsloth unsloth_zoo && python fine_tune.py --model "$(BASE_MODEL)" --data-dir $(DATA_DIR) --output-dir $(OUTPUT_DIR) --gguf-quantization $(GGUF_QUANTIZATION)'

evaluate: ## Evaluate a model against test set
	uv run python evaluate.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--test-file $(DATA_DIR)/test.jsonl \
		--output $(OUTPUT_DIR)/eval-results.json

evaluate-quick: ## Evaluate without LLM judge (edit distance only)
	uv run python evaluate.py \
		--base-url "$(LLM_BASE_URL)" \
		--api-key "$(LLM_API_KEY)" \
		--model "$(LLM_MODEL)" \
		--test-file $(DATA_DIR)/test.jsonl \
		--skip-judge

all: generate train evaluate ## Run full pipeline

clean: ## Remove generated data and outputs
	rm -rf $(DATA_DIR) $(OUTPUT_DIR)
