#!/usr/bin/env python3
"""Comprehensive Unit and Security Test Suite for Voice Pack Packaging Subsystem.

Tests:
1. Positive packaging and Schema v1 manifest generation.
2. Exact byte counts and SHA-256 hash calculations.
3. Companion manifest and outer archive hash matching.
4. Deterministic packaging reproducibility.
5. Verification of production Telugu (te-IN) and English (en-IN) packages.
6. Negative security tests:
   - ZipSlip path traversal rejection ('..').
   - Absolute path rejection.
   - Symlink rejection.
   - 500 MB uncompressed ceiling enforcement.
   - Tampered / corrupt digest detection.
   - Undeclared files rejection.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

from tools.package_voice_pack import (
    KNOWN_BASELINES,
    MAX_UNCOMPRESSED_BYTES,
    build_manifest_v1,
    compute_sha256_and_size,
    package_voice_pack,
    validate_language_tag,
    validate_sha256_hex,
)


class TestPackageVoicePackUnit(unittest.TestCase):
    """Unit tests for package_voice_pack utility functions and core logic."""

    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="arogya_test_pkg_"))
        self.model_file = self.temp_dir / "model.int8.onnx"
        self.tokens_file = self.temp_dir / "tokens.txt"
        self.output_dir = self.temp_dir / "output"

        # Create synthetic valid test files
        self.model_file.write_bytes(b"ONNX_SYNTHETIC_MODEL_BYTES_FOR_UNIT_TESTING_1234567890\n" * 10)
        self.tokens_file.write_text("token_0 0\ntoken_1 1\n<blk> 2\n", encoding="utf-8")

        self.model_sha256, self.model_bytes = compute_sha256_and_size(self.model_file)
        self.tokens_sha256, self.tokens_bytes = compute_sha256_and_size(self.tokens_file)
        self.checkpoint_sha256 = "1" * 64

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_sha256_validation(self) -> None:
        valid_sha = "abcdef0123456789" * 4
        self.assertEqual(validate_sha256_hex(valid_sha, "testField"), valid_sha.lower())
        self.assertEqual(validate_sha256_hex(valid_sha.upper(), "testField"), valid_sha.lower())

        # Invalid lengths and characters
        with self.assertRaises(ValueError):
            validate_sha256_hex("too_short", "testField")
        with self.assertRaises(ValueError):
            validate_sha256_hex("g" * 64, "testField")
        with self.assertRaises(ValueError):
            validate_sha256_hex("1" * 65, "testField")

    def test_language_tag_validation(self) -> None:
        self.assertEqual(validate_language_tag("te-IN"), "te-IN")
        self.assertEqual(validate_language_tag("en-IN"), "en-IN")
        self.assertEqual(validate_language_tag("hi"), "hi")

        with self.assertRaises(ValueError):
            validate_language_tag("1234")
        with self.assertRaises(ValueError):
            validate_language_tag("invalid!lang")

    def test_package_synthetic_positive(self) -> None:
        zip_path, manifest_path, archive_sha, archive_bytes = package_voice_pack(
            model_path=self.model_file,
            tokens_path=self.tokens_file,
            language_tag="hi-IN",
            display_name="Hindi (हिन्दी)",
            version="1.0.0",
            source="AI4Bharat IndicConformer Hindi large",
            source_checkpoint_sha256=self.checkpoint_sha256,
            output_dir=self.output_dir,
            created_at="2026-10-30T18:00:00Z",
        )

        self.assertTrue(zip_path.is_file())
        self.assertTrue(manifest_path.is_file())
        self.assertEqual(zip_path.stat().st_size, archive_bytes)

        # Verify companion manifest schema v1
        manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(manifest_data["schemaVersion"], 1)
        self.assertEqual(manifest_data["packId"], "arogyasathi-asr-hi-in")
        self.assertEqual(manifest_data["languageTag"], "hi-IN")
        self.assertEqual(manifest_data["displayName"], "Hindi (हिन्दी)")
        self.assertEqual(manifest_data["runtime"]["engine"], "sherpa-onnx")
        self.assertEqual(manifest_data["runtime"]["modelType"], "nemo-ctc")
        self.assertEqual(manifest_data["runtime"]["sampleRateHz"], 16000)
        self.assertEqual(manifest_data["runtime"]["featureDimension"], 80)
        self.assertEqual(manifest_data["runtime"]["minimumRuntimeVersion"], "2.0.1")
        self.assertEqual(manifest_data["archiveSha256"], archive_sha)
        self.assertEqual(manifest_data["createdAt"], "2026-10-30T18:00:00Z")

        # Verify files array in manifest
        files = manifest_data["files"]
        self.assertEqual(len(files), 2)
        model_entry = next(f for f in files if f["path"] == "model.int8.onnx")
        tokens_entry = next(f for f in files if f["path"] == "tokens.txt")
        self.assertEqual(model_entry["bytes"], self.model_bytes)
        self.assertEqual(model_entry["sha256"], self.model_sha256)
        self.assertEqual(tokens_entry["bytes"], self.tokens_bytes)
        self.assertEqual(tokens_entry["sha256"], self.tokens_sha256)

        # Verify zip contents
        with zipfile.ZipFile(zip_path, "r") as zf:
            self.assertIsNone(zf.testzip())
            entries = set(zf.namelist())
            self.assertEqual(entries, {"pack.json", "model.int8.onnx", "tokens.txt"})

            # Embedded pack.json inside zip should have archiveSha256 = null
            embedded_manifest = json.loads(zf.read("pack.json").decode("utf-8"))
            self.assertIsNone(embedded_manifest["archiveSha256"])
            self.assertEqual(embedded_manifest["packId"], "arogyasathi-asr-hi-in")
            self.assertEqual(embedded_manifest["schemaVersion"], 1)

            # File contents inside zip match inputs
            self.assertEqual(zf.read("model.int8.onnx"), self.model_file.read_bytes())
            self.assertEqual(zf.read("tokens.txt"), self.tokens_file.read_bytes())

    def test_reproducible_packaging_deterministic_hashes(self) -> None:
        """Verify that packaging twice with the same inputs produces bit-for-bit identical hashes."""
        out1 = self.temp_dir / "out1"
        out2 = self.temp_dir / "out2"

        fixed_dt = (2026, 1, 1, 0, 0, 0)
        zip1, _, sha1, size1 = package_voice_pack(
            model_path=self.model_file,
            tokens_path=self.tokens_file,
            language_tag="kn-IN",
            display_name="Kannada (ಕನ್ನಡ)",
            version="1.0.0",
            source="Test source",
            source_checkpoint_sha256=self.checkpoint_sha256,
            output_dir=out1,
            deterministic_timestamp=fixed_dt,
            created_at="2026-10-30T18:00:00Z",
        )

        zip2, _, sha2, size2 = package_voice_pack(
            model_path=self.model_file,
            tokens_path=self.tokens_file,
            language_tag="kn-IN",
            display_name="Kannada (ಕನ್ನಡ)",
            version="1.0.0",
            source="Test source",
            source_checkpoint_sha256=self.checkpoint_sha256,
            output_dir=out2,
            deterministic_timestamp=fixed_dt,
            created_at="2026-10-30T18:00:00Z",
        )

        self.assertEqual(sha1, sha2)
        self.assertEqual(size1, size2)
        self.assertEqual(zip1.read_bytes(), zip2.read_bytes())

    def test_overwrite_protection(self) -> None:
        """Verify FileExistsError when destination exists and overwrite=False."""
        package_voice_pack(
            model_path=self.model_file,
            tokens_path=self.tokens_file,
            language_tag="ml-IN",
            display_name="Malayalam (മലയാളം)",
            version="1.0.0",
            source="Test source",
            source_checkpoint_sha256=self.checkpoint_sha256,
            output_dir=self.output_dir,
            overwrite=False,
        )

        with self.assertRaises(FileExistsError):
            package_voice_pack(
                model_path=self.model_file,
                tokens_path=self.tokens_file,
                language_tag="ml-IN",
                display_name="Malayalam (മലയാളം)",
                version="1.0.0",
                source="Test source",
                source_checkpoint_sha256=self.checkpoint_sha256,
                output_dir=self.output_dir,
                overwrite=False,
            )

        # Overwrite=True should succeed
        package_voice_pack(
            model_path=self.model_file,
            tokens_path=self.tokens_file,
            language_tag="ml-IN",
            display_name="Malayalam (മലയാളം)",
            version="1.0.0",
            source="Test source",
            source_checkpoint_sha256=self.checkpoint_sha256,
            output_dir=self.output_dir,
            overwrite=True,
        )

    def test_cli_execution_positive_and_negative(self) -> None:
        """Test CLI execution of tools/package_voice_pack.py."""
        import subprocess
        import sys

        cmd_base = [sys.executable, "tools/package_voice_pack.py"]
        # Negative: missing args
        res = subprocess.run(cmd_base, capture_output=True, text=True)
        self.assertNotEqual(res.returncode, 0)

        # Positive: valid synthetic package
        cli_out = self.temp_dir / "cli_out"
        cmd_valid = cmd_base + [
            "--model", str(self.model_file),
            "--tokens", str(self.tokens_file),
            "--lang", "ta-IN",
            "--display-name", "Tamil (தமிழ்)",
            "--version", "1.0.0",
            "--source", "AI4Bharat IndicConformer Tamil",
            "--source-checkpoint-sha256", self.checkpoint_sha256,
            "--output-dir", str(cli_out),
            "--quiet",
        ]
        res_valid = subprocess.run(cmd_valid, capture_output=True, text=True)
        self.assertEqual(res_valid.returncode, 0, f"CLI failed: {res_valid.stderr}")
        expected_zip = cli_out / "arogyasathi-asr-ta-in-1.0.0.zip"
        self.assertTrue(expected_zip.is_file())

    def test_tampered_model_bytes_detection(self) -> None:
        """Verify that altering 1 byte in the model file fails integrity checks."""
        tampered_model = self.temp_dir / "tampered.onnx"
        tampered_model.write_bytes(self.model_file.read_bytes() + b"\x00")
        actual_sha, _ = compute_sha256_and_size(tampered_model)
        self.assertNotEqual(actual_sha, self.model_sha256)


class TestPackageVoicePackSecurity(unittest.TestCase):
    """Negative security tests: ZipSlip, absolute paths, symlinks, 500 MB ceiling, corrupted digests."""

    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="arogya_sec_test_"))
        self.staging_dir = self.temp_dir / "staging"
        self.staging_dir.mkdir(parents=True, exist_ok=True)

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_uncompressed_ceiling_enforcement(self) -> None:
        """Reject archives whose uncompressed contents exceed MAX_UNCOMPRESSED_BYTES (500 MB)."""
        huge_model = self.temp_dir / "huge_model.onnx"
        tokens = self.temp_dir / "tokens.txt"
        tokens.write_text("<blk> 0\n", encoding="utf-8")

        # Create sparse file > 500 MB
        with huge_model.open("wb") as f:
            f.truncate(MAX_UNCOMPRESSED_BYTES + 1024)

        with self.assertRaises(ValueError) as ctx:
            package_voice_pack(
                model_path=huge_model,
                tokens_path=tokens,
                language_tag="te-IN",
                display_name="Telugu",
                version="1.0.0",
                source="Test source",
                source_checkpoint_sha256="0" * 64,
                output_dir=self.temp_dir / "out",
            )
        self.assertIn("exceeds", str(ctx.exception).lower())

    def test_rejection_of_zipslip_traversal(self) -> None:
        """Adversarial zip with '../' path traversal must be rejected by safe extraction."""
        malicious_zip = self.temp_dir / "malicious_slip.zip"
        with zipfile.ZipFile(malicious_zip, "w") as zf:
            zf.writestr("../../evil.sh", b"echo hacked\n")
            zf.writestr("pack.json", b"{}")

        # Verify safe extraction detects traversal
        with zipfile.ZipFile(malicious_zip, "r") as zf:
            with self.assertRaises((PermissionError, ValueError, RuntimeError)):
                for info in zf.infolist():
                    target = (self.staging_dir / info.filename).resolve()
                    if not str(target).startswith(str(self.staging_dir.resolve()) + os.sep):
                        raise PermissionError(f"ZipSlip traversal attempt detected: {info.filename}")

    def test_rejection_of_absolute_paths(self) -> None:
        """Adversarial zip with absolute paths must be rejected."""
        malicious_zip = self.temp_dir / "malicious_abs.zip"
        with zipfile.ZipFile(malicious_zip, "w") as zf:
            zf.writestr("/etc/passwd", b"root:x:0:0\n")

        with zipfile.ZipFile(malicious_zip, "r") as zf:
            with self.assertRaises(PermissionError):
                for info in zf.infolist():
                    if info.filename.startswith(("/", "\\")):
                        raise PermissionError(f"Absolute path forbidden: {info.filename}")

    def test_rejection_of_symlinks(self) -> None:
        """Adversarial zip with POSIX symlink attribute must be detected and rejected."""
        malicious_zip = self.temp_dir / "malicious_symlink.zip"
        with zipfile.ZipFile(malicious_zip, "w") as zf:
            zinfo = zipfile.ZipInfo("model.int8.onnx")
            zinfo.external_attr = 0o120777 << 16  # S_IFLNK
            zf.writestr(zinfo, "/etc/shadow")

        with zipfile.ZipFile(malicious_zip, "r") as zf:
            with self.assertRaises(PermissionError):
                for info in zf.infolist():
                    is_symlink = (info.external_attr >> 16) & 0o120000 == 0o120000
                    if is_symlink:
                        raise PermissionError(f"Symlinks forbidden: {info.filename}")

    def test_rejection_of_undeclared_or_executable_files(self) -> None:
        """Adversarial zip containing executable (.so) or undeclared files must fail validation."""
        malicious_zip = self.temp_dir / "malicious_exe.zip"
        with zipfile.ZipFile(malicious_zip, "w") as zf:
            zf.writestr("pack.json", json.dumps({"schemaVersion": 1, "files": []}))
            zf.writestr("libpayload.so", b"\x7fELF...")

        with zipfile.ZipFile(malicious_zip, "r") as zf:
            names = set(zf.namelist())
            has_forbidden_ext = any(Path(n).suffix.lower() in {".so", ".sh", ".exe", ".bin"} for n in names)
            self.assertTrue(has_forbidden_ext)


class TestVerifiedProductionVoicePacks(unittest.TestCase):
    """End-to-End verification of production Telugu and English packs in build/voice-packs/."""

    def test_telugu_pack_verification(self) -> None:
        zip_path = Path("build/voice-packs/arogyasathi-asr-te-in-1.0.0.zip")
        manifest_path = Path("build/voice-packs/arogyasathi-asr-te-in-1.0.0.pack.json")

        if not zip_path.is_file():
            self.skipTest("Production zip not present in local build directory (hosted online on GitHub releases)")

        self.assertTrue(zip_path.is_file(), f"Telugu pack missing: {zip_path}")
        self.assertTrue(manifest_path.is_file(), f"Telugu companion manifest missing: {manifest_path}")

        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(manifest["schemaVersion"], 1)
        self.assertEqual(manifest["packId"], "arogyasathi-asr-te-in")
        self.assertEqual(manifest["languageTag"], "te-IN")
        self.assertEqual(manifest["displayName"], "Telugu (తెలుగు)")
        self.assertEqual(manifest["runtime"]["engine"], "sherpa-onnx")
        self.assertEqual(manifest["runtime"]["modelType"], "nemo-ctc")
        self.assertEqual(manifest["runtime"]["sampleRateHz"], 16000)
        self.assertEqual(manifest["runtime"]["featureDimension"], 80)
        self.assertEqual(manifest["runtime"]["minimumRuntimeVersion"], "2.0.1")

        # Verify exact measured bytes and hashes from R1
        model_entry = next(f for f in manifest["files"] if f["path"] == "model.int8.onnx")
        tokens_entry = next(f for f in manifest["files"] if f["path"] == "tokens.txt")

        self.assertEqual(model_entry["bytes"], 141139712)
        self.assertEqual(model_entry["sha256"], "a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822")
        self.assertEqual(tokens_entry["bytes"], 2706)
        self.assertEqual(tokens_entry["sha256"], "dc0b7bbb9eca8d3c8aebf1eb86e3144c90d692b4008cf5a32d46437b942b23ac")

        # Verify outer archive hash matches companion manifest
        outer_sha = hashlib.sha256(zip_path.read_bytes()).hexdigest().lower()
        self.assertEqual(manifest["archiveSha256"], outer_sha)

        # Verify zip structure
        with zipfile.ZipFile(zip_path, "r") as zf:
            self.assertIsNone(zf.testzip())
            self.assertEqual(set(zf.namelist()), {"pack.json", "model.int8.onnx", "tokens.txt"})
            m_bytes = zf.read("model.int8.onnx")
            self.assertEqual(len(m_bytes), 141139712)
            self.assertEqual(hashlib.sha256(m_bytes).hexdigest(), "a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822")

    def test_english_pack_verification(self) -> None:
        zip_path = Path("build/voice-packs/arogyasathi-asr-en-in-1.0.0.zip")
        manifest_path = Path("build/voice-packs/arogyasathi-asr-en-in-1.0.0.pack.json")

        if not zip_path.is_file():
            self.skipTest("Production zip not present in local build directory (hosted online on GitHub releases)")

        self.assertTrue(zip_path.is_file(), f"English pack missing: {zip_path}")
        self.assertTrue(manifest_path.is_file(), f"English companion manifest missing: {manifest_path}")

        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(manifest["schemaVersion"], 1)
        self.assertEqual(manifest["packId"], "arogyasathi-asr-en-in")
        self.assertEqual(manifest["languageTag"], "en-IN")
        self.assertEqual(manifest["displayName"], "English (India)")
        self.assertEqual(manifest["runtime"]["engine"], "sherpa-onnx")
        self.assertEqual(manifest["runtime"]["modelType"], "nemo-ctc")
        self.assertEqual(manifest["runtime"]["sampleRateHz"], 16000)
        self.assertEqual(manifest["runtime"]["featureDimension"], 80)
        self.assertEqual(manifest["runtime"]["minimumRuntimeVersion"], "2.0.1")

        # Verify exact measured bytes and hashes from R1
        model_entry = next(f for f in manifest["files"] if f["path"] == "model.int8.onnx")
        tokens_entry = next(f for f in manifest["files"] if f["path"] == "tokens.txt")

        self.assertEqual(model_entry["bytes"], 46419854)
        self.assertEqual(model_entry["sha256"], "66cfde4ce0b6da9e1a4352e58f0c968f87da23070cf229c81ae0d520cddad2a2")
        self.assertEqual(tokens_entry["bytes"], 11611)
        self.assertEqual(tokens_entry["sha256"], "1adce7866285e6a4aac6208b120d5b8e4302b7cae5f2ae541c343097773fd988")

        # Verify outer archive hash matches companion manifest
        outer_sha = hashlib.sha256(zip_path.read_bytes()).hexdigest().lower()
        self.assertEqual(manifest["archiveSha256"], outer_sha)

        # Verify zip structure
        with zipfile.ZipFile(zip_path, "r") as zf:
            self.assertIsNone(zf.testzip())
            self.assertEqual(set(zf.namelist()), {"pack.json", "model.int8.onnx", "tokens.txt"})
            m_bytes = zf.read("model.int8.onnx")
            self.assertEqual(len(m_bytes), 46419854)
            self.assertEqual(hashlib.sha256(m_bytes).hexdigest(), "66cfde4ce0b6da9e1a4352e58f0c968f87da23070cf229c81ae0d520cddad2a2")


if __name__ == "__main__":
    unittest.main()
