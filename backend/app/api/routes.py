import json
import logging
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status

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


from ..drugs.repository import drug_repository
from ..services.pipeline_v2 import pipeline_v2


@router.get("/drugs/search", summary="Search Egyptian Drug Database")
async def search_drugs(q: str, limit: int = 10):
    """Searches registered Egyptian Drug Authority medications by commercial name, Arabic name, or alias."""
    candidates = drug_repository.search_candidates(q, limit=limit)
    return {
        "query": q,
        "count": len(candidates),
        "results": [
            {
                "drug": c[0].model_dump(),
                "similarity_score": c[1],
            }
            for c in candidates
        ],
    }


@router.get("/drugs/count", summary="Get Drug Database Count")
async def get_drug_count():
    """Returns total count of registered Egyptian pharmaceutical records."""
    return {"total_registered_drugs": drug_repository.get_total_count()}


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
