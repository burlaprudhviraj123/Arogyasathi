"""Manifest Schema Version 1 Specification Validator.

Derived strictly from ORIGINAL_REQUEST.md (§R1) and PROJECT.md (§Feature Inventory & Interface Contracts).
Used by E2E tests to validate real and candidate voice pack manifests.
"""

from __future__ import annotations

import datetime
import re
from typing import Any, Dict, List, Tuple


class ManifestValidationError(ValueError):
    """Raised when a voice pack manifest violates Schema v1 specification."""
    pass


HEX64_REGEX = re.compile(r"^[0-9a-f]{64}$")
BCP47_REGEX = re.compile(r"^[a-z]{2,3}(-[A-Z][a-z]{3})?(-[A-Z]{2}|\d{3})?$")


def validate_manifest_schema_v1(manifest: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """Validate a voice pack manifest dictionary against Schema Version 1.

    Returns:
        (is_valid, error_list)
    """
    errors: List[str] = []

    # 1. Root fields check
    required_root_keys = [
        "schemaVersion",
        "packId",
        "languageTag",
        "displayName",
        "runtime",
        "model",
        "files",
        "archiveSha256",
        "createdAt",
    ]
    for key in required_root_keys:
        if key not in manifest:
            errors.append(f"Missing mandatory root field: '{key}'")

    if errors:
        return False, errors

    # 2. schemaVersion
    if not isinstance(manifest["schemaVersion"], int) or isinstance(manifest["schemaVersion"], bool):
        errors.append(f"'schemaVersion' must be an integer, got {type(manifest['schemaVersion']).__name__}")
    elif manifest["schemaVersion"] != 1:
        errors.append(f"'schemaVersion' must be exactly 1, got {manifest['schemaVersion']}")

    # 3. packId
    if not isinstance(manifest["packId"], str) or not manifest["packId"].strip():
        errors.append("'packId' must be a non-empty string")

    # 4. languageTag
    if not isinstance(manifest["languageTag"], str) or not manifest["languageTag"].strip():
        errors.append("'languageTag' must be a non-empty string")
    elif not BCP47_REGEX.match(manifest["languageTag"]):
        errors.append(f"'languageTag' '{manifest['languageTag']}' is not a valid BCP-47 language tag")

    # 5. displayName
    if not isinstance(manifest["displayName"], str) or not manifest["displayName"].strip():
        errors.append("'displayName' must be a non-empty string")

    # 6. runtime
    runtime = manifest.get("runtime")
    if not isinstance(runtime, dict):
        errors.append("'runtime' must be a JSON object")
    else:
        if runtime.get("engine") != "sherpa-onnx":
            errors.append(f"'runtime.engine' must be 'sherpa-onnx', got '{runtime.get('engine')}'")
        if runtime.get("modelType") != "nemo-ctc":
            errors.append(f"'runtime.modelType' must be 'nemo-ctc', got '{runtime.get('modelType')}'")
        if runtime.get("sampleRateHz") != 16000:
            errors.append(f"'runtime.sampleRateHz' must be 16000, got {runtime.get('sampleRateHz')}")
        if runtime.get("featureDimension") != 80:
            errors.append(f"'runtime.featureDimension' must be 80, got {runtime.get('featureDimension')}")
        if not isinstance(runtime.get("minimumRuntimeVersion"), str) or not runtime.get("minimumRuntimeVersion"):
            errors.append("'runtime.minimumRuntimeVersion' must be a non-empty version string (e.g. '2.0.1')")

    # 7. model
    model = manifest.get("model")
    if not isinstance(model, dict):
        errors.append("'model' must be a JSON object")
    else:
        if not isinstance(model.get("source"), str) or not model.get("source", "").strip():
            errors.append("'model.source' must be a non-empty string")
        chk = model.get("sourceCheckpointSha256", "")
        if not isinstance(chk, str) or not HEX64_REGEX.match(chk):
            errors.append("'model.sourceCheckpointSha256' must be a 64-character lowercase hex SHA-256")
        if not isinstance(model.get("version"), str) or not model.get("version", "").strip():
            errors.append("'model.version' must be a non-empty semantic version string")

    # 8. files
    files = manifest.get("files")
    if not isinstance(files, list) or len(files) == 0:
        errors.append("'files' must be a non-empty array of file descriptors")
    else:
        declared_paths = set()
        for idx, file_entry in enumerate(files):
            if not isinstance(file_entry, dict):
                errors.append(f"'files[{idx}]' must be a JSON object")
                continue
            path = file_entry.get("path")
            if not isinstance(path, str) or not path.strip():
                errors.append(f"'files[{idx}].path' must be a non-empty string")
            else:
                declared_paths.add(path)
                if "/" in path or "\\" in path or ".." in path:
                    errors.append(f"'files[{idx}].path' '{path}' must be a flat relative filename without path separators")

            byte_count = file_entry.get("bytes")
            if not isinstance(byte_count, int) or isinstance(byte_count, bool) or byte_count <= 0:
                errors.append(f"'files[{idx}].bytes' must be a positive integer, got {byte_count}")

            sha = file_entry.get("sha256")
            if not isinstance(sha, str) or not HEX64_REGEX.match(sha):
                errors.append(f"'files[{idx}].sha256' must be a 64-character lowercase hex string, got '{sha}'")

        if "model.int8.onnx" not in declared_paths:
            errors.append("Mandatory file 'model.int8.onnx' missing from 'files' array")
        if "tokens.txt" not in declared_paths:
            errors.append("Mandatory file 'tokens.txt' missing from 'files' array")

    # 9. archiveSha256
    arc_sha = manifest.get("archiveSha256")
    if not isinstance(arc_sha, str) or not HEX64_REGEX.match(arc_sha):
        errors.append(f"'archiveSha256' must be a 64-character lowercase hex string, got '{arc_sha}'")

    # 10. createdAt
    created_at = manifest.get("createdAt")
    if not isinstance(created_at, str):
        errors.append("'createdAt' must be an ISO-8601 timestamp string")
    else:
        iso_pattern = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$")
        if not iso_pattern.match(created_at):
            errors.append(f"'createdAt' '{created_at}' is not a valid ISO-8601 UTC timestamp (expected YYYY-MM-DDTHH:MM:SSZ)")
        else:
            try:
                # Normalize trailing Z for Python fromisoformat
                ts = created_at.replace("Z", "+00:00")
                datetime.datetime.fromisoformat(ts)
            except ValueError:
                errors.append(f"'createdAt' '{created_at}' is not a valid ISO-8601 UTC timestamp")

    return (len(errors) == 0), errors


def assert_manifest_schema_v1(manifest: Dict[str, Any]) -> None:
    """Convenience helper asserting manifest validity, raising ManifestValidationError on failure."""
    is_valid, errors = validate_manifest_schema_v1(manifest)
    if not is_valid:
        raise ManifestValidationError(f"Manifest failed Schema v1 validation: {'; '.join(errors)}")
