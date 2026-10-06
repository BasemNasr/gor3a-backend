import json
import re
from pathlib import Path
from typing import Dict, List, Tuple
from .schema import GroundTruthAnnotation

EGYPTIAN_PHONE_PATTERN = re.compile(r"(01[0125]\d{8})")
EGYPTIAN_NATIONAL_ID_PATTERN = re.compile(r"([23]\d{13})")


def validate_ground_truth_dict(data: dict) -> Tuple[bool, List[str]]:
    """
    Validates a ground truth annotation dictionary against medical consistency
    and privacy/anonymization guidelines.
    """
    errors: List[str] = []

    try:
        annotation = GroundTruthAnnotation.model_validate(data)
    except Exception as exc:
        return False, [f"Schema validation failure: {exc}"]

    # 1. Anonymization Check
    raw_str = json.dumps(data)
    phone_matches = EGYPTIAN_PHONE_PATTERN.findall(raw_str)
    if phone_matches:
        errors.append(f"PII detected: Egyptian phone number found in data: {phone_matches}")

    national_id_matches = EGYPTIAN_NATIONAL_ID_PATTERN.findall(raw_str)
    if national_id_matches:
        errors.append(f"PII detected: Egyptian national ID pattern found: {national_id_matches}")

    # 2. Clinical Consistency Checks
    for idx, med in enumerate(annotation.medications):
        # If status is unreadable, name must be None
        if med.status == "unreadable" and med.name is not None:
            errors.append(
                f"Medication #{idx+1}: status is 'unreadable' but name is '{med.name}'. "
                "Unreadable items must have name=None per non-guessing policy."
            )

        # If status is identified, name should not be None
        if med.status == "identified" and not med.name:
            errors.append(f"Medication #{idx+1}: status is 'identified' but name is empty.")

        # If confidence is high, status shouldn't be unreadable
        if med.status == "unreadable" and med.confidence > 0.4:
            errors.append(
                f"Medication #{idx+1}: status is 'unreadable' but confidence is {med.confidence} (>0.40)."
            )

    return len(errors) == 0, errors


def validate_ground_truth_file(file_path: Path) -> Tuple[bool, List[str]]:
    """Validates a ground_truth.json file on disk."""
    if not file_path.exists():
        return False, [f"File not found: {file_path}"]

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as exc:
        return False, [f"JSON decoding error: {exc}"]

    return validate_ground_truth_dict(data)
