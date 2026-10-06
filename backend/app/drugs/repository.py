import re
from typing import Dict, List, Optional, Tuple
from ..benchmark.metrics import normalize_text, string_similarity
from .models import Drug
from .seed_data import SEED_EGYPTIAN_DRUGS


class DrugRepository:
    """
    In-memory indexed repository for Egyptian Drug Authority (EDA) records.
    Provides fast exact, alias, and fuzzy candidate lookups with full source provenance.
    """

    def __init__(self, drugs: Optional[List[Drug]] = None):
        self._drugs: List[Drug] = drugs or list(SEED_EGYPTIAN_DRUGS)
        self._id_index: Dict[str, Drug] = {}
        self._name_index: Dict[str, List[Drug]] = {}
        self._build_indexes()

    def _build_indexes(self):
        self._id_index.clear()
        self._name_index.clear()

        for drug in self._drugs:
            self._id_index[drug.id] = drug

            # Index by normalized English name
            norm_en = normalize_text(drug.commercial_name_en)
            if norm_en:
                self._name_index.setdefault(norm_en, []).append(drug)

            # Index by normalized Arabic name
            if drug.commercial_name_ar:
                norm_ar = normalize_text(drug.commercial_name_ar)
                if norm_ar:
                    self._name_index.setdefault(norm_ar, []).append(drug)

            # Index by aliases
            for alias in drug.aliases:
                norm_alias = normalize_text(alias)
                if norm_alias:
                    self._name_index.setdefault(norm_alias, []).append(drug)

    def get_total_count(self) -> int:
        return len(self._drugs)

    def get_all(self) -> List[Drug]:
        return list(self._drugs)

    def find_by_id(self, drug_id: str) -> Optional[Drug]:
        return self._id_index.get(drug_id)

    def get_by_name(self, name: str) -> Optional[Drug]:
        """Returns the first matching Drug record by normalized name or alias."""
        norm = normalize_text(name)
        matches = self._name_index.get(norm)
        return matches[0] if matches else None

    def search_candidates(self, query: str, limit: int = 5) -> List[Tuple[Drug, float]]:
        """
        Retrieves top drug candidates from the Egyptian Drug Database
        for a given query string (e.g., 'Flajyl', 'Curam 1g', 'كيورام').
        Returns list of (Drug, similarity_score) sorted descending by score.
        """
        norm_q = normalize_text(query)
        if not norm_q:
            return []

        # 1. Exact match check
        if norm_q in self._name_index:
            exact_matches = self._name_index[norm_q]
            return [(d, 1.0) for d in exact_matches[:limit]]

        # 2. Fuzzy / Substring scoring across unique drugs
        scored_candidates: List[Tuple[Drug, float]] = []
        seen_ids = set()

        for drug in self._drugs:
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

            # Check if query contains the full drug name as a discrete word (e.g. "curam 1g tablet" contains "curam")
            norm_en = normalize_text(drug.commercial_name_en)
            if norm_en:
                if re.search(rf"\b{re.escape(norm_en)}\b", norm_q):
                    best_sim = max(best_sim, 0.90)
                elif norm_q in norm_en and len(norm_q) >= 3:
                    ratio = len(norm_q) / len(norm_en)
                    best_sim = max(best_sim, min(0.80, 0.45 + 0.35 * ratio))

            if best_sim >= 0.50:
                scored_candidates.append((drug, best_sim))
                seen_ids.add(drug.id)

        # Sort descending by score
        scored_candidates.sort(key=lambda x: x[1], reverse=True)
        return scored_candidates[:limit]


# Global singleton instance
drug_repository = DrugRepository()
