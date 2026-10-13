import collections
import csv
import io
import json
import logging
import math
import re
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from ..utils.text import normalize_text, string_similarity
from .clinical_knowledge import get_clinical_knowledge
from .models import Drug
from .pricing import calculate_drug_pricing
from .seed_data import SEED_EGYPTIAN_DRUGS
from .web_enricher import web_enricher

logger = logging.getLogger(__name__)

DEFAULT_CSV_PATH = PROJECT_ROOT / "dataset" / "drugs" / "egyptian-drugs.csv"
CUSTOM_DRUGS_PATH = PROJECT_ROOT / "dataset" / "drugs" / "custom_updated_drugs.json"
TMP_CUSTOM_DRUGS_PATH = Path("/tmp/custom_updated_drugs.json")


def enrich_drug_with_clinical_info(drug: Drug, allow_live_web_fetch: bool = False) -> Drug:
    """Enriches a Drug model with structured clinical indications, likely diagnosis, and packaging visual."""
    if drug.clinical_indication is None:
        info = get_clinical_knowledge(
            drug_name=drug.commercial_name_en,
            generic_name=drug.generic_name,
            drug_class=drug.drug_class,
            dosage_form=drug.dosage_form,
            eda_id=drug.id,
            price_egp=drug.price_egp,
        )
        
        # Check Altibbi & Web Medical Profile for real photos and detailed indications (from local cache by default)
        web_profile = web_enricher.get_profile(
            commercial_name=drug.commercial_name_en,
            generic_name=drug.generic_name,
            commercial_name_ar=drug.commercial_name_ar,
            allow_live_fetch=allow_live_web_fetch,
        )
        if web_profile:
            info["web_profile"] = web_profile
            info["source_url"] = web_profile.get("source_url")
            info["source"] = web_profile.get("source", "موسوعة الطبي الطبية")
            if web_profile.get("what_it_treats_detailed"):
                info["what_it_treats_detailed"] = web_profile["what_it_treats_detailed"]
            if web_profile.get("contraindications"):
                info["contraindications"] = web_profile["contraindications"]
            if web_profile.get("precautions"):
                info["precautions"] = web_profile["precautions"]
            if web_profile.get("image_url"):
                info["real_image_url"] = web_profile["image_url"]
                info["svg_fallback_url"] = info.get("image_url") or f"/api/drugs/{drug.id}/image"
                info["image_url"] = web_profile["image_url"]
                info["image_type"] = "web_photo"

        drug.clinical_indication = info.get("indication_short")
        drug.likely_diagnosis = info.get("likely_diagnosis")
        drug.indication_details = info
        drug.image_url = info.get("image_url")
    return drug



def parse_csv_drug_row(i: int, row: Dict[str, str]) -> Drug:
    """Parses a row from the karem505 Egyptian drug database into a structured Drug model."""
    en_raw = row.get("commercial_name_en", "").strip()
    ar_raw = row.get("commercial_name_ar", "").strip() or None
    sci_raw = row.get("scientific_name", "").strip()

    # Extract concentration / strength
    str_match = re.search(
        r"(\d+(?:\.\d+)?\s*(?:MG\/5ML|MCG|MG|GM|G|%|IU|ML))", en_raw, re.IGNORECASE
    )
    strength = str_match.group(1).strip() if str_match else None

    # Clean base commercial brand name
    base_name = en_raw
    if str_match:
        before_str = en_raw[: str_match.start()].strip()
        if len(before_str) >= 2:
            base_name = before_str

    form_suffix_pattern = (
        r"\b(?:"
        r"EYE\s+(?:SUSP(?:\.|ENSION)?(?:\s+DROPS?)?|DROPS?|GEL|OINTMENT|OINT\.?)|"
        r"EAR\s+DROPS?|NASAL\s+DROPS?|ORAL\s+DROPS?|DROPS?|"
        r"SUSP(?:\.|ENSION)?|SYRUP|ORAL\s+LIQUID|SOLUTION|SOLN\.?|"
        r"F\.C\.\s*TABLETS?|TABLETS?|TABS?|CAPSULES?|CAPS?|SACHETS?|SUPP(?:OSITORIES)?|"
        r"AMPOULES?|AMPS?|VIALS?|INJ(?:\.|ECTION)?|"
        r"CREAM|OINTMENT|OINT\.?|GEL|LOTION|SHAMPOO|SPRAY|INHALER"
        r")\b.*$"
    )
    cleaned_base = re.sub(form_suffix_pattern, "", base_name, flags=re.IGNORECASE).strip()
    if len(cleaned_base) >= 2:
        base_name = cleaned_base

    # Pharmaceutical dosage form classification
    form = "Other"
    if re.search(r"\b(?:TAB|TABS|F\.C\.TABS?|TABLETS?)\b", en_raw, re.IGNORECASE):
        form = "Tablet"
    elif re.search(r"\b(?:CAP|CAPS|CAPSULES?)\b", en_raw, re.IGNORECASE):
        form = "Capsule"
    elif re.search(r"\b(?:SYR|SYRUP|SUSP|SUSPENSION|ORAL LIQUID)\b", en_raw, re.IGNORECASE):
        form = "Suspension"
    elif re.search(r"\b(?:DROPS?|ORAL DROPS?)\b", en_raw, re.IGNORECASE):
        form = "Drops"
    elif re.search(r"\b(?:CREAM|OINT|OINTMENT|GEL)\b", en_raw, re.IGNORECASE):
        form = "Topical"
    elif re.search(r"\b(?:AMP|AMPS|AMPOULES?|VIAL|VIALS|INJ|INJECTION)\b", en_raw, re.IGNORECASE):
        form = "Injection"
    elif re.search(r"\b(?:INH|INHALER|SPRAY)\b", en_raw, re.IGNORECASE):
        form = "Inhalation"

    price = None
    try:
        if row.get("price_egp"):
            price = float(row["price_egp"])
    except ValueError:
        pass

    active_ingredients = [x.strip() for x in sci_raw.split("+") if x.strip()]

    return Drug(
        id=f"EDA-DB-{i+1:05d}",
        commercial_name_en=base_name.title() or en_raw.title(),
        full_commercial_name_en=en_raw,
        commercial_name_ar=ar_raw,
        generic_name=sci_raw or "Unspecified",
        active_ingredients=active_ingredients,
        dosage_form=form,
        strength=strength,
        manufacturer=row.get("manufacturer", "").strip() or None,
        price_egp=price,
        route=row.get("route", "").strip() or None,
        drug_class=row.get("drug_class", "").strip() or None,
        aliases=[en_raw],
        source="Egyptian Drug Authority (EDA) / karem505 dataset",
        source_last_updated="June 2026",
    )


class DrugRepository:
    """
    High-performance in-memory indexed repository for Egyptian Drug Authority (EDA) records.
    Provides sub-millisecond exact, Arabic, alias, and fuzzy candidate lookups across 25,098 medicines,
    along with advanced multi-field search, facet filtering, and clinical alternatives discovery.
    """

    def __init__(
        self,
        drugs: Optional[List[Drug]] = None,
        csv_path: Optional[Path] = DEFAULT_CSV_PATH,
    ):
        self._drugs: List[Drug] = []
        self._id_index: Dict[str, Drug] = {}
        self._name_index: Dict[str, List[Drug]] = {}
        self._bucket_index: Dict[str, List[Drug]] = {}  # 1st-char bucket for fast fuzzy filtering
        self._generic_index: Dict[str, List[Drug]] = {}
        self._class_index: Dict[str, List[Drug]] = {}
        self._token_index: Dict[str, List[int]] = {}
        self._facets_cache: Optional[Dict[str, Any]] = None
        self._custom_drug_ids: set = set()

        # 1. Load curated seeds first to preserve priority & curated IDs
        if drugs is not None:
            self._drugs.extend(drugs)
        else:
            self._drugs.extend(SEED_EGYPTIAN_DRUGS)

        # 2. Load 25k Egyptian Drug Database if CSV exists
        if csv_path and csv_path.exists():
            self._load_from_csv(csv_path)

        # 3. Load persistent custom updated / added drugs if present
        self._load_custom_updated_drugs()

        self._staged_imports: Dict[str, Dict[str, Any]] = {}
        self._build_indexes()

    def _load_from_csv(self, csv_path: Path):
        try:
            with open(csv_path, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for i, row in enumerate(reader):
                    drug = parse_csv_drug_row(i, row)
                    self._drugs.append(drug)
            logger.info(f"Loaded {len(self._drugs)} medications into Egyptian Drug Repository.")
        except Exception as exc:
            logger.error(f"Failed to load Egyptian drug database CSV from {csv_path}: {exc}")

    def _build_indexes(self):
        self._id_index.clear()
        self._name_index.clear()
        self._bucket_index.clear()
        self._generic_index.clear()
        self._class_index.clear()
        self._token_index.clear()
        self._facets_cache = None

        for idx, drug in enumerate(self._drugs):
            self._id_index[drug.id] = drug

            # Index by normalized English commercial name
            norm_en = normalize_text(drug.commercial_name_en)
            if norm_en:
                self._name_index.setdefault(norm_en, []).append(drug)
                first_char = norm_en[0]
                self._bucket_index.setdefault(first_char, []).append(drug)

            # Index by full English commercial name if present
            if drug.full_commercial_name_en:
                norm_full = normalize_text(drug.full_commercial_name_en)
                if norm_full and norm_full != norm_en:
                    self._name_index.setdefault(norm_full, []).append(drug)

            # Index by normalized Arabic commercial name
            if drug.commercial_name_ar:
                norm_ar = normalize_text(drug.commercial_name_ar)
                if norm_ar:
                    self._name_index.setdefault(norm_ar, []).append(drug)
                    first_ar = norm_ar[0]
                    self._bucket_index.setdefault(first_ar, []).append(drug)

            # Index by aliases
            for alias in drug.aliases:
                norm_alias = normalize_text(alias)
                if norm_alias:
                    self._name_index.setdefault(norm_alias, []).append(drug)

            # Index by generic / active substance
            gen_key = (drug.generic_name or "").strip().upper()
            if gen_key and gen_key != "UNSPECIFIED":
                self._generic_index.setdefault(gen_key, []).append(drug)
                norm_gen = normalize_text(drug.generic_name)
                if norm_gen and norm_gen != norm_en:
                    self._name_index.setdefault(norm_gen, []).append(drug)
                    if norm_gen[0] not in self._bucket_index:
                        self._bucket_index.setdefault(norm_gen[0], []).append(drug)

            # Index by drug class
            cls_key = (drug.drug_class or "").strip().upper()
            if cls_key and cls_key != ".":
                self._class_index.setdefault(cls_key, []).append(drug)

            # Token index for fast full-text searching
            tokens = set()
            for text in (
                drug.commercial_name_en,
                drug.full_commercial_name_en,
                drug.commercial_name_ar,
                drug.generic_name,
                drug.manufacturer,
                drug.drug_class,
            ):
                if text:
                    for t in re.findall(r"[\w\u0600-\u06FF]+", text.lower()):
                        if len(t) >= 2:
                            tokens.add(t)
            for t in tokens:
                self._token_index.setdefault(t, []).append(idx)

    def get_total_count(self) -> int:
        return len(self._drugs)

    def get_all(self) -> List[Drug]:
        return list(self._drugs)

    def find_by_id(self, drug_id: str, allow_live_fetch: bool = True) -> Optional[Drug]:
        drug = self._id_index.get(drug_id)
        if drug:
            enrich_drug_with_clinical_info(drug, allow_live_web_fetch=allow_live_fetch)
        return drug

    def get_by_name(self, name: str) -> Optional[Drug]:
        """Returns the first matching Drug record by normalized name or alias."""
        norm = normalize_text(name)
        matches = self._name_index.get(norm)
        if matches:
            drug = matches[0]
            enrich_drug_with_clinical_info(drug)
            return drug
        return None

    def search_candidates(self, query: str, limit: int = 5) -> List[Tuple[Drug, float]]:
        """
        Retrieves top drug candidates from the Egyptian Drug Database
        for prescription OCR and normalizer (sub-millisecond candidate lookup).
        """
        norm_q = normalize_text(query)
        if not norm_q:
            return []

        # 1. Exact match check (< 0.01 ms)
        if norm_q in self._name_index:
            exact_matches = self._name_index[norm_q]
            return [(d, 1.0) for d in exact_matches[:limit]]

        scored_candidates: List[Tuple[Drug, float]] = []
        seen_ids = set()

        # 2. Check if query contains the full drug name as a discrete word
        for drug in self._drugs[:100]:
            norm_en = normalize_text(drug.commercial_name_en)
            if norm_en and re.search(rf"\b{re.escape(norm_en)}\b", norm_q):
                scored_candidates.append((drug, 0.95))
                seen_ids.add(drug.id)

        # 3. Filter candidates by initial character / prefix bucket
        first_char = norm_q[0] if norm_q else ""
        candidate_pool = self._bucket_index.get(first_char, [])
        if not candidate_pool or len(candidate_pool) < 20:
            candidate_pool = list(SEED_EGYPTIAN_DRUGS) + candidate_pool

        for drug in candidate_pool:
            if drug.id in seen_ids:
                continue

            best_sim = 0.0

            # Compare commercial name English
            en_sim = string_similarity(norm_q, drug.commercial_name_en)
            best_sim = max(best_sim, en_sim)

            # Compare commercial name Arabic
            if drug.commercial_name_ar:
                ar_sim = string_similarity(norm_q, drug.commercial_name_ar)
                best_sim = max(best_sim, ar_sim)

            # Compare aliases
            for alias in drug.aliases:
                al_sim = string_similarity(norm_q, alias)
                best_sim = max(best_sim, al_sim)

            # Compare generic / active substance name
            if drug.generic_name:
                gen_sim = string_similarity(norm_q, drug.generic_name)
                best_sim = max(best_sim, gen_sim)

            # Substring / prefix / token check
            norm_en = normalize_text(drug.commercial_name_en)
            norm_full = normalize_text(drug.full_commercial_name_en) if drug.full_commercial_name_en else ""
            if norm_en:
                q_tokens = norm_q.split()
                en_tokens = norm_en.split()
                full_tokens = norm_full.split() if norm_full else []

                if q_tokens and en_tokens and q_tokens[0] == en_tokens[0]:
                    q_rest = set(q_tokens[1:])
                    en_rest = set(en_tokens[1:])
                    if not q_rest:
                        best_sim = max(best_sim, 0.96)
                    else:
                        overlap = len(q_rest.intersection(en_rest))
                        best_sim = max(best_sim, min(0.98, 0.90 + 0.04 * overlap))
                elif q_tokens and full_tokens and q_tokens[0] == full_tokens[0]:
                    best_sim = max(best_sim, 0.95)
                elif re.search(rf"\b{re.escape(norm_en)}\b", norm_q):
                    best_sim = max(best_sim, 0.92)
                elif norm_q in norm_en and len(norm_q) >= 3:
                    ratio = len(norm_q) / len(norm_en)
                    best_sim = max(best_sim, min(0.88, 0.60 + 0.35 * ratio))

            if best_sim >= 0.50:
                scored_candidates.append((drug, best_sim))
                seen_ids.add(drug.id)

        scored_candidates.sort(key=lambda x: x[1], reverse=True)
        return scored_candidates[:limit]

    def search_advanced(
        self,
        query: str = "",
        form: Optional[str] = None,
        route: Optional[str] = None,
        manufacturer: Optional[str] = None,
        drug_class: Optional[str] = None,
        price_min: Optional[float] = None,
        price_max: Optional[float] = None,
        sort_by: str = "relevance",
        page: int = 1,
        page_size: int = 24,
    ) -> Dict[str, Any]:
        """
        Advanced full-featured search across 25,098 Egyptian medications.
        Supports multi-word text, Arabic & English, generic & trade names,
        category filters, price range, sorting, and pagination.
        """
        q = (query or "").strip().lower()
        candidates_with_score: List[Tuple[Drug, float]] = []

        # 1. Candidate selection
        if q:
            # Query tokens
            q_tokens = re.findall(r"[\w\u0600-\u06FF]+", q)
            matched_indices: set = set()
            for token in q_tokens:
                if token in self._token_index:
                    matched_indices.update(self._token_index[token])
                else:
                    # Partial token match in token index
                    for t_key, idx_list in self._token_index.items():
                        if token in t_key:
                            matched_indices.update(idx_list)

            target_pool = [self._drugs[i] for i in matched_indices] if matched_indices else self._drugs
        else:
            target_pool = self._drugs

        # 2. Filter & score
        for drug in target_pool:
            # Attribute filters
            if form and form.lower() != (drug.dosage_form or "").lower():
                continue
            if route and route.lower() not in (drug.route or "").lower():
                continue
            if manufacturer and manufacturer.lower() not in (drug.manufacturer or "").lower():
                continue
            if drug_class and drug_class.lower() not in (drug.drug_class or "").lower():
                continue
            if price_min is not None and (drug.price_egp is None or drug.price_egp < price_min):
                continue
            if price_max is not None and (drug.price_egp is None or drug.price_egp > price_max):
                continue

            score = 0.0
            if q:
                en = (drug.commercial_name_en or "").lower()
                full = (drug.full_commercial_name_en or "").lower()
                ar = (drug.commercial_name_ar or "").lower()
                gen = (drug.generic_name or "").lower()
                mfg = (drug.manufacturer or "").lower()
                cls = (drug.drug_class or "").lower()

                # Exact matches
                if q == en or q == full or (ar and q == ar):
                    score = 1000.0
                elif en.startswith(q) or full.startswith(q):
                    score = 700.0
                elif ar and ar.startswith(q):
                    score = 650.0
                elif q in en or q in full:
                    score = 450.0
                elif ar and q in ar:
                    score = 400.0
                elif q == gen:
                    score = 350.0
                elif gen.startswith(q):
                    score = 300.0
                elif q in gen:
                    score = 220.0
                elif any(q in act.lower() for act in drug.active_ingredients):
                    score = 200.0
                elif q in mfg:
                    score = 120.0
                elif q in cls:
                    score = 90.0

                if score <= 0:
                    continue

            candidates_with_score.append((drug, score))

        # 3. Sorting
        if q and sort_by == "relevance":
            candidates_with_score.sort(
                key=lambda x: (x[1], x[0].price_egp is not None, x[0].price_egp or 0),
                reverse=True,
            )
        elif sort_by == "price_asc":
            candidates_with_score.sort(
                key=lambda x: (x[0].price_egp is None, x[0].price_egp or 0)
            )
        elif sort_by == "price_desc":
            candidates_with_score.sort(
                key=lambda x: (x[0].price_egp is not None, x[0].price_egp or 0),
                reverse=True,
            )
        elif sort_by == "name_asc":
            candidates_with_score.sort(key=lambda x: (x[0].commercial_name_en or "").lower())
        elif sort_by == "name_desc":
            candidates_with_score.sort(
                key=lambda x: (x[0].commercial_name_en or "").lower(), reverse=True
            )
        else:
            # Default fallback
            candidates_with_score.sort(
                key=lambda x: (x[0].price_egp is not None, x[0].price_egp or 0), reverse=True
            )

        # 4. Pagination
        total = len(candidates_with_score)
        total_pages = max(1, math.ceil(total / max(1, page_size)))
        page = max(1, min(page, total_pages)) if total > 0 else 1
        start = (page - 1) * page_size
        page_items = [c[0] for c in candidates_with_score[start : start + page_size]]

        # 5. Enrich with pricing summary and clinical indications for each drug
        results_list = []
        for d in page_items:
            enrich_drug_with_clinical_info(d)
            pricing = calculate_drug_pricing(d)
            results_list.append(
                {
                    "drug": d.model_dump(),
                    "pricing": pricing.model_dump(),
                }
            )

        return {
            "query": query,
            "total": total,
            "page": page,
            "page_size": page_size,
            "total_pages": total_pages,
            "has_next": page < total_pages,
            "has_prev": page > 1,
            "results": results_list,
        }

    def find_alternatives(self, drug_id: str, limit: int = 12) -> Dict[str, Any]:
        """
        Finds exact generic equivalents (same active ingredients) and therapeutic
        class alternatives in the Egyptian market, with pricing comparisons.
        """
        drug = self.find_by_id(drug_id)
        if not drug:
            return {
                "target_drug": None,
                "exact_generics": [],
                "therapeutic_alternatives": [],
            }

        gen_key = (drug.generic_name or "").strip().upper()
        cls_key = (drug.drug_class or "").strip().upper()

        # 1. Exact Generics (المثائل بنفس المادة الفعالة)
        generics = []
        if gen_key and gen_key != "UNSPECIFIED":
            for d in self._generic_index.get(gen_key, []):
                if d.id != drug.id:
                    generics.append(d)

        generics.sort(
            key=lambda x: (
                x.dosage_form == drug.dosage_form,
                x.price_egp is not None,
                -(x.price_egp or 0),
            ),
            reverse=True,
        )

        # 2. Therapeutic Class Alternatives (بدائل نفس المجموعة العلاجية)
        therapeutics = []
        if cls_key and cls_key != ".":
            for d in self._class_index.get(cls_key, []):
                if d.id != drug.id and (d.generic_name or "").upper() != gen_key:
                    therapeutics.append(d)

        therapeutics.sort(
            key=lambda x: (
                x.dosage_form == drug.dosage_form,
                x.price_egp is not None,
                -(x.price_egp or 0),
            ),
            reverse=True,
        )

        def to_card(d: Drug):
            enrich_drug_with_clinical_info(d)
            p = calculate_drug_pricing(d)
            return {
                "drug": d.model_dump(),
                "pricing": p.model_dump(),
            }

        enrich_drug_with_clinical_info(drug)
        return {
            "target_drug": drug.model_dump(),
            "target_pricing": calculate_drug_pricing(drug).model_dump(),
            "exact_generics_count": len(generics),
            "exact_generics": [to_card(d) for d in generics[:limit]],
            "therapeutic_alternatives_count": len(therapeutics),
            "therapeutic_alternatives": [to_card(d) for d in therapeutics[:limit]],
        }

    def get_facets(self) -> Dict[str, Any]:
        """
        Returns precomputed search filter facets across the 25,098 Egyptian pharmaceutical database.
        """
        if self._facets_cache:
            return self._facets_cache

        form_counts = collections.Counter()
        route_counts = collections.Counter()
        mfg_counts = collections.Counter()
        class_counts = collections.Counter()
        prices = []

        for d in self._drugs:
            if d.dosage_form:
                form_counts[d.dosage_form] += 1
            if d.route:
                route_counts[d.route] += 1
            if d.manufacturer:
                mfg_counts[d.manufacturer] += 1
            if d.drug_class and d.drug_class != ".":
                class_counts[d.drug_class] += 1
            if d.price_egp is not None and d.price_egp > 0:
                prices.append(d.price_egp)

        self._facets_cache = {
            "total_registered_drugs": len(self._drugs),
            "priced_drugs_count": len(prices),
            "min_price": min(prices) if prices else 0.0,
            "max_price": max(prices) if prices else 0.0,
            "dosage_forms": [{"name": k, "count": v} for k, v in form_counts.most_common(20)],
            "routes": [{"name": k, "count": v} for k, v in route_counts.most_common(15)],
            "top_manufacturers": [{"name": k, "count": v} for k, v in mfg_counts.most_common(35)],
            "top_drug_classes": [{"name": k, "count": v} for k, v in class_counts.most_common(35)],
        }
        return self._facets_cache

    def _load_custom_updated_drugs(self):
        """Loads persistently saved custom updated or newly added medicines."""
        source_path = None
        if CUSTOM_DRUGS_PATH.exists():
            source_path = CUSTOM_DRUGS_PATH
        elif TMP_CUSTOM_DRUGS_PATH.exists():
            source_path = TMP_CUSTOM_DRUGS_PATH

        if not source_path:
            return

        try:
            with open(source_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                custom_list = data.get("drugs", [])
                existing_map = {d.id: idx for idx, d in enumerate(self._drugs)}
                applied_count = 0
                for item in custom_list:
                    drug_obj = Drug(**item)
                    self._custom_drug_ids.add(drug_obj.id)
                    if drug_obj.id in existing_map:
                        self._drugs[existing_map[drug_obj.id]] = drug_obj
                    else:
                        self._drugs.append(drug_obj)
                        existing_map[drug_obj.id] = len(self._drugs) - 1
                    applied_count += 1
                logger.info(f"Loaded {applied_count} custom/updated drugs from {source_path}")
        except Exception as exc:
            logger.warning(f"Failed to load custom updated drugs from {source_path}: {exc}")

    def save_custom_drugs(self) -> bool:
        """Saves only custom updated or newly added drugs into custom_updated_drugs.json."""
        if not self._custom_drug_ids:
            return True

        custom_drugs = [self._id_index[did].model_dump() for did in self._custom_drug_ids if did in self._id_index]
        data = {
            "version": "2026.1",
            "count": len(custom_drugs),
            "drugs": custom_drugs,
        }
        saved = False
        try:
            CUSTOM_DRUGS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(CUSTOM_DRUGS_PATH, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            saved = True
        except Exception as e:
            logger.warning(f"Could not write to {CUSTOM_DRUGS_PATH} (read-only): {e}")

        try:
            TMP_CUSTOM_DRUGS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(TMP_CUSTOM_DRUGS_PATH, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            saved = True
        except Exception as e:
            logger.warning(f"Could not write to {TMP_CUSTOM_DRUGS_PATH}: {e}")

        return saved

    def stage_import(self, raw_records: List[Dict[str, Any]], filename: str = "import.csv") -> Dict[str, Any]:
        """
        Parses incoming dataset, checks against existing 25k records without altering the database,
        and categorizes every record into:
        1. 'new': Brand new drug not found in current database.
        2. 'updated': Existing drug found, with changes (e.g. updated price, arabic name, active ingredient).
        3. 'duplicate': Existing drug found with 100% identical data.
        """
        staging_id = str(uuid.uuid4())
        new_items: List[Dict[str, Any]] = []
        updated_items: List[Dict[str, Any]] = []
        duplicate_items: List[Dict[str, Any]] = []

        # Build quick normalized lookup map for fast diffing
        name_map: Dict[str, List[Drug]] = {}
        full_name_map: Dict[str, List[Drug]] = {}
        base_name_map: Dict[str, List[Drug]] = {}
        ar_name_map: Dict[str, List[Drug]] = {}
        for d in self._drugs:
            key = normalize_text(d.commercial_name_en)
            if key:
                name_map.setdefault(key, []).append(d)
                base = re.sub(r"\b\d+(\.\d+)?\s*(mg|g|gm|ml|mcg|iu|%)\b", "", key).strip()
                if base and base != key:
                    base_name_map.setdefault(base, []).append(d)
            if d.full_commercial_name_en:
                fkey = normalize_text(d.full_commercial_name_en)
                if fkey:
                    full_name_map.setdefault(fkey, []).append(d)
            if d.commercial_name_ar:
                arkey = normalize_text(d.commercial_name_ar)
                if arkey:
                    ar_name_map.setdefault(arkey, []).append(d)

        batch_seen: set = set()

        def normalize_form_str(form_str: Optional[str]) -> str:
            if not form_str:
                return "Other"
            fl = form_str.lower()
            if any(x in fl for x in ["tab", "قرص", "أقراص"]):
                return "Tablet"
            if any(x in fl for x in ["cap", "كبسول", "كبسولات"]):
                return "Capsule"
            if any(x in fl for x in ["susp", "syr", "شراب", "معلق"]):
                return "Suspension"
            if any(x in fl for x in ["drop", "نقط", "قطرة"]):
                return "Drops"
            if any(x in fl for x in ["cream", "oint", "مرهم", "كريم", "gel", "جل"]):
                return "Topical"
            if any(x in fl for x in ["amp", "vial", "inj", "حقن", "حقنة", "أمبول"]):
                return "Injection"
            if any(x in fl for x in ["inh", "spray", "بخاخ", "استنشاق"]):
                return "Inhalation"
            return form_str.title()

        def extract_clean_price(val: Any) -> Optional[float]:
            if val is None or val == "":
                return None
            try:
                s = str(val).strip()
                cleaned = re.sub(r"[^\d.]", "", s)
                if cleaned:
                    return round(float(cleaned), 2)
            except (ValueError, TypeError):
                pass
            return None

        for idx, row in enumerate(raw_records):
            # 1. Flexible column extraction (English & Arabic aliases)
            def get_f(aliases: List[str]) -> Optional[str]:
                for a in aliases:
                    if a in row and row[a] is not None:
                        v = str(row[a]).strip()
                        if v and v.lower() != "nan" and v.lower() != "null":
                            return v
                return None

            name_en = get_f([
                "commercial_name_en", "trade_name", "trade_name_en", "TradeName",
                "name", "brand_name", "NameEn", "product_name", "commercial_name",
                "اسم الدواء بالانجليزي", "الاسم بالانجليزية"
            ]) or ""
            if not name_en:
                continue

            name_ar = get_f([
                "commercial_name_ar", "trade_name_ar", "TradeNameAr", "name_ar",
                "arabic_name", "commercial_ar", "اسم الدواء بالعربي", "الاسم بالعربية",
                "اسم الدواء", "الاسم التجاري"
            ])

            generic = get_f([
                "generic_name", "scientific_name", "active_ingredient",
                "active_ingredients", "ActiveIngredient", "GenericName",
                "ScientificName", "المادة الفعالة", "الاسم العلمي"
            ]) or "Unspecified"

            dosage_form = normalize_form_str(get_f([
                "dosage_form", "form", "Form", "dosageForm", "DosageForm",
                "الشكل الصيدلي", "شكل الدواء"
            ]))

            strength = get_f([
                "strength", "concentration", "Strength", "Concentration",
                "التركيز", "تركيز الدواء"
            ])

            price_val = extract_clean_price(get_f([
                "price_egp", "price", "Price", "public_price", "retail_price",
                "السعر", "سعر الدواء", "سعر الجمهور"
            ]))

            manufacturer = get_f([
                "manufacturer", "company", "Company", "Manufacturer",
                "الشركة المصنعة", "الشركة", "المصنع"
            ])

            drug_class = get_f([
                "drug_class", "category", "class", "Class", "Category",
                "التصنيف الدوائي", "المجموعة العلاجية", "التصنيف"
            ])

            route = get_f(["route", "Route", "طريقة الاستخدام"])
            given_id = get_f(["id", "drug_id", "eda_id", "ID", "code", "رقم التسجيل"])

            norm_key = normalize_text(name_en)

            # Check internal duplicate within this upload batch
            batch_item_key = (norm_key, (strength or "").strip().lower(), (dosage_form or "").lower(), round(price_val, 2) if price_val is not None else None)
            if batch_item_key in batch_seen:
                duplicate_items.append({
                    "existing_id": "BATCH-DUP",
                    "commercial_name_en": name_en,
                    "dosage_form": dosage_form,
                    "strength": strength or "N/A",
                    "price_egp": price_val,
                    "reason": "تكرار متطابق داخل نفس الملف المرفوع (Duplicate Row)",
                })
                continue
            batch_seen.add(batch_item_key)

            # 2. Match against current repository
            matched_drug: Optional[Drug] = None

            # Priority 1: Match by given ID
            if given_id and given_id in self._id_index:
                matched_drug = self._id_index[given_id]

            # Priority 2: Match by exact or normalized commercial / full / base name
            candidates: List[Drug] = []
            if not matched_drug:
                if norm_key in name_map:
                    candidates = name_map[norm_key]
                elif norm_key in full_name_map:
                    candidates = full_name_map[norm_key]
                else:
                    base_in = re.sub(r"\b\d+(\.\d+)?\s*(mg|g|gm|ml|mcg|iu|%)\b", "", norm_key).strip()
                    if base_in and base_in in name_map:
                        candidates = name_map[base_in]
                    elif base_in and base_in in base_name_map:
                        candidates = base_name_map[base_in]

            # Match Arabic name if still no candidate
            if not matched_drug and not candidates and name_ar:
                ar_k = normalize_text(name_ar)
                if ar_k in ar_name_map:
                    candidates = ar_name_map[ar_k]

            if not matched_drug and candidates:
                norm_str_in = re.sub(r"\s+", "", (strength or "").lower())
                # First try matching both form and strength
                for c in candidates:
                    norm_c_str = re.sub(r"\s+", "", (c.strength or "").lower())
                    form_match = (not dosage_form or dosage_form == "Other" or not c.dosage_form or c.dosage_form.lower() == dosage_form.lower())
                    if norm_str_in and norm_c_str and norm_str_in == norm_c_str and form_match:
                        matched_drug = c
                        break
                # Second try matching strength only
                if not matched_drug and norm_str_in:
                    for c in candidates:
                        norm_c_str = re.sub(r"\s+", "", (c.strength or "").lower())
                        if norm_str_in == norm_c_str:
                            matched_drug = c
                            break
                # Third try matching form only
                if not matched_drug and dosage_form and dosage_form != "Other":
                    for c in candidates:
                        if c.dosage_form and c.dosage_form.lower() == dosage_form.lower():
                            matched_drug = c
                            break
                # Fallback to first candidate if only one exists or no specific filter matched
                if not matched_drug:
                    matched_drug = candidates[0]

            # 3. Categorize into (Updated, Duplicate, or New)
            if matched_drug:
                diffs: List[Dict[str, Any]] = []

                # Price diff check
                if price_val is not None:
                    old_p = round(matched_drug.price_egp, 2) if matched_drug.price_egp is not None else None
                    if old_p is None or abs(price_val - old_p) > 0.01:
                        diffs.append({
                            "field": "price_egp",
                            "label": "السعر الرسمي (EGP)",
                            "old": f"{old_p:.2f} EGP" if old_p is not None else "غير مسجل",
                            "new": f"{price_val:.2f} EGP",
                        })

                # Arabic name diff check
                if name_ar and name_ar != matched_drug.commercial_name_ar:
                    diffs.append({
                        "field": "commercial_name_ar",
                        "label": "الاسم التجاري بالعربي",
                        "old": matched_drug.commercial_name_ar or "غير مسجل",
                        "new": name_ar,
                    })

                # Generic / active diff check
                if generic and generic != "Unspecified" and generic != matched_drug.generic_name:
                    diffs.append({
                        "field": "generic_name",
                        "label": "المادة الفعالة (Generic)",
                        "old": matched_drug.generic_name,
                        "new": generic,
                    })

                # Strength diff check
                if strength and strength != matched_drug.strength:
                    diffs.append({
                        "field": "strength",
                        "label": "التركيز",
                        "old": matched_drug.strength or "غير محدد",
                        "new": strength,
                    })

                # Manufacturer diff check
                if manufacturer and manufacturer != matched_drug.manufacturer:
                    diffs.append({
                        "field": "manufacturer",
                        "label": "الشركة المصنعة",
                        "old": matched_drug.manufacturer or "غير مسجل",
                        "new": manufacturer,
                    })

                if diffs:
                    updated_items.append({
                        "existing_id": matched_drug.id,
                        "commercial_name_en": matched_drug.commercial_name_en,
                        "full_commercial_name_en": matched_drug.full_commercial_name_en or matched_drug.commercial_name_en,
                        "dosage_form": dosage_form or matched_drug.dosage_form,
                        "changes": diffs,
                        "proposed": {
                            "price_egp": price_val if price_val is not None else matched_drug.price_egp,
                            "commercial_name_ar": name_ar or matched_drug.commercial_name_ar,
                            "generic_name": generic if generic != "Unspecified" else matched_drug.generic_name,
                            "strength": strength or matched_drug.strength,
                            "dosage_form": dosage_form or matched_drug.dosage_form,
                            "manufacturer": manufacturer or matched_drug.manufacturer,
                            "drug_class": drug_class or matched_drug.drug_class,
                            "route": route or matched_drug.route,
                        },
                    })
                else:
                    duplicate_items.append({
                        "existing_id": matched_drug.id,
                        "commercial_name_en": matched_drug.commercial_name_en,
                        "dosage_form": matched_drug.dosage_form,
                        "strength": matched_drug.strength or "N/A",
                        "price_egp": matched_drug.price_egp,
                        "reason": "متطابق تماماً مع المستحضر المسجل حالياً (نفس السعر والمواصفات)",
                    })
            else:
                # Brand new medicine to add
                new_id = given_id or f"EDA-NEW-{len(self._drugs) + len(new_items) + 1:05d}"
                new_items.append({
                    "id": new_id,
                    "commercial_name_en": name_en.title(),
                    "full_commercial_name_en": name_en,
                    "commercial_name_ar": name_ar,
                    "generic_name": generic,
                    "dosage_form": dosage_form,
                    "strength": strength,
                    "price_egp": price_val,
                    "manufacturer": manufacturer,
                    "drug_class": drug_class,
                    "route": route,
                    "source": "EDA Updated Registry (New Addition)",
                    "source_last_updated": "2026",
                })

        staged_data = {
            "staging_id": staging_id,
            "filename": filename,
            "summary": {
                "total_records": len(raw_records),
                "new_count": len(new_items),
                "updated_count": len(updated_items),
                "duplicate_count": len(duplicate_items),
            },
            "new_items": new_items,
            "updated_items": updated_items,
            "duplicate_items": duplicate_items,
        }

        self._staged_imports[staging_id] = staged_data
        return {
            "staging_id": staging_id,
            "filename": filename,
            "summary": staged_data["summary"],
            "new_sample": new_items[:50],
            "updated_sample": updated_items[:50],
            "duplicate_sample": duplicate_items[:50],
        }

    def get_staging_items(
        self,
        staging_id: str,
        item_type: str = "new",
        page: int = 1,
        limit: int = 50,
        search: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Returns paginated and searchable lists of staged items for admin review."""
        staged = self._staged_imports.get(staging_id)
        if not staged:
            return {"error": "Staging session expired or not found"}

        if item_type == "updated":
            items = staged.get("updated_items", [])
        elif item_type == "duplicate":
            items = staged.get("duplicate_items", [])
        else:
            items = staged.get("new_items", [])

        # Filter by search term
        if search:
            q = search.lower().strip()
            items = [
                x for x in items
                if q in str(x.get("commercial_name_en", "")).lower()
                or q in str(x.get("commercial_name_ar", "")).lower()
                or q in str(x.get("generic_name", "")).lower()
                or q in str(x.get("existing_id", "")).lower()
                or q in str(x.get("id", "")).lower()
            ]

        total = len(items)
        start = (page - 1) * limit
        end = start + limit
        paginated = items[start:end]

        return {
            "staging_id": staging_id,
            "type": item_type,
            "total": total,
            "page": page,
            "limit": limit,
            "total_pages": math.ceil(total / limit) if total > 0 else 1,
            "items": paginated,
        }

    def commit_import(
        self,
        staging_id: str,
        import_new: bool = True,
        apply_updates: bool = True,
    ) -> Dict[str, Any]:
        """
        Commits verified staged changes to the active in-memory repository and saves to disk.
        Does not touch duplicate records.
        """
        staged = self._staged_imports.get(staging_id)
        if not staged:
            raise ValueError("Staging session expired or not found. Please upload the file again.")

        new_items = staged.get("new_items", []) if import_new else []
        updated_items = staged.get("updated_items", []) if apply_updates else []
        duplicates_count = len(staged.get("duplicate_items", []))

        # 1. Apply field updates to existing drugs
        updated_count = 0
        for u in updated_items:
            existing_id = u["existing_id"]
            if existing_id in self._id_index:
                drug = self._id_index[existing_id]
                prop = u.get("proposed", {})
                if prop.get("price_egp") is not None:
                    drug.price_egp = prop["price_egp"]
                if prop.get("commercial_name_ar"):
                    drug.commercial_name_ar = prop["commercial_name_ar"]
                if prop.get("generic_name") and prop["generic_name"] != "Unspecified":
                    drug.generic_name = prop["generic_name"]
                if prop.get("strength"):
                    drug.strength = prop["strength"]
                if prop.get("dosage_form"):
                    drug.dosage_form = prop["dosage_form"]
                if prop.get("manufacturer"):
                    drug.manufacturer = prop["manufacturer"]
                if prop.get("drug_class"):
                    drug.drug_class = prop["drug_class"]
                if prop.get("route"):
                    drug.route = prop["route"]
                drug.source_last_updated = "October 2026 (Admin Updated)"
                self._custom_drug_ids.add(drug.id)
                updated_count += 1

        # 2. Add brand new medicines
        added_count = 0
        for n in new_items:
            drug = Drug(
                id=n.get("id") or f"EDA-NEW-{len(self._drugs) + 1:05d}",
                commercial_name_en=n.get("commercial_name_en", "Unnamed Medicine"),
                full_commercial_name_en=n.get("full_commercial_name_en") or n.get("commercial_name_en"),
                commercial_name_ar=n.get("commercial_name_ar"),
                generic_name=n.get("generic_name") or "Unspecified",
                active_ingredients=[x.strip() for x in (n.get("generic_name") or "").split("+") if x.strip()],
                dosage_form=n.get("dosage_form") or "Other",
                strength=n.get("strength"),
                manufacturer=n.get("manufacturer"),
                price_egp=n.get("price_egp"),
                route=n.get("route"),
                drug_class=n.get("drug_class"),
                aliases=[n.get("commercial_name_en", "")],
                source="Egyptian Drug Authority (EDA) / Admin New Import",
                source_last_updated="October 2026",
            )
            self._drugs.append(drug)
            self._custom_drug_ids.add(drug.id)
            added_count += 1

        # 3. Rebuild repository indexes
        self._build_indexes()

        # 4. Save persistent dataset to disk
        self.save_custom_drugs()

        # Clean up staging session
        self._staged_imports.pop(staging_id, None)

        return {
            "success": True,
            "message": f"تمت العملية بنجاح: تم إضافة {added_count:,} صنف جديد وتحديث {updated_count:,} صنف موجود، مع تجاهل {duplicates_count:,} صنف مكرر تماماً دون أي ازدواجية.",
            "added_count": added_count,
            "updated_count": updated_count,
            "ignored_duplicates_count": duplicates_count,
            "total_drugs": len(self._drugs),
        }

    def export_drugs_json(self) -> str:
        """Exports all current drugs as clean formatted JSON string."""
        data = {
            "export_date": "2026-10-13",
            "total_count": len(self._drugs),
            "drugs": [d.model_dump() for d in self._drugs],
        }
        return json.dumps(data, ensure_ascii=False, indent=2)

    def export_drugs_csv(self) -> str:
        """Exports all current drugs as clean CSV string."""
        output = io.StringIO()
        fieldnames = [
            "id", "commercial_name_en", "commercial_name_ar", "generic_name",
            "dosage_form", "strength", "price_egp", "manufacturer",
            "drug_class", "route", "source"
        ]
        writer = csv.DictWriter(output, fieldnames=fieldnames)
        writer.writeheader()
        for d in self._drugs:
            writer.writerow({
                "id": d.id,
                "commercial_name_en": d.commercial_name_en,
                "commercial_name_ar": d.commercial_name_ar or "",
                "generic_name": d.generic_name,
                "dosage_form": d.dosage_form,
                "strength": d.strength or "",
                "price_egp": d.price_egp if d.price_egp is not None else "",
                "manufacturer": d.manufacturer or "",
                "drug_class": d.drug_class or "",
                "route": d.route or "",
                "source": d.source,
            })
        return output.getvalue()


# Global singleton instance loaded once at startup
drug_repository = DrugRepository()
