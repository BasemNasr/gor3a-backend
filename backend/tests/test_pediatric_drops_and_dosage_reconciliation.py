import io
import pytest
from PIL import Image

from backend.app.drugs.clinical_knowledge import (
    get_clinical_knowledge,
    infer_prescription_overall_diagnosis,
)
from backend.app.models.prescription import (
    AnalysisMetadata,
    AnalysisResponse,
    ImageInfo,
    MedicationItem,
    PatientInfo,
    PrescriptionAnalysis,
)
from backend.app.services.normalizer import drug_normalizer
from backend.app.services.pipeline_v2 import (
    PrescriptionPipelineV2,
    extract_duration_from_directions,
    reconcile_dosage_form_and_instructions,
    synthesize_clinical_instruction,
)


def create_dummy_image_bytes() -> bytes:
    img = Image.new("RGB", (400, 500), color=(250, 250, 245))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_reconcile_atlabacillus_drops_dosage_and_duration():
    """Atlabacillus drop: ٥ نقط يومياً لمدة اسبوعين -> 5 drops once daily for 2 weeks."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
        dose="٢ سم",  # Simulated OCR/VLM dummy hallucination
        frequency="كل ٨ ساعات",  # Simulated OCR/VLM dummy hallucination
        raw_instructions="٥ نقط يومياً لمدة اسبوعين",
        dosage_form="Drops",
    )
    dur = extract_duration_from_directions(
        duration=None,
        raw_text="Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
        instructions=instr,
    )

    assert dose == "5 drops (٥ نقط)"
    assert freq == "once daily (يومياً)"
    assert dur == "2 weeks (لمدة أسبوعين)"
    assert "سم" not in (dose or "")
    assert "٨ ساعات" not in (freq or "")


def test_reconcile_cetal_drops_half_dropper_and_prn():
    """Cetal drop: ١/٢ قطارة عند اللزوم لمدة ٥ أيام -> 1/2 dropper as needed for 5 days."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Cetul drop ١/٢ قطارة عند اللزوم لمدة ٥ أيام",
        dose="١ سم",  # Simulated OCR dummy
        frequency="كل ٨ ساعات",  # Simulated OCR dummy
        raw_instructions="١/٢ قطارة عند اللزوم لمدة ٥ أيام",
        dosage_form="Drops",
    )
    dur = extract_duration_from_directions(
        duration=None,
        raw_text="Cetul drop ١/٢ قطارة عند اللزوم لمدة ٥ أيام",
        instructions=instr,
    )

    assert dose == "1/2 dropper (١/٢ قطارة)"
    assert "عند اللزوم" in (freq or "")
    assert dur == "5 days (لمدة ٥ أيام)"
    assert "سم" not in (dose or "")


def test_reconcile_enrich_drops_alternate_days_and_one_month():
    """Enrich drops: ١/٢ قطارة يوم بعد يوم لمدة شهر -> 1/2 dropper alternate days for 1 month."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Enrich drops ١/٢ قطارة يوم بعد يوم لمدة شهر",
        dose="٢ سم",  # Simulated OCR dummy
        frequency="كل ٨ ساعات",  # Simulated OCR dummy
        raw_instructions="١/٢ قطارة يوم بعد يوم لمدة شهر",
        dosage_form="Drops",
    )
    dur = extract_duration_from_directions(
        duration=None,
        raw_text="Enrich drops ١/٢ قطارة يوم بعد يوم لمدة شهر",
        instructions=instr,
    )

    assert dose == "1/2 dropper (١/٢ قطارة)"
    assert freq == "every other day (يوم بعد يوم)"
    assert dur == "1 month (لمدة شهر)"
    assert "سم" not in (dose or "")
    assert "٨ ساعات" not in (freq or "")


def test_reconcile_cevilene_drops_dosage_and_duration():
    """Cevilin drop: ٥ نقط يومياً لمدة شهر -> 5 drops once daily for 1 month."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Cevilin drop ٥ نقط يومياً لمدة شهر",
        dose="٢ سم",  # Simulated OCR dummy
        frequency="كل ٨ ساعات",  # Simulated OCR dummy
        raw_instructions="٥ نقط يومياً لمدة شهر",
        dosage_form="Drops",
    )
    dur = extract_duration_from_directions(
        duration=None,
        raw_text="Cevilin drop ٥ نقط يومياً لمدة شهر",
        instructions=instr,
    )

    assert dose == "5 drops (٥ نقط)"
    assert freq == "once daily (يومياً)"
    assert dur == "1 month (لمدة شهر)"
    assert "سم" not in (dose or "")


def test_normalizer_pediatric_prescription_safety_and_aliases():
    """Verifies that Cetul maps to Cetal, Cervilin maps to Cevilene (not Digoxine), and drops are boosted."""
    # 1. Cetul -> Cetal (Drops)
    res_cetal = drug_normalizer.normalize(
        raw_text="Cetul drop ١/٢ قطارة",
        predicted_name="Cetul",
        initial_confidence=0.85,
    )
    assert res_cetal.confirmed_drug is not None
    assert "Cetal" in res_cetal.confirmed_drug.name
    assert "Drops" in res_cetal.confirmed_drug.dosage_form

    # 2. Cervilin / Cevilin -> Cevilene (Drops - Vitamin C), NEVER Cardixin (Digoxine)
    res_cevilin = drug_normalizer.normalize(
        raw_text="Cervilin drop ٥ نقط يومياً",
        predicted_name="Cervilin",
        initial_confidence=0.85,
    )
    assert res_cevilin.confirmed_drug is not None
    assert "Cevilene" in res_cevilin.confirmed_drug.name
    assert res_cevilin.confirmed_drug.generic_name == "VITAMIN C"
    # Verify Cardixin is definitely not confirmed
    assert "Cardixin" not in res_cevilin.confirmed_drug.name
    assert "DIGOXINE" not in res_cevilin.confirmed_drug.generic_name

    # 3. Atlabacillus
    res_atla = drug_normalizer.normalize(
        raw_text="Atlabacillus drop ٥ نقط",
        predicted_name="Atlabacillus",
        initial_confidence=0.85,
    )
    assert res_atla.confirmed_drug is not None
    assert "Atlabacillus" in res_atla.confirmed_drug.name

    # 4. Enrich
    res_enrich = drug_normalizer.normalize(
        raw_text="Enrich drops ١/٢ قطارة",
        predicted_name="Enrich",
        initial_confidence=0.85,
    )
    assert res_enrich.confirmed_drug is not None
    assert "Enrich" in res_enrich.confirmed_drug.name
    assert "Drops" in res_enrich.confirmed_drug.dosage_form


def test_infer_prescription_overall_diagnosis_diarrhea_anemia():
    """Verifies that Atlabacillus + Enrich + Cetal + Cevilene infers Diarrhea & Iron Deficiency Anemia."""
    meds = [
        {"name": "Atlabacillus", "active_ingredient": "Lactobacillus rhamnosus", "raw_text": "Atlabacillus drop"},
        {"name": "Cetal", "active_ingredient": "Paracetamol", "raw_text": "Cetul drop"},
        {"name": "Enrich", "active_ingredient": "Ferric Hydroxide Polymaltose", "raw_text": "Enrich drops"},
        {"name": "Cevilene", "active_ingredient": "Vitamin C", "raw_text": "Cevilin drop"},
    ]
    diag = infer_prescription_overall_diagnosis(meds)
    assert "إسهال" in diag or "معوية" in diag
    assert "أنيميا" in diag


@pytest.mark.asyncio
async def test_full_pipeline_v2_end_to_end_pediatric_prescription():
    """Simulates end-to-end processing of this exact prescription through Pipeline V2."""
    class MockPediatricAnalyzer:
        async def analyze_image(self, *args, **kwargs):
            analysis = PrescriptionAnalysis(
                prescription=PatientInfo(patient_age="1y", patient_weight_kg=9.5),
                medications=[
                    MedicationItem(
                        raw_text="Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
                        name="Atlabacillus",
                        instructions="٥ نقط يومياً لمدة اسبوعين",
                        confidence=0.92,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Cetul drop ١/٢ قطارة عند اللزوم لمدة ٥ أيام",
                        name="Cetul",
                        instructions="١/٢ قطارة عند اللزوم لمدة ٥ أيام",
                        confidence=0.90,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Enrich drops ١/٢ قطارة يوم بعد يوم لمدة شهر",
                        name="Enrich",
                        instructions="١/٢ قطارة يوم بعد يوم لمدة شهر",
                        confidence=0.92,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Cevilin drop ٥ نقط يومياً لمدة شهر",
                        name="Cevilin",
                        instructions="٥ نقط يومياً لمدة شهر",
                        confidence=0.88,
                        status="identified",
                    ),
                ],
                dietary_and_herbal_advice=None,
                overall_confidence=0.90,
            )
            metadata = AnalysisMetadata(
                provider="gemini",
                model="gemini-2.5-flash",
                processing_time_ms=120,
                image_info=ImageInfo(filename="dawi_prescription.jpg", size_bytes=2048, width=600, height=800, format="JPEG"),
            )
            return AnalysisResponse(analysis=analysis, metadata=metadata)

    pipeline = PrescriptionPipelineV2(analyzer=MockPediatricAnalyzer())
    res = await pipeline.process_prescription(image_bytes=create_dummy_image_bytes())

    assert len(res.analysis.medications) == 4

    # Med 1: Atlabacillus
    m1 = res.analysis.medications[0]
    assert m1.name == "Atlabacillus"
    assert m1.dose == "5 drops (٥ نقط)"
    assert m1.frequency == "once daily (يومياً)"
    assert m1.duration == "2 weeks (لمدة أسبوعين)"

    # Med 2: Cetal
    m2 = res.analysis.medications[1]
    assert m2.name == "Cetal"
    assert m2.dose == "1/2 dropper (١/٢ قطارة)"
    assert "عند اللزوم" in (m2.frequency or "")
    assert m2.duration == "5 days (لمدة ٥ أيام)"

    # Med 3: Enrich
    m3 = res.analysis.medications[2]
    assert m3.name == "Enrich"
    assert m3.dose == "1/2 dropper (١/٢ قطارة)"
    assert m3.frequency == "every other day (يوم بعد يوم)"
    assert m3.duration == "1 month (لمدة شهر)"

    # Med 4: Cevilene (not Digoxine / Cardixin)
    m4 = res.analysis.medications[3]
    assert m4.name == "Cevilene"
    assert m4.dose == "5 drops (٥ نقط)"
    assert m4.frequency == "once daily (يومياً)"
    assert m4.duration == "1 month (لمدة شهر)"
    assert m4.lasa_warning is None  # No false alarm LASA warning for Cardixin!

    # Overall Diagnosis
    assert "أنيميا" in res.analysis.overall_likely_diagnosis


def test_extract_duration_contradiction_resolution():
    """Tests that extract_duration_from_directions resolves conflicts between clean_dur and raw_text."""
    # When LLM says 2 weeks, but raw_text clearly says 'لمدة شهر'
    dur = extract_duration_from_directions(
        duration="2 weeks (لمدة أسبوعين)",
        raw_text="Cevilin drop ٥ نقط يومياً لمدة شهر",
        instructions="٥ نقط يومياً لمدة شهر",
    )
    assert dur == "1 month (لمدة شهر)"

    # When LLM says 1 month, but raw_text clearly says 'لمدة اسبوعين'
    dur2 = extract_duration_from_directions(
        duration="1 month (لمدة شهر)",
        raw_text="Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
        instructions="٥ نقط يومياً لمدة اسبوعين",
    )
    assert dur2 == "2 weeks (لمدة أسبوعين)"


@pytest.mark.asyncio
async def test_cross_medication_harmonization_when_cevilin_leaked_duration():
    """
    Simulates a vision model leaking '2 weeks' from Line 1 onto Line 4 (Cevilin),
    and tests that pipeline_v2 harmonizes Cevilene's duration to 1 month based on Enrich drops.
    """
    class MockLeakingAnalyzer:
        async def analyze_image(self, *args, **kwargs) -> AnalysisResponse:
            analysis = PrescriptionAnalysis(
                prescription=PatientInfo(patient_age="1y", patient_weight_kg=8.5),
                medications=[
                    MedicationItem(
                        raw_text="Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
                        name="Atlabacillus",
                        duration="2 weeks (لمدة أسبوعين)",
                        confidence=0.95,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Enrich drops ١/٢ قطارة يوم بعد يوم لمدة شهر",
                        name="Enrich",
                        duration="1 month (لمدة شهر)",
                        confidence=0.92,
                        status="identified",
                    ),
                    MedicationItem(
                        # LLM mistakenly duplicated 'لمدة اسبوعين' from Atlabacillus
                        raw_text="Cevilin drop ٥ نقط يومياً لمدة اسبوعين",
                        name="Cevilin",
                        duration="2 weeks (لمدة أسبوعين)",
                        confidence=0.88,
                        status="identified",
                    ),
                ],
                dietary_and_herbal_advice=None,
                overall_confidence=0.91,
            )
            metadata = AnalysisMetadata(
                provider="gemini",
                model="gemini-2.5-flash",
                processing_time_ms=100,
                image_info=ImageInfo(filename="rx.jpg", size_bytes=1024, width=600, height=800, format="JPEG"),
            )
            return AnalysisResponse(analysis=analysis, metadata=metadata)

    pipeline = PrescriptionPipelineV2(analyzer=MockLeakingAnalyzer())
    res = await pipeline.process_prescription(image_bytes=create_dummy_image_bytes())

    cevilin = res.analysis.medications[2]
    assert cevilin.name == "Cevilene"
    # Should be harmonized to 1 month
    assert cevilin.duration == "1 month (لمدة شهر)"
    assert cevilin.lasa_warning is None


@pytest.mark.asyncio
async def test_ent_tonsillitis_otitis_prescription_normalization_and_diagnosis():
    """Tests the adult ENT prescription: Ceftriaxone, Cipromega, Trimed Flu, Mucotec, Dongeel spray."""
    class MockENTAnalyzer:
        async def analyze_image(self, *args, **kwargs) -> AnalysisResponse:
            analysis = PrescriptionAnalysis(
                prescription=PatientInfo(patient_age="35y", patient_weight_kg=78.0),
                medications=[
                    MedicationItem(
                        raw_text="Ceftriaxone 1gm / 24 hs بعد عمل اختبار حساسية",
                        name="Ceftriaxone",
                        dose="1 injection (١ حقنة)",
                        frequency="once daily (كل ٢٤ ساعة)",
                        instructions="١ حقنة عضل / ٢٤ ساعة بعد عمل اختبار حساسية",
                        confidence=0.96,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Cipromega gtt نقط للأذن / ١٢ ساعة لمدة اسبوع",
                        name="Cipromega",
                        dose="3 drops (٣ نقط)",
                        frequency="twice daily (كل ١٢ ساعة)",
                        duration="1 week (لمدة أسبوع)",
                        instructions="نقط للأذن كل ١٢ ساعة لمدة أسبوع",
                        confidence=0.95,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Trimed Flu t قرص / ٨ ساعات",
                        name="Trimed Flu",
                        dose="1 tablet (قرص واحد)",
                        frequency="3 times daily (كل ٨ ساعات)",
                        instructions="قرص بعد الأكل كل ٨ ساعات",
                        confidence=0.94,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Mucotec 300mg كبسولة / ١٢ ساعة",
                        name="Mucotec",
                        dose="1 capsule (كبسولة واحدة)",
                        frequency="twice daily (كل ١٢ ساعة)",
                        instructions="كبسولة كل ١٢ ساعة بعد الأكل",
                        confidence=0.95,
                        status="identified",
                    ),
                    MedicationItem(
                        raw_text="Dongeel oral spray بخاخ بالفم ٤ مرات يومياً",
                        name="Dongeel",
                        dose="Oral spray (بخاخ بالفم)",
                        frequency="4 times daily (٤ مرات يومياً)",
                        instructions="بخاخ بالفم ٤ مرات يومياً",
                        confidence=0.92,
                        status="identified",
                    ),
                ],
                dietary_and_herbal_advice=None,
                overall_confidence=0.94,
            )
            metadata = AnalysisMetadata(
                provider="local_vlm",
                model="qwen2.5vl:3b",
                processing_time_ms=2500,
                image_info=ImageInfo(filename="dawi_ent_rx.jpg", size_bytes=1024, width=600, height=800, format="JPEG"),
            )
            return AnalysisResponse(analysis=analysis, metadata=metadata)

    pipeline = PrescriptionPipelineV2(analyzer=MockENTAnalyzer())
    res = await pipeline.process_prescription(image_bytes=create_dummy_image_bytes())

    assert len(res.analysis.medications) == 5
    m_names = [m.name for m in res.analysis.medications]
    assert "Ceftriaxone" in m_names[0]
    assert "Cipromega" in m_names[1]
    assert "Trimed Flu" in m_names[2]
    assert "Mucotec" in m_names[3]
    assert "Dongeel" in m_names[4]

    # Verify overall diagnosis inference
    assert "اللوزتين" in res.analysis.overall_likely_diagnosis
    assert "الأذن الوسطى" in res.analysis.overall_likely_diagnosis


