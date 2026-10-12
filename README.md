---
title: Gor3a Backend Api
emoji: 💊
colorFrom: green
colorTo: blue
sdk: gradio
app_file: app.py
pinned: false
license: mit
---

# ℞ Prescription AI — Egyptian Medical Prescriptions

> **AI-Powered Handwritten Prescription Recognition & Clinical Information Extraction**  
> *Targeted for Egyptian Handwritten Medical Prescriptions with Strict Clinical Safety & Uncertainty Preservation.*

---

## 📋 Overview

Prescription AI is an AI-powered system designed to recognize and transcribe handwritten prescriptions issued by doctors and clinics in Egypt. 

Egyptian prescriptions present distinct clinical and technical challenges:
- Mix of **English commercial brand names** (e.g., *Flagyl, Augmentin, Cetal, Antinal*) and occasional Arabic trade names.
- Dense pharmaceutical abbreviations (*Tab, Susp, Syr, Amp, Cap, Supp, prn, tid, bid, IM, IV*).
- **Arabic handwriting** for instructions, doses, frequencies, and durations (*"ملعقة ٣ مرات يومياً بعد الأكل"*).
- Header/footer demographics: patient age (*"العمر: ٨ شهور"*), weight (*"الوزن: 8.14 كجم"*).
- Complex, degraded, or cursive doctor handwriting.

### 🛡️ Non-Negotiable Clinical Safety Mandate
1. **Zero Hallucination:** The AI must **NEVER** guess or invent a medication, dose, or frequency.
2. **Uncertainty Preservation:** If handwriting is blurred, smeared, or ambiguous, the field **MUST** be returned as `null` with `status: "uncertain"` or `"unreadable"`, and logged under `uncertain_items`.
3. **No Contextual Assumption:** The model is strictly prohibited from inferring a medication solely because it commonly accompanies another drug, or guessing a dosage because it is a common pediatric dose.
4. **Information Extraction Only:** The system extracts what is written on the page. It does **NOT** provide medical diagnosis or treatment advice.

---

## 🏛️ Architecture

The vision architecture is **provider-independent**, designed to decouple model evaluation from backend infrastructure:

```
                  ┌────────────────────────┐
                  │  PrescriptionAnalyzer  │
                  │   (Domain Service)     │
                  └───────────┬────────────┘
                              │
                  ┌───────────▼────────────┐
                  │     VisionProvider     │
                  │  (Abstract Interface)  │
                  └─────┬──────┬─────┬──┬──┘
                        │      │     │  │
            ┌───────────┘      │     │  └───────────┐
            ▼                  ▼     ▼              ▼
   ┌────────────────┐ ┌──────────┐ ┌────────┐ ┌────────────────┐
   │ GeminiProvider │ │  OpenAI  │ │ Claude │ │  MockProvider  │
   │ (Google GenAI) │ │ Provider │ │Provider│ │ (Offline Demo) │
   └────────────────┘ └──────────┘ └────────┘ └────────────────┘
```

- **`PrescriptionAnalyzer`**: Coordinates image validation, prompt injection, provider execution, clinical output validation, token usage tracking, and cost calculation.
- **`VisionProvider`**: Interface implemented by Google Gemini, OpenAI, Anthropic Claude, and offline baseline mock providers.
- **`BenchmarkRunner`**: Automated evaluation suite executing standardized Egyptian prescription test cases across all providers to measure clinical accuracy and safety.

---

## 📂 Project Structure

```
prescription-ai/
├── backend/
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                  # FastAPI application & static UI mounting
│   │   ├── config.py                # Pydantic Settings & environment resolution
│   │   ├── api/
│   │   │   ├── __init__.py
│   │   │   └── routes.py            # Endpoints: /api/analyze, /api/benchmark/*, /api/providers
│   │   ├── benchmark/
│   │   │   ├── __init__.py
│   │   │   ├── metrics.py           # 13 benchmark metrics & LASA critical error detector
│   │   │   └── runner.py            # Automated multi-model benchmark executor & report generator
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   └── prescription.py      # Pydantic schemas (PrescriptionAnalysis, MedicationItem, etc.)
│   │   ├── providers/
│   │   │   ├── __init__.py
│   │   │   ├── base.py              # VisionProvider abstract base class & ProviderResult
│   │   │   ├── gemini.py            # Google Gemini implementation (google-genai SDK)
│   │   │   ├── openai_provider.py   # OpenAI implementation (AsyncOpenAI SDK)
│   │   │   ├── claude.py            # Anthropic Claude implementation (AsyncAnthropic SDK)
│   │   │   ├── mock_provider.py     # Offline Mock provider for CI, testing & safety demos
│   │   │   └── factory.py           # Provider registry & lookup
│   │   ├── services/
│   │   │   ├── __init__.py
│   │   │   └── analyzer.py          # PrescriptionAnalyzer domain service
│   │   └── utils/
│   │       ├── __init__.py
│   │       ├── cost.py              # Token cost estimation per model
│   │       └── image.py             # Image validation & inspection (Pillow)
│   ├── tests/
│   │   ├── __init__.py
│   │   ├── test_models.py           # Pydantic validation & safety tests
│   │   ├── test_analyzer.py         # Analyzer & provider integration tests
│   │   ├── test_benchmark.py        # Benchmark metrics & runner tests
│   │   └── test_api.py              # FastAPI endpoints tests
│   └── requirements.txt             # Backend dependencies
│
├── web/
│   ├── index.html                   # Prescription AI Lab & Benchmark Suite web interface
│   ├── styles.css                   # Responsive clinical theme stylesheet
│   └── app.js                       # Preview, live lab, & benchmark matrix renderer
│
├── dataset/
│   ├── raw/
│   │   └── sample_egyptian_prescription.jpg  # Bundled sample Egyptian prescription
│   ├── ground_truth/
│   └── benchmark/                   # Standardized Egyptian prescription benchmark cases
│       ├── rx_001/                  # Pediatric GI Case (Flagyl, Cetal, Antinal)
│       ├── rx_002/                  # Adult Respiratory (Curam 1g, Congestal, Toplexil)
│       ├── rx_003/                  # High-Risk Look-Alike Case (Losec 20mg vs Lasix test)
│       ├── rx_004/                  # Egyptian Arabic Dosing Case (Cataflam, Brufen)
│       ├── rx_005/                  # Degraded Handwriting Safety Stress-Test
│       ├── benchmark_results.json   # Aggregated benchmark outputs
│       └── BENCHMARK_REPORT.md      # Auto-generated Markdown comparison matrix
│
├── prompts/
│   └── prescription_analysis_v1.txt # Versioned Egyptian prescription OCR prompt
│
├── .env.example                     # Environment template
├── .gitignore
├── pytest.ini
├── requirements.txt
└── README.md
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Python 3.10+** (Python 3.11, 3.12, 3.13, or 3.14)
- Pip and virtualenv

### 2. Clone and Setup Environment

```bash
# Navigate to project directory
cd /Users/basemnasr/Gor3a

# Create virtual environment
python3 -m venv .venv

# Activate virtual environment
# On macOS / Linux:
source .venv/bin/activate
# On Windows:
# .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### 3. Configure API Credentials

Copy the environment template and insert your API key(s):

```bash
cp .env.example .env
```

Edit `.env`:
```ini
# At least one provider key is recommended for live AI vision:
GEMINI_API_KEY=your_gemini_api_key_here
OPENAI_API_KEY=your_openai_api_key_here

# Default configuration
DEFAULT_PROVIDER=gemini
DEFAULT_MODEL=gemini-2.5-flash
```

*(Note: Even without API keys, you can immediately test the entire system offline using the built-in **Offline Mock / Demo Provider**).*

### 4. Run the Backend & Lab

```bash
# Start server with auto-reload
uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```

Or run directly:
```bash
python backend/app/main.py
```

### 5. Open the Local Prescription AI Lab

Open your browser at:
👉 **[http://localhost:8000](http://localhost:8000)**

You can also view the interactive OpenAPI documentation at:
👉 **[http://localhost:8000/docs](http://localhost:8000/docs)**

---

## 🧪 Testing the Lab

1. **One-Click Demo:** Click the **"🧪 Load Sample Egyptian Prescription"** button in the UI.
2. Select your provider (**Gemini**, **OpenAI**, or **Offline Mock**).
3. Click **"Analyze Prescription"**.
4. The system will display:
   - **Patient Details:** Age, Weight.
   - **Medications List:** Brand Name, Active Ingredient, Raw Doctor Transcription, Strength, Dose, Frequency, Duration, Instructions, and Confidence Meter.
   - **Status Badges:** `identified` (Green), `uncertain` (Amber), `unreadable` (Red).
   - **Uncertain / Unreadable Banner:** Human-readable explanations of every illegible or omitted parameter.
   - **Performance Stats:** Latency (ms), Estimated Cost ($), Token Usage.
   - **Raw Structured JSON:** Complete JSON inspector with one-click copy.

---

## 🔌 API Reference

### `POST /api/analyze`
Accepts a multipart file upload containing a prescription image and returns clinical extraction data.

#### Request Parameters (multipart/form-data):
- `file` *(required)*: Image file (JPEG, PNG, WEBP).
- `provider` *(optional)*: AI provider (`gemini`, `openai`, `mock`). Defaults to configured default.
- `model` *(optional)*: Specific model identifier (e.g., `gemini-2.5-flash`, `gpt-4o`).

#### Example cURL Request:
```bash
curl -X POST http://localhost:8000/api/analyze \
  -F "file=@dataset/raw/sample_egyptian_prescription.jpg" \
  -F "provider=mock"
```

#### Example JSON Response:
```json
{
  "analysis": {
    "prescription": {
      "patient_age": "8 months",
      "patient_weight_kg": 8.14
    },
    "medications": [
      {
        "raw_text": "Flagyl 125mg/5ml susp 3.5 ml tid x 5d",
        "name": "Flagyl",
        "active_ingredient": "Metronidazole",
        "strength": "125mg/5ml",
        "dose": "3.5 ml",
        "frequency": "tid (every 8 hours)",
        "duration": "5 days",
        "instructions": "after feeding",
        "confidence": 0.94,
        "status": "identified"
      },
      {
        "raw_text": "Cetal drops 10 drops prn",
        "name": "Cetal",
        "active_ingredient": "Paracetamol",
        "strength": "100mg/ml",
        "dose": "10 drops",
        "frequency": "upon fever / prn (عند اللزوم)",
        "duration": null,
        "instructions": "for temperature above 38.5C",
        "confidence": 0.89,
        "status": "identified"
      },
      {
        "raw_text": "Antinal ... 1 spoon ? ambiguous freq",
        "name": "Antinal",
        "active_ingredient": "Nifuroxazide",
        "strength": null,
        "dose": "1 spoon",
        "frequency": null,
        "duration": null,
        "instructions": null,
        "confidence": 0.52,
        "status": "uncertain"
      }
    ],
    "uncertain_items": [
      "Medication #3 (Antinal) strength is obscured by ink blot",
      "Medication #3 frequency handwriting is blurred; not inferred per clinical safety protocol",
      "Duration for Cetal drops is unspecified (marked PRN / as needed)"
    ],
    "overall_confidence": 0.81
  },
  "metadata": {
    "provider": "mock",
    "model": "mock-pediatric-rx-v1",
    "processing_time_ms": 351,
    "cost_estimate": null,
    "usage": {
      "prompt_tokens": 1420,
      "completion_tokens": 312,
      "total_tokens": 1732
    },
    "image_info": {
      "filename": "sample_egyptian_prescription.jpg",
      "width": 900,
      "height": 1200,
      "format": "JPEG",
      "size_bytes": 81398
    },
    "raw_model_output": "..."
  }
}
```

### `GET /api/providers`
Returns registered AI vision providers and their active status:
```json
{
  "providers": [
    {
      "id": "gemini",
      "display_name": "Google Gemini",
      "is_configured": true,
      "default_model": "gemini-2.5-flash",
      "supported_models": ["gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash"]
    },
    {
      "id": "openai",
      "display_name": "OpenAI",
      "is_configured": false,
      "default_model": "gpt-4o",
      "supported_models": ["gpt-4o", "gpt-4o-mini"]
    },
    {
      "id": "mock",
      "display_name": "Offline Mock / Demo Provider",
      "is_configured": true,
      "default_model": "mock-pediatric-rx-v1",
      "supported_models": ["mock-pediatric-rx-v1"]
    }
  ]
}
```

## 🧪 Testing the System & Current Progress

There are 4 distinct ways to test the system:

### Method 1: Interactive Web Lab (Recommended)
1. Start the server:
   ```bash
   source .venv/bin/activate
   uvicorn backend.app.main:app --port 8000 --reload
   ```
2. Open your browser at **[http://localhost:8000](http://localhost:8000)**.
3. **Test Single Prescription Lab:**
   - Click **"🧪 Load Sample Egyptian Prescription"** (or upload your own prescription image).
   - Select **Vision AI Provider** (e.g. `Offline Mock / Demo Provider`, or configured `Google Gemini` / `OpenAI`).
   - Under **AI Pipeline Architecture**, toggle between:
     - **Pipeline V2: Vision + Egyptian Drug Database (Recommended)**
     - **Pipeline V1: Raw Multimodal Vision Only**
   - Click **Analyze Prescription**.
   - Notice the rich extracted cards featuring:
     - 🇪🇬 **EDA Badge** (e.g., `EDA: EDA-00101`) verifying official Egyptian registration.
     - ⚠️ **LASA Warning Banner** if a drug has Look-Alike / Sound-Alike risks (e.g., *Losec* vs *Lasix*).
     - 🔍 **Expandable EDA Candidates Accordion** showing matching candidates and similarity percentages.
     - 🛡️ **Preserved Uncertainty** (blurred items remain `null` / `uncertain` per clinical safety mandate).

4. **Test Model Benchmark Suite:**
   - Click the **"📊 Model Benchmark Suite"** tab at the top.
   - Inspect the standardized Egyptian clinical test cases (`rx_001` through `rx_005`).
   - Select the models to benchmark (e.g. `Mock Conservative [Pipeline V2 + EDA]`, `Mock Conservative [Pipeline V1]`, `Mock Aggressive [Pipeline V1 Baseline]`).
   - Click **"▶ Run Multi-Model Benchmark"**.
   - Review the Comparative Ranking Matrix measuring Medication Name Accuracy, Critical Error Rate, Uncertainty Detection, Strength/Dose/Frequency Accuracy, Latency, and Cost.

---

### Method 2: CLI Benchmark Runner
Run the benchmark directly from your terminal:
```bash
# Run benchmark comparing Pipeline V2 (with EDA) vs V1 (vision only) across mock models
python -m backend.app.benchmark.runner --models mock:mock-conservative-v1:v2,mock:mock-conservative-v1:v1,mock:mock-aggressive-v1:v1

# Or run with live providers (if API keys are set in .env):
python -m backend.app.benchmark.runner --providers gemini,mock --pipelines v1,v2
```
This generates:
- `dataset/benchmark/benchmark_results.json` (Structured metric data)
- `dataset/benchmark/BENCHMARK_REPORT.md` (Human-readable Markdown matrix)

---

### Method 3: Automated Pytest Test Suite
Execute the full automated test suite containing 37 tests:
```bash
pytest -v
```
All 37 unit, safety, normalizer, and integration tests verify:
- **Pydantic Schemas:** Demographic and medication validation.
- **Clinical Safety Rules:** Strict zero-hallucination constraint, uncertainty preservation, and confidence bounds.
- **Provider Adapters:** Gemini, OpenAI, Claude, and Mock adapters.
- **Egyptian Drug Database (EDA):** Exact lookup, Arabic alias matching, and fuzzy phonetic ranking.
- **Drug Normalizer:** LASA critical error warnings, dosage reconciliation, and confidence calibration.
- **AI Pipeline V2:** End-to-end multimodal vision + EDA cross-referencing.
- **Benchmark Suite & Runner:** 13 clinical metrics and report generation.
- **FastAPI Endpoints:** `/api/analyze` (V1 & V2), `/api/drugs/*`, `/api/benchmark/*`.

---

### Method 4: Egyptian Drug Authority (EDA) API Search
Test the Egyptian pharmaceutical database lookup directly via cURL or browser:
```bash
# Search for commercial brand or active ingredient
curl "http://localhost:8000/api/drugs/search?q=cetal"

# Search with Arabic name
curl "http://localhost:8000/api/drugs/search?q=فلاجيل"

# Total registered medications count
curl "http://localhost:8000/api/drugs/count"
```

---

## 🗺️ Roadmap Progress

| Phase | Milestone | Status | Key Deliverables |
|:---:|:---|:---:|:---|
| **0** | Project Setup & Foundation | ✅ Complete | Repo structure, Pydantic clinical models, Python 3.14 env |
| **1** | Local Prescription AI Lab | ✅ Complete | Abstract VisionProvider, Gemini/OpenAI/Claude/Mock, Web UI Lab |
| **2** | Model Benchmark Suite | ✅ Complete | 5 Egyptian test cases, 13 metrics, LASA critical error engine, Benchmark UI |
| **3** | Dataset & Ground Truth | ✅ Complete | Standardized schema, PII validator, Egyptian phone/national ID scrubbers |
| **4** | Egyptian Drug Authority Database | ✅ Complete | In-memory EDA repository, indexed brand/Arabic/alias/formulation lookup |
| **5** | AI Pipeline V2 (Drug Normalization) | ✅ Complete | DrugNormalizer, candidate ranking, confidence recalibration, LASA warnings |
| **6** | Human-in-the-Loop Pharmacist Review | ⏳ Up Next | Verification UI, edit log, pharmacist confirmation audit trail |
| **7** | Production Backend | ⏳ Future | Auth, rate limiting, async job queuing, database persistence |
| **8** | Compose Multiplatform Mobile App | ⏳ Future | Android & iOS native mobile client with camera capture |
