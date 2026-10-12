"""
Packages the complete 572 Egyptian prescription dataset (images + JSON)
into a self-contained zip file for 1-click Google Colab / Cloud GPU fine-tuning.
"""

import json
import logging
import shutil
import zipfile
from pathlib import Path

from ..config import PROJECT_ROOT

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

EXPORTS_DIR = PROJECT_ROOT / "dataset" / "training_exports"
PACKAGE_DIR = EXPORTS_DIR / "colab_package"
PACKAGE_IMAGES_DIR = PACKAGE_DIR / "images"


def package_dataset_for_colab() -> Path:
    logger.info("Packaging dataset for 1-click Colab / Cloud GPU training...")
    if PACKAGE_DIR.exists():
        shutil.rmtree(PACKAGE_DIR)

    PACKAGE_IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    train_src = EXPORTS_DIR / "vlm_lora_train.json"
    val_src = EXPORTS_DIR / "vlm_lora_val.json"

    with open(train_src, "r", encoding="utf-8") as f:
        train_data = json.load(f)

    with open(val_src, "r", encoding="utf-8") as f:
        val_data = json.load(f)

    copied_images = set()

    def _process_split(split_items):
        new_items = []
        for it in split_items:
            orig_rel_path = it["image"]
            src_abs = PROJECT_ROOT / orig_rel_path

            if not src_abs.exists():
                logger.warning(f"Image not found: {src_abs}")
                continue

            # Target clean filename
            clean_name = f"{it['id']}_{src_abs.name}"
            clean_name = clean_name.replace(" ", "_").replace("(", "").replace(")", "")
            dest_img = PACKAGE_IMAGES_DIR / clean_name

            if clean_name not in copied_images:
                shutil.copy2(src_abs, dest_img)
                copied_images.add(clean_name)

            new_it = dict(it)
            new_it["image"] = f"images/{clean_name}"
            new_items.append(new_it)
        return new_items

    colab_train = _process_split(train_data)
    colab_val = _process_split(val_data)

    with open(PACKAGE_DIR / "train.json", "w", encoding="utf-8") as f:
        json.dump(colab_train, f, ensure_ascii=False, indent=2)

    with open(PACKAGE_DIR / "val.json", "w", encoding="utf-8") as f:
        json.dump(colab_val, f, ensure_ascii=False, indent=2)

    # Create zip file
    zip_path = EXPORTS_DIR / "gor3a_rx_dataset_572.zip"
    logger.info(f"Compressing into {zip_path}...")

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
        for file in PACKAGE_DIR.rglob("*"):
            if file.is_file():
                rel = file.relative_to(PACKAGE_DIR)
                zipf.write(file, arcname=str(rel))

    logger.info(f"Packaged {len(copied_images)} images into {zip_path} ({zip_path.stat().st_size / (1024*1024):.1f} MB)")
    return zip_path


if __name__ == "__main__":
    z = package_dataset_for_colab()
    print("Done:", z)
