"""
Dataset Builder & Unifier for Egyptian Medical Prescription VLM Fine-Tuning.
Consolidates:
1. 561 verified clean bilingual prescriptions from 'newdataset arabic , english /dataset/'
2. New prescriptions from 'dataset/new Prescriptions images /'
3. Authentic clinical WhatsApp prescriptions from 'dataset/new Prescriptions images /my /'

Outputs unified training and validation splits in standard Qwen2.5-VL and OpenAI Vision formats:
- dataset/training_exports/vlm_lora_train.json
- dataset/training_exports/vlm_lora_val.json
- dataset/training_exports/training_manifest.json
"""

import json
import logging
import random
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..config import PROJECT_ROOT

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

EXPORTS_DIR = PROJECT_ROOT / "dataset" / "training_exports"
try:
    EXPORTS_DIR.mkdir(parents=True, exist_ok=True)
except OSError:
    pass


def clean_str(val: Any) -> Optional[str]:
    if val is None:
        return None
    s = str(val).strip()
    return s if s and s.lower() not in ("null", "none", "unknown", "nan") else None


def format_prescription_sample(
    image_rel_path: str,
    patient_age: Optional[str] = None,
    patient_weight_kg: Optional[float] = None,
    medications: Optional[List[Dict[str, Any]]] = None,
    dietary_advice: Optional[str] = None,
) -> Dict[str, Any]:
    """Builds a standardized conversation sample for Qwen2.5-VL / Vision-Language LoRA training."""
    med_list = []
    for m in (medications or []):
        raw_t = clean_str(m.get("raw_text") or m.get("Medication_name_raw") or m.get("name"))
        name = clean_str(m.get("name") or m.get("Medication_name") or raw_t)
        dose = clean_str(m.get("dose") or m.get("Dosage") or m.get("Dosage_raw"))
        freq = clean_str(m.get("frequency") or m.get("Frequency") or m.get("Frequency_raw"))
        dur = clean_str(m.get("duration") or m.get("Duration") or m.get("Duration_raw"))
        instr = clean_str(m.get("instructions") or m.get("Instructions") or m.get("Instructions_raw"))

        if not raw_t and not name:
            continue

        med_list.append({
            "raw_text": raw_t or name,
            "name": name or raw_t,
            "active_ingredient": clean_str(m.get("active_ingredient")),
            "strength": clean_str(m.get("strength")),
            "dose": dose,
            "frequency": freq,
            "duration": dur,
            "instructions": instr,
            "confidence": 0.95,
            "status": "identified",
        })

    assistant_json = {
        "prescription": {
            "patient_age": patient_age,
            "patient_weight_kg": patient_weight_kg,
        },
        "dietary_and_herbal_advice": dietary_advice,
        "medications": med_list,
        "uncertain_items": [],
        "overall_confidence": 0.95,
    }

    prompt_text = "Transcribe and extract structured clinical information from this handwritten Egyptian prescription."

    return {
        "id": Path(image_rel_path).stem,
        "image": str(image_rel_path),
        "conversations": [
            {
                "role": "user",
                "content": f"<image>\n{prompt_text}",
            },
            {
                "role": "assistant",
                "content": json.dumps(assistant_json, ensure_ascii=False, indent=2),
            },
        ],
        "ground_truth": assistant_json,
    }


def build_complete_dataset(train_split_ratio: float = 0.85) -> Dict[str, Any]:
    """Consolidates all available prescription collections into train/val datasets."""
    all_samples: List[Dict[str, Any]] = []
    logger.info("Collecting prescriptions from repository...")

    # 1. Collection A: 'newdataset arabic , english ' (561 verified clean images)
    clean_json_path = PROJECT_ROOT / "newdataset arabic , english " / "dataset" / "all_prescriptions_clean.json"
    clean_imgs_dir = PROJECT_ROOT / "newdataset arabic , english " / "dataset" / "prescriptions"

    if clean_json_path.exists() and clean_imgs_dir.exists():
        with open(clean_json_path, "r", encoding="utf-8") as f:
            cases = json.load(f)

        count_a = 0
        for c in cases:
            fn = c.get("filename")
            if not fn:
                continue
            img_path = clean_imgs_dir / fn
            if not img_path.exists():
                continue

            rel_p = img_path.relative_to(PROJECT_ROOT)
            sample = format_prescription_sample(
                image_rel_path=str(rel_p),
                medications=c.get("medications", []),
            )
            all_samples.append(sample)
            count_a += 1

        logger.info(f"Loaded {count_a} verified cases from newdataset arabic , english")

    # 2. Collection B: 'dataset/new Prescriptions images ' (Audit results & new webp/jpegs)
    audit_json = PROJECT_ROOT / "dataset" / "new Prescriptions images " / "audit_results.json"
    audit_dir = PROJECT_ROOT / "dataset" / "new Prescriptions images "

    if audit_json.exists():
        with open(audit_json, "r", encoding="utf-8") as f:
            audit_cases = json.load(f)

        count_b = 0
        for ac in audit_cases:
            fn = ac.get("file")
            if not fn:
                continue
            img_path = audit_dir / fn
            if not img_path.exists():
                continue

            rel_p = img_path.relative_to(PROJECT_ROOT)
            meds_parsed = []
            for raw_m in ac.get("medications", []):
                # e.g. "Augmentin (1 tablet 1 tablet 3 times daily (حقن كل ٣ أيام))"
                m_match = re.match(r"^([^\(]+)(?:\((.*)\))?", str(raw_m))
                if m_match:
                    name_part = m_match.group(1).strip()
                    instr_part = m_match.group(2).strip() if m_match.group(2) else None
                    meds_parsed.append({
                        "name": name_part,
                        "raw_text": str(raw_m),
                        "instructions": instr_part,
                    })

            sample = format_prescription_sample(
                image_rel_path=str(rel_p),
                medications=meds_parsed,
                dietary_advice=clean_str(ac.get("herbal_advice")),
            )
            all_samples.append(sample)
            count_b += 1

        logger.info(f"Loaded {count_b} cases from new Prescriptions images audit")

    # 3. Collection C: 'dataset/new Prescriptions images /my ' (Real verified clinic prescriptions)
    my_dir = PROJECT_ROOT / "dataset" / "new Prescriptions images " / "my "
    if my_dir.exists():
        count_c = 0
        # Specifically add ground truth for the verified prescriptions in 'my'
        for img_file in my_dir.glob("*.jpeg"):
            fn = img_file.name
            rel_p = str(img_file.relative_to(PROJECT_ROOT))

            if "11.43.38 AM (1)" in fn:
                # Verified: Bassem Nasr Ahmed - ENT Tonsillitis / Otitis
                sample = format_prescription_sample(
                    image_rel_path=rel_p,
                    patient_age="35y",
                    patient_weight_kg=None,
                    medications=[
                        {
                            "raw_text": "Ceftriaxone 1gm / 24 hs بعد عمل اختبار حساسية",
                            "name": "Ceftriaxone",
                            "strength": "1gm",
                            "dose": "1 injection (١ حقنة)",
                            "frequency": "once daily (كل ٢٤ ساعة)",
                            "instructions": "١ حقنة عضل / ٢٤ ساعة بعد عمل اختبار حساسية",
                        },
                        {
                            "raw_text": "Cipromega gtt نقط للأذن / ١٢ ساعة لمدة اسبوع",
                            "name": "Cipromega",
                            "dose": "3 drops (٣ نقط)",
                            "frequency": "twice daily (كل ١٢ ساعة)",
                            "duration": "1 week (لمدة أسبوع)",
                            "instructions": "نقط للأذن كل ١٢ ساعة لمدة أسبوع",
                        },
                        {
                            "raw_text": "Trimed Flu t قرص / ٨ ساعات",
                            "name": "Trimed Flu",
                            "dose": "1 tablet (قرص واحد)",
                            "frequency": "3 times daily (كل ٨ ساعات)",
                            "instructions": "قرص بعد الأكل كل ٨ ساعات",
                        },
                        {
                            "raw_text": "Mucotec 300mg كبسولة / ١٢ ساعة",
                            "name": "Mucotec",
                            "strength": "300mg",
                            "dose": "1 capsule (كبسولة واحدة)",
                            "frequency": "twice daily (كل ١٢ ساعة)",
                            "instructions": "كبسولة كل ١٢ ساعة بعد الأكل",
                        },
                        {
                            "raw_text": "Dongeel oral spray بخاخ بالفم ٤ مرات يومياً",
                            "name": "Dongeel",
                            "dose": "Oral spray (بخاخ بالفم)",
                            "frequency": "4 times daily (٤ مرات يومياً)",
                            "instructions": "بخاخ بالفم ٤ مرات يومياً",
                        },
                    ],
                )
                all_samples.append(sample)
                count_c += 1

            elif "11.43.38 AM" in fn and "(" not in fn:
                # Verified: Wateen Bassem Nasr - Pediatric Diarrhea & Anemia
                sample = format_prescription_sample(
                    image_rel_path=rel_p,
                    patient_age="1y",
                    patient_weight_kg=9.5,
                    medications=[
                        {
                            "raw_text": "Atlabacillus drop ٥ نقط يومياً لمدة اسبوعين",
                            "name": "Atlabacillus",
                            "dose": "5 drops (٥ نقط)",
                            "frequency": "once daily (يومياً)",
                            "duration": "2 weeks (لمدة أسبوعين)",
                            "instructions": "٥ نقط يومياً لمدة اسبوعين",
                        },
                        {
                            "raw_text": "Cetul drop ١/٢ قطارة عند اللزوم لمدة ٥ أيام",
                            "name": "Cetal",
                            "dose": "1/2 dropper (١/٢ قطارة)",
                            "frequency": "as needed (عند اللزوم)",
                            "duration": "5 days (لمدة ٥ أيام)",
                            "instructions": "١/٢ قطارة عند اللزوم لمدة ٥ أيام",
                        },
                        {
                            "raw_text": "Enrich drops ١/٢ قطارة يوم بعد يوم لمدة شهر",
                            "name": "Enrich",
                            "dose": "1/2 dropper (١/٢ قطارة)",
                            "frequency": "every other day (يوم بعد يوم)",
                            "duration": "1 month (لمدة شهر)",
                            "instructions": "١/٢ قطارة يوم بعد يوم لمدة شهر",
                        },
                        {
                            "raw_text": "Cevilin drop ٥ نقط يومياً لمدة شهر",
                            "name": "Cevilene",
                            "dose": "5 drops (٥ نقط)",
                            "frequency": "once daily (يومياً)",
                            "duration": "1 month (لمدة شهر)",
                            "instructions": "٥ نقط يومياً لمدة شهر",
                        },
                    ],
                )
                all_samples.append(sample)
                count_c += 1

        logger.info(f"Loaded {count_c} verified cases from 'my' clinic folder")

    # Shuffle with seed for reproducible split
    random.seed(42)
    random.shuffle(all_samples)

    total_samples = len(all_samples)
    split_idx = int(total_samples * train_split_ratio)
    train_samples = all_samples[:split_idx]
    val_samples = all_samples[split_idx:]

    train_export = EXPORTS_DIR / "vlm_lora_train.json"
    val_export = EXPORTS_DIR / "vlm_lora_val.json"
    manifest_export = EXPORTS_DIR / "training_manifest.json"

    with open(train_export, "w", encoding="utf-8") as f:
        json.dump(train_samples, f, ensure_ascii=False, indent=2)

    with open(val_export, "w", encoding="utf-8") as f:
        json.dump(val_samples, f, ensure_ascii=False, indent=2)

    stats = {
        "total_prescriptions": total_samples,
        "train_samples": len(train_samples),
        "val_samples": len(val_samples),
        "split_ratio": f"{int(train_split_ratio*100)}% / {int((1-train_split_ratio)*100)}%",
        "languages": "Bilingual (Egyptian Arabic Handwriting + English Brand Names)",
        "train_export": str(train_export),
        "val_export": str(val_export),
    }

    with open(manifest_export, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)

    logger.info(f"Successfully compiled unified dataset: {len(train_samples)} Train, {len(val_samples)} Val.")
    return stats


if __name__ == "__main__":
    s = build_complete_dataset()
    print(json.dumps(s, indent=2))
