import sys
import traceback
from pathlib import Path

# Add project root to path so backend package can be resolved
ROOT_DIR = Path(__file__).resolve().parent.parent
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

try:
    from backend.app.main import app
except Exception as e:
    from fastapi import FastAPI
    from fastapi.responses import JSONResponse

    app = FastAPI(title="Gor3a Startup Error Fallback")
    err_tb = traceback.format_exc()

    @app.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "HEAD"])
    async def fallback_error(path: str):
        return JSONResponse(
            status_code=500,
            content={
                "error": "Backend Startup Failed",
                "detail": str(e),
                "traceback": err_tb.splitlines(),
            },
        )
