import re
from typing import Optional


def normalize_text(text: Optional[str]) -> str:
    """Normalizes Arabic/English text for semantic comparison."""
    if not text:
        return ""
    s = text.lower().strip()
    # Normalize Arabic characters
    s = re.sub(r"[أإآ]", "ا", s)
    s = re.sub(r"ة", "ه", s)
    s = re.sub(r"ى", "ي", s)
    s = re.sub(r"[٠-٩]", lambda m: str("٠١٢٣٤٥٦٧٨٩".index(m.group(0))), s)
    # Remove punctuation
    s = re.sub(r"[^\w\s]", "", s)
    # Collapse whitespace
    s = re.sub(r"\s+", " ", s).strip()
    return s


def string_similarity(a: str, b: str) -> float:
    """Calculates Levenshtein-based similarity ratio between 0.0 and 1.0."""
    na = normalize_text(a)
    nb = normalize_text(b)
    if not na and not nb:
        return 1.0
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0

    len_a, len_b = len(na), len(nb)
    dp = [[0] * (len_b + 1) for _ in range(len_a + 1)]
    for i in range(len_a + 1):
        dp[i][0] = i
    for j in range(len_b + 1):
        dp[0][j] = j

    for i in range(1, len_a + 1):
        for j in range(1, len_b + 1):
            cost = 0 if na[i - 1] == nb[j - 1] else 1
            dp[i][j] = min(
                dp[i - 1][j] + 1,       # deletion
                dp[i][j - 1] + 1,       # insertion
                dp[i - 1][j - 1] + cost # substitution
            )

    dist = dp[len_a][len_b]
    max_len = max(len_a, len_b)
    if max_len == 0:
        return 1.0
    return 1.0 - (dist / max_len)


def levenshtein_distance(seq_a, seq_b) -> int:
    """Calculates Levenshtein edit distance between two sequences (chars or word tokens)."""
    len_a, len_b = len(seq_a), len(seq_b)
    if len_a == 0:
        return len_b
    if len_b == 0:
        return len_a

    dp = list(range(len_b + 1))
    for item_a in seq_a:
        new_dp = [dp[0] + 1] + [0] * len_b
        for j, item_b in enumerate(seq_b, 1):
            cost = 0 if item_a == item_b else 1
            new_dp[j] = min(
                dp[j] + 1,          # deletion
                new_dp[j - 1] + 1,   # insertion
                dp[j - 1] + cost    # substitution
            )
        dp = new_dp
    return dp[len_b]


def calculate_cer(reference: Optional[str], hypothesis: Optional[str], normalize: bool = True) -> float:
    """
    Calculates Character Error Rate (CER) = Levenshtein(chars) / len(reference).
    Returns a float between 0.0 (perfect) and 1.0 (completely inaccurate).
    """
    ref = normalize_text(reference) if normalize else (reference or "").strip()
    hyp = normalize_text(hypothesis) if normalize else (hypothesis or "").strip()

    if not ref and not hyp:
        return 0.0
    if not ref:
        return 1.0

    dist = levenshtein_distance(list(ref), list(hyp))
    return round(min(1.0, dist / len(ref)), 4)


def calculate_wer(reference: Optional[str], hypothesis: Optional[str], normalize: bool = True) -> float:
    """
    Calculates Word Error Rate (WER) = Levenshtein(words) / len(reference_words).
    Returns a float between 0.0 (perfect) and 1.0 (completely inaccurate).
    """
    ref = normalize_text(reference) if normalize else (reference or "").strip()
    hyp = normalize_text(hypothesis) if normalize else (hypothesis or "").strip()

    ref_words = ref.split()
    hyp_words = hyp.split()

    if not ref_words and not hyp_words:
        return 0.0
    if not ref_words:
        return 1.0

    dist = levenshtein_distance(ref_words, hyp_words)
    return round(min(1.0, dist / len(ref_words)), 4)


def calculate_precision_recall_f1(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    """
    Calculates Precision, Recall, and F1 Score for medication entity identification.
    Precision = TP / (TP + FP)
    Recall = TP / (TP + FN)
    F1 = 2 * (Precision * Recall) / (Precision + Recall)
    """
    precision = tp / (tp + fp) if (tp + fp) > 0 else (1.0 if (tp == 0 and fn == 0) else 0.0)
    recall = tp / (tp + fn) if (tp + fn) > 0 else (1.0 if (tp == 0 and fp == 0) else 0.0)
    if precision + recall > 0:
        f1 = (2.0 * precision * recall) / (precision + recall)
    else:
        f1 = 0.0
    return round(precision, 4), round(recall, 4), round(f1, 4)


DOSAGE_FORM_PATTERN = re.compile(
    r"\b(eye\s+drops?|drops?|tablets?|tabs?|capsules?|caps?|suspension|susp|syrup|syr|ampoules?|amps?|ointment|oint|cream|gel|sachets?|vial|e\.?d\.?|e\.?o\.?|نقط|قطرة|أقراص|قرص|كبسول|كبسولات|معلق|شراب|أمبول|حقن|مرهم|كريم|جل|فوار|أكياس)\b",
    re.IGNORECASE,
)


def clean_clinical_field(val: Optional[object]) -> Optional[str]:
    """
    Cleans clinical string fields.
    Converts literal 'null', 'none', 'unknown', 'n/a', 'nil', 'undefined',
    'null null for null', or empty/whitespace strings to None.
    """
    if val is None:
        return None
    s = str(val).strip()
    if not s:
        return None
    low = s.lower()
    if low in (
        "null",
        "none",
        "unknown",
        "n/a",
        "na",
        "nil",
        "undefined",
        "not specified",
        "null null for null",
        "null null",
        "for null",
    ):
        return None
    return s


def extract_drug_brand_stem(text: Optional[str]) -> str:
    """
    Strips dosage form markers, symbols, and strength numbers from a raw handwriting line
    to isolate the core commercial brand stem for high-recall database matching.
    e.g. 'Lubrivic Eye Drop' -> 'Lubrivic'
         'Efemyo Eye Drop'   -> 'Efemyo'
         'Cataflam 50mg Tab' -> 'Cataflam'
    """
    s = clean_clinical_field(text)
    if not s:
        return ""
    # Remove R/ or R\ prefix
    s = re.sub(r"^[Rr][/\\]?\s*", "", s).strip()
    # Remove dosage forms
    s_cleaned = DOSAGE_FORM_PATTERN.sub("", s).strip()
    # Remove strength numbers (e.g. 500mg, 50 mg, 0.5%)
    s_cleaned = re.sub(r"\b\d+(?:\.\d+)?\s*(?:mg|gm|g|ml|mcg|%)\b", "", s_cleaned, flags=re.IGNORECASE).strip()
    s_cleaned = re.sub(r"\s+", " ", s_cleaned).strip()
    return s_cleaned if len(s_cleaned) >= 2 else s


