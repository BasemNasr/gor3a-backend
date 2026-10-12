import json
import logging
from typing import Any, Dict, List, Optional

from ..dataset.bilingual_loader import (
    BilingualDatasetLoader,
    BilingualPrescriptionCase,
    bilingual_dataset_loader,
)

logger = logging.getLogger(__name__)


class FewShotPromptLearner:
    """
    Selects and compiles authentic Egyptian bilingual prescription exemplars from the
    training split to inject as few-shot in-context learning demonstrations into multimodal prompts.
    """

    def __init__(self, loader: Optional[BilingualDatasetLoader] = None):
        self.loader = loader or bilingual_dataset_loader

    def select_curated_exemplars(self, k: int = 3) -> List[BilingualPrescriptionCase]:
        """
        Selects k diverse training cases demonstrating varied handwriting patterns:
        - Arabic instructions (e.g., بعد الأكل, قبل الأكل)
        - Frequency patterns (e.g., 1x3, مرتين يومياً)
        - Egyptian brand names and strengths
        """
        train_cases = self.loader.get_split("train")
        if not train_cases:
            return []

        # Find cases with rich Arabic instructions and multi-medication lists
        scored_cases = []
        for c in train_cases:
            score = len(c.raw_medications) * 2
            for m in c.raw_medications:
                if m.get("Instructions"):
                    score += 3
                if m.get("Dosage"):
                    score += 2
                if m.get("Frequency"):
                    score += 2
            scored_cases.append((c, score))

        scored_cases.sort(key=lambda x: x[1], reverse=True)
        return [c for c, _ in scored_cases[:k]]

    def generate_few_shot_prompt_text(self, k: int = 3) -> str:
        """
        Generates in-context learning demonstration text to append to the system prompt.
        """
        exemplars = self.select_curated_exemplars(k=k)
        if not exemplars:
            return ""

        parts = [
            "\n---",
            "### 📖 FEW-SHOT IN-CONTEXT EXAMPLES FROM EGYPTIAN CLINICS:",
            "Below are verified examples demonstrating how to transcribe Arabic instructions,",
            "abbreviations, and Egyptian medication names:\n",
        ]

        for i, ex in enumerate(exemplars, 1):
            analysis = ex.to_prescription_analysis()
            parts.append(f"#### Example {i} ({ex.filename}):")
            for m in analysis.medications:
                parts.append(
                    f"- **Medication:** {m.name} | **Dose:** {m.dose or 'null'} | "
                    f"**Frequency:** {m.frequency or 'null'} | **Instructions (AR):** {m.instructions or 'null'}"
                )
            parts.append("")

        parts.append("---")
        return "\n".join(parts)


few_shot_learner = FewShotPromptLearner()
