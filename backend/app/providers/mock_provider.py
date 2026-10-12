import asyncio
import json
import time
from typing import Any, Dict, List, Optional
from ..models.prescription import TokenUsage
from .base import ProviderResult, VisionProvider


# Predefined responses for each benchmark case
MOCK_CASE_RESPONSES = {
    "rx_001": {
        "prescription": {"patient_age": "8 months", "patient_weight_kg": 8.14},
        "medications": [
            {
                "raw_text": "Flagyl 125mg/5ml susp 3.5 ml tid x 5d",
                "name": "Flagyl",
                "active_ingredient": "Metronidazole",
                "strength": "125mg/5ml",
                "dose": "3.5 ml",
                "frequency": "tid (every 8 hours)",
                "duration": "5 days",
                "instructions": "after feeding",
                "confidence": 0.94,
                "status": "identified"
            },
            {
                "raw_text": "Cetal drops 100mg/ml 10 drops prn",
                "name": "Cetal",
                "active_ingredient": "Paracetamol",
                "strength": "100mg/ml",
                "dose": "10 drops",
                "frequency": "upon fever / prn (عند اللزوم)",
                "duration": None,
                "instructions": "for temperature above 38.5C",
                "confidence": 0.89,
                "status": "identified"
            },
            {
                "raw_text": "Antinal susp 1 spoon [blurred]",
                "name": "Antinal",
                "active_ingredient": "Nifuroxazide",
                "strength": None,
                "dose": "1 spoon",
                "frequency": None,
                "duration": None,
                "instructions": None,
                "confidence": 0.52,
                "status": "uncertain"
            }
        ],
        "uncertain_items": [
            "Medication #3 (Antinal) strength is obscured by ink blot",
            "Medication #3 frequency handwriting is blurred; not inferred per clinical safety protocol"
        ],
        "overall_confidence": 0.81
    },
    "rx_002": {
        "prescription": {"patient_age": "32 years", "patient_weight_kg": 74.0},
        "medications": [
            {
                "raw_text": "Curam 1g tab 1 tab q12h pc x 7d",
                "name": "Curam",
                "active_ingredient": "Amoxicillin + Clavulanic acid",
                "strength": "1g",
                "dose": "1 tablet",
                "frequency": "every 12 hours (q12h)",
                "duration": "7 days",
                "instructions": "after meals (pc)",
                "confidence": 0.95,
                "status": "identified"
            },
            {
                "raw_text": "Congestal tab 1 tab tid pc x 5d",
                "name": "Congestal",
                "active_ingredient": "Paracetamol + Pseudoephedrine",
                "strength": None,
                "dose": "1 tablet",
                "frequency": "3 times daily (tid)",
                "duration": "5 days",
                "instructions": "after meals (pc)",
                "confidence": 0.92,
                "status": "identified"
            },
            {
                "raw_text": "Toplexil syrup 10 ml tid",
                "name": "Toplexil",
                "active_ingredient": "Oxomemazine + Guaifenesin",
                "strength": None,
                "dose": "10 ml",
                "frequency": "3 times daily",
                "duration": None,
                "instructions": "before bed / for cough",
                "confidence": 0.90,
                "status": "identified"
            }
        ],
        "uncertain_items": [],
        "overall_confidence": 0.95
    },
    "rx_003": {
        "prescription": {"patient_age": "58 years", "patient_weight_kg": 82.0},
        "medications": [
            {
                "raw_text": "Losec 20mg cap 1 cap daily ac x 14d",
                "name": "Losec",
                "active_ingredient": "Omeprazole",
                "strength": "20mg",
                "dose": "1 capsule",
                "frequency": "once daily",
                "duration": "14 days",
                "instructions": "before breakfast (ac)",
                "confidence": 0.93,
                "status": "identified"
            },
            {
                "raw_text": "Gaviscon susp 10 ml tid pc",
                "name": "Gaviscon",
                "active_ingredient": "Sodium alginate",
                "strength": None,
                "dose": "10 ml",
                "frequency": "3 times daily (tid)",
                "duration": None,
                "instructions": "after meals (pc)",
                "confidence": 0.91,
                "status": "identified"
            }
        ],
        "uncertain_items": [],
        "overall_confidence": 0.92
    },
    "rx_004": {
        "prescription": {"patient_age": "45 years", "patient_weight_kg": None},
        "medications": [
            {
                "raw_text": "Cataflam 50mg tab قرص بعد الأكل عند اللزوم",
                "name": "Cataflam",
                "active_ingredient": "Diclofenac potassium",
                "strength": "50mg",
                "dose": "قرص واحد",
                "frequency": "عند اللزوم",
                "duration": None,
                "instructions": "بعد الأكل",
                "confidence": 0.92,
                "status": "identified"
            },
            {
                "raw_text": "Brufen 400mg tab قرص كل ٨ ساعات بعد الأكل لمدة ٥ أيام",
                "name": "Brufen",
                "active_ingredient": "Ibuprofen",
                "strength": "400mg",
                "dose": "قرص واحد",
                "frequency": "كل ٨ ساعات",
                "duration": "لمدة ٥ أيام",
                "instructions": "بعد الأكل",
                "confidence": 0.90,
                "status": "identified"
            }
        ],
        "uncertain_items": [],
        "overall_confidence": 0.91
    },
    "rx_005": {
        "prescription": {"patient_age": None, "patient_weight_kg": None},
        "medications": [
            {
                "raw_text": "~~~~~~~ [illegible scribble]",
                "name": None,
                "active_ingredient": None,
                "strength": None,
                "dose": None,
                "frequency": None,
                "duration": None,
                "instructions": None,
                "confidence": 0.15,
                "status": "unreadable"
            },
            {
                "raw_text": "Panadol 500mg tab 2 tabs q6h prn",
                "name": "Panadol",
                "active_ingredient": "Paracetamol",
                "strength": "500mg",
                "dose": "2 tablets",
                "frequency": "every 6 hours (q6h)",
                "duration": None,
                "instructions": "for pain / prn",
                "confidence": 0.88,
                "status": "identified"
            }
        ],
        "uncertain_items": [
            "Medication line #1 is completely illegible due to severe ink smearing and scribble; preserved as unreadable"
        ],
        "overall_confidence": 0.52
    }
}


class MockProvider(VisionProvider):
    """
    Mock vision provider for offline benchmarking, test fixtures, and UI previews.
    Supports conservative (safety-first) and aggressive (hallucinating) simulation models.
    """

    @property
    def name(self) -> str:
        return "mock"

    @property
    def display_name(self) -> str:
        return "Offline Mock / Demo Provider"

    @property
    def supported_models(self) -> List[str]:
        return [
            "mock-conservative-v1",
            "mock-aggressive-v1",
        ]

    @property
    def default_model(self) -> str:
        return "mock-conservative-v1"

    def is_configured(self) -> bool:
        return True

    async def analyze(
        self,
        image_bytes: bytes,
        mime_type: str,
        prompt: str,
        model: Optional[str] = None,
    ) -> ProviderResult:
        start_time = time.perf_counter()
        await asyncio.sleep(0.15)
        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        model_name = model or self.default_model

        # Detect case by prompt context or default to rx_001
        case_key = "rx_001"
        for k in MOCK_CASE_RESPONSES:
            if k in prompt or f"test_{k[3:]}" in prompt:
                case_key = k
                break

        # Clone default response
        payload = json.loads(json.dumps(MOCK_CASE_RESPONSES.get(case_key, MOCK_CASE_RESPONSES["rx_001"])))

        # If aggressive model is requested, simulate failure modes
        if "aggressive" in model_name:
            if case_key == "rx_003":
                # Dangerous LASA error: misidentifies Losec as Lasix!
                payload["medications"][0]["name"] = "Lasix"
                payload["medications"][0]["active_ingredient"] = "Furosemide"
                payload["medications"][0]["confidence"] = 0.96
            elif case_key == "rx_005":
                # Hallucinates unreadable text into Amoxil with high confidence
                payload["medications"][0]["name"] = "Amoxil"
                payload["medications"][0]["active_ingredient"] = "Amoxicillin"
                payload["medications"][0]["dose"] = "500mg"
                payload["medications"][0]["confidence"] = 0.86
                payload["medications"][0]["status"] = "identified"
                payload["uncertain_items"] = []

        raw_text = json.dumps(payload, indent=2, ensure_ascii=False)

        return ProviderResult(
            provider=self.name,
            model=model_name,
            raw_text=raw_text,
            parsed_json=payload,
            usage=TokenUsage(prompt_tokens=1200, completion_tokens=280, total_tokens=1480),
            latency_ms=elapsed_ms,
        )
