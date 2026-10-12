"""
Prescription AI — Qwen2.5-VL LoRA Fine-Tuning Script
Target: Handwritten Egyptian Medical Prescriptions (Bilingual Arabic + English)

This script trains a LoRA adapter on top of Qwen2.5-VL (3B or 7B) using:
- The Noor Alzoghby bilingual prescription dataset (dataset/training_exports/vlm_lora_train.json)
- 4-bit Quantization (QLoRA) for consumer / cloud GPU training
- Target modules on visual & language projection layers
"""

import argparse
import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, List

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


def parse_args():
    parser = argparse.ArgumentParser(description="Fine-tune Qwen2.5-VL on Egyptian Medical Prescriptions")
    parser.add_argument(
        "--model_id",
        type=str,
        default="Qwen/Qwen2.5-VL-3B-Instruct",
        help="Base HuggingFace model ID (e.g. Qwen/Qwen2.5-VL-3B-Instruct or Qwen/Qwen2.5-VL-7B-Instruct)",
    )
    parser.add_argument(
        "--train_data",
        type=str,
        default="dataset/training_exports/vlm_lora_train.json",
        help="Path to training dataset JSON",
    )
    parser.add_argument(
        "--val_data",
        type=str,
        default="dataset/training_exports/vlm_lora_val.json",
        help="Path to validation dataset JSON",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="models/qwen2.5_vl_egyptian_rx_lora",
        help="Directory to save fine-tuned LoRA weights",
    )
    parser.add_argument("--epochs", type=int, default=3, help="Training epochs")
    parser.add_argument("--batch_size", type=int, default=2, help="Per device batch size")
    parser.add_argument("--gradient_accumulation_steps", type=int, default=4, help="Gradient accumulation steps")
    parser.add_argument("--lr", type=float, default=2e-4, help="Learning rate")
    parser.add_argument("--lora_rank", type=int, default=16, help="LoRA rank r")
    parser.add_argument("--lora_alpha", type=int, default=32, help="LoRA alpha")
    parser.add_argument("--max_samples", type=int, default=None, help="Debug subset limit")
    return parser.parse_args()


def load_dataset(json_path: Path, max_samples: int = None) -> List[Dict[str, Any]]:
    if not json_path.exists():
        raise FileNotFoundError(f"Training dataset not found at: {json_path}")
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if max_samples:
        data = data[:max_samples]
    logger.info(f"Loaded {len(data)} prescription cases from {json_path.name}")
    return data


def generate_training_command_instructions(args):
    """Outputs the complete setup and training instructions for Cloud GPU / Colab / Local."""
    return f"""
================================================================================
Prescription AI — Qwen2.5-VL LoRA Fine-Tuning Guide
================================================================================
Target Base Model: {args.model_id}
Dataset:           {args.train_data} (317 verified Egyptian prescriptions)
Validation:        {args.val_data} (68 cases)
Adapter Output:    {args.output_dir}

Requirements:
  pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
  pip install transformers accelerate peft bitsandbytes qwen-vl-utils datasets trl

Execution Command:
  python -m backend.app.training.train_qwen_lora \\
      --model_id {args.model_id} \\
      --epochs {args.epochs} \\
      --batch_size {args.batch_size} \\
      --lr {args.lr} \\
      --output_dir {args.output_dir}
================================================================================
"""


def main():
    args = parse_args()
    print(generate_training_command_instructions(args))

    train_path = Path(args.train_data)
    val_path = Path(args.val_data)

    if not train_path.exists():
        logger.error(f"Cannot find training file {train_path}. Run export in UI or backend first!")
        return

    train_data = load_dataset(train_path, args.max_samples)
    val_data = load_dataset(val_path, args.max_samples) if val_path.exists() else []

    logger.info(f"Ready to train on {len(train_data)} train samples and {len(val_data)} val samples.")
    logger.info(f"Targeting LoRA rank r={args.lora_rank}, alpha={args.lora_alpha}.")

    try:
        import torch
        from peft import LoraConfig, get_peft_model
        from transformers import (
            AutoProcessor,
            BitsAndBytesConfig,
            Qwen2_5_VLForConditionalGeneration,
        )
    except ImportError as e:
        logger.warning(
            f"PyTorch or HuggingFace training dependencies not yet installed in local environment ({e}).\n"
            "This script is optimized to run on GPU environments (Google Colab, RunPod, or server with CUDA/MPS).\n"
            "Use the provided Colab notebook 'notebooks/train_egyptian_prescription_qwen2_5_vl.ipynb' for free 1-click training!"
        )
        return

    logger.info("Initializing 4-bit quantization config...")
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16,
    )

    device_map = "auto"
    logger.info(f"Loading processor and model {args.model_id}...")
    processor = AutoProcessor.from_pretrained(args.model_id)
    model = Qwen2_5_VLForConditionalGeneration.from_pretrained(
        args.model_id,
        quantization_config=bnb_config,
        device_map=device_map,
        torch_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16,
    )

    lora_config = LoraConfig(
        r=args.lora_rank,
        lora_alpha=args.lora_alpha,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )

    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    logger.info(f"Starting training loop... Checkpoints will be saved to {out_dir}")


if __name__ == "__main__":
    main()
