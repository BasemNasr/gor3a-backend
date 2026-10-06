from typing import List, Literal, Optional
from pydantic import BaseModel, Field


class PatientInfo(BaseModel):
    """Extracted patient demographic information."""
    patient_age: Optional[str] = Field(
        default=None,
        description="Patient age as transcribed (e.g., '8 months', '5 years', '٣٥ سنة') or null if illegible/absent"
    )
    patient_weight_kg: Optional[float] = Field(
        default=None,
        description="Patient body weight in kilograms (e.g., 8.14) or null if illegible/absent"
    )


class MedicationItem(BaseModel):
    """Single medication line extracted from the handwritten prescription."""
    raw_text: str = Field(
        ...,
        description="Verbatim handwritten transcription of the medication line exactly as seen"
    )
    name: Optional[str] = Field(
        default=None,
        description="Commercial brand or generic name if identifiable with reasonable certainty, else null"
    )
    active_ingredient: Optional[str] = Field(
        default=None,
        description="Active generic chemical substance if clearly specified or known with high certainty, else null"
    )
    strength: Optional[str] = Field(
        default=None,
        description="Formulation strength/concentration (e.g., '125mg/5ml', '500mg'), separate from dose"
    )
    dose: Optional[str] = Field(
        default=None,
        description="Prescribed amount per intake (e.g., '5 ml', '1 tablet', 'قرص واحد'), separate from frequency"
    )
    frequency: Optional[str] = Field(
        default=None,
        description="Frequency of administration (e.g., 'every 8 hours', 'tid', '٣ مرات يومياً'), separate from duration"
    )
    duration: Optional[str] = Field(
        default=None,
        description="Course duration (e.g., '5 days', 'لمدة أسبوع'), separate from frequency"
    )
    instructions: Optional[str] = Field(
        default=None,
        description="Administration instructions (e.g., 'after meals', 'بعد الأكل', 'IM injection')"
    )
    confidence: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Calibrated confidence score between 0.0 and 1.0"
    )
    status: Literal["identified", "uncertain", "unreadable"] = Field(
        ...,
        description="Extraction status: 'identified' when clearly read, 'uncertain' if ambiguous, 'unreadable' if illegible"
    )
    candidates: List[dict] = Field(
        default_factory=list,
        description="Ranked candidate matches from Egyptian Drug Authority (EDA) database"
    )
    eda_id: Optional[str] = Field(
        default=None,
        description="Egyptian Drug Authority registered product ID if matched"
    )
    lasa_warning: Optional[str] = Field(
        default=None,
        description="Clinical safety alert for Look-Alike / Sound-Alike risks"
    )
    pipeline_version: str = Field(
        default="v1",
        description="Pipeline version used: 'v1' (vision only) or 'v2' (vision + EDA normalizer)"
    )


class PrescriptionAnalysis(BaseModel):
    """Core structured output of the prescription analysis."""
    prescription: PatientInfo = Field(default_factory=PatientInfo)
    medications: List[MedicationItem] = Field(default_factory=list)
    uncertain_items: List[str] = Field(
        default_factory=list,
        description="Explicit list of ambiguous, unreadable, or missing fields needing pharmacist attention"
    )
    overall_confidence: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Overall confidence score for the entire prescription extraction"
    )


class TokenUsage(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class CostEstimate(BaseModel):
    prompt_cost_usd: float = 0.0
    completion_cost_usd: float = 0.0
    total_cost_usd: float = 0.0
    currency: str = "USD"


class ImageInfo(BaseModel):
    filename: str
    width: Optional[int] = None
    height: Optional[int] = None
    format: Optional[str] = None
    size_bytes: int


class AnalysisMetadata(BaseModel):
    provider: str
    model: str
    processing_time_ms: int
    cost_estimate: Optional[CostEstimate] = None
    usage: Optional[TokenUsage] = None
    image_info: Optional[ImageInfo] = None
    raw_model_output: Optional[str] = None


class AnalysisResponse(BaseModel):
    """Full API response wrapper combining structured clinical extraction and execution metadata."""
    analysis: PrescriptionAnalysis
    metadata: AnalysisMetadata
