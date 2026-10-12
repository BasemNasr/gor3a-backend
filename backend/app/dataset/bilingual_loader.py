import json
import logging
import random
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from ..models.prescription import MedicationItem, PatientInfo, PrescriptionAnalysis

logger = logging.getLogger(__name__)

PRIMARY_DATASET_DIR = PROJECT_ROOT / "dataset" / "bilingual_prescriptions"
FALLBACK_DATASET_DIR = PROJECT_ROOT / "newdataset arabic , english " / "dataset"


class BilingualPrescriptionCase:
    """Represents a single prescription case from the Noor Alzoghby bilingual dataset."""

    def __init__(
        self,
        case_id: str,
        filename: str,
        image_path: Path,
        raw_medications: List[Dict[str, Any]],
    ):
        self.case_id = case_id
        self.filename = filename
        self.image_path = image_path
        self.raw_medications = raw_medications

    def to_prescription_analysis(self) -> PrescriptionAnalysis:
        """Converts raw dataset annotations into standardized PrescriptionAnalysis."""
        med_items: List[MedicationItem] = []
        uncertains: List[str] = []

        for m in self.raw_medications:
            name = m.get("Medication_name") or m.get("Medication_name_raw")
            dose = m.get("Dosage") or m.get("Dosage_raw")
            freq = m.get("Frequency") or m.get("Frequency_raw")
            dur = m.get("Duration") or m.get("Duration_raw")
            instr = m.get("Instructions") or m.get("Instructions_raw")

            raw_text = " ".join(
                str(x) for x in [name, dose, freq, dur, instr] if x is not None
            )

            status = "identified" if name else "uncertain"
            if not name:
                uncertains.append(f"Illegible or missing medication name in {self.filename}")

            med_items.append(
                MedicationItem(
                    raw_text=raw_text or "Prescribed item",
                    name=name,
                    strength=dose if dose and ("mg" in str(dose).lower() or "gm" in str(dose).lower()) else None,
                    dose=dose,
                    frequency=freq,
                    duration=dur,
                    instructions=instr,
                    confidence=1.0 if name else 0.5,
                    status=status,
                    pipeline_version="ground_truth",
                )
            )

        return PrescriptionAnalysis(
            prescription=PatientInfo(),
            medications=med_items,
            uncertain_items=uncertains,
            overall_confidence=1.0,
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "case_id": self.case_id,
            "filename": self.filename,
            "image_path": str(self.image_path),
            "medication_count": len(self.raw_medications),
            "medications": self.raw_medications,
            "has_image": self.image_path.exists(),
        }


class BilingualDatasetLoader:
    """
    Ingests and harmonizes the Bilingual Medical Prescriptions (Arabic & English) dataset.
    Provides reproducible Train (70%) / Validation (15%) / Test (15%) splits.
    """

    def __init__(self, dataset_dir: Optional[Path] = None):
        self.dataset_dir = dataset_dir or self._resolve_dataset_dir()
        self.cases: List[BilingualPrescriptionCase] = []
        self._splits: Optional[Dict[str, List[BilingualPrescriptionCase]]] = None
        self.load()

    def _resolve_dataset_dir(self) -> Path:
        if PRIMARY_DATASET_DIR.exists() and (PRIMARY_DATASET_DIR / "all_prescriptions_clean.json").exists():
            return PRIMARY_DATASET_DIR
        if FALLBACK_DATASET_DIR.exists() and (FALLBACK_DATASET_DIR / "all_prescriptions_clean.json").exists():
            return FALLBACK_DATASET_DIR
        return PRIMARY_DATASET_DIR

    def load(self):
        """Loads and filters valid cases that possess an existing image file."""
        json_file = self.dataset_dir / "all_prescriptions_clean.json"
        img_dir = self.dataset_dir / "images"
        if not img_dir.exists():
            img_dir = self.dataset_dir / "prescriptions"

        if not json_file.exists():
            logger.warning(f"Bilingual prescriptions json not found at {json_file}")
            return

        with open(json_file, "r", encoding="utf-8") as f:
            raw_data = json.load(f)

        self.cases = []
        for i, item in enumerate(raw_data):
            fn = item.get("filename", "")
            if not fn:
                continue

            img_path = img_dir / fn
            raw_meds = item.get("medications", [])
            # Only keep cases with real image files and verified medication annotations
            if img_path.exists() and len(raw_meds) > 0:
                case_id = f"bilingual_{Path(fn).stem}"
                case = BilingualPrescriptionCase(
                    case_id=case_id,
                    filename=fn,
                    image_path=img_path,
                    raw_medications=raw_meds,
                )
                self.cases.append(case)

        logger.info(f"Loaded {len(self.cases)} valid paired bilingual prescription cases.")
        self._generate_splits()

    def _generate_splits(self, seed: int = 42):
        """Generates deterministic Train (70%), Val (15%), Test (15%) splits."""
        if not self.cases:
            self._splits = {"train": [], "val": [], "test": []}
            return

        # Deterministic shuffle
        shuffled = list(self.cases)
        rng = random.Random(seed)
        rng.shuffle(shuffled)

        n = len(shuffled)
        n_train = int(n * 0.70)
        n_val = int(n * 0.15)

        self._splits = {
            "train": sorted(shuffled[:n_train], key=lambda c: c.filename),
            "val": sorted(shuffled[n_train : n_train + n_val], key=lambda c: c.filename),
            "test": sorted(shuffled[n_train + n_val :], key=lambda c: c.filename),
        }

    def get_stats(self) -> Dict[str, Any]:
        """Returns comprehensive summary statistics of the dataset."""
        total_meds = sum(len(c.raw_medications) for c in self.cases)
        arabic_instr_count = sum(
            1 for c in self.cases for m in c.raw_medications if m.get("Instructions")
        )
        return {
            "dataset_name": "Bilingual Medical Prescriptions (Arabic & English)",
            "source": "Noor Alzoghby (Kaggle)",
            "total_verified_cases": len(self.cases),
            "total_medications": total_meds,
            "medications_with_arabic_instructions": arabic_instr_count,
            "splits": {
                "train_count": len(self._splits["train"]) if self._splits else 0,
                "val_count": len(self._splits["val"]) if self._splits else 0,
                "test_count": len(self._splits["test"]) if self._splits else 0,
            },
        }

    def get_split(self, split_name: str) -> List[BilingualPrescriptionCase]:
        if not self._splits:
            self._generate_splits()
        return self._splits.get(split_name, [])

    def get_case(self, case_id: str) -> Optional[BilingualPrescriptionCase]:
        for c in self.cases:
            if c.case_id == case_id or c.filename == case_id:
                return c
        return None


# Global singleton instance
bilingual_dataset_loader = BilingualDatasetLoader()
