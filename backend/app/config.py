import os
from pathlib import Path
from typing import List, Optional
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


# Project root resolution (parent of backend/app)
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent


class Settings(BaseSettings):
    # API Keys
    gemini_api_key: Optional[str] = Field(default=None, alias="GEMINI_API_KEY")
    openai_api_key: Optional[str] = Field(default=None, alias="OPENAI_API_KEY")
    anthropic_api_key: Optional[str] = Field(default=None, alias="ANTHROPIC_API_KEY")

    # Local Open-Source VLM (Ollama / vLLM / Local Server)
    local_vlm_url: str = Field(default="http://localhost:11434/v1", alias="LOCAL_VLM_URL")
    local_vlm_api_key: str = Field(default="ollama", alias="LOCAL_VLM_API_KEY")
    local_vlm_model: str = Field(default="gor3a-rx", alias="LOCAL_VLM_MODEL")

    # Defaults
    default_provider: str = Field(default="gemini", alias="DEFAULT_PROVIDER")
    default_model: str = Field(default="gemini-2.0-flash", alias="DEFAULT_MODEL")

    # Server config
    app_host: str = Field(default="0.0.0.0", alias="APP_HOST")
    app_port: int = Field(default=8000, alias="APP_PORT")
    debug: bool = Field(default=False, alias="DEBUG")
    cors_origins: str = Field(default="*", alias="CORS_ORIGINS")

    # Paths
    prompt_path: str = Field(
        default="prompts/prescription_analysis_v1.txt",
        alias="PROMPT_PATH"
    )

    log_level: str = Field(default="INFO", alias="LOG_LEVEL")

    model_config = SettingsConfigDict(
        env_file=(PROJECT_ROOT / ".env", PROJECT_ROOT / "backend" / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def cors_origin_list(self) -> List[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def resolved_prompt_path(self) -> Path:
        p = Path(self.prompt_path)
        if p.is_absolute():
            return p
        return PROJECT_ROOT / p


settings = Settings()
