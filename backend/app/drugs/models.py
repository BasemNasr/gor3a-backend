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
    aliases: List[str] = Field(default_factory=list, description="Known handwriting variations, phonetic spellings, and abbreviations")
    price_egp: Optional[float] = Field(None, description="Retail price in Egyptian Pounds (EGP)")
    route: Optional[str] = Field(None, description="Route of administration: ORAL.SOLID, TOPICAL, etc.")
    drug_class: Optional[str] = Field(None, description="Therapeutic pharmacological class")
    full_commercial_name_en: Optional[str] = Field(None, description="Original full commercial name including pack size")
    source: str = Field(..., description="Official regulatory data source or provenance")
    source_last_updated: str = Field(..., description="Timestamp or date when this record was verified from source")
    status: Optional[str] = Field("marketed", description="EDA registration status")
    clinical_indication: Optional[str] = Field(None, description="Concise Arabic summary of what the drug does and what it treats")
    likely_diagnosis: Optional[str] = Field(None, description="Probable clinical diagnosis or condition addressed by this drug")
    indication_details: Optional[dict] = Field(None, description="Comprehensive clinical breakdown: mechanism, precautions, uses")
    image_url: Optional[str] = Field(None, description="Endpoint or image URL for official packaging visual")
