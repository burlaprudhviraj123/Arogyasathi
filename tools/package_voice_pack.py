#!/usr/bin/env python3
"""Standard Voice Pack Packaging and Manifest Generation Tool for Arogyasathi.

Produces standard Schema v1 .zip voice pack archives containing pack.json,
model.int8.onnx, and tokens.txt at root level, enforces ZipSlip, symlink,
and 500 MB uncompressed limits, computes exact byte sizes and lowercase
SHA-256 digests, and generates companion metadata for catalogue signing.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

SCHEMA_URI = "https://arogyasathi.org/schemas/voice-pack-v1.json"
SCHEMA_VERSION = 1
MAX_UNCOMPRESSED_BYTES = 524_288_000  # 500 MB
CHUNK_SIZE = 1024 * 1024  # 1 MB chunked reading

KNOWN_BASELINES: dict[str, dict[str, int | str]] = {
    "te-IN": {
        "model_bytes": 141_139_712,
        "model_sha256": "a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822",
        "tokens_bytes": 2_706,
        "tokens_sha256": "dc0b7bbb9eca8d3c8aebf1eb86e3144c90d692b4008cf5a32d46437b942b23ac",
    },
    "en-IN": {
        "model_bytes": 46_419_854,
        "model_sha256": "66cfde4ce0b6da9e1a4352e58f0c968f87da23070cf229c81ae0d520cddad2a2",
        "tokens_bytes": 11_611,
        "tokens_sha256": "1adce7866285e6a4aac6208b120d5b8e4302b7cae5f2ae541c343097773fd988",
    },
}


def compute_sha256_and_size(file_path: Path) -> tuple[str, int]:
    """Compute exact byte count and lowercase hex SHA-256 digest in chunks."""
    digest = hashlib.sha256()
    total_bytes = 0
    with file_path.open("rb") as stream:
        while True:
            chunk = stream.read(CHUNK_SIZE)
            if not chunk:
                break
            digest.update(chunk)
            total_bytes += len(chunk)
    return digest.hexdigest().lower(), total_bytes


def validate_sha256_hex(sha_str: str, field_name: str) -> str:
    """Ensure a string is exactly 64 lowercase hexadecimal characters."""
    val = sha_str.strip().lower()
    if not re.match(r"^[0-9a-f]{64}$", val):
        raise ValueError(f"Invalid {field_name}: expected 64 hex characters, got '{sha_str}'")
    return val


def validate_language_tag(lang_tag: str) -> str:
    """Validate BCP-47 language tag format (e.g. te-IN, en-IN, hi)."""
    val = lang_tag.strip()
    if not re.match(r"^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$", val):
        raise ValueError(f"Invalid language tag: '{lang_tag}'. Must conform to BCP-47 format.")
    return val


def build_manifest_v1(
    pack_id: str,
    language_tag: str,
    display_name: str,
    version: str,
    source: str,
    source_checkpoint_sha256: str,
    files_list: list[dict[str, any]],
    archive_sha256: str | None,
    created_at: str,
    runtime_engine: str = "sherpa-onnx",
    runtime_model_type: str = "nemo-ctc",
    sample_rate_hz: int = 16000,
    feature_dimension: int = 80,
    minimum_runtime_version: str = "2.0.1",
) -> dict:
    """Construct Schema v1 voice pack manifest dictionary."""
    return {
        "$schema": SCHEMA_URI,
        "schemaVersion": SCHEMA_VERSION,
        "packId": pack_id,
        "languageTag": language_tag,
        "displayName": display_name,
        "runtime": {
            "engine": runtime_engine,
            "modelType": runtime_model_type,
            "sampleRateHz": sample_rate_hz,
            "featureDimension": feature_dimension,
            "minimumRuntimeVersion": minimum_runtime_version,
        },
        "model": {
            "source": source,
            "sourceCheckpointSha256": source_checkpoint_sha256,
            "version": version,
        },
        "files": sorted(files_list, key=lambda f: f["path"]),
        "archiveSha256": archive_sha256,
        "createdAt": created_at,
    }


def package_voice_pack(
    model_path: str | Path,
    tokens_path: str | Path,
    language_tag: str,
    display_name: str,
    version: str,
    source: str,
    source_checkpoint_sha256: str,
    output_dir: str | Path,
    pack_id: str | None = None,
    created_at: str | None = None,
    runtime_engine: str = "sherpa-onnx",
    runtime_model_type: str = "nemo-ctc",
    sample_rate_hz: int = 16000,
    feature_dimension: int = 80,
    minimum_runtime_version: str = "2.0.1",
    enforce_baseline: bool = False,
    overwrite: bool = False,
    deterministic_timestamp: tuple[int, int, int, int, int, int] | None = None,
) -> tuple[Path, Path, str, int]:
    """Package model and tokens into standard Arogyasathi voice pack archive.

    Returns:
        tuple of (archive_path, companion_manifest_path, archive_sha256, archive_bytes)
    """
    model_path = Path(model_path).resolve()
    tokens_path = Path(tokens_path).resolve()
    output_dir = Path(output_dir).resolve()

    if not model_path.exists():
        raise FileNotFoundError(f"Model file not found: {model_path}")
    if not model_path.is_file():
        raise ValueError(f"Model path is not a regular file: {model_path}")

    if not tokens_path.exists():
        raise FileNotFoundError(f"Tokens file not found: {tokens_path}")
    if not tokens_path.is_file():
        raise ValueError(f"Tokens path is not a regular file: {tokens_path}")

    # Validate inputs
    lang_validated = validate_language_tag(language_tag)
    checkpoint_sha = validate_sha256_hex(source_checkpoint_sha256, "sourceCheckpointSha256")
    
    if not display_name or not display_name.strip():
        raise ValueError("display_name must be a non-empty string")
    if not version or not version.strip():
        raise ValueError("version must be a non-empty string")
    if not source or not source.strip():
        raise ValueError("source must be a non-empty string")

    # Compute hashes and sizes
    model_sha256, model_bytes = compute_sha256_and_size(model_path)
    tokens_sha256, tokens_bytes = compute_sha256_and_size(tokens_path)

    # Validate against known baselines if enforced or language recognized
    if enforce_baseline and lang_validated in KNOWN_BASELINES:
        baseline = KNOWN_BASELINES[lang_validated]
        if model_bytes != baseline["model_bytes"]:
            raise ValueError(
                f"Model size mismatch for {lang_validated}: {model_bytes} != {baseline['model_bytes']}"
            )
        if model_sha256 != baseline["model_sha256"]:
            raise ValueError(
                f"Model SHA256 mismatch for {lang_validated}: {model_sha256} != {baseline['model_sha256']}"
            )
        if tokens_bytes != baseline["tokens_bytes"]:
            raise ValueError(
                f"Tokens size mismatch for {lang_validated}: {tokens_bytes} != {baseline['tokens_bytes']}"
            )
        if tokens_sha256 != baseline["tokens_sha256"]:
            raise ValueError(
                f"Tokens SHA256 mismatch for {lang_validated}: {tokens_sha256} != {baseline['tokens_sha256']}"
            )

    # Uncompressed Size Guard (500 MB ceiling)
    # Estimate ~4KB for pack.json
    total_uncompressed = model_bytes + tokens_bytes + 4096
    if total_uncompressed > MAX_UNCOMPRESSED_BYTES:
        raise ValueError(
            f"Uncompressed archive size ({total_uncompressed} bytes) exceeds {MAX_UNCOMPRESSED_BYTES} bytes (500 MB) limit"
        )

    # Normalize pack ID and timestamps
    if not pack_id:
        normalized_lang = lang_validated.lower().replace("_", "-")
        pack_id = f"arogyasathi-asr-{normalized_lang}"

    if not created_at:
        created_at = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    if deterministic_timestamp:
        dt_tuple = deterministic_timestamp
    elif created_at:
        try:
            dt = datetime.datetime.fromisoformat(created_at.replace("Z", "+00:00"))
            dt_tuple = (dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
        except Exception:
            dt_tuple = (2026, 1, 1, 0, 0, 0)
    else:
        dt_tuple = (2026, 1, 1, 0, 0, 0)

    output_dir.mkdir(parents=True, exist_ok=True)
    zip_filename = f"{pack_id}-{version}.zip"
    zip_path = output_dir / zip_filename
    companion_manifest_path = output_dir / f"{pack_id}-{version}.pack.json"

    if zip_path.exists() and not overwrite:
        raise FileExistsError(f"Output archive already exists: {zip_path}. Use --overwrite to replace.")

    files_list = [
        {"path": "model.int8.onnx", "bytes": model_bytes, "sha256": model_sha256},
        {"path": "tokens.txt", "bytes": tokens_bytes, "sha256": tokens_sha256},
    ]

    # Embedded pack.json inside the zip has archiveSha256 = null
    # (avoiding circular dependency since zip contains pack.json)
    embedded_manifest = build_manifest_v1(
        pack_id=pack_id,
        language_tag=lang_validated,
        display_name=display_name,
        version=version,
        source=source,
        source_checkpoint_sha256=checkpoint_sha,
        files_list=files_list,
        archive_sha256=None,
        created_at=created_at,
        runtime_engine=runtime_engine,
        runtime_model_type=runtime_model_type,
        sample_rate_hz=sample_rate_hz,
        feature_dimension=feature_dimension,
        minimum_runtime_version=minimum_runtime_version,
    )
    embedded_manifest_bytes = (
        json.dumps(embedded_manifest, indent=2, ensure_ascii=False) + "\n"
    ).encode("utf-8")

    # Build zip file deterministically and safely
    with zipfile.ZipFile(zip_path, mode="w", compression=zipfile.ZIP_DEFLATED) as zipf:
        # 1. pack.json
        zinfo_manifest = zipfile.ZipInfo(filename="pack.json", date_time=dt_tuple)
        zinfo_manifest.compress_type = zipfile.ZIP_DEFLATED
        zinfo_manifest.external_attr = 0o644 << 16  # standard regular file permissions
        zipf.writestr(zinfo_manifest, embedded_manifest_bytes)

        # 2. model.int8.onnx
        zinfo_model = zipfile.ZipInfo(filename="model.int8.onnx", date_time=dt_tuple)
        zinfo_model.compress_type = zipfile.ZIP_DEFLATED
        zinfo_model.external_attr = 0o644 << 16
        with model_path.open("rb") as f_model:
            zipf.writestr(zinfo_model, f_model.read())

        # 3. tokens.txt
        zinfo_tokens = zipfile.ZipInfo(filename="tokens.txt", date_time=dt_tuple)
        zinfo_tokens.compress_type = zipfile.ZIP_DEFLATED
        zinfo_tokens.external_attr = 0o644 << 16
        with tokens_path.open("rb") as f_tokens:
            zipf.writestr(zinfo_tokens, f_tokens.read())

    # Compute outer archive SHA-256 and size
    archive_sha256, archive_bytes = compute_sha256_and_size(zip_path)

    # Write companion manifest with authoritative outer archive SHA-256
    companion_manifest = build_manifest_v1(
        pack_id=pack_id,
        language_tag=lang_validated,
        display_name=display_name,
        version=version,
        source=source,
        source_checkpoint_sha256=checkpoint_sha,
        files_list=files_list,
        archive_sha256=archive_sha256,
        created_at=created_at,
        runtime_engine=runtime_engine,
        runtime_model_type=runtime_model_type,
        sample_rate_hz=sample_rate_hz,
        feature_dimension=feature_dimension,
        minimum_runtime_version=minimum_runtime_version,
    )
    companion_manifest_path.write_text(
        json.dumps(companion_manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    # Write companion sha256 checksum file
    sha256_file = output_dir / f"{zip_filename}.sha256"
    sha256_file.write_text(f"{archive_sha256}  {zip_filename}\n", encoding="utf-8")

    # Immediate self-verification of archive safety and integrity
    with zipfile.ZipFile(zip_path, mode="r") as check_zip:
        corrupted = check_zip.testzip()
        if corrupted is not None:
            raise RuntimeError(f"Archive verification failed: corrupted entry '{corrupted}'")

        entries = set(check_zip.namelist())
        expected_entries = {"pack.json", "model.int8.onnx", "tokens.txt"}
        if entries != expected_entries:
            raise RuntimeError(
                f"Archive entries mismatch: found {sorted(entries)}, expected {sorted(expected_entries)}"
            )

        total_extracted_size = 0
        for name in entries:
            info = check_zip.getinfo(name)
            # Rejection of symlinks
            is_symlink = (info.external_attr >> 16) & 0o120000 == 0o120000
            if is_symlink:
                raise RuntimeError(f"Archive safety violation: symlink detected for '{name}'")
            # Rejection of traversal or absolute paths
            if "/" in name or "\\" in name or ".." in name:
                raise RuntimeError(f"Archive safety violation: path traversal detected for '{name}'")
            total_extracted_size += info.file_size

        if total_extracted_size > MAX_UNCOMPRESSED_BYTES:
            raise RuntimeError(
                f"Archive safety violation: extracted size {total_extracted_size} exceeds 500 MB limit"
            )

    return zip_path, companion_manifest_path, archive_sha256, archive_bytes


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Package offline ASR models into standard Arogyasathi voice packs."
    )
    # Required arguments
    parser.add_argument("--model", required=True, type=Path, help="Path to quantized INT8 ONNX model.")
    parser.add_argument("--tokens", required=True, type=Path, help="Path to tokens.txt vocabulary file.")
    parser.add_argument("--lang", required=True, type=str, help="Language BCP-47 tag (e.g. te-IN, en-IN).")
    parser.add_argument("--display-name", required=True, type=str, help="Display name (e.g. 'Telugu (తెలుగు)').")
    parser.add_argument("--version", required=True, type=str, help="Pack version (e.g. 1.0.0).")
    parser.add_argument("--source", required=True, type=str, help="Model source description.")
    parser.add_argument(
        "--source-checkpoint-sha256",
        required=True,
        type=str,
        help="64-character lowercase hex SHA-256 of source checkpoint.",
    )
    parser.add_argument("--output-dir", required=True, type=Path, help="Output directory for generated packages.")

    # Optional / Tuning arguments
    parser.add_argument("--pack-id", type=str, default=None, help="Custom packId (default: arogyasathi-asr-<lang>).")
    parser.add_argument("--created-at", type=str, default=None, help="ISO-8601 UTC creation timestamp.")
    parser.add_argument("--runtime-engine", type=str, default="sherpa-onnx", help="Runtime engine.")
    parser.add_argument("--runtime-model-type", type=str, default="nemo-ctc", help="Model type.")
    parser.add_argument("--sample-rate", type=int, default=16000, help="Sample rate in Hz.")
    parser.add_argument("--feature-dim", type=int, default=80, help="Feature dimension.")
    parser.add_argument("--min-runtime-version", type=str, default="2.0.1", help="Minimum runtime version.")
    parser.add_argument("--enforce-baseline", action="store_true", help="Enforce exact size and hash for te-IN/en-IN.")
    parser.add_argument("--overwrite", "-f", action="store_true", help="Overwrite existing output archive.")
    parser.add_argument("--quiet", "-q", action="store_true", help="Suppress non-error console output.")
    return parser.parse_args()


def main() -> int:
    try:
        args = parse_arguments()
        zip_p, manifest_p, sha, size = package_voice_pack(
            model_path=args.model,
            tokens_path=args.tokens,
            language_tag=args.lang,
            display_name=args.display_name,
            version=args.version,
            source=args.source,
            source_checkpoint_sha256=args.source_checkpoint_sha256,
            output_dir=args.output_dir,
            pack_id=args.pack_id,
            created_at=args.created_at,
            runtime_engine=args.runtime_engine,
            runtime_model_type=args.runtime_model_type,
            sample_rate_hz=args.sample_rate,
            feature_dimension=args.feature_dim,
            minimum_runtime_version=args.min_runtime_version,
            enforce_baseline=args.enforce_baseline,
            overwrite=args.overwrite,
        )
        if not args.quiet:
            print("==================================================")
            print(" Arogyasathi Voice Pack Successfully Packaged")
            print("==================================================")
            print(f" Archive:        {zip_p}")
            print(f" Archive Size:   {size:,} bytes")
            print(f" Archive SHA256: {sha}")
            print(f" Manifest:       {manifest_p}")
            print(" Status:         VERIFIED & READY")
            print("==================================================")
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
