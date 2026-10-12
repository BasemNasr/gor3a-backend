import io
import pytest
from PIL import Image
from backend.app.models.prescription import AnalysisResponse, MedicationItem, PatientInfo, PrescriptionAnalysis
from backend.app.services.pipeline_v2 import PrescriptionPipelineV2, reconcile_dosage_form_and_instructions


def create_dummy_image_bytes() -> bytes:
    img = Image.new("RGB", (400, 500), color=(250, 250, 245))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_reconcile_fixes_hasana_typo_to_injection():
    """Verifies that OCR error 'حصانة' is corrected to 'حقنة' for injections/ampoules."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Becozyme amp 1 ampoule حصانة كل ٣ أيام",
        dose=None,
        frequency=None,
        raw_instructions="حصانة كل ٣ أيام",
        dosage_form="Ampoule",
    )

    assert "حقنة" in instr
    assert "حصانة" not in instr
    assert dose == "1 ampoule (حقنة واحدة)"
    assert instr == "حقنة كل ٣ أيام"


def test_reconcile_fixes_hesa_typo_to_injection():
    """Verifies that OCR typo 'حصة' is corrected to 'حقنة'."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Betolvex amp حصة كل أسبوع",
        dose=None,
        frequency=None,
        raw_instructions="حصة كل أسبوع",
        dosage_form="Ampoule",
    )

    assert "حقنة" in instr
    assert "حصة" not in instr
    assert dose == "1 ampoule (حقنة واحدة)"


@pytest.mark.asyncio
async def test_pipeline_v2_preserves_dietary_and_herbal_advice():
    """Verifies that dietary_and_herbal_advice is preserved through Pipeline V2."""
    class DummyAnalyzer:
        async def analyze_image(self, *args, **kwargs):
            from backend.app.models.prescription import AnalysisMetadata, ImageInfo
            analysis = PrescriptionAnalysis(
                prescription=PatientInfo(),
                medications=[
                    MedicationItem(
                        raw_text="Becozyme amp حصانة كل ٣ أيام",
                        name="Becozyme",
                        instructions="حصانة كل ٣ أيام",
                        status="identified",
                        confidence=0.95,
                    ),
                    MedicationItem(
                        raw_text="Betolvex amp حقنة كل أسبوع",
                        name="Betolvex",
                        instructions="حقنة كل أسبوع",
                        status="identified",
                        confidence=0.95,
                    ),
                    MedicationItem(
                        raw_text="Dimra tab 1x2 قرص بعد الأكل كل ١٢ ساعة",
                        name="Dimra",
                        dose="1 tablet",
                        frequency="twice daily",
                        instructions="قرص بعد الأكل كل ١٢ ساعة",
                        status="identified",
                        confidence=0.95,
                    ),
                    MedicationItem(
                        raw_text="Rubalgine cream دهان ٣ مرات",
                        name="Rubalgine",
                        instructions="دهان ٣ مرات",
                        status="identified",
                        confidence=0.95,
                    ),
                ],
                dietary_and_herbal_advice="معلقة كبيرة على كوب لبن فاتر بعد الفطار",
                overall_confidence=0.95,
            )
            metadata = AnalysisMetadata(
                provider="mock",
                model="mock-vlm",
                processing_time_ms=50,
                image_info=ImageInfo(filename="test.jpg", size_bytes=1024, width=400, height=500, format="JPEG"),
            )
            return AnalysisResponse(analysis=analysis, metadata=metadata)

    pipeline = PrescriptionPipelineV2(analyzer=DummyAnalyzer())
    res = await pipeline.process_prescription(
        image_bytes=create_dummy_image_bytes(),
        provider_name="mock",
    )

    assert res.analysis.dietary_and_herbal_advice == "معلقة كبيرة على كوب لبن فاتر بعد الفطار"
    assert len(res.analysis.medications) == 4

    # Check Becozyme injection fix
    becozyme = res.analysis.medications[0]
    assert "حقنة" in becozyme.instructions
    assert "حصانة" not in becozyme.instructions

    # Check Dimra tablet
    dimra = res.analysis.medications[2]
    assert "قرص" in dimra.instructions


def test_reconcile_tablet_prevents_injection_hallucination():
    """Verifies that tablet and capsule prescriptions never output injection (حقنة) or drops."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Tavanic 500 mg tab. قرص / ٢٤ ساعة (بعد الأكل)",
        dose="1 tab",
        frequency="1x24 hours (after meals)",
        raw_instructions="1 tab 3 times daily (حقنة كل ٣ مرات يومياً)",
        dosage_form="Tablet",
    )
    assert "حقنة" not in (instr or "")
    assert "قرص" in (instr or "")
    assert freq == "once daily (كل ٢٤ ساعة)"


def test_reconcile_capsule_count_and_twice_daily():
    """Verifies that capsule formulation extracts count and 12-hour interval correctly."""
    dose, freq, instr = reconcile_dosage_form_and_instructions(
        raw_text="Emox 500 mg caps. ٢ كبسولة / ١٢ ساعة (بعد الأكل)",
        dose=None,
        frequency="1x12 hours",
        raw_instructions="٢ كبسولة / ١٢ ساعة (بعد الأكل)",
        dosage_form="Capsule",
    )
    assert dose == "2 capsule"
    assert freq == "twice daily (كل ١٢ ساعة)"
    assert "حقنة" not in (instr or "")


def test_extract_duration_from_directions():
    """Verifies that duration is properly extracted from Arabic directions."""
    from backend.app.services.pipeline_v2 import extract_duration_from_directions

    dur1 = extract_duration_from_directions(
        duration=None,
        raw_text="Septazol ٦ سم كل ٨ ساعات لمدة ٥ أيام",
        instructions="٦ سم كل ٨ ساعات لمدة ٥ أيام",
    )
    assert dur1 == "5 days (لمدة ٥ أيام)"

    dur2 = extract_duration_from_directions(
        duration=None,
        raw_text="Augmentin 5 ml 1x2 لمدة أسبوع",
        instructions="5 ml twice daily",
    )
    assert dur2 == "1 week (لمدة أسبوع)"


def test_normalize_pediatric_prescription_drugs():
    """Verifies that Septazole, Respipect, and Limitless Fortalase are accurately identified from OCR variants."""
    from backend.app.services.normalizer import drug_normalizer

    # 1. Septazole from 'Sejotazal' with liquid dose '6 ml'
    res1 = drug_normalizer.normalize(
        raw_text="R v Sejotazal v 6 ml كل ٨ ساعات لمدة ٥ أيام",
        predicted_name="Sejotazal",
        extracted_strength=None,
        initial_confidence=0.85,
    )
    assert res1.confirmed_drug is not None
    assert "Septazole" in res1.confirmed_drug.name
    assert res1.confirmed_drug.dosage_form == "Suspension"

    # 2. Respipect from 'Respicleps'
    res2 = drug_normalizer.normalize(
        raw_text="Respicleps v",
        predicted_name="Respicleps",
        extracted_strength=None,
        initial_confidence=0.85,
    )
    assert res2.confirmed_drug is not None
    assert "Respipect" in res2.confirmed_drug.name

    # 3. Limitless Fortalase from 'Limetless Fortiless'
    res3 = drug_normalizer.normalize(
        raw_text="Limetless Fortiless v",
        predicted_name="Limetless Fortiless",
        extracted_strength=None,
        initial_confidence=0.85,
    )
    assert res3.confirmed_drug is not None
    assert "Limitless Fortalase" in res3.confirmed_drug.name


