"""
Tests for EDA Drug Database Search, Pricing Engine, and Clinical Substitutes.
Validates search across 25,098 Egyptian pharmaceutical products,
expected 2024-2026 market pricing calculations, strip pricing, and API endpoints.
"""

from fastapi.testclient import TestClient
from backend.app.drugs.models import Drug
from backend.app.drugs.pricing import calculate_drug_pricing, parse_packaging
from backend.app.drugs.repository import drug_repository
from backend.app.main import app

client = TestClient(app)


def test_drug_repository_total_count_is_25098():
    """Validates the total count of official registered Egyptian medicines."""
    total = drug_repository.get_total_count()
    assert total >= 25098


def test_packaging_parser_tablets_and_strips():
    """Tests packaging parser extracting units and strip counts from commercial names."""
    pack20 = parse_packaging("PANADOL EXTRA 24 F.C. TABS.", "Tablet")
    assert pack20 is not None
    assert pack20["units"] == 24
    assert pack20["unit_type"] == "Tablet"
    assert pack20["strips"] == 2

    pack_amp = parse_packaging("VOLTAREN 75 MG 5 AMPOULES", "Injection")
    assert pack_amp is not None
    assert pack_amp["units"] == 5
    assert pack_amp["unit_type"] == "Ampoule/Vial"

    pack_sachet = parse_packaging("PANADOL COLD & FLU 10 SACHETS", "Other")
    assert pack_sachet is not None
    assert pack_sachet["units"] == 10
    assert pack_sachet["unit_type"] == "Sachet"


def test_pricing_engine_calculations():
    """Tests calculation of expected current Egyptian market prices and strip prices."""
    # Test sample drug with 54 EGP price
    test_drug = Drug(
        id="TEST-001",
        commercial_name_en="Testamol 24 Tablets",
        full_commercial_name_en="TESTAMOL 500 MG 24 TABLETS",
        generic_name="Paracetamol",
        dosage_form="Tablet",
        price_egp=54.0,
        manufacturer="Local Egyptian Pharma",
        drug_class="ANALGESICS & ANTIPYRETICS",
        source="EDA",
        source_last_updated="2026",
    )

    pricing = calculate_drug_pricing(test_drug)
    assert pricing.registered_price_egp == 54.0
    assert pricing.expected_price_current_egp is not None
    assert pricing.expected_price_current_egp > 54.0
    assert pricing.expected_price_min_egp <= pricing.expected_price_current_egp <= pricing.expected_price_max_egp
    assert pricing.strips_count == 2
    assert pricing.expected_strip_price_egp is not None
    assert pricing.expected_strip_price_egp == round(pricing.expected_price_current_egp / 2, 2)


def test_pricing_engine_chronic_tier():
    """Tests lower adjustment ceiling applied to chronic disease medications."""
    chronic_drug = Drug(
        id="TEST-CHRONIC",
        commercial_name_en="Concor 5mg",
        full_commercial_name_en="CONCOR 5 MG 30 TABLETS",
        generic_name="Bisoprolol Fumarate",
        dosage_form="Tablet",
        price_egp=40.0,
        manufacturer="Amoun / Merck",
        drug_class="ANTIHYPERTENSIVE.BETA BLOCKER",
        source="EDA",
        source_last_updated="2026",
    )

    pricing = calculate_drug_pricing(chronic_drug)
    assert "أمراض المزمنة" in pricing.pricing_tier or "Chronic" in pricing.pricing_tier_en
    # Chronic tier is +20% to +30%
    assert pricing.expected_price_min_egp == 48.0
    assert pricing.expected_price_max_egp == 52.0


def test_advanced_search_by_brand_and_arabic():
    """Tests multi-field advanced search by English brand, Arabic brand, and active substance."""
    # Search Panadol
    res_en = drug_repository.search_advanced(query="panadol", page=1, page_size=10)
    assert res_en["total"] >= 10
    assert any("panadol" in d["drug"]["commercial_name_en"].lower() for d in res_en["results"])

    # Search Arabic: سيتال
    res_ar = drug_repository.search_advanced(query="سيتال", page=1, page_size=10)
    assert res_ar["total"] >= 10
    assert any("سيتال" in (d["drug"]["commercial_name_ar"] or "") for d in res_ar["results"])

    # Search Scientific: Metronidazole
    res_sci = drug_repository.search_advanced(query="metronidazole", page=1, page_size=10)
    assert res_sci["total"] >= 10
    assert any("metronidazole" in d["drug"]["generic_name"].lower() for d in res_sci["results"])


def test_advanced_search_filtering_and_sorting():
    """Tests dosage form filter, price filter, and sorting."""
    # Filter form Tablet + sort price asc
    res = drug_repository.search_advanced(
        query="amoxicillin",
        form="Tablet",
        sort_by="price_asc",
        page=1,
        page_size=10,
    )
    assert res["total"] > 0
    assert all(d["drug"]["dosage_form"] == "Tablet" for d in res["results"])

    # Verify price ascending order
    prices = [d["pricing"]["registered_price_egp"] for d in res["results"] if d["pricing"]["registered_price_egp"] is not None]
    assert prices == sorted(prices)


def test_find_alternatives_and_generics():
    """Tests discovery of exact generic substitutes and therapeutic alternatives."""
    # Lookup Curam or Augmentin
    curam = drug_repository.get_by_name("Curam")
    assert curam is not None

    alts = drug_repository.find_alternatives(curam.id, limit=5)
    assert alts["exact_generics_count"] > 0
    assert len(alts["exact_generics"]) > 0
    first_gen = alts["exact_generics"][0]
    assert "pricing" in first_gen
    assert first_gen["drug"]["generic_name"].upper() == curam.generic_name.upper()


def test_api_drugs_search_catalog_mode():
    """Tests GET /api/drugs/search with catalog mode and pagination."""
    resp = client.get("/api/drugs/search?mode=catalog&query=panadol&page=1&page_size=10")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total"] >= 10
    assert data["page"] == 1
    assert len(data["results"]) <= 10
    assert "pricing" in data["results"][0]


def test_api_drugs_get_by_id_endpoint():
    """Tests GET /api/drugs/{drug_id} returning full details and expected current price."""
    resp = client.get("/api/drugs/EDA-00101")
    assert resp.status_code == 200
    data = resp.json()
    assert data["drug"]["id"] == "EDA-00101"
    assert data["drug"]["commercial_name_en"] == "Flagyl"
    assert "pricing" in data
    assert data["pricing"]["registered_price_egp"] == 15.0
    assert data["pricing"]["expected_price_current_egp"] is not None


def test_api_drugs_alternatives_endpoint():
    """Tests GET /api/drugs/{drug_id}/alternatives."""
    resp = client.get("/api/drugs/EDA-00101/alternatives?limit=5")
    assert resp.status_code == 200
    data = resp.json()
    assert "exact_generics" in data
    assert "exact_generics_count" in data


def test_api_drugs_facets_endpoint():
    """Tests GET /api/drugs/facets returning distribution across 25,098 medicines."""
    resp = client.get("/api/drugs/facets")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total_registered_drugs"] >= 25098
    assert len(data["dosage_forms"]) > 0
    assert len(data["routes"]) > 0
    assert len(data["top_manufacturers"]) > 0


def test_serve_drugs_html_page():
    """Tests GET /drugs serving the dedicated EDA Drug Database web page."""
    resp = client.get("/drugs")
    assert resp.status_code == 200
    assert "EDA Drug Database" in resp.text
    assert "25,098" in resp.text
    assert "drugs.js" in resp.text


def test_api_drugs_search_form_filters():
    """Tests GET /api/drugs/search with Arabic and English dosage form filters."""
    # Arabic 'أقراص'
    resp_ar = client.get("/api/drugs/search?mode=catalog&form=%D8%A3%D9%82%D8%B1%D8%A7%D8%B5&page=1&page_size=10")
    assert resp_ar.status_code == 200
    data_ar = resp_ar.json()
    assert data_ar["total"] >= 7000
    assert len(data_ar["results"]) == 10
    assert all(r["drug"]["dosage_form"] == "Tablet" for r in data_ar["results"])

    # Arabic 'كبسولات'
    resp_cap = client.get("/api/drugs/search?mode=catalog&form=كبسولات&page=1&page_size=5")
    assert resp_cap.status_code == 200
    data_cap = resp_cap.json()
    assert data_cap["total"] >= 2000
    assert all(r["drug"]["dosage_form"] == "Capsule" for r in data_cap["results"])

    # English plural 'Tablets'
    resp_en = client.get("/api/drugs/search?mode=catalog&form=Tablets&page=1&page_size=5")
    assert resp_en.status_code == 200
    assert resp_en.json()["total"] >= 7000
