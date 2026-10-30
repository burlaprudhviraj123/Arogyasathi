"""Tier 1 Feature Coverage: Manifest Schema Version 1 Specification.

Verifies:
- Standard voice pack manifest schema compliance
- Mandatory field presence and type checking
- Runtime specification constraints (sherpa-onnx, nemo-ctc, 16000 Hz, 80 mel dims)
- Files array structure and mandatory model/token files
- BCP-47 language tag validation and ISO-8601 timestamps
"""

from __future__ import annotations

import copy
import unittest

from tests.e2e.common.helpers import make_english_manifest, make_valid_manifest
from tests.e2e.common.manifest_validator import (
    ManifestValidationError,
    assert_manifest_schema_v1,
    validate_manifest_schema_v1,
)


class TestManifestSchemaV1Feature(unittest.TestCase):
    """Test suite verifying Schema v1 manifest specification compliance."""

    def test_valid_telugu_manifest_passes_schema_v1(self) -> None:
        """Verify that a compliant Telugu Schema v1 manifest passes validation without error."""
        manifest = make_valid_manifest()
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertTrue(is_valid, f"Expected valid manifest, got errors: {errors}")
        self.assertEqual(len(errors), 0)
        # Verify assert helper does not raise
        assert_manifest_schema_v1(manifest)

    def test_valid_english_manifest_passes_schema_v1(self) -> None:
        """Verify that a compliant English Schema v1 manifest passes validation without error."""
        manifest = make_english_manifest()
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertTrue(is_valid, f"Expected valid manifest, got errors: {errors}")
        self.assertEqual(len(errors), 0)
        assert_manifest_schema_v1(manifest)

    def test_missing_mandatory_root_fields_rejected(self) -> None:
        """Verify that omitting any mandatory root field causes validation to fail."""
        mandatory_fields = [
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
        for field in mandatory_fields:
            with self.subTest(missing_field=field):
                manifest = make_valid_manifest()
                del manifest[field]
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid, f"Expected failure when '{field}' is omitted")
                self.assertTrue(any(field in err for err in errors), f"Error message should mention '{field}'")
                with self.assertRaises(ManifestValidationError):
                    assert_manifest_schema_v1(manifest)

    def test_invalid_schema_version_rejected(self) -> None:
        """Verify that schemaVersion != 1 is strictly rejected."""
        invalid_versions = [0, 2, 99, -1, "1", 1.0, None]
        for v in invalid_versions:
            with self.subTest(invalid_version=v):
                manifest = make_valid_manifest()
                manifest["schemaVersion"] = v
                is_valid, errors = validate_manifest_schema_v1(manifest)
                self.assertFalse(is_valid)
                self.assertTrue(any("schemaVersion" in e for e in errors))

    def test_runtime_engine_and_model_type_constraints(self) -> None:
        """Verify that runtime must specify engine='sherpa-onnx' and modelType='nemo-ctc'."""
        manifest = make_valid_manifest()
        manifest["runtime"]["engine"] = "whisper"
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("engine" in e for e in errors))

        manifest = make_valid_manifest()
        manifest["runtime"]["modelType"] = "conformer-rnnt"
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("modelType" in e for e in errors))

    def test_runtime_audio_parameters(self) -> None:
        """Verify sampleRateHz must be 16000 and featureDimension must be 80."""
        manifest = make_valid_manifest()
        manifest["runtime"]["sampleRateHz"] = 44100
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("sampleRateHz" in e for e in errors))

        manifest = make_valid_manifest()
        manifest["runtime"]["featureDimension"] = 40
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("featureDimension" in e for e in errors))

    def test_mandatory_files_array_contents(self) -> None:
        """Verify that files array must declare model.int8.onnx and tokens.txt."""
        # Missing model.int8.onnx
        manifest = make_valid_manifest()
        manifest["files"] = [
            {"path": "tokens.txt", "bytes": 2706, "sha256": "d" * 64}
        ]
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("model.int8.onnx" in e for e in errors))

        # Missing tokens.txt
        manifest = make_valid_manifest()
        manifest["files"] = [
            {"path": "model.int8.onnx", "bytes": 141139712, "sha256": "a" * 64}
        ]
        is_valid, errors = validate_manifest_schema_v1(manifest)
        self.assertFalse(is_valid)
        self.assertTrue(any("tokens.txt" in e for e in errors))

    def test_bcp47_language_tag_format(self) -> None:
        """Verify that languageTag adheres to BCP-47 formatting."""
        valid_tags = ["te-IN", "en-IN", "hi-IN", "ta-IN", "kn-IN", "ml-IN", "en-US"]
        for tag in valid_tags:
            manifest = make_valid_manifest(language_tag=tag)
            is_valid, _ = validate_manifest_schema_v1(manifest)
            self.assertTrue(is_valid, f"Valid tag {tag} was rejected")

        invalid_tags = ["", "   ", "123", "te_IN", "TELUGU", "invalid--tag"]
        for tag in invalid_tags:
            manifest = make_valid_manifest(language_tag=tag)
            is_valid, errors = validate_manifest_schema_v1(manifest)
            self.assertFalse(is_valid, f"Invalid tag {tag} was incorrectly accepted")
            self.assertTrue(any("languageTag" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
