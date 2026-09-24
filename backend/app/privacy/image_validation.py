from __future__ import annotations

import struct

from app.errors import ProductAPIError

IMAGE_MIN_DIMENSION = 64
IMAGE_MAX_DIMENSION = 6_000
IMAGE_MAX_PIXELS = 30_000_000


def matches_image_signature(image_bytes: bytes, content_type: str | None) -> bool:
    if content_type == "image/jpeg":
        return image_bytes.startswith(b"\xff\xd8\xff")
    if content_type == "image/png":
        return image_bytes.startswith(b"\x89PNG\r\n\x1a\n")
    if content_type == "image/webp":
        return (
            len(image_bytes) >= 12 and image_bytes[:4] == b"RIFF" and image_bytes[8:12] == b"WEBP"
        )
    return False


def parse_image_dimensions(
    image_bytes: bytes,
    content_type: str | None,
) -> tuple[int, int] | None:
    """Parse image dimensions without decoding untrusted media."""
    try:
        if content_type == "image/png":
            if len(image_bytes) < 24:
                return None
            return struct.unpack(">II", image_bytes[16:24])

        if content_type == "image/jpeg":
            offset = 2
            while offset + 3 < len(image_bytes):
                if image_bytes[offset] != 0xFF:
                    break
                marker = image_bytes[offset + 1]
                if marker in (0xC0, 0xC2):
                    if offset + 9 < len(image_bytes):
                        height, width = struct.unpack(
                            ">HH",
                            image_bytes[offset + 5 : offset + 9],
                        )
                        return width, height
                    break
                segment_length = struct.unpack(
                    ">H",
                    image_bytes[offset + 2 : offset + 4],
                )[0]
                offset += 2 + segment_length
            return None

        if content_type == "image/webp":
            if len(image_bytes) < 30:
                return None
            chunk_id = image_bytes[12:16]
            if chunk_id == b"VP8L":
                bits = struct.unpack("<I", image_bytes[21:25])[0]
                return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
            if chunk_id == b"VP8X":
                width = struct.unpack("<I", image_bytes[24:27] + b"\x00")[0] + 1
                height = struct.unpack("<I", image_bytes[27:30] + b"\x00")[0] + 1
                return width, height
            if chunk_id == b"VP8 ":
                raw_width, raw_height = struct.unpack("<HH", image_bytes[26:30])
                return raw_width & 0x3FFF, raw_height & 0x3FFF
    except (IndexError, struct.error):
        return None
    return None


def validate_image_dimensions(image_bytes: bytes, content_type: str | None) -> None:
    dimensions = parse_image_dimensions(image_bytes, content_type)
    if dimensions is None:
        return
    width, height = dimensions
    if width < IMAGE_MIN_DIMENSION or height < IMAGE_MIN_DIMENSION:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            (
                "Dimensi gambar terlalu kecil. Minimal "
                f"{IMAGE_MIN_DIMENSION}×{IMAGE_MIN_DIMENSION} piksel."
            ),
        )
    if width > IMAGE_MAX_DIMENSION or height > IMAGE_MAX_DIMENSION:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            (
                "Dimensi gambar terlalu besar. Maksimal "
                f"{IMAGE_MAX_DIMENSION}×{IMAGE_MAX_DIMENSION} piksel."
            ),
        )
    if width * height > IMAGE_MAX_PIXELS:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            "Jumlah piksel gambar melebihi batas yang diizinkan (30 juta piksel).",
        )
