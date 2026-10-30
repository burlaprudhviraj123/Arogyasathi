"""Shared fixtures and helper functions for E2E tests."""

from __future__ import annotations

import hashlib
import json
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

from .artifacts import (
    ENGLISH_EXPECTED_BYTES,
    ENGLISH_EXPECTED_SHA256,
    ENGLISH_TOKENS_BYTES,
    ENGLISH_TOKENS_SHA256,
    TELUGU_EXPECTED_BYTES,
    TELUGU_EXPECTED_SHA256,
    TELUGU_TOKENS_BYTES,
    TELUGU_TOKENS_SHA256,
)


def compute_sha256(data_or_path: Union[bytes, str, Path]) -> str:
    """Compute 64-character lowercase hex SHA-256 digest."""
    h = hashlib.sha256()
    if isinstance(data_or_path, (str, Path)):
        p = Path(data_or_path)
        if p.exists() and p.is_file():
            with p.open("rb") as f:
                while chunk := f.read(65536):
                    h.update(chunk)
            return h.hexdigest().lower()
        else:
            h.update(str(data_or_path).encode("utf-8"))
            return h.hexdigest().lower()
    elif isinstance(data_or_path, bytes):
        h.update(data_or_path)
        return h.hexdigest().lower()
    else:
        raise TypeError(f"Unsupported input type {type(data_or_path)}")


def make_valid_manifest(
    pack_id: str = "arogyasathi-asr-te-in",
    language_tag: str = "te-IN",
    display_name: str = "Telugu (తెలుగు)",
    model_bytes: int = TELUGU_EXPECTED_BYTES,
    model_sha256: str = TELUGU_EXPECTED_SHA256,
    tokens_bytes: int = TELUGU_TOKENS_BYTES,
    tokens_sha256: str = TELUGU_TOKENS_SHA256,
    archive_sha256: str = "0" * 64,
) -> Dict[str, Any]:
    """Generate a canonical Schema v1 manifest dictionary."""
    return {
        "schemaVersion": 1,
        "packId": pack_id,
        "languageTag": language_tag,
        "displayName": display_name,
        "runtime": {
            "engine": "sherpa-onnx",
            "modelType": "nemo-ctc",
            "sampleRateHz": 16000,
            "featureDimension": 80,
            "minimumRuntimeVersion": "2.0.1",
        },
        "model": {
            "source": "AI4Bharat IndicConformer Telugu hybrid RNN-T large",
            "sourceCheckpointSha256": "70aea39388043beae2b06af21df47811d23a743ac5641d7b5c0d20d04fa87a3c",
            "version": "1.0.0",
        },
        "files": [
            {
                "path": "model.int8.onnx",
                "bytes": model_bytes,
                "sha256": model_sha256,
            },
            {
                "path": "tokens.txt",
                "bytes": tokens_bytes,
                "sha256": tokens_sha256,
            },
        ],
        "archiveSha256": archive_sha256,
        "createdAt": "2026-10-30T18:00:00Z",
    }


def make_english_manifest(
    pack_id: str = "arogyasathi-asr-en-in",
    language_tag: str = "en-IN",
    display_name: str = "English (Indian / General)",
    archive_sha256: str = "e" * 64,
) -> Dict[str, Any]:
    """Generate a canonical Schema v1 manifest dictionary for English."""
    return {
        "schemaVersion": 1,
        "packId": pack_id,
        "languageTag": language_tag,
        "displayName": display_name,
        "runtime": {
            "engine": "sherpa-onnx",
            "modelType": "nemo-ctc",
            "sampleRateHz": 16000,
            "featureDimension": 80,
            "minimumRuntimeVersion": "2.0.1",
        },
        "model": {
            "source": "NVIDIA / sherpa-onnx NeMo Conformer CTC Small",
            "sourceCheckpointSha256": "808a1d9d688b060b008b4fb50817e0b1cb51e572000000000000000000000000",
            "version": "1.0.0",
        },
        "files": [
            {
                "path": "model.int8.onnx",
                "bytes": ENGLISH_EXPECTED_BYTES,
                "sha256": ENGLISH_EXPECTED_SHA256,
            },
            {
                "path": "tokens.txt",
                "bytes": ENGLISH_TOKENS_BYTES,
                "sha256": ENGLISH_TOKENS_SHA256,
            },
        ],
        "archiveSha256": archive_sha256,
        "createdAt": "2026-10-30T18:00:00Z",
    }


def create_zip_archive(
    zip_path: Path,
    file_map: Dict[str, Union[bytes, str]],
    symlinks: Optional[Dict[str, str]] = None,
) -> Path:
    """Create a zip archive on disk with arbitrary file contents and optional symlinks."""
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name, content in file_map.items():
            if isinstance(content, str):
                data = content.encode("utf-8")
            else:
                data = content
            zf.writestr(name, data)

        if symlinks:
            for link_name, target in symlinks.items():
                info = zipfile.ZipInfo(link_name)
                # Set Unix symlink mode (0120000)
                info.create_system = 3  # Unix
                info.external_attr = 0o120777 << 16
                zf.writestr(info, target)

    return zip_path
