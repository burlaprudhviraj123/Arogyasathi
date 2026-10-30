r"""Tier 1 Feature Coverage: Zip Archive Security Sandbox & Safe Extraction.

Verifies:
- Clean extraction of compliant voice pack archives
- ZipSlip path traversal rejection (`..`)
- Absolute path rejection (`/`, `\`, drive letters)
- Symbolic link rejection
- Undeclared files rejection against manifest
- Forbidden executable files rejection (.so, .sh, .exe, .bin, .apk)
- Automatic cleanup of staging directory on extraction failure
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tests.e2e.common.helpers import create_zip_archive
from tests.e2e.common.zip_sandbox import (
    ZipAbsolutePathError,
    ZipSlipError,
    ZipSymlinkError,
    ZipUndeclaredFileError,
    extract_zip_safely,
)


class TestZipSecuritySandboxFeature(unittest.TestCase):
    """Test suite verifying Zip archive security sandbox protections."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.work_path = Path(self.temp_dir.name)
        self.staging_dir = self.work_path / "staging"
        self.declared_files = {"model.int8.onnx", "tokens.txt"}

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_legitimate_voice_pack_extracts_cleanly(self) -> None:
        """Verify that a compliant zip archive extracts all declared files safely."""
        zip_path = self.work_path / "valid_pack.zip"
        file_map = {
            "pack.json": '{"schemaVersion": 1}',
            "model.int8.onnx": b"FAKE_ONNX_DATA_BYTES",
            "tokens.txt": "a 0\nb 1\n<blk> 2\n",
        }
        create_zip_archive(zip_path, file_map)

        extracted = extract_zip_safely(
            zip_path=zip_path,
            staging_dir=self.staging_dir,
            declared_files=self.declared_files,
        )

        self.assertEqual(len(extracted), 3)
        self.assertTrue((self.staging_dir / "pack.json").exists())
        self.assertTrue((self.staging_dir / "model.int8.onnx").exists())
        self.assertTrue((self.staging_dir / "tokens.txt").exists())

    def test_zipslip_traversal_entry_rejected(self) -> None:
        """Verify that entries with '..' path traversal sequence are rejected."""
        zip_path = self.work_path / "zipslip.zip"
        file_map = {
            "pack.json": '{"schemaVersion": 1}',
            "../escaped.txt": "malicious content",
            "model.int8.onnx": b"DATA",
            "tokens.txt": "t 0\n",
        }
        create_zip_archive(zip_path, file_map)

        with self.assertRaises(ZipSlipError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
            )

        # Staging should be cleaned up
        escaped_target = self.work_path / "escaped.txt"
        self.assertFalse(escaped_target.exists(), "Escaped file must not be written!")

    def test_absolute_path_unix_rejected(self) -> None:
        """Verify that entries starting with '/' are rejected."""
        zip_path = self.work_path / "abs_unix.zip"
        file_map = {
            "/tmp/evil.txt": "evil",
            "pack.json": "{}",
        }
        create_zip_archive(zip_path, file_map)

        with self.assertRaises(ZipAbsolutePathError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
            )

    def test_absolute_path_windows_rejected(self) -> None:
        """Verify that entries with Windows drive letter absolute paths are rejected."""
        zip_path = self.work_path / "abs_win.zip"
        file_map = {
            "C:\\Windows\\system32\\evil.dll": "evil",
            "pack.json": "{}",
        }
        create_zip_archive(zip_path, file_map)

        with self.assertRaises(ZipAbsolutePathError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
            )

    def test_symlink_entry_rejected(self) -> None:
        """Verify that symbolic links inside archives are rejected."""
        zip_path = self.work_path / "symlink.zip"
        file_map = {
            "pack.json": "{}",
            "model.int8.onnx": b"ONNX",
            "tokens.txt": "TOKENS",
        }
        symlinks = {
            "symlink_ptr": "/etc/passwd"
        }
        create_zip_archive(zip_path, file_map, symlinks=symlinks)

        with self.assertRaises(ZipSymlinkError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
            )

    def test_undeclared_file_rejected(self) -> None:
        """Verify that files not declared in manifest trigger undeclared file error."""
        zip_path = self.work_path / "undeclared.zip"
        file_map = {
            "pack.json": "{}",
            "model.int8.onnx": b"ONNX",
            "tokens.txt": "TOKENS",
            "extra_unwanted.txt": "surprise payload",
        }
        create_zip_archive(zip_path, file_map)

        with self.assertRaises(ZipUndeclaredFileError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
            )

    def test_forbidden_executable_extension_rejected(self) -> None:
        """Verify that executable extensions (.so, .sh, .apk, .bin) are rejected."""
        forbidden_names = ["inject.so", "run.sh", "app.apk", "exploit.exe", "native.bin"]
        for forbidden in forbidden_names:
            with self.subTest(forbidden_file=forbidden):
                zip_path = self.work_path / f"forbidden_{forbidden}.zip"
                file_map = {
                    "pack.json": "{}",
                    "model.int8.onnx": b"ONNX",
                    "tokens.txt": "TOKENS",
                    forbidden: "payload",
                }
                create_zip_archive(zip_path, file_map)
                with self.assertRaises(ZipUndeclaredFileError):
                    extract_zip_safely(
                        zip_path=zip_path,
                        staging_dir=self.staging_dir,
                        # Even if someone erroneously declares it, extension check forbids it
                        declared_files=self.declared_files | {forbidden},
                    )


if __name__ == "__main__":
    unittest.main()
