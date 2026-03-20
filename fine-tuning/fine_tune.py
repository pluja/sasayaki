#!/usr/bin/env python3
"""Fine-tune a Qwen3.5 model for dictation post-processing using Unsloth.

Usage:
    python fine_tune.py --model unsloth/Qwen3.5-2B --data-dir data/ --output-dir output/
"""

import argparse
from pathlib import Path

from unsloth import FastLanguageModel
from unsloth.chat_templates import get_chat_template
from datasets import load_dataset
from trl import SFTTrainer, SFTConfig


def main():
    parser = argparse.ArgumentParser(description="Fine-tune a model for dictation post-processing")
    parser.add_argument("--model", default="unsloth/Qwen3.5-2B", help="Base model name")
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent / "output")
    parser.add_argument("--max-seq-length", type=int, default=2048)
    parser.add_argument("--lora-rank", type=int, default=16)
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--gradient-accumulation", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--gguf-quantization", nargs="+", default=["q4_k_m", "q8_0"])
    parser.add_argument("--push-to-hub", type=str, default=None)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    max_seq_length = args.max_seq_length

    print(f"Loading model: {args.model}")
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=args.model,
        max_seq_length=max_seq_length,
        load_in_4bit=False,
        load_in_16bit=True,
        full_finetuning=False,
    )

    tokenizer = get_chat_template(tokenizer, chat_template="qwen-2.5")

    model = FastLanguageModel.get_peft_model(
        model,
        r=args.lora_rank,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_alpha=args.lora_rank,
        lora_dropout=0,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=3407,
        max_seq_length=max_seq_length,
    )

    dataset_train = load_dataset("json", data_files=str(args.data_dir / "train.jsonl"), split="train")
    dataset_val = load_dataset("json", data_files=str(args.data_dir / "val.jsonl"), split="train")

    def formatting_prompts_func(examples):
        convos = examples["conversations"]
        texts = [tokenizer.apply_chat_template(convo, tokenize=False, add_generation_prompt=False) for convo in convos]
        return {"text": texts}

    dataset_train = dataset_train.map(formatting_prompts_func, batched=True)
    dataset_val = dataset_val.map(formatting_prompts_func, batched=True)

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset_train,
        eval_dataset=dataset_val,
        args=SFTConfig(
            dataset_text_field="text",
            max_seq_length=max_seq_length,
            per_device_train_batch_size=args.batch_size,
            gradient_accumulation_steps=args.gradient_accumulation,
            learning_rate=args.learning_rate,
            warmup_steps=10,
            num_train_epochs=args.epochs,
            logging_steps=10,
            eval_strategy="steps",
            eval_steps=50,
            save_strategy="steps",
            save_steps=100,
            save_total_limit=3,
            load_best_model_at_end=True,
            metric_for_best_model="eval_loss",
            output_dir=str(args.output_dir / "checkpoints"),
            optim="adamw_8bit",
            seed=3407,
            report_to="none",
        ),
    )

    print("Starting training...")
    trainer.train()

    print("Training complete. Exporting to GGUF...")
    for quant in args.gguf_quantization:
        gguf_dir = args.output_dir / f"gguf-{quant}"
        print(f"  Exporting {quant} -> {gguf_dir}")
        model.save_pretrained_gguf(str(gguf_dir), tokenizer, quantization_method=quant)

        if args.push_to_hub:
            print(f"  Pushing {quant} to {args.push_to_hub}")
            model.push_to_hub_gguf(args.push_to_hub, tokenizer, quantization_method=quant)

    print("Done.")


if __name__ == "__main__":
    main()
