"""Tier 2 Boundary & Corner Cases: Manifest Schema Version 1 Boundaries.

Verifies:
- Empty and whitespace-only string values across all fields
- Boundary sample rates (8000, 22050, 44100, 48000, -16000)
- Boundary feature dimensions (0, 40, 64, 128, -80)
- Non-integer and non-positive byte counts (0, -1, 1.5)
- SHA-256 boundary cases (63 chars, 65 chars, uppercase hex, non-hex chars)
- ISO-8601 timestamp edge cases (missing Z, invalid format, non-UTC)
- Path traversal sequences in declared file paths
"""

from __future__ import annotations

import unittest

from tests.e2e.common.helpers import make_valid_manifest
from tests.e2e.common.manifest_validator import (
    ManifestValidationError,
    assert_manifest_schema_v1,
    validate_manifest_schema_v1,
)


class TestManifestSchemaBoundaries(unittest.TestCase):
    """Test suite for boundary values and corner cases in Schema v1 manifests."""

    def test_empty_and_whitespace_string_fields(self) -> None:
        """Verify that empty or whitespace-only strings are rejected for all string fields."""
        fields_to_test = [
            ("packId", ""),
            ("packId", "   "),
            ("displayName", ""),
            ("displayName", "\t\n "),
            ("createdAt", ""),
        ]
        for field, bad_val in fields_to_test:
            with self.subTest(field=field, value=repr(bad_val)):
                manifest = make_valid_manifest()
                manifest[field] = bad_val
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any(field in e for e in errors))

    def test_boundary_sample_rates(self) -> None:
        """Verify that non-16000 sample rates are rejected."""
        boundary_rates = [0, -16000, 8000, 11025, 22050, 32000, 44100, 48000, 96000]
        for rate in boundary_rates:
            with self.subTest(rate=rate):
                manifest = make_valid_manifest()
                manifest["runtime"]["sampleRateHz"] = rate
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("sampleRateHz" in e for e in errors))

    def test_boundary_feature_dimensions(self) -> None:
        """Verify that non-80 feature dimensions are rejected."""
        boundary_dims = [-80, 0, 1, 40, 64, 79, 81, 128]
        for dim in boundary_dims:
            with self.subTest(dim=dim):
                manifest = make_valid_manifest()
                manifest["runtime"]["featureDimension"] = dim
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("featureDimension" in e for e in errors))

    def test_file_byte_count_boundaries(self) -> None:
        """Verify that zero, negative, and non-integer byte counts are rejected."""
        bad_bytes = [0, -1, -141139712, 141139712.5, "141139712", True, False, None]
        for bb in bad_bytes:
            with self.subTest(byte_val=bb):
                manifest = make_valid_manifest()
                manifest["files"][0]["bytes"] = bb
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("bytes" in e for e in errors))

    def test_sha256_hash_boundaries(self) -> None:
        """Verify SHA-256 strings must be exactly 64 lowercase hex characters."""
        bad_hashes = [
            "",
            "a" * 63,  # 63 chars (too short)
            "a" * 65,  # 65 chars (too long)
            "A" * 64,  # Uppercase hex
            "a" * 63 + "G",  # Non-hex character G
            "z" * 64,  # Non-hex character z
            " " + "a" * 63,  # Leading whitespace
            "a" * 63 + " ",  # Trailing whitespace
        ]
        for h in bad_hashes:
            with self.subTest(bad_hash=repr(h)):
                manifest = make_valid_manifest()
                manifest["archiveSha256"] = h
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("archiveSha256" in e for e in errors))

    def test_timestamp_boundary_formats(self) -> None:
        """Verify that timestamps must be valid ISO-8601 strings."""
        bad_timestamps = [
            "2026-10-30",  # Date only without time
            "2026/10/30 18:00:00",  # Slashes instead of dashes
            "not-a-timestamp",
            "2026-13-45T99:99:99Z",  # Invalid calendar numbers
            1730311200,  # Unix epoch integer
        ]
        for ts in bad_timestamps:
            with self.subTest(timestamp=ts):
                manifest = make_valid_manifest()
                manifest["createdAt"] = ts
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("createdAt" in e for e in errors))

    def test_file_path_traversal_boundaries(self) -> None:
        """Verify that paths declared in 'files' array cannot contain path separators or traversal."""
        bad_paths = [
            "../model.int8.onnx",
            "/model.int8.onnx",
            "models/model.int8.onnx",
            "model.int8.onnx/..",
            "..\\model.int8.onnx",
        ]
        for bp in bad_paths:
            with self.subTest(bad_path=bp):
                manifest = make_valid_manifest()
                manifest["files"][0]["path"] = bp
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("path" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
