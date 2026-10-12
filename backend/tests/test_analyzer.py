import io
import pytest
from PIL import Image
from backend.app.providers.base import ProviderResult
from backend.app.providers.gemini import GeminiProvider, clean_json_response
from backend.app.providers.openai_provider import OpenAIProvider
from backend.app.services.analyzer import PrescriptionAnalyzer
from backend.app.utils.cost import estimate_cost


def create_dummy_image_bytes() -> bytes:
    """Creates a valid JPEG image in-memory for testing."""
    img = Image.new("RGB", (400, 500), color=(250, 250, 245))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


@pytest.mark.asyncio
async def test_prompt_loading():
    analyzer = PrescriptionAnalyzer()
    prompt = analyzer.load_prompt()
    assert len(prompt) > 100
    assert "CLINICAL SAFETY RULES" in prompt
    assert "NEVER GUESS" in prompt
    assert "EGYPTIAN PRESCRIPTION" in prompt


@pytest.mark.asyncio
async def test_analyzer_with_mock_provider():
    analyzer = PrescriptionAnalyzer()
    img_bytes = create_dummy_image_bytes()

    result = await analyzer.analyze_image(
        image_bytes=img_bytes,
        filename="test_prescription.jpg",
        mime_type="image/jpeg",
        provider_name="mock",
    )

    assert result.analysis is not None
    assert result.analysis.prescription.patient_age == "8 months"
    assert result.analysis.prescription.patient_weight_kg == 8.14
    assert len(result.analysis.medications) >= 2

    # Check first medication
    flagyl = result.analysis.medications[0]
    assert flagyl.name == "Flagyl"
    assert flagyl.status == "identified"
    assert flagyl.confidence >= 0.9

    # Check uncertain item preservation
    assert len(result.analysis.uncertain_items) > 0

    # Check metadata
    assert result.metadata.provider == "mock"
    assert result.metadata.processing_time_ms > 0
    assert result.metadata.image_info.width == 400
    assert result.metadata.image_info.height == 500


@pytest.mark.asyncio
async def test_analyzer_invalid_image_fails():
    analyzer = PrescriptionAnalyzer()
    with pytest.raises(ValueError, match="Invalid or corrupted image format"):
        await analyzer.analyze_image(
            image_bytes=b"not an image data",
            filename="broken.jpg",
            provider_name="mock",
        )


@pytest.mark.asyncio
async def test_gemini_unconfigured_error():
    provider = GeminiProvider(api_key=None)
    assert not provider.is_configured()
    with pytest.raises(RuntimeError, match="GEMINI_API_KEY"):
        await provider.analyze(
            image_bytes=create_dummy_image_bytes(),
            mime_type="image/jpeg",
            prompt="Analyze",
        )


@pytest.mark.asyncio
async def test_openai_unconfigured_error():
    provider = OpenAIProvider(api_key=None)
    assert not provider.is_configured()
    with pytest.raises(RuntimeError, match="OPENAI_API_KEY"):
        await provider.analyze(
            image_bytes=create_dummy_image_bytes(),
            mime_type="image/jpeg",
            prompt="Analyze",
        )


@pytest.mark.asyncio
async def test_claude_unconfigured_error():
    from backend.app.providers.claude import ClaudeProvider
    provider = ClaudeProvider(api_key=None)
    assert not provider.is_configured()
    with pytest.raises(RuntimeError, match="ANTHROPIC_API_KEY"):
        await provider.analyze(
            image_bytes=create_dummy_image_bytes(),
            mime_type="image/jpeg",
            prompt="Analyze",
        )


def test_clean_json_response_strips_markdown():
    wrapped = "```json\n{\"medications\": []}\n```"
    assert clean_json_response(wrapped) == '{"medications": []}'

    plain = '{"medications": []}'
    assert clean_json_response(plain) == '{"medications": []}'


def test_cost_estimation():
    gemini_cost = estimate_cost("gemini-2.5-flash", 1000, 500)
    assert gemini_cost is not None
    assert gemini_cost.total_cost_usd > 0
    assert gemini_cost.currency == "USD"

    openai_cost = estimate_cost("gpt-4o", 1000, 500)
    assert openai_cost is not None
    assert openai_cost.total_cost_usd > gemini_cost.total_cost_usd


@pytest.mark.asyncio
async def test_local_vlm_provider_config_and_error():
    from backend.app.providers.local_vlm import LocalVLMProvider
    provider = LocalVLMProvider(base_url="http://127.0.0.1:54321/v1")
    assert provider.name == "local_vlm"
    assert "qwen2.5-vl:7b" in provider.supported_models
    assert provider.is_configured()

    with pytest.raises(RuntimeError, match="Could not connect to local VLM server"):
        await provider.analyze(
            image_bytes=create_dummy_image_bytes(),
            mime_type="image/jpeg",
            prompt="Analyze",
            model="qwen2.5-vl:7b",
        )
