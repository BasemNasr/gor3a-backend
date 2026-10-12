import io
from typing import Tuple
from PIL import Image, UnidentifiedImageError
from ..models.prescription import ImageInfo


SUPPORTED_MIME_TYPES = {
    "image/jpeg": "JPEG",
    "image/jpg": "JPEG",
    "image/png": "PNG",
    "image/webp": "WEBP",
    "image/heic": "HEIC",
    "image/heif": "HEIF",
}


def validate_and_inspect_image(
    image_bytes: bytes,
    filename: str,
    mime_type: str = "image/jpeg"
) -> Tuple[ImageInfo, bytes, str]:
    """
    Validates that the uploaded bytes form a legitimate image,
    inspects dimensions, and returns cleaned ImageInfo, bytes, and normalized MIME type.
    """
    if len(image_bytes) == 0:
        raise ValueError("Uploaded image file is empty.")

    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            img.verify()
    except (UnidentifiedImageError, Exception) as exc:
        raise ValueError(f"Invalid or corrupted image format: {str(exc)}") from exc

    # Re-open for dimension inspection and handwriting clarity enhancement
    with Image.open(io.BytesIO(image_bytes)) as img:
        width, height = img.size
        img_format = img.format or "JPEG"

        # Determine normalized MIME type
        normalized_mime = "image/jpeg"
        if img_format.upper() in ("PNG",):
            normalized_mime = "image/png"
        elif img_format.upper() in ("WEBP",):
            normalized_mime = "image/webp"

        orig_width, orig_height, orig_size = width, height, len(image_bytes)
        processed_bytes = image_bytes

        # Preserve prescription handwriting resolution:
        # Only downscale very large camera scans (> 2048px) to prevent memory issues.
        # Preserve native resolution for standard photos (800px - 2048px) to protect fine Arabic strokes.
        max_dim = max(width, height)
        target_max_dim = 1920
        rgb_img = img.convert("RGB") if img.mode != "RGB" else img
        needs_resize = False
        scale = 1.0

        if max_dim > 2048:
            scale = target_max_dim / max_dim
            needs_resize = True
        elif max_dim < 600:
            scale = min(2.5, 1000 / max_dim)
            needs_resize = True

        if needs_resize and scale != 1.0:
            new_w, new_h = max(1, int(width * scale)), max(1, int(height * scale))
            img_resized = rgb_img.resize((new_w, new_h), Image.Resampling.LANCZOS)
            buf = io.BytesIO()
            img_resized.save(buf, format="JPEG", quality=95)
            processed_bytes = buf.getvalue()
            normalized_mime = "image/jpeg"


        info = ImageInfo(
            filename=filename,
            width=orig_width,
            height=orig_height,
            format=img_format,
            size_bytes=orig_size,
        )

        return info, processed_bytes, normalized_mime
