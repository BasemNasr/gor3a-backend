import pytest
from backend.app.drugs.clinical_knowledge import infer_prescription_overall_diagnosis
from backend.app.models.prescription import MedicationItem
from backend.app.services.normalizer import DrugNormalizer
from backend.app.services.pipeline_v2 import (
    PrescriptionPipelineV2,
    extract_duration_from_directions,
    reconcile_dosage_form_and_instructions,
    synthesize_clinical_instruction,
)


def test_vacation_medications_normalization_and_forms():
    normalizer = DrugNormalizer()
    
    # 1. Fenistil drops
    res_fen = normalizer.normalize("Fenistil drops", predicted_name="Fenistil", initial_confidence=0.95)
    assert res_fen.confirmed_drug is not None
    assert res_fen.confirmed_drug.name == "Fenistil"
    assert res_fen.confirmed_drug.dosage_form == "Drops"

    # 2. Cough sed S-pp (Suppositories)
    res_cough = normalizer.normalize("Cough sed S-pp", predicted_name="Cough sed S-pp", initial_confidence=0.95)
    assert res_cough.confirmed_drug is not None
    assert res_cough.confirmed_drug.name == "Coughsed"
    assert res_cough.confirmed_drug.dosage_form == "Suppository"

    # 3. Off spray
    res_off = normalizer.normalize("off spray", predicted_name="off spray", initial_confidence=0.95)
    assert res_off.confirmed_drug is not None
    assert res_off.confirmed_drug.name == "Off!"
    assert res_off.confirmed_drug.dosage_form == "Spray"

    # 4. Solo lotion أو Bringo
    res_solo = normalizer.normalize("Solo lotion أو Bringo", predicted_name="Solo lotion", initial_confidence=0.95)
    assert res_solo.confirmed_drug is not None
    assert res_solo.confirmed_drug.name == "Solo Lotion"
    assert res_solo.confirmed_drug.dosage_form == "Topical"


def test_vacation_dosage_reconciliation_purges_dummy_values():
    # Test Fenistil drops: must purge '1 tab' and '500mg', extract 7 drops
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Fenistil drops - ٧ نقط بالفم عند الحكة والهرش",
        dose="1 tab",  # dummy hallucination
        frequency="1x3",  # dummy hallucination
        raw_instructions="1 tab 1x3 for 5 days بعد الأكل",  # dummy hallucination
        dosage_form="Drops",
    )
    assert "tab" not in str(dose).lower()
    assert "7 drops" in str(dose).lower() or "٧ نقط" in str(dose)
    assert "بعد الأكل" not in str(instr)
    assert "حكة" in str(freq) or "هرش" in str(freq) or "اللزوم" in str(freq)

    # Test Coughsed: must be suppository, not tab
    dose_c, freq_c, instr_c = reconcile_dosage_form_and_instructions(
        raw_text="Cough sed S-pp - لبوسة عند الكحة",
        dose="1 tab",
        frequency="1x3",
        raw_instructions="1 tab 1x3 بعد الأكل",
        dosage_form="Suppository",
    )
    assert "tab" not in str(dose_c).lower()
    assert "suppository" in str(dose_c).lower() or "لبوسة" in str(dose_c)
    assert "بعد الأكل" not in str(instr_c)

    # Test Off spray: must be spray, not tab
    dose_s, freq_s, instr_s = reconcile_dosage_form_and_instructions(
        raw_text="off spray - كل ساعتين على البحر",
        dose="1 tab",
        frequency="1x3",
        raw_instructions="1 tab 1x3 بعد الأكل",
        dosage_form="Spray",
    )
    assert "tab" not in str(dose_s).lower()
    assert "spray" in str(dose_s).lower() or "بخة" in str(dose_s)
    assert "بعد الأكل" not in str(instr_s)
    assert "البحر" in str(instr_s) or "ساعتين" in str(freq_s)

    # Test Solo lotion: must be topical, not tab
    dose_l, freq_l, instr_l = reconcile_dosage_form_and_instructions(
        raw_text="Solo lotion أو Bringo - دهان لحمو النيل",
        dose="1 tab",
        frequency="1x3",
        raw_instructions="1 tab 1x3 بعد الأكل",
        dosage_form="Topical",
    )
    assert "tab" not in str(dose_l).lower()
    assert "topical" in str(dose_l).lower() or "دهان" in str(dose_l)
    assert "بعد الأكل" not in str(instr_l)
    assert "حمو النيل" in str(instr_l)


def test_vacation_duration_anti_hallucination():
    # If the doctor did not physically write days/weeks in raw_text, dummy '5 days' must be stripped
    dur = extract_duration_from_directions(
        duration="5 days",
        raw_text="Fenistil drops - ٧ نقط بالفم عند الحكة والهرش",
        instructions="٧ نقط بالفم عند الحكة والهرش",
    )
    assert dur is None


def test_vacation_kit_overall_diagnosis():
    meds = [
        {"name": "Fenistil", "raw_text": "Fenistil drops - ٧ نقط بالفم عند الحكة والهرش"},
        {"name": "Coughsed", "raw_text": "Cough sed S-pp - لبوسة عند الكحة"},
        {"name": "Off!", "raw_text": "off spray - كل ساعتين على البحر"},
        {"name": "Solo Lotion", "raw_text": "Solo lotion أو Bringo - دهان لحمو النيل"},
    ]
    diag = infer_prescription_overall_diagnosis(meds)
    assert diag is not None
    assert "المصيف" in diag or "Beach" in diag
    assert "حمو النيل" in diag or "الحشرات" in diag
