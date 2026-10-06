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

    # Re-open for dimension inspection (verify() closes/invalidates the image object)
    with Image.open(io.BytesIO(image_bytes)) as img:
        width, height = img.size
        img_format = img.format or "JPEG"

        # Determine normalized MIME type
        normalized_mime = "image/jpeg"
        if img_format.upper() in ("PNG",):
            normalized_mime = "image/png"
        elif img_format.upper() in ("WEBP",):
            normalized_mime = "image/webp"

        info = ImageInfo(
            filename=filename,
            width=width,
            height=height,
            format=img_format,
            size_bytes=len(image_bytes)
        )

        return info, image_bytes, normalized_mime
