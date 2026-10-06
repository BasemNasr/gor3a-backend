from datetime import date
from typing import List, Optional
from pydantic import BaseModel, Field


class Drug(BaseModel):
    """
    Normalized internal representation of an Egyptian registered pharmaceutical product.
    Strictly preserves source provenance and official regulatory metadata.
    """
    id: str = Field(..., description="Unique internal drug identifier (e.g., 'EGY-MED-00124')")
    commercial_name_en: str = Field(..., description="English commercial / trade brand name")
    commercial_name_ar: Optional[str] = Field(None, description="Arabic trade name (e.g., 'فلاجيل', 'سيتال')")
    generic_name: str = Field(..., description="International Nonproprietary Name (INN) / generic substance")
    active_ingredients: List[str] = Field(default_factory=list, description="Active chemical ingredients")
    dosage_form: str = Field(..., description="Pharmaceutical form: Tablet, Suspension, Syrup, Ampoule, Drops, etc.")
    strength: Optional[str] = Field(None, description="Standard concentration/strength (e.g., '125mg/5ml', '500mg')")
    manufacturer: Optional[str] = Field(None, description="Manufacturing company registered with EDA")
    registration_number: Optional[str] = Field(None, description="Egyptian Drug Authority (EDA) registration code")
    status: str = Field(default="marketed", description="Regulatory status: marketed, registered, withdrawn, cancelled")
    aliases: List[str] = Field(default_factory=list, description="Known handwriting variations, phonetic spellings, and abbreviations")
    source: str = Field(..., description="Official regulatory data source or provenance")
    source_last_updated: str = Field(..., description="Timestamp or date when this record was verified from source")
