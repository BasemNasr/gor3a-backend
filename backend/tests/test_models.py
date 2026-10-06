import pytest
from pydantic import ValidationError
from backend.app.models.prescription import (
    MedicationItem,
    PatientInfo,
    PrescriptionAnalysis,
)


def test_patient_info_valid():
    p = PatientInfo(patient_age="8 months", patient_weight_kg=8.14)
    assert p.patient_age == "8 months"
    assert p.patient_weight_kg == 8.14

    p_none = PatientInfo()
    assert p_none.patient_age is None
    assert p_none.patient_weight_kg is None


def test_medication_item_valid():
    med = MedicationItem(
        raw_text="Flagyl 125mg/5ml susp",
        name="Flagyl",
        active_ingredient="Metronidazole",
        strength="125mg/5ml",
        dose="5 ml",
        frequency="tid",
        duration="5 days",
        confidence=0.95,
        status="identified",
    )
    assert med.name == "Flagyl"
    assert med.confidence == 0.95
    assert med.status == "identified"


def test_medication_item_uncertainty_safety():
    """Validates that uncertain fields accept null and status can be 'uncertain' or 'unreadable'."""
    med = MedicationItem(
        raw_text="Flaj... unreadable line",
        name=None,
        active_ingredient=None,
        strength=None,
        dose=None,
        frequency=None,
        duration=None,
        confidence=0.25,
        status="unreadable",
    )
    assert med.name is None
    assert med.status == "unreadable"


def test_medication_item_invalid_confidence():
    with pytest.raises(ValidationError):
        MedicationItem(
            raw_text="Test",
            confidence=1.5,  # Must be <= 1.0
            status="identified",
        )


def test_medication_item_invalid_status():
    with pytest.raises(ValidationError):
        MedicationItem(
            raw_text="Test",
            confidence=0.8,
            status="guessed",  # Not a valid literal
        )


def test_prescription_analysis_schema():
    analysis = PrescriptionAnalysis(
        prescription=PatientInfo(patient_age="2 years", patient_weight_kg=12.0),
        medications=[
            MedicationItem(
                raw_text="Cetal syrup",
                name="Cetal",
                active_ingredient="Paracetamol",
                dose="5 ml",
                confidence=0.9,
                status="identified",
            )
        ],
        uncertain_items=["Frequency handwriting is unclear"],
        overall_confidence=0.88,
    )
    assert len(analysis.medications) == 1
    assert len(analysis.uncertain_items) == 1
    assert analysis.overall_confidence == 0.88
