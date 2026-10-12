import base64
import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..config import PROJECT_ROOT
from ..dataset.bilingual_loader import BilingualDatasetLoader, bilingual_dataset_loader
from ..services.analyzer import PrescriptionAnalyzer

logger = logging.getLogger(__name__)

EXPORTS_DIR = PROJECT_ROOT / "dataset" / "training_exports"


def encode_image_base64(image_path: Path) -> str:
    """Encodes an image to a base64 string."""
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


class FineTuneDatasetExporter:
    """
    Exports the Bilingual Arabic/English Prescription training dataset
    into standardized fine-tuning formats for OpenAI, Gemini, and open-source VLMs.
    """

    def __init__(
        self,
        loader: Optional[BilingualDatasetLoader] = None,
        exports_dir: Optional[Path] = EXPORTS_DIR,
    ):
        self.loader = loader or bilingual_dataset_loader
        self.exports_dir = exports_dir or EXPORTS_DIR
        self.analyzer = PrescriptionAnalyzer()
        self.exports_dir.mkdir(parents=True, exist_ok=True)

    def export_openai_vision_jsonl(self, max_cases: Optional[int] = None) -> Dict[str, str]:
        """
        Exports Train and Val splits to OpenAI Vision Fine-Tuning JSONL format.
        Format specification:
        {"messages": [{"role": "system", ...}, {"role": "user", ...[image_url, text]}, {"role": "assistant", ...}]}
        """
        system_prompt = self.analyzer.load_prompt()

        def _build_lines(split_name: str) -> List[str]:
            cases = self.loader.get_split(split_name)
            if max_cases:
                cases = cases[:max_cases]

            lines = []
            for c in cases:
                if not c.image_path.exists():
                    continue

                b64_img = encode_image_base64(c.image_path)
                data_uri = f"data:image/jpeg;base64,{b64_img}"
                target_json = c.to_prescription_analysis().model_dump_json(indent=2)

                record = {
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {
                            "role": "user",
                            "content": [
                                {
                                    "type": "text",
                                    "text": "Transcribe and extract structured clinical information from this Egyptian prescription.",
                                },
                                {
                                    "type": "image_url",
                                    "image_url": {"url": data_uri},
                                },
                            ],
                        },
                        {"role": "assistant", "content": target_json},
                    ]
                }
                lines.append(json.dumps(record, ensure_ascii=False))
            return lines

        train_path = self.exports_dir / "openai_vision_train.jsonl"
        val_path = self.exports_dir / "openai_vision_val.jsonl"

        train_lines = _build_lines("train")
        val_lines = _build_lines("val")

        with open(train_path, "w", encoding="utf-8") as f:
            f.write("\n".join(train_lines))

        with open(val_path, "w", encoding="utf-8") as f:
            f.write("\n".join(val_lines))

        logger.info(f"Exported OpenAI Vision JSONL: {len(train_lines)} train, {len(val_lines)} val.")
        return {
            "format": "OpenAI Vision JSONL",
            "train_file": str(train_path),
            "train_count": len(train_lines),
            "val_file": str(val_path),
            "val_count": len(val_lines),
        }

    def export_vlm_lora_manifest(self, max_cases: Optional[int] = None) -> Dict[str, str]:
        """
        Exports dataset to HuggingFace VLM (e.g. Qwen2-VL / LLaVA / Florence) LoRA training format.
        """
        system_prompt = "You are a medical OCR specialist for handwritten Egyptian prescriptions."

        def _build_manifest(split_name: str) -> List[Dict[str, Any]]:
            cases = self.loader.get_split(split_name)
            if max_cases:
                cases = cases[:max_cases]

            manifest = []
            for c in cases:
                if not c.image_path.exists():
                    continue

                manifest.append(
                    {
                        "id": c.case_id,
                        "image": str(c.image_path.resolve()),
                        "conversations": [
                            {
                                "from": "human",
                                "value": f"<image>\n{system_prompt}\nExtract all medications and instructions into JSON.",
                            },
                            {
                                "from": "gpt",
                                "value": c.to_prescription_analysis().model_dump_json(indent=2),
                            },
                        ],
                    }
                )
            return manifest

        train_path = self.exports_dir / "vlm_lora_train.json"
        val_path = self.exports_dir / "vlm_lora_val.json"

        train_data = _build_manifest("train")
        val_data = _build_manifest("val")

        with open(train_path, "w", encoding="utf-8") as f:
            json.dump(train_data, f, indent=2, ensure_ascii=False)

        with open(val_path, "w", encoding="utf-8") as f:
            json.dump(val_data, f, indent=2, ensure_ascii=False)

        logger.info(f"Exported VLM LoRA format: {len(train_data)} train, {len(val_data)} val.")
        return {
            "format": "HuggingFace VLM LoRA JSON",
            "train_file": str(train_path),
            "train_count": len(train_data),
            "val_file": str(val_path),
            "val_count": len(val_data),
        }

    def export_all(self, max_cases: Optional[int] = None) -> Dict[str, Any]:
        """Runs all exporters and returns summary manifest."""
        return {
            "openai_vision": self.export_openai_vision_jsonl(max_cases=max_cases),
            "vlm_lora": self.export_vlm_lora_manifest(max_cases=max_cases),
            "exports_directory": str(self.exports_dir),
        }


fine_tune_exporter = FineTuneDatasetExporter()
