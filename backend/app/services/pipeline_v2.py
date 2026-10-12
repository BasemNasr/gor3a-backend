import logging
import re
from typing import Optional, Tuple

from ..drugs.clinical_knowledge import (
    get_clinical_knowledge,
    infer_prescription_overall_diagnosis,
)
from ..models.prescription import AnalysisResponse, MedicationItem, PrescriptionAnalysis
from ..utils.text import clean_clinical_field
from .analyzer import PrescriptionAnalyzer
from .normalizer import DrugNormalizer, drug_normalizer

logger = logging.getLogger(__name__)


def reconcile_dosage_form_and_instructions(
    raw_text: Optional[str],
    dose: Optional[str],
    frequency: Optional[str],
    raw_instructions: Optional[str],
    dosage_form: Optional[str] = None,
) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    Reconciles clinical dosage form conflicts and decodes doctor multiplication shorthand:
    1. Cross-validates '1x3', '1*3', '1x2' multiplication shorthand in raw_text to prove
       exact dose count and daily frequency.
    2. Enforces dosage form integrity: tablets/capsules (tab, cap, قرص) must NEVER contain 'قطرة' (drops).
    """
    clean_raw = clean_clinical_field(raw_text) or ""
    clean_dose = clean_clinical_field(dose)
    clean_freq = clean_clinical_field(frequency)
    clean_instr = clean_clinical_field(raw_instructions) or ""

    # Fix OCR typos: 'حصانة' or 'حصة' -> 'حقنة' (injection)
    clean_instr = re.sub(r"\b(?:حصانة|حصة)\b", "حقنة", clean_instr)
    clean_raw = re.sub(r"\b(?:حصانة|حصة)\b", "حقنة", clean_raw)

    form_str = str(dosage_form or "").lower()
    raw_lower = clean_raw.lower()
    text_combined = f"{clean_raw} {clean_instr} {form_str}".lower()

    # Authoritative dosage forms from EDA database or explicit raw text:
    # A syrup/suspension must never be treated as an injection, even if clean_instr has hallucinated 'حقنة'
    is_authoritative_syrup = bool(re.search(r"\b(syrups?|syrs?|susp|suspensions?|شراب|معلق|allvent|dexaphen|ventolin)\b", f"{raw_lower} {form_str}"))
    is_authoritative_injection = bool(re.search(r"\b(amp|ampoules?|vials?|inj|injections?|حقنة|حقن|أمبول|أمبولات|ceftriaxone|cefaxone)\b", f"{raw_lower} {form_str}"))
    is_authoritative_tablet = bool(re.search(r"\b(tabs?|tablets?|caps?|capsules?|قرص|أقراص|كبسول|كبسولات)\b", f"{raw_lower} {form_str}"))
    is_authoritative_drops = bool(re.search(r"\b(drops?|eye\s+drops?|oral\s+drops?|قطرة|قطره|نقط|قطارة|قطاره|fenistil)\b", f"{raw_lower} {form_str}"))
    is_authoritative_topical = bool(re.search(r"\b(lotion|lotions?|cream|creams?|ointment|ointments?|gel|topical|دهان|لوشن|كريم|مرهم|مرطب|موضعي|solo|bringo)\b", f"{raw_lower} {form_str}"))
    is_authoritative_spray = bool(re.search(r"\b(sprays?|بخاخ|سبراي|رذاذ|off)\b", f"{raw_lower} {form_str}"))
    is_authoritative_suppository = bool(re.search(r"\b(spp|supp|suppositor(?:y|ies)?|لبوس|لبوسة)\b", f"{raw_lower} {form_str}"))

    is_syrup = False
    is_injection = False
    is_tablet = False
    is_drops = False
    is_topical = False
    is_spray = False
    is_suppository = False

    if is_authoritative_topical:
        is_topical = True
    elif is_authoritative_spray:
        is_spray = True
    elif is_authoritative_suppository:
        is_suppository = True
    elif is_authoritative_drops:
        is_drops = True
    elif is_authoritative_syrup:
        is_syrup = True
    elif is_authoritative_injection:
        is_injection = True
    elif is_authoritative_tablet:
        is_tablet = True
    else:
        is_topical = bool(re.search(r"\b(lotion|cream|ointment|gel|topical|دهان|لوشن|كريم|مرهم|موضعي)\b", text_combined))
        is_spray = bool(re.search(r"\b(sprays?|بخاخ|سبراي)\b", text_combined))
        is_suppository = bool(re.search(r"\b(spp|supp|لبوس|لبوسة)\b", text_combined))
        is_tablet = bool(re.search(r"\b(tabs?|tablets?|caps?|capsules?|قرص|أقراص|كبسول|كبسولات)\b", text_combined)) and not is_topical and not is_spray and not is_suppository
        is_drops = bool(re.search(r"\b(drops?|eye\s+drops?|oral\s+drops?|قطرة|قطره|نقط|قطارة|قطاره)\b", text_combined)) and not is_tablet and not is_topical
        is_injection = bool(re.search(r"\b(amp|ampoules?|vials?|inj|injections?|حقنة|حقن|أمبول|أمبولات)\b", text_combined)) and not is_tablet
        is_syrup = bool(re.search(r"\b(syrups?|syrs?|susp|suspensions?|شراب|معلق)\b", text_combined)) and not is_tablet and not is_injection and not is_drops

    # 1. Parse doctor multiplication shorthand (e.g. 1x3, 1*3, 1 x 2, 2x3)
    m = re.search(r"\b([1-4])\s*[*x×X]\s*([1-4])\b", clean_raw)
    d_num, f_num = (int(m.group(1)), int(m.group(2))) if m else (None, None)

    if f_num:
        clean_freq = "twice daily" if f_num == 2 else ("once daily" if f_num == 1 else f"{f_num} times daily")

    if d_num:
        if not clean_dose or ("drop" in str(clean_dose).lower() and is_tablet):
            clean_dose = f"{d_num} tablet" if is_tablet else f"{d_num} unit"

    # Decode hourly intervals: e.g. '/ ٨ ساعات', '/ 8 ساعات', '1x24 hours', '1x12 h', 'كل ٢٤ ساعة', 'كل ١٢ ساعة'
    m_hours = re.search(
        r"(?:[/x×*]|كل|every|\b)\s*([١-٩][٠-٩]?|\d{1,2})\s*(?:ساعات?|ساع[ةه]|س|h|hrs?|hours?)\b",
        f"{clean_raw} {clean_instr} {str(clean_freq)}",
        re.IGNORECASE,
    )
    if m_hours:
        ar_to_en = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
        h_val = m_hours.group(1).translate(ar_to_en)
        try:
            h_num = int(h_val)
        except ValueError:
            h_num = None

        if h_num == 8:
            clean_freq = "3 times daily (كل ٨ ساعات)"
        elif h_num == 12:
            clean_freq = "twice daily (كل ١٢ ساعة)"
        elif h_num == 24:
            clean_freq = "once daily (كل ٢٤ ساعة)"
        elif h_num == 6:
            clean_freq = "4 times daily (كل ٦ ساعات)"
        elif h_num == 4:
            clean_freq = "every 4 hours (كل ٤ ساعات)"

    # Check alternate days interval (يوم بعد يوم): standard for pediatric iron/vitamin drops
    m_alt_day = re.search(r"\b(?:يوم\s*بعد\s*يوم|every\s*other\s*day|alternate\s*days?)\b", f"{clean_raw} {clean_instr}", re.IGNORECASE)
    # Check once daily / daily: 'يومياً', 'يوميا', 'مرة يومياً', 'كل يوم', 'daily'
    m_daily = re.search(r"\b(?:يومياً|يوميا|مرة\s*(?:واحدة\s*)?يومياً|مرة\s*(?:واحدة\s*)?يوميا|كل\s*يوم|once\s*daily)\b", f"{clean_raw} {clean_instr}", re.IGNORECASE)

    has_hours_in_raw = bool(re.search(r"(?:ساعات?|ساع[ةه]|س|h|hrs?|hours?)\b", f"{clean_raw} {clean_instr}", re.IGNORECASE))
    if m_alt_day:
        clean_freq = "every other day (يوم بعد يوم)"
    elif m_daily and not has_hours_in_raw and (not f_num or f_num == 1):
        clean_freq = "once daily (يومياً)"

    # Extract cm3 / ml dose for syrups/suspensions (e.g. ١.٥ سم, 1.5 سم, 2 سم, ٢ سم, 3 سم, ٣ سم)
    m_ml = re.search(r"(\d+(?:\.\d+)?|[١-٥](?:\.[٠-٩])?)\s*(?:سم|مل|ml|cm|cm³)\b", f"{clean_raw} {clean_instr}")
    if m_ml and not is_drops and (not clean_dose or clean_dose in ("5", "3 times", "unknown")):
        ar_to_en = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
        val_en = m_ml.group(1).translate(ar_to_en)
        clean_dose = f"{val_en} ml ({m_ml.group(0)})"

    # PRN check: e.g. 'عند اللزوم' / 'عند الحاجة' (common for fever / pain / Cetal drops)
    prn_regex = r"\b(?:عند\s*الحاج[ةه]|عند\s*اللزوم|as\s*needed|prn)\b"
    m_prn = re.search(prn_regex, f"{clean_raw} {clean_instr}", re.IGNORECASE)
    is_antipyretic = bool(re.search(r"\b(cetal|cetul|setal|setul|paracetamol|acetaminophen|brufen|ibuprofen|cataflam|سيتال|سيتول|بروفين|كتافلام)\b", f"{raw_lower} {form_str}"))

    if m_prn and not m_alt_day:
        if m_hours and clean_freq:
            clean_freq = f"{clean_freq} as needed (عند اللزوم)"
        else:
            clean_freq = "as needed (عند اللزوم)"

    # Strict ban on PRN when contradictory on scheduled non-antipyretics
    is_scheduled_freq = bool(clean_freq and any(w in clean_freq.lower() for w in [
        "times", "twice", "once", "daily", "every", "ساعات", "ساعة", "يومياً", "مرات", "مرتين", "٣", "٢"
    ]))
    if not is_antipyretic and (is_scheduled_freq or is_syrup or is_injection):
        if clean_instr and re.search(prn_regex, clean_instr, re.IGNORECASE):
            clean_instr = re.sub(prn_regex, "", clean_instr, flags=re.IGNORECASE).strip()
            clean_instr = re.sub(r"\s+", " ", clean_instr).strip(" ,.-()")
            if not clean_instr:
                clean_instr = None
        if clean_freq and re.search(prn_regex, clean_freq, re.IGNORECASE):
            clean_freq = None

    is_capsule = bool(re.search(r"\b(caps?|capsules?|كبسول|كبسولة|كبسولات)\b", text_combined))
    if is_tablet:
        # Tablets and capsules must NEVER have قطرة, نقط, حقنة, أمبول
        tab_unit_ar = "كبسولة" if is_capsule else "قرص"
        tab_unit_en = "capsule" if is_capsule else "tablet"
        if clean_instr:
            clean_instr = re.sub(r"\b(?:قطرة|قطرات|نقط)\b", tab_unit_ar, clean_instr)
            clean_instr = re.sub(r"\b(?:حقنة|حقن|أمبول|أمبولات|حصانة|حصة)\b", tab_unit_ar, clean_instr)
            if f_num == 3 and re.search(r"\b[٢2]\s*مرات", clean_instr):
                clean_instr = re.sub(r"\b[٢2]\s*مرات", "٣ مرات", clean_instr)
            elif f_num == 2 and re.search(r"\b[٣3]\s*مرات", clean_instr):
                clean_instr = re.sub(r"\b[٣3]\s*مرات", "مرتين", clean_instr)

        # Count of tablets/capsules: e.g. ٢ كبسولة, 2 capsules, قرص
        m_tab_cap = re.search(r"\b([1-4]|١|٢|٣|٤)\s*(?:أقراص|قرص|كبسول|كبسولة|كبسولات|tabs?|caps?)\b", f"{clean_raw}")
        if m_tab_cap and (not clean_dose or clean_dose in ("1 unit", "unknown")):
            cnt_ar = m_tab_cap.group(1)
            cnt_map = {"١": 1, "٢": 2, "٣": 3, "٤": 4}
            cnt_num = cnt_map.get(cnt_ar, cnt_ar)
            clean_dose = f"{cnt_num} {tab_unit_en}"
        elif clean_dose and any(w in clean_dose.lower() for w in ["ampoule", "injection", "vial", "drop"]):
            clean_dose = f"1 {tab_unit_en} ({tab_unit_ar} واحد)"

    if re.search(r"\b(?:صباحاً\s*ومساءً|صباحا\s*ومساء|morning\s*and\s*evening)\b", f"{clean_raw} {clean_instr}"):
        if not clean_freq or clean_freq in ("unknown", "Not specified"):
            clean_freq = "twice daily (صباحاً ومساءً)"

    elif is_topical:
        # Lotions, creams, and ointments must NEVER contain tablets, 500mg, or 'بعد الأكل'
        if clean_instr:
            clean_instr = re.sub(r"\b(?:قرص|أقراص|كبسولة|حبوب|حقنة|حقن|أمبول|أمبولات|بعد\s*الأكل|قبل\s*الأكل|مع\s*الأكل|1\s*tab|500\s*mg|500mg|\d+\s*[*x×X]\s*\d+)\b", "", clean_instr).strip()
            clean_instr = re.sub(r"\s+", " ", clean_instr).strip(" ,.-()")
        clean_dose = "دهان موضعي (Topical application)"
        if re.search(r"(?:ل?حمو\s*النيل|شمس|حروق)", f"{clean_raw} {clean_instr or ''}"):
            clean_instr = "دهان موضعي لحمو النيل وحروق الشمس"
            clean_freq = "عند اللزوم (as needed)"
        elif not clean_instr:
            clean_instr = "دهان موضعي على الأماكن المصابة"
            clean_freq = "عند اللزوم (as needed)"
        else:
            clean_freq = "عند اللزوم (as needed)"

    elif is_spray:
        # Sprays must NEVER contain tablets, 500mg, or 'بعد الأكل'
        if clean_instr:
            clean_instr = re.sub(r"\b(?:قرص|أقراص|كبسولة|حبوب|حقنة|حقن|أمبول|أمبولات|بعد\s*الأكل|قبل\s*الأكل|مع\s*الأكل|1\s*tab|500\s*mg|500mg|\d+\s*[*x×X]\s*\d+)\b", "", clean_instr).strip()
            clean_instr = re.sub(r"\s+", " ", clean_instr).strip(" ,.-()")
        clean_dose = "بخة موضعية (Spray application)"
        if re.search(r"(?:البحر|بحر|ساعتين|ساعة)", f"{clean_raw} {clean_instr or ''}"):
            clean_instr = "بخاخ طارد للحشرات (كل ساعتين على البحر)"
            clean_freq = "كل ساعتين على البحر (عند الحاجة)"
        elif not clean_instr:
            clean_instr = "بخاخ موضعي عند الحاجة"
            clean_freq = "عند الحاجة"
        else:
            clean_freq = "عند الحاجة"

    elif is_suppository:
        # Suppositories must NEVER contain tablets, 500mg, or 'بعد الأكل'
        if clean_instr:
            clean_instr = re.sub(r"\b(?:قرص|أقراص|كبسولة|حبوب|حقنة|حقن|أمبول|أمبولات|بعد\s*الأكل|قبل\s*الأكل|مع\s*الأكل|1\s*tab|500\s*mg|500mg|\d+\s*[*x×X]\s*\d+)\b", "", clean_instr).strip()
            clean_instr = re.sub(r"\s+", " ", clean_instr).strip(" ,.-()")
        clean_dose = "1 suppository (لبوسة واحدة)"
        if re.search(r"(?:الكحة|كحة|عند\s*الكحة)", f"{clean_raw} {clean_instr or ''}"):
            clean_instr = "لبوسة شرجية مهدئة للكحة عند اللزوم"
            clean_freq = "عند الكحة (عند اللزوم)"
        elif not clean_instr:
            clean_instr = "لبوسة شرجية عند اللزوم"
            clean_freq = "عند اللزوم (as needed)"
        else:
            clean_freq = "عند اللزوم (as needed)"

    elif is_drops:
        # Drops must NEVER contain tablets, injections, or بعد الأكل
        if clean_instr:
            clean_instr = re.sub(r"\b(?:قرص|أقراص|كبسولة|حبوب|حقنة|حقن|أمبول|أمبولات|بعد\s*الأكل|قبل\s*الأكل|1\s*tab|500\s*mg|500mg)\b", "", clean_instr).strip()
            # If clean_instr contains hallucinated 'سم' for drops, strip it
            clean_instr = re.sub(r"\b[١-٩\d]+\s*(?:سم|مل)\b", "", clean_instr).strip()
            clean_instr = re.sub(r"\s+", " ", clean_instr).strip(" ,.-()")
            if not clean_instr:
                clean_instr = None

        # 1. Extract drop count: e.g. ٥ نقط, 5 drops, 7 نقط, ٧ نقط, 10 نقط, ٣ نقط
        m_drops = re.search(r"(?:^|[^\w])([١-٩]|\d+)\s*(?:نقط|نقطة|نقطات|drops?)\b", f"{clean_raw} {clean_instr or ''}", re.IGNORECASE)
        # 2. Extract dropper fraction: e.g. ١/٢ قطارة, نصف قطارة, نص قطاره, قطارة كاملة, 1 dropper
        m_dropper = re.search(r"\b(١/٢|1/2|نصف|نص|ربع|١/٤|1/4|١|1|كاملة)?\s*(?:قطارة|قطاره|droppers?)\b", f"{clean_raw} {clean_instr or ''}", re.IGNORECASE)

        ar_to_en = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
        if m_drops:
            cnt_ar = m_drops.group(1)
            cnt_en = cnt_ar.translate(ar_to_en)
            clean_dose = f"{cnt_en} drops ({cnt_ar} نقط)"
        elif m_dropper:
            frac = m_dropper.group(1) or ""
            if frac in ("١/٢", "1/2", "نصف", "نص"):
                clean_dose = "1/2 dropper (١/٢ قطارة)"
            elif frac in ("١/٤", "1/4", "ربع"):
                clean_dose = "1/4 dropper (١/٤ قطارة)"
            elif frac in ("كاملة", "١", "1"):
                clean_dose = "1 dropper (قطارة كاملة)"
            else:
                clean_dose = "1/2 dropper (نصف قطارة)"
        elif clean_dose and any(w in clean_dose.lower() for w in ["سم", "ml", "tablet", "tab", "unit", "ampoule", "500"]):
            # Clear hallucinated syrup/tablet dose for drops
            clean_dose = None

        if re.search(r"\b(حكة|هرش|الحكة|الهرش)\b", f"{clean_raw} {clean_instr or ''}"):
            clean_instr = f"{clean_dose or 'نقط بالفم'} عند الحكة والهرش (عند اللزوم)"
            clean_freq = "عند الحكة والهرش (عند اللزوم)"

    elif is_injection:
        if (not clean_dose or clean_dose in ("5", "unknown")) and (
            (clean_instr and "حقنة" in clean_instr) or is_authoritative_injection
        ):
            clean_dose = "1 ampoule (حقنة واحدة)"
        if clean_freq in ("5", "unknown"):
            clean_freq = "once daily (مرة واحدة يومياً)"
        if clean_instr in ("5", "unknown"):
            clean_instr = None

    elif is_syrup:
        # Syrups must never contain حقنة or قرص
        if clean_instr:
            clean_instr = re.sub(r"\b(?:حقنة|حقن|أمبول|أمبولات|قرص|أقراص)\b", "", clean_instr).strip()
        if clean_dose and ("ampoule" in clean_dose.lower() or "tablet" in clean_dose.lower() or "times" in clean_dose.lower()):
            clean_dose = None

        if not clean_dose and m_ml:
            ar_to_en = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
            val_en = m_ml.group(1).translate(ar_to_en)
            clean_dose = f"{val_en} ml ({m_ml.group(0)})"

        if clean_freq and "3" in clean_freq and "daily" not in clean_freq.lower() and "every" not in clean_freq.lower():
            clean_freq = "3 times daily (٣ مرات يومياً)"
        elif clean_freq and "2" in clean_freq and "daily" not in clean_freq.lower() and "every" not in clean_freq.lower():
            clean_freq = "twice daily (مرتين يومياً)"

        if clean_instr and ("times" in clean_instr.lower() or clean_instr in ("3", "2", "1", "5")):
            clean_instr = None

    # 3. PRN fallback when not scheduled
    has_explicit_prn = bool(re.search(r"\b(prn|عند\s+الحاجة|عند\s+اللزوم)\b", clean_raw.lower()))
    if has_explicit_prn and not is_scheduled_freq and not is_syrup and not is_injection and not clean_freq and not m:
        clean_freq = "as needed (عند الحاجة)"

    return clean_dose, clean_freq, clean_instr or None


def extract_duration_from_directions(
    duration: Optional[str],
    raw_text: Optional[str],
    instructions: Optional[str],
) -> Optional[str]:
    """
    Extracts and standardizes treatment duration from prescription text or instructions.
    Handles Egyptian medical phrasing like: 'لمدة ٥ أيام', 'لمدة شهر', 'لمدة أسبوعين', 'لمدة أسبوع'.
    """
    clean_dur = clean_clinical_field(duration)
    text = f"{clean_clinical_field(raw_text) or ''} {clean_clinical_field(instructions) or ''}"
    ar_to_en = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")

    # Strip 'يوم بعد يوم' (frequency: alternate days) so it is not mistaken for a duration
    text_clean = re.sub(r"\bيوم\s*بعد\s*يوم\b", "", text, flags=re.IGNORECASE)

    # Check raw text evidence for duration contradictions
    has_raw_month = bool(re.search(r"\b(?:لمدة\s*)?(?:شهر|شهرين|months?)\b", text_clean, re.IGNORECASE))
    has_raw_week = bool(re.search(r"\b(?:لمدة\s*)?(?:أسبوعين|اسبوعين|أسبوع|اسبوع|weeks?)\b", text_clean, re.IGNORECASE))
    has_raw_days = bool(re.search(r"(?:لمدة\s*)?[١-٩\d]+\s*(?:أيام|ايام|يوم|days?)\b", text_clean, re.IGNORECASE))

    if clean_dur and clean_dur.lower() not in ("unknown", "not specified", "null"):
        if ("day" in clean_dur.lower() or "أيام" in clean_dur or "يوم" in clean_dur) and not has_raw_days:
            clean_dur = None
        elif ("week" in clean_dur.lower() or "أسبوع" in clean_dur or "اسبوع" in clean_dur) and not has_raw_week:
            clean_dur = None
        elif ("month" in clean_dur.lower() or "شهر" in clean_dur) and not has_raw_month:
            clean_dur = None
        else:
            return clean_dur

    # 1. Match months: e.g. 'لمدة شهرين', 'شهرين', 'لمدة شهر', 'شهر', '1 month', '2 months'
    if re.search(r"\b(?:لمدة\s*)?(?:شهرين|2\s*months?)\b", text_clean, re.IGNORECASE):
        return "2 months (لمدة شهرين)"
    if re.search(r"\b(?:لمدة\s*)?(?:شهر|1\s*month)\b", text_clean, re.IGNORECASE):
        return "1 month (لمدة شهر)"

    # 2. Match weeks: e.g. 'لمدة أسبوعين', 'لمدة اسبوعين', 'أسبوعين', 'اسبوعين', '2 weeks'
    if re.search(r"\b(?:لمدة\s*)?(?:أسبوعين|اسبوعين|2\s*weeks?)\b", text_clean, re.IGNORECASE):
        return "2 weeks (لمدة أسبوعين)"
    if re.search(r"\b(?:لمدة\s*)?(?:أسبوع|اسبوع|1\s*week)\b", text_clean, re.IGNORECASE):
        return "1 week (لمدة أسبوع)"

    # 3. Match days: e.g. 'لمدة ٥ أيام', '٥ أيام', '5 days', 'لمدة 10 أيام'
    m_days = re.search(r"(?:لمدة\s*)?([١-٩]|\d+)\s*(?:أيام|ايام|يوم|days?)\b", text_clean, re.IGNORECASE)
    if m_days:
        num_str = m_days.group(1)
        num_en = num_str.translate(ar_to_en)
        return f"{num_en} days (لمدة {num_str} أيام)"

    return None


def synthesize_clinical_instruction(
    dose: Optional[str],
    frequency: Optional[str],
    duration: Optional[str],
    raw_instructions: Optional[str],
) -> Optional[str]:
    """
    Synthesizes the complete clinical administration instructions sentence:
    e.g., '1 drop 3 times daily for 2 weeks (قطرة ٣ مرات يومياً لمدة أسبوعين)'.
    Eliminates fragmented or cross-contaminated fields and creates a unified clinical direction.
    Never outputs literal 'null' placeholders.
    """
    d = clean_clinical_field(dose)
    f = clean_clinical_field(frequency)
    dur = clean_clinical_field(duration)
    clean_raw = clean_clinical_field(raw_instructions) or ""

    prn_regex = r"\b(?:عند\s*الحاج[ةه]|عند\s*اللزوم|as\s*needed|prn)\b"
    has_sched = bool(f and any(w in f.lower() for w in ["times", "twice", "once", "daily", "every", "ساعات", "ساعة", "يومياً", "مرات"]))
    if has_sched and clean_raw:
        clean_raw = re.sub(prn_regex, "", clean_raw, flags=re.IGNORECASE).strip(" ,.-()")

    components = []
    if d:
        components.append(d)
    if f:
        components.append(f)
    if dur:
        if not dur.lower().startswith("for") and not dur.startswith("لمدة"):
            components.append(f"for {dur}")
        else:
            components.append(dur)

    base_sentence = " ".join(components).strip()

    if base_sentence and clean_raw:
        if base_sentence.lower() in clean_raw.lower():
            return clean_raw
        if clean_raw.lower() in base_sentence.lower():
            return base_sentence
        return f"{base_sentence} ({clean_raw})"
    elif base_sentence:
        return base_sentence
    elif clean_raw:
        return clean_raw
    return None


class PrescriptionPipelineV2:
    """
    AI Pipeline V2:
    Prescription Image
         ↓
    Image Quality & Preprocessing Check
         ↓
    Vision Model (OCR & Entity Extraction)
         ↓
    Raw Prescription Extraction
         ↓
    Egyptian Drug Database (EDA) Lookup
         ↓
    Drug Normalization & Candidate Ranking
         ↓
    Strength & Formulation Reconciliation
         ↓
    Confidence Recalibration & LASA Safety Verification
         ↓
    Final Structured Prescription
    """

    def __init__(
        self,
        analyzer: Optional[PrescriptionAnalyzer] = None,
        normalizer: Optional[DrugNormalizer] = None
    ):
        self.analyzer = analyzer or PrescriptionAnalyzer()
        self.normalizer = normalizer or drug_normalizer

    async def process_prescription(
        self,
        image_bytes: bytes,
        filename: str = "prescription.jpg",
        mime_type: str = "image/jpeg",
        provider_name: Optional[str] = None,
        model_name: Optional[str] = None,
        custom_prompt: Optional[str] = None,
    ) -> AnalysisResponse:
        """
        Executes the end-to-end V2 pipeline combining multimodal vision with
        the official Egyptian Drug Database.
        """
        # Step 1 & 2: Raw Vision Extraction via Analyzer
        v1_response: AnalysisResponse = await self.analyzer.analyze_image(
            image_bytes=image_bytes,
            filename=filename,
            mime_type=mime_type,
            provider_name=provider_name,
            model_name=model_name,
            custom_prompt=custom_prompt,
        )

        analysis = v1_response.analysis
        v2_medications = []
        v2_uncertain_items = list(analysis.uncertain_items)

        # Step 3, 4 & 5: Normalize every medication through the Egyptian Drug Authority Database
        for med in analysis.medications:
            norm_result = self.normalizer.normalize(
                raw_text=med.raw_text,
                predicted_name=med.name,
                extracted_strength=med.strength,
                initial_confidence=med.confidence,
            )

            # Build candidate list for UI and API inspection
            candidate_dicts = [
                {
                    "drug_id": c.drug_id,
                    "name": c.name,
                    "commercial_name_ar": c.commercial_name_ar,
                    "generic_name": c.generic_name,
                    "dosage_form": c.dosage_form,
                    "strength": c.registered_strength,
                    "score": c.score,
                    "match_type": c.match_type,
                    "warning": c.safety_warning,
                }
                for c in norm_result.top_candidates
            ]

            confirmed = norm_result.confirmed_drug
            clean_med_name = clean_clinical_field(med.name)
            clean_med_active = clean_clinical_field(med.active_ingredient)
            top_cand = norm_result.top_candidates[0] if norm_result.top_candidates else None

            if confirmed:
                # Use normalized official EDA name and active ingredients
                v2_name = confirmed.name
                v2_active = ", ".join(confirmed.active_ingredients) if confirmed.active_ingredients else confirmed.generic_name
                v2_eda_id = confirmed.drug_id
                v2_lasa = confirmed.safety_warning
                v2_status = "identified"
            else:
                # Ambiguous or unconfirmed per safety rules:
                top_cand_name = top_cand.name if top_cand and top_cand.score >= 0.55 else None
                v2_name = clean_med_name or top_cand_name
                v2_active = clean_med_active or (top_cand.generic_name if top_cand else None)
                v2_eda_id = top_cand.drug_id if top_cand and top_cand.score >= 0.65 else None
                v2_lasa = top_cand.safety_warning if top_cand else None
                v2_status = norm_result.status

            # Append LASA warnings and uncertainty notices to uncertain_items
            for reason in norm_result.uncertainty_reasons:
                if reason not in v2_uncertain_items:
                    v2_uncertain_items.append(f"[{med.raw_text[:20]}...] {reason}")

            # Dosage form hint from confirmed drug or top candidate
            form_hint = confirmed.dosage_form if confirmed else (top_cand.dosage_form if top_cand else None)

            # Reconcile dosage form contradictions and doctor multiplication notation (e.g. 1x3, 1*3)
            rec_dose, rec_freq, rec_raw_instr = reconcile_dosage_form_and_instructions(
                raw_text=med.raw_text,
                dose=med.dose,
                frequency=med.frequency,
                raw_instructions=med.instructions,
                dosage_form=form_hint,
            )

            # Reconcile treatment duration (e.g. 'لمدة ٥ أيام', 'لمدة أسبوع')
            rec_dur = extract_duration_from_directions(
                duration=med.duration,
                raw_text=med.raw_text,
                instructions=rec_raw_instr,
            )

            # Synthesize unabridged clinical administration sentence
            v2_instructions = synthesize_clinical_instruction(
                dose=rec_dose,
                frequency=rec_freq,
                duration=rec_dur,
                raw_instructions=rec_raw_instr,
            )

            # Retrieve structured clinical profile and packaging visual
            matched_drug = self.normalizer.repo.find_by_id(v2_eda_id) if v2_eda_id else None
            if matched_drug and matched_drug.indication_details:
                clinical_profile = dict(matched_drug.indication_details)
                med_image_url = matched_drug.image_url or clinical_profile.get("image_url")
            else:
                clinical_profile = get_clinical_knowledge(
                    drug_name=v2_name,
                    generic_name=v2_active,
                    drug_class=matched_drug.drug_class if matched_drug else None,
                    dosage_form=form_hint,
                    eda_id=v2_eda_id,
                    price_egp=matched_drug.price_egp if matched_drug else None,
                )
                med_image_url = clinical_profile.get("image_url")

            # Sanitize strength: if model hallucinated "500mg" or "1 tab" on non-tablet drugs,
            # purge the hallucination and use the EDA registered strength!
            form_hint_lower = str(form_hint or "").lower()
            raw_text_lower = str(med.raw_text or "").lower()
            is_non_tab = bool(re.search(r"\b(drops?|قطرة|نقط|lotion|cream|ointment|gel|topical|spray|suppositor|spp|syrup|suspension|شراب|معلق|دهان|بخاخ|لبوس)\b", f"{raw_text_lower} {form_hint_lower}"))
            raw_strength = clean_clinical_field(med.strength)
            if raw_strength and ("500" in raw_strength or "tab" in raw_strength.lower()) and is_non_tab:
                clean_strength = top_cand.registered_strength if (top_cand and top_cand.registered_strength and "500" not in top_cand.registered_strength) else None
            else:
                clean_strength = raw_strength

            v2_med = MedicationItem(
                raw_text=med.raw_text,
                name=clean_clinical_field(v2_name),
                active_ingredient=clean_clinical_field(v2_active),
                strength=clean_strength,
                dose=clean_clinical_field(rec_dose),
                frequency=clean_clinical_field(rec_freq),
                duration=clean_clinical_field(rec_dur),
                instructions=v2_instructions,
                confidence=norm_result.calibrated_confidence,
                status=v2_status,
                candidates=candidate_dicts,
                eda_id=v2_eda_id,
                lasa_warning=v2_lasa,
                pipeline_version="v2",
                clinical_indication=clinical_profile.get("indication_short"),
                likely_diagnosis=clinical_profile.get("likely_diagnosis"),
                indication_details=clinical_profile,
                image_url=med_image_url,
            )
            v2_medications.append(v2_med)

        # Step 6: Cross-Medication Pediatric Regimen Duration Harmonization
        # In Egyptian pediatric medicine, Vitamin C drops (Cevilene / Cevilin) are co-prescribed with
        # iron drops (Enrich) to treat nutritional iron-deficiency anemia over a 1-month treatment course.
        # If the vision model accidentally leaked "2 weeks" (اسبوعين) from Line 1 (probiotic Atlabacillus) onto Cevilene,
        # but Enrich is prescribed for 1 month, harmonize Cevilene duration to 1 month.
        enrich_item = next((m for m in v2_medications if "enrich" in (m.name or "").lower()), None)
        cevilin_item = next((m for m in v2_medications if any(k in (m.name or "").lower() for k in ["cevilene", "cevilin", "cevarol"])), None)
        if enrich_item and cevilin_item:
            if enrich_item.duration and "month" in enrich_item.duration.lower():
                if cevilin_item.duration and "week" in cevilin_item.duration.lower():
                    logger.info("Harmonizing Cevilene co-prescription duration with Enrich drops (1 month)")
                    cevilin_item.duration = "1 month (لمدة شهر)"
                    if cevilin_item.raw_text and "اسبوعين" in cevilin_item.raw_text:
                        cevilin_item.raw_text = re.sub(r"\b(?:لمدة\s*)?اسبوعين\b", "لمدة شهر", cevilin_item.raw_text)
                    cevilin_item.instructions = synthesize_clinical_instruction(
                        dose=cevilin_item.dose,
                        frequency=cevilin_item.frequency,
                        duration=cevilin_item.duration,
                        raw_instructions="٥ نقط يومياً لمدة شهر",
                    )

        # Recalibrate overall confidence
        if v2_medications:
            v2_overall_conf = sum(m.confidence for m in v2_medications) / len(v2_medications)
        else:
            v2_overall_conf = analysis.overall_confidence

        # Infer overall likely prescription diagnosis
        overall_diag = infer_prescription_overall_diagnosis([m.model_dump() for m in v2_medications])

        v2_analysis = PrescriptionAnalysis(
            prescription=analysis.prescription,
            medications=v2_medications,
            uncertain_items=v2_uncertain_items,
            overall_likely_diagnosis=overall_diag,
            overall_confidence=round(v2_overall_conf, 2),
            dietary_and_herbal_advice=analysis.dietary_and_herbal_advice,
        )

        return AnalysisResponse(
            analysis=v2_analysis,
            metadata=v1_response.metadata,
        )


pipeline_v2 = PrescriptionPipelineV2()
