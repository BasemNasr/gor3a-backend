"""
Web Drug Enrichment & Medical Information Service.
Integrates online medical encyclopedias (Altibbi - موسوعة الطبي) and pharmaceutical sources
to fetch authentic packaging photos, detailed disease descriptions, clinical uses,
contraindications, and precautions for Egyptian medicines.
"""

import json
import logging
import os
import re
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

CACHE_FILE_PATH = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "dataset", "drugs", "web_cache.json")
)

USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


def _normalize_name_key(text: Optional[str]) -> str:
    """Normalizes medication name for consistent dictionary lookups."""
    if not text:
        return ""
    t = text.lower().strip()
    # Normalize Arabic chars
    t = re.sub(r"[إأآا]", "ا", t)
    t = re.sub(r"[ىي]", "ي", t)
    t = re.sub(r"ة", "ه", t)
    t = re.sub(r"[\u064B-\u065F]", "", t)  # strip tashkeel
    t = re.sub(r"[-_\s\.]+", " ", t).strip()
    return t


def _clean_html_text(raw_html: Optional[str]) -> str:
    """Converts HTML snippet into clean readable plain text with bullets."""
    if not raw_html:
        return ""
    text = re.sub(r'<li[^>]*>', ' • ', raw_html)
    text = re.sub(r'</li>', '\n', text)
    text = re.sub(r'<p[^>]*>', '', text)
    text = re.sub(r'</p>', '\n', text)
    text = re.sub(r'<br\s*/?>', '\n', text)
    text = re.sub(r'<[^>]+>', ' ', text)
    text = (
        text.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", '"')
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    )
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    return "\n".join(lines)


class WebDrugEnricher:
    """
    Retrieves authentic medical data and real photos from Altibbi (الطبي)
    with local disk cache, seed dictionaries, and live online scraper fallbacks.
    """

    def __init__(self, cache_file: str = CACHE_FILE_PATH):
        self.cache_file = cache_file
        self.profiles: Dict[str, Dict[str, Any]] = {}
        self.seed_map: Dict[str, str] = {}
        self._load_cache()

    def _load_cache(self) -> None:
        """Loads cached web profiles from disk if available."""
        if os.path.exists(self.cache_file):
            try:
                with open(self.cache_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.profiles = data.get("profiles", {})
                    self.seed_map = data.get("seed_map", {})
                    logger.info("Loaded %d web drug profiles from cache", len(self.profiles))
            except Exception as e:
                logger.warning("Could not read web drug cache: %s", e)

    def _save_cache(self) -> None:
        """Saves memory cache back to disk."""
        try:
            os.makedirs(os.path.dirname(self.cache_file), exist_ok=True)
            with open(self.cache_file, "w", encoding="utf-8") as f:
                json.dump(
                    {"profiles": self.profiles, "seed_map": self.seed_map},
                    f,
                    ensure_ascii=False,
                    indent=2,
                )
        except Exception as e:
            logger.warning("Could not write to web drug cache: %s", e)

    def get_profile(
        self,
        commercial_name: Optional[str] = None,
        generic_name: Optional[str] = None,
        commercial_name_ar: Optional[str] = None,
        allow_live_fetch: bool = True,
    ) -> Optional[Dict[str, Any]]:
        """
        Retrieves rich medical profile and real packaging image from Altibbi.
        Checks trade brand, Arabic name, generic INN, and aliases.
        """
        candidates_to_try: List[str] = []

        for val in [commercial_name_ar, commercial_name, generic_name]:
            if val:
                norm = _normalize_name_key(val)
                if norm and norm not in candidates_to_try:
                    candidates_to_try.append(norm)

                # Add isolated single-word brand stem (e.g. "nazowell" from "NAZOWELL 150 MG 3 CAPS.")
                first_word = norm.split()[0] if norm else ""
                if first_word and len(first_word) >= 3 and first_word not in candidates_to_try:
                    candidates_to_try.append(first_word)

        # 1. Check in cache via seed_map or direct slug
        for cand in candidates_to_try:
            slug = self.seed_map.get(cand) or self.seed_map.get(_normalize_name_key(cand))
            if slug and slug in self.profiles:
                return self.profiles[slug]

            # Direct match in profiles
            if cand in self.profiles:
                return self.profiles[cand]

        # 2. Try generic substances mappings
        generic_map = {
            "fluconazole": "ديفلوكان",
            "mometasone": "نازونيكس",
            "metronidazole": "فلاجيل",
            "amoxicillin": "اوجمنتين",
            "nifuroxazide": "انتينال",
            "paracetamol": "باراسيتامول",
            "diclofenac": "كاتافلام",
            "pantoprazole": "بانتوبرازول",
            "esomeprazole": "نيكسيوم",
            "ibuprofen": "بروفين",
            "azithromycin": "زيثروماكس",
        }
        if generic_name:
            gn = generic_name.lower().strip()
            for k, slug_target in generic_map.items():
                if k in gn:
                    if slug_target in self.profiles:
                        return self.profiles[slug_target]

        # 3. Dynamic live fetch from Altibbi if allowed
        if allow_live_fetch:
            for cand in candidates_to_try:
                # Only try Arabic slugs or translated generic terms
                profile = self._scrape_altibbi(cand)
                if profile:
                    slug = profile["slug"]
                    self.profiles[slug] = profile
                    self.seed_map[cand] = slug
                    self._save_cache()
                    return profile

        return None

    def _scrape_altibbi(self, query: str) -> Optional[Dict[str, Any]]:
        """Live scraper for Altibbi drug pages."""
        # Clean query
        q_clean = query.strip()
        if not q_clean:
            return None

        # Build Altibbi URL
        adwya_encoded = urllib.parse.quote("الادوية")
        slug_encoded = urllib.parse.quote(q_clean)
        url = f"https://altibbi.com/{adwya_encoded}/{slug_encoded}"

        headers = {"User-Agent": USER_AGENT}
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=5) as resp:
                final_url = urllib.parse.unquote(resp.geturl())
                if final_url.endswith("/الادوية/الاسماء-التجارية") or final_url.endswith("/الادوية"):
                    return None

                html = resp.read().decode("utf-8", errors="ignore")

                # Image (filter out Altibbi generic stock placeholder illustrations like parsley)
                img_m = re.search(r'https://cdn\.altibbi\.com/cdn/cache/large/image/[^\s"\'<>]+', html)
                img_url = img_m.group(0) if img_m else None
                if img_url and any(bad in img_url for bad in ("faa0918631d3ee1b46c64a26f3cd62b8", "0498671e1179d115720e5f5bbae114f0")):
                    img_url = None

                # Title
                title_m = re.search(r'<title>(.*?)</title>', html)
                title = title_m.group(1).split("|")[0].strip() if title_m else q_clean

                # Uses (termText0)
                uses_m = re.search(r'<article[^>]*id=["\']termText0["\'][^>]*>(.*?)</article>', html, re.S)
                uses_text = _clean_html_text(uses_m.group(1)) if uses_m else ""

                # Contraindications (termText1)
                contra_m = re.search(r'<article[^>]*id=["\']termText1["\'][^>]*>(.*?)</article>', html, re.S)
                contra_text = _clean_html_text(contra_m.group(1)) if contra_m else ""

                # Precautions (termText3)
                prec_m = re.search(r'<article[^>]*id=["\']termText3["\'][^>]*>(.*?)</article>', html, re.S)
                prec_text = _clean_html_text(prec_m.group(1)) if prec_m else ""

                if not uses_text and not img_url:
                    return None

                return {
                    "slug": q_clean,
                    "title": title,
                    "image_url": img_url,
                    "source_url": final_url,
                    "what_it_treats_detailed": uses_text,
                    "contraindications": contra_text,
                    "precautions": prec_text,
                    "concise_indication": (uses_text.splitlines()[0] if uses_text else "")[:140],
                    "source": "Altibbi Medical Encyclopedia (موسوعة الطبي)",
                }
        except Exception as e:
            logger.debug("Altibbi scrape for '%s' failed: %s", query, e)
            return None

    def get_local_image_path(self, profile_or_slug: Any) -> Optional[str]:
        """Returns path to cached image on local disk if available, or downloads it from Altibbi CDN."""
        images_dir = os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "..", "..", "dataset", "drugs", "images")
        )
        if not os.path.exists(images_dir):
            try:
                os.makedirs(images_dir, exist_ok=True)
            except Exception:
                return None

        slug = ""
        img_url = ""
        if isinstance(profile_or_slug, dict):
            slug = profile_or_slug.get("slug") or ""
            img_url = profile_or_slug.get("image_url") or ""
        elif isinstance(profile_or_slug, str):
            slug = profile_or_slug

        if not slug:
            return None

        safe_slug = re.sub(r'[\\/*?:"<>|]', "", slug).strip()
        for ext in [".png", ".jpg", ".webp", ".jpeg"]:
            p = os.path.join(images_dir, f"{safe_slug}{ext}")
            if os.path.exists(p) and os.path.getsize(p) > 0:
                return p

        # If not cached locally but img_url is present, download it
        if img_url:
            ext = ".webp"
            if ".png" in img_url:
                ext = ".png"
            elif ".jpg" in img_url or ".jpeg" in img_url:
                ext = ".jpg"
            out_file = os.path.join(images_dir, f"{safe_slug}{ext}")
            try:
                req = urllib.request.Request(img_url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=5) as res:
                    with open(out_file, "wb") as f_out:
                        f_out.write(res.read())
                if os.path.exists(out_file) and os.path.getsize(out_file) > 0:
                    return out_file
            except Exception as e:
                logger.debug("Could not cache image for %s: %s", slug, e)

        return None


# Global singleton
web_enricher = WebDrugEnricher()
