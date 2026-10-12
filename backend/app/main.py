import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .api.routes import router as api_router
from .config import PROJECT_ROOT, settings
from .providers.factory import init_providers

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("prescription_ai")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: register AI providers
    logger.info("Initializing Prescription AI providers...")
    init_providers()
    logger.info("Prescription AI Lab backend started successfully.")
    yield
    logger.info("Prescription AI Lab backend shutting down.")


app = FastAPI(
    title="Prescription AI Lab",
    description="Handwritten Egyptian Medical Prescription Recognition and Clinical Information Extraction",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API Router
app.include_router(api_router, prefix="/api")

# Serve Web UI static files (only if not running under Gradio / Hugging Face Spaces)
WEB_DIR = PROJECT_ROOT / "web"
is_hf_space = bool(os.environ.get("SPACE_ID") or os.environ.get("IS_GRADIO"))
if WEB_DIR.exists() and not is_hf_space:
    app.mount("/static", StaticFiles(directory=str(WEB_DIR)), name="static")

    @app.api_route("/", methods=["GET", "HEAD"], include_in_schema=False)
    async def serve_index():
        index_file = WEB_DIR / "index.html"
        if index_file.exists():
            return FileResponse(index_file)
        return {"message": "Prescription AI API is running. UI files not found in web/."}

    @app.api_route("/drugs", methods=["GET", "HEAD"], include_in_schema=False)
    @app.api_route("/eda", methods=["GET", "HEAD"], include_in_schema=False)
    @app.api_route("/database", methods=["GET", "HEAD"], include_in_schema=False)
    async def serve_drugs_page():
        drugs_file = WEB_DIR / "drugs.html"
        if drugs_file.exists():
            return FileResponse(drugs_file)
        return {"message": "EDA Drug Database web page not found in web/drugs.html."}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.app_host,
        port=settings.app_port,
        reload=settings.debug,
    )
