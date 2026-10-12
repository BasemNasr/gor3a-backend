import os
import json
import logging
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status
from fastapi.responses import FileResponse, Response

from ..drugs.clinical_knowledge import get_clinical_knowledge
from ..drugs.packaging_image import generate_drug_package_svg
from ..drugs.web_enricher import web_enricher
from ..models.prescription import AnalysisResponse
from ..providers.factory import list_available_providers
from ..services.analyzer import PrescriptionAnalyzer

logger = logging.getLogger(__name__)
router = APIRouter()

analyzer = PrescriptionAnalyzer()


@router.get("/health", summary="Health Check")
async def health_check():
    """Returns the operational status of the Prescription AI service."""
    return {
        "status": "healthy",
        "service": "Prescription AI Lab",
        "version": "1.0.0",
        "phase": "Phase 1 - Local Prescription AI Lab",
    }


@router.get("/providers", summary="List Vision Providers")
async def get_providers():
    """Lists registered vision AI providers, their configuration state, and models."""
    return {"providers": list_available_providers()}


from ..drugs.pricing import calculate_drug_pricing
from ..drugs.repository import drug_repository
from ..services.pipeline_v2 import pipeline_v2


@router.get("/drugs/search", summary="Search Egyptian Drug Database")
async def search_drugs(
    q: Optional[str] = None,
    query: Optional[str] = None,
    form: Optional[str] = None,
    route: Optional[str] = None,
    manufacturer: Optional[str] = None,
    drug_class: Optional[str] = None,
    price_min: Optional[float] = None,
    price_max: Optional[float] = None,
    sort_by: str = "relevance",
    page: Optional[int] = None,
    page_size: int = 24,
    limit: Optional[int] = None,
    mode: Optional[str] = None,
):
    """
    Searches registered Egyptian Drug Authority medications across 25,098 records.
    Supports simple candidate similarity ranking (for prescription OCR) or full
    paginated catalog browsing with dosage form, route, manufacturer, and price filters.
    """
    search_q = q if q is not None else (query or "")

    # If simple candidate matching requested (no page, no filters, mode != catalog)
    if (
        page is None
        and form is None
        and route is None
        and manufacturer is None
        and drug_class is None
        and price_min is None
        and price_max is None
        and mode != "catalog"
    ):
        target_limit = limit or 10
        candidates = drug_repository.search_candidates(search_q, limit=target_limit)
        return {
            "query": search_q,
            "count": len(candidates),
            "results": [
                {
                    "drug": c[0].model_dump(),
                    "similarity_score": c[1],
                    "pricing": calculate_drug_pricing(c[0]).model_dump(),
                }
                for c in candidates
            ],
        }

    # Full catalog search with pagination, filters, and market pricing
    current_page = page if page is not None else 1
    current_size = limit if limit is not None else page_size
    return drug_repository.search_advanced(
        query=search_q,
        form=form,
        route=route,
        manufacturer=manufacturer,
        drug_class=drug_class,
        price_min=price_min,
        price_max=price_max,
        sort_by=sort_by,
        page=current_page,
        page_size=current_size,
    )


@router.get("/drugs/count", summary="Get Drug Database Count")
async def get_drug_count():
    """Returns total count of registered Egyptian pharmaceutical records."""
    return {"total_registered_drugs": drug_repository.get_total_count()}


@router.get("/drugs/facets", summary="Get Drug Database Facets & Statistics")
async def get_drug_facets():
    """Returns filter facets (dosage forms, routes, top manufacturers, price range) for the 25,098 Egyptian pharmaceutical database."""
    return drug_repository.get_facets()


@router.api_route("/drugs/image-by-name", methods=["GET", "HEAD"], summary="Generate Drug Packaging Visual by Name (SVG)")
async def get_drug_image_by_name(
    name: str,
    ar_name: Optional[str] = None,
    form: Optional[str] = None,
    strength: Optional[str] = None,
    generic: Optional[str] = None,
    drug_class: Optional[str] = None,
    price: Optional[float] = None,
):
    """
    Generates dynamic vector pharmaceutical packaging art for any drug name,
    handwriting transcription, or custom dosage form.
    """
    matched = drug_repository.get_by_name(name)
    if matched:
        return Response(
            content=generate_drug_package_svg(
                commercial_name_en=matched.commercial_name_en,
                commercial_name_ar=matched.commercial_name_ar or ar_name,
                strength=matched.strength or strength,
                dosage_form=matched.dosage_form or form or "Tablet",
                generic_name=matched.generic_name or generic,
                category_slug=matched.drug_class or drug_class,
                eda_id=matched.id,
                manufacturer=matched.manufacturer,
                price_egp=matched.price_egp or price,
            ),
            media_type="image/svg+xml",
            headers={"Cache-Control": "public, max-age=86400"},
        )

    svg = generate_drug_package_svg(
        commercial_name_en=name,
        commercial_name_ar=ar_name,
        strength=strength,
        dosage_form=form or "Tablet",
        generic_name=generic,
        category_slug=drug_class,
        eda_id="EDA-REG",
        price_egp=price,
    )
    return Response(
        content=svg,
        media_type="image/svg+xml",
        headers={"Cache-Control": "public, max-age=86400"},
    )


@router.get("/drugs/{drug_id}", summary="Get Drug Details & Expected Egyptian Price")
async def get_drug_details(drug_id: str):
    """
    Returns comprehensive clinical and regulatory details for a registered Egyptian drug,
    including official registered price, expected current pharmacy price in Egypt (2024-2026),
    strip/unit pricing, and pricing tier justification.
    """
    drug = drug_repository.find_by_id(drug_id)
    if not drug:
        raise HTTPException(status_code=404, detail=f"Drug not found with ID {drug_id}")

    pricing = calculate_drug_pricing(drug)
    alts = drug_repository.find_alternatives(drug_id, limit=6)

    return {
        "drug": drug.model_dump(),
        "pricing": pricing.model_dump(),
        "exact_generics_count": alts["exact_generics_count"],
        "therapeutic_alternatives_count": alts["therapeutic_alternatives_count"],
        "sample_generics": alts["exact_generics"][:4],
        "sample_therapeutic": alts["therapeutic_alternatives"][:4],
    }


@router.get("/drugs/{drug_id}/alternatives", summary="Get Drug Substitutes & Generic Alternatives")
async def get_drug_alternatives(drug_id: str, limit: int = 12):
    """
    Returns exact generic equivalents (same active ingredient) and therapeutic class
    alternatives with Egyptian market pricing comparisons.
    """
    drug = drug_repository.find_by_id(drug_id)
    if not drug:
        raise HTTPException(status_code=404, detail=f"Drug not found with ID {drug_id}")

    return drug_repository.find_alternatives(drug_id, limit=limit)


@router.api_route("/drugs/{drug_id}/image", methods=["GET", "HEAD"], summary="Get Drug Packaging Visual Image (Photo with SVG Fallback)")
async def get_drug_image(drug_id: str, type: Optional[str] = None):
    """
    Renders authentic photographic packaging of the Egyptian drug if available,
    or falls back gracefully to high-res regulatory SVG vector visual.
    Never returns 404 or broken image.
    """
    drug = drug_repository.find_by_id(drug_id)
    if not drug:
        svg = generate_drug_package_svg(
            commercial_name_en=drug_id,
            commercial_name_ar="مستحضر صيدلاني",
            strength="",
            dosage_form="Medicine",
            generic_name="Pharmaceutical",
            category_slug=None,
            eda_id=drug_id,
            price_egp=None,
        )
        return Response(content=svg, media_type="image/svg+xml")

    # If caller specifically requested SVG format
    if type == "svg":
        svg = generate_drug_package_svg(
            commercial_name_en=drug.commercial_name_en,
            commercial_name_ar=drug.commercial_name_ar,
            strength=drug.strength,
            dosage_form=drug.dosage_form,
            generic_name=drug.generic_name,
            category_slug=drug.drug_class,
            eda_id=drug.id,
            manufacturer=drug.manufacturer,
            price_egp=drug.price_egp,
        )
        return Response(
            content=svg,
            media_type="image/svg+xml",
            headers={"Cache-Control": "public, max-age=86400"},
        )

    # 1. Check for authentic local cached photo
    details = drug.indication_details or {}
    web_profile = details.get("web_profile") or {}
    local_img_path = (
        web_enricher.get_local_image_path(web_profile)
        or web_enricher.get_local_image_path(drug.commercial_name_en)
        or web_enricher.get_local_image_path(drug.commercial_name_ar)
    )

    if local_img_path and os.path.exists(local_img_path):
        ext = os.path.splitext(local_img_path)[1].lower()
        media_map = {
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".webp": "image/webp",
        }
        media_type = media_map.get(ext, "image/webp")
        return FileResponse(
            local_img_path,
            media_type=media_type,
            headers={"Cache-Control": "public, max-age=86400"},
        )

    # 2. Fallback to authentic SVG vector packaging
    svg = generate_drug_package_svg(
        commercial_name_en=drug.commercial_name_en,
        commercial_name_ar=drug.commercial_name_ar,
        strength=drug.strength,
        dosage_form=drug.dosage_form,
        generic_name=drug.generic_name,
        category_slug=drug.drug_class,
        eda_id=drug.id,
        manufacturer=drug.manufacturer,
        price_egp=drug.price_egp,
    )
    return Response(
        content=svg,
        media_type="image/svg+xml",
        headers={"Cache-Control": "public, max-age=86400"},
    )


@router.get("/drugs/{drug_id}/clinical-info", summary="Get Clinical Indications & Diagnosis Profile")
async def get_drug_clinical_info(drug_id: str):
    """
    Returns structured clinical indications, what the drug treats, likely physician
    diagnosis, mechanism of action, and patient counseling guidance.
    """
    drug = drug_repository.find_by_id(drug_id)
    if not drug:
        raise HTTPException(status_code=404, detail=f"Drug not found with ID {drug_id}")

    return drug.indication_details or get_clinical_knowledge(
        drug_name=drug.commercial_name_en,
        generic_name=drug.generic_name,
        drug_class=drug.drug_class,
        dosage_form=drug.dosage_form,
        eda_id=drug.id,
        price_egp=drug.price_egp,
    )


@router.post(
    "/analyze",
    response_model=AnalysisResponse,
    summary="Analyze Handwritten Prescription Image",
)
async def analyze_prescription(
    file: UploadFile = File(..., description="Prescription image file (JPEG, PNG, WEBP)"),
    provider: Optional[str] = Form(None, description="Vision AI provider (gemini, openai, claude, mock)"),
    model: Optional[str] = Form(None, description="Specific model identifier"),
    pipeline_version: str = Form("v2", description="Pipeline version: 'v1' (vision only) or 'v2' (vision + EDA normalizer)"),
):
    """
    Accepts an uploaded handwritten Egyptian medical prescription image,
    runs OCR and clinical entity extraction through the selected vision AI provider,
    and applies Egyptian Drug Authority (EDA) normalization in Pipeline V2.
    """
    if not file:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No prescription file uploaded.",
        )

    try:
        content = await file.read()
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to read uploaded file: {str(exc)}",
        )

    if len(content) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is empty (0 bytes).",
        )

    try:
        if pipeline_version.lower() == "v2":
            response = await pipeline_v2.process_prescription(
                image_bytes=content,
                filename=file.filename or "prescription.jpg",
                mime_type=file.content_type or "image/jpeg",
                provider_name=provider,
                model_name=model,
            )
        else:
            response = await analyzer.analyze_image(
                image_bytes=content,
                filename=file.filename or "prescription.jpg",
                mime_type=file.content_type or "image/jpeg",
                provider_name=provider,
                model_name=model,
            )
        return response
    except ValueError as exc:
        logger.warning(f"Validation error in prescription analysis: {exc}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        )
    except RuntimeError as exc:
        logger.error(f"Provider runtime error: {exc}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        )
    except Exception as exc:
        logger.exception("Unexpected error during prescription analysis")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Internal analysis failure: {str(exc)}",
        )


# ==============================================================================
# Phase 2: Benchmark Endpoints
# ==============================================================================
from fastapi.responses import FileResponse
from pydantic import BaseModel
from ..benchmark.runner import BenchmarkRunner

benchmark_runner = BenchmarkRunner()


class BenchmarkRunRequest(BaseModel):
    models: Optional[List[Dict[str, str]]] = None
    use_cache: bool = True


@router.get("/benchmark/cases", summary="List Benchmark Cases")
async def list_benchmark_cases():
    """Lists available Egyptian handwritten prescription test cases and ground truth summaries."""
    cases = benchmark_runner.discover_cases()
    results = []
    for c in cases:
        gt_file = c / "ground_truth.json"
        gt_data = {}
        if gt_file.exists():
            with open(gt_file, "r", encoding="utf-8") as f:
                gt_data = json.load(f)

        meds = gt_data.get("medications", [])
        results.append({
            "case_id": c.name,
            "patient": gt_data.get("prescription", {}),
            "medication_count": len(meds),
            "medications_summary": [m.get("name") or "Unreadable" for m in meds],
            "uncertain_items": gt_data.get("uncertain_items", []),
            "has_image": (c / "image.jpg").exists(),
        })
    return {"cases": results}


@router.get("/benchmark/case/{case_id}/image", summary="Get Benchmark Case Image")
async def get_benchmark_image(case_id: str):
    """Serves the test image for a specific benchmark case."""
    case_path = benchmark_runner.benchmark_dir / case_id / "image.jpg"
    if not case_path.exists():
        raise HTTPException(status_code=404, detail=f"Case image not found for {case_id}")
    return FileResponse(case_path, media_type="image/jpeg")


@router.get("/benchmark/results", summary="Get Latest Benchmark Results")
async def get_benchmark_results():
    """Returns the latest benchmark comparative report and JSON metrics."""
    results_file = benchmark_runner.benchmark_dir / "benchmark_results.json"
    report_file = benchmark_runner.benchmark_dir / "BENCHMARK_REPORT.md"

    results_data = {}
    if results_file.exists():
        with open(results_file, "r", encoding="utf-8") as f:
            results_data = json.load(f)

    report_markdown = ""
    if report_file.exists():
        with open(report_file, "r", encoding="utf-8") as f:
            report_markdown = f.read()

    return {
        "results": results_data,
        "markdown_report": report_markdown,
    }


@router.post("/benchmark/run", summary="Execute Multi-Model Benchmark")
async def run_benchmark_endpoint(req: Optional[BenchmarkRunRequest] = None):
    """Executes the benchmark evaluation across requested vision models."""
    models_to_run = (req.models if req and req.models else [
        {"provider": "mock", "model": "mock-conservative-v1"},
        {"provider": "mock", "model": "mock-aggressive-v1"},
    ])
    use_cache = req.use_cache if req else True

    try:
        results = await benchmark_runner.run_benchmark(models_to_run, use_cache=use_cache)
        return {
            "status": "completed",
            "results": results,
            "markdown_report": benchmark_runner.generate_markdown_report(results),
        }
    except Exception as exc:
        logger.exception("Benchmark run failed")
        raise HTTPException(
            status_code=500,
            detail=f"Benchmark execution failed: {str(exc)}"
        )


# ==============================================================================
# Phase 3 & 5: Bilingual Arabic-English Dataset & Training Endpoints
# ==============================================================================
from ..dataset.bilingual_loader import bilingual_dataset_loader
from ..training.fine_tune_exporter import fine_tune_exporter
from ..benchmark.bilingual_runner import bilingual_benchmark_runner


class BilingualBenchmarkRequest(BaseModel):
    provider: str = "mock"
    model: Optional[str] = None
    pipeline_version: str = "v2"
    max_cases: int = 15


class ExportTrainingRequest(BaseModel):
    max_cases: Optional[int] = None


@router.get("/bilingual/stats", summary="Get Bilingual Dataset Statistics")
async def get_bilingual_stats():
    """Returns summary statistics for the Noor Alzoghby bilingual prescription dataset."""
    stats = bilingual_dataset_loader.get_stats()
    stats["total_registered_drugs_available"] = drug_repository.get_total_count()
    return stats


@router.get("/bilingual/cases", summary="List Bilingual Prescription Cases")
async def list_bilingual_cases(
    split: Optional[str] = None,
    limit: int = 24,
    offset: int = 0,
    search: Optional[str] = None,
):
    """Browses cases from the bilingual prescription dataset with optional split filter or search."""
    if split:
        cases = bilingual_dataset_loader.get_split(split.lower())
    else:
        cases = bilingual_dataset_loader.cases

    if search:
        norm_s = search.lower().strip()
        filtered = []
        for c in cases:
            med_text = " ".join(
                str(m.get("Medication_name") or "") + " " + str(m.get("Instructions") or "")
                for m in c.raw_medications
            ).lower()
            if norm_s in c.filename.lower() or norm_s in med_text:
                filtered.append(c)
        cases = filtered

    total = len(cases)
    paginated = cases[offset : offset + limit]

    return {
        "total": total,
        "offset": offset,
        "limit": limit,
        "cases": [c.to_dict() for c in paginated],
    }


@router.get("/bilingual/case/{case_id}/image", summary="Get Bilingual Prescription Image")
async def get_bilingual_case_image(case_id: str):
    """Serves the handwritten prescription image for a bilingual dataset case."""
    case = bilingual_dataset_loader.get_case(case_id)
    if not case or not case.image_path.exists():
        raise HTTPException(status_code=404, detail=f"Case image not found for {case_id}")
    return FileResponse(case.image_path, media_type="image/jpeg")


@router.post("/bilingual/export-training", summary="Export Fine-Tuning Datasets")
async def export_training_datasets(req: Optional[ExportTrainingRequest] = None):
    """
    Exports the training split into standardized fine-tuning formats
    (OpenAI Vision JSONL and HuggingFace VLM LoRA JSON) under dataset/training_exports/.
    """
    max_cases = req.max_cases if req else None
    try:
        manifest = fine_tune_exporter.export_all(max_cases=max_cases)
        return {
            "status": "success",
            "message": "Fine-tuning datasets exported successfully.",
            "manifest": manifest,
        }
    except Exception as exc:
        logger.exception("Fine-tuning export failed")
        raise HTTPException(status_code=500, detail=f"Training export failed: {str(exc)}")


@router.post("/bilingual/benchmark/run", summary="Evaluate Model on Bilingual Test Split")
async def run_bilingual_benchmark(req: BilingualBenchmarkRequest):
    """
    Runs clinical evaluation of a vision model on the Noor Alzoghby bilingual test split,
    measuring medication accuracy, dosage accuracy, and Arabic instruction extraction.
    """
    try:
        results = await bilingual_benchmark_runner.evaluate_model_on_test_split(
            provider_name=req.provider,
            model_name=req.model,
            pipeline_version=req.pipeline_version,
            max_cases=req.max_cases,
        )
        return {"status": "completed", "results": results}
    except Exception as exc:
        logger.exception("Bilingual evaluation failed")
        raise HTTPException(status_code=500, detail=f"Bilingual benchmark failed: {str(exc)}")


# ==============================================================================
# Phase 2.5: Egyptian 100-Prescription Curated Dataset Endpoints
# ==============================================================================
DATASET_100_DIR = benchmark_runner.benchmark_dir.parent / "egyptian_100_prescriptions"


@router.get("/dataset100/stats", summary="Get 100-Prescription Dataset Statistics")
async def get_dataset100_stats():
    """Returns statistics for the curated 100 Egyptian Prescriptions dataset (70 train, 15 val, 15 test)."""
    manifest_path = DATASET_100_DIR / "manifest.json"
    if not manifest_path.exists():
        raise HTTPException(status_code=404, detail="Dataset manifest not found.")
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    return manifest


@router.get("/dataset100/cases", summary="List 100-Prescription Cases")
async def list_dataset100_cases(
    split: str = "all",
    search: Optional[str] = None,
    limit: int = 30,
    offset: int = 0,
):
    """Lists cases from the 100-Prescription dataset, filtered by split (train, val, test) or search."""
    if not DATASET_100_DIR.exists():
        raise HTTPException(status_code=404, detail="Dataset directory not found.")

    splits_to_check = ["train", "val", "test"] if split == "all" else [split.lower()]
    cases = []

    for sp in splits_to_check:
        sp_dir = DATASET_100_DIR / sp
        if not sp_dir.exists():
            continue
        for case_dir in sorted(sp_dir.iterdir()):
            gt_file = case_dir / "ground_truth.json"
            if case_dir.is_dir() and gt_file.exists():
                with open(gt_file, "r", encoding="utf-8") as f:
                    gt_data = json.load(f)
                gt_data["split"] = sp
                gt_data["has_image"] = (case_dir / "image.jpg").exists()
                cases.append(gt_data)

    if search:
        norm_s = search.lower().strip()
        filtered = []
        for c in cases:
            med_text = " ".join(
                str(m.get("name") or "") + " " + str(m.get("instructions") or "")
                for m in c.get("medications", [])
            ).lower()
            if norm_s in c.get("case_id", "").lower() or norm_s in med_text:
                filtered.append(c)
        cases = filtered

    total = len(cases)
    paginated = cases[offset : offset + limit]
    return {
        "total": total,
        "offset": offset,
        "limit": limit,
        "split": split,
        "cases": paginated,
    }


@router.get("/dataset100/case/{split}/{case_id}/image", summary="Get 100-Prescription Case Image")
async def get_dataset100_image(split: str, case_id: str):
    """Serves the handwritten prescription image for a case in the 100-dataset."""
    img_path = DATASET_100_DIR / split / case_id / "image.jpg"
    if not img_path.exists():
        raise HTTPException(status_code=404, detail=f"Image not found for {case_id} in {split}")
    return FileResponse(img_path, media_type="image/jpeg")


