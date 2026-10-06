from typing import List, Literal, Optional
from pydantic import BaseModel, Field


class GroundTruthMedication(BaseModel):
    raw_text: str = Field(..., description="Doctor's verbatim transcription")
    name: Optional[str] = Field(None, description="Standard verified drug name or null if unreadable")
    active_ingredient: Optional[str] = Field(None, description="Chemical substance")
    strength: Optional[str] = Field(None, description="Formulation strength/concentration or null")
    dose: Optional[str] = Field(None, description="Intake quantity or null")
    frequency: Optional[str] = Field(None, description="Frequency or null")
    duration: Optional[str] = Field(None, description="Course duration or null")
    instructions: Optional[str] = Field(None, description="Usage instructions or null")
    confidence: float = Field(..., ge=0.0, le=1.0)
    status: Literal["identified", "uncertain", "unreadable"]


class GroundTruthPrescription(BaseModel):
    patient_age: Optional[str] = None
    patient_weight_kg: Optional[float] = None


class GroundTruthAnnotation(BaseModel):
    """Authoritative ground truth schema for Egyptian prescription benchmarks."""
    case_id: Optional[str] = None
    specialty: Optional[str] = Field(None, description="Clinical domain, e.g. Pediatrics, Cardiology")
    prescription: GroundTruthPrescription
    medications: List[GroundTruthMedication]
    uncertain_items: List[str] = Field(default_factory=list)
    overall_confidence: float = Field(..., ge=0.0, le=1.0)
    reviewer_notes: Optional[str] = Field(None, description="Pharmacist/Physician verification notes")
