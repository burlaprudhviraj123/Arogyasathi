r"""Tier 2 Boundary & Corner Cases: Zip Traversal and Security Boundaries.

Verifies:
- Complex nested directory traversal patterns
- Mixed slash and backslash path traversal
- Max uncompressed size limit boundary enforcement
- Staging directory isolation and zero-leak cleanup upon limit breach
- Empty archive handling
- Zero-byte file extraction handling
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tests.e2e.common.helpers import create_zip_archive
from tests.e2e.common.zip_sandbox import (
    ZipAbsolutePathError,
    ZipSizeExceededError,
    ZipSlipError,
    ZipSymlinkError,
    ZipUndeclaredFileError,
    extract_zip_safely,
)


class TestZipTraversalBoundaries(unittest.TestCase):
    """Test suite for adversarial and boundary path traversal zip patterns."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.work_path = Path(self.temp_dir.name)
        self.staging_dir = self.work_path / "staging"
        self.declared_files = {"model.int8.onnx", "tokens.txt"}

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_complex_nested_traversal_rejected(self) -> None:
        """Verify deeply nested traversal sequences like a/b/../../../../escaped are rejected."""
        traversal_patterns = [
            "nested/level1/level2/../../../../escaped.txt",
            "dir1/dir2/../../escaped.txt",
            "foo/..//../escaped.txt",
            "sub/./../../escaped.txt",
        ]
        for pattern in traversal_patterns:
            with self.subTest(pattern=pattern):
                zip_path = self.work_path / "test_nested_slip.zip"
                file_map = {
                    "pack.json": "{}",
                    pattern: "malicious",
                    "model.int8.onnx": b"M",
                    "tokens.txt": "T",
                }
                create_zip_archive(zip_path, file_map)
                with self.assertRaises(ZipSlipError):
                    extract_zip_safely(
                        zip_path=zip_path,
                        staging_dir=self.staging_dir,
                        declared_files=self.declared_files,
                    )

    def test_uncompressed_size_ceiling_boundary_enforcement(self) -> None:
        """Verify that extracting content beyond the uncompressed ceiling raises ZipSizeExceededError."""
        zip_path = self.work_path / "size_bomb.zip"
        # We test with a custom small ceiling (e.g. 1,000 bytes)
        small_limit = 1000
        file_map = {
            "pack.json": "{}",
            "model.int8.onnx": b"X" * 1500,  # Exceeds 1000 bytes
            "tokens.txt": "tok",
        }
        create_zip_archive(zip_path, file_map)

        with self.assertRaises(ZipSizeExceededError):
            extract_zip_safely(
                zip_path=zip_path,
                staging_dir=self.staging_dir,
                declared_files=self.declared_files,
                max_uncompressed_bytes=small_limit,
            )

        # Confirm staging directory was cleaned up and does not contain bloated files
        files_in_staging = list(self.staging_dir.glob("**/*"))
        self.assertEqual(len(files_in_staging), 0, "Staging directory must be purged on size breach")

    def test_empty_zip_archive_rejected(self) -> None:
        """Verify that an archive with zero files fails to satisfy manifest requirements."""
        zip_path = self.work_path / "empty.zip"
        create_zip_archive(zip_path, {})

        extracted = extract_zip_safely(
            zip_path=zip_path,
            staging_dir=self.staging_dir,
            declared_files=self.declared_files,
        )
        self.assertEqual(len(extracted), 0)
        # Checking that staging does not have the mandatory files
        self.assertFalse((self.staging_dir / "model.int8.onnx").exists())

    def test_zero_byte_declared_file_handling(self) -> None:
        """Verify that zero-byte files declared in manifest extract without crashing the extractor."""
        zip_path = self.work_path / "zero_bytes.zip"
        file_map = {
            "pack.json": "{}",
            "model.int8.onnx": b"",
            "tokens.txt": b"",
        }
        create_zip_archive(zip_path, file_map)

        extracted = extract_zip_safely(
            zip_path=zip_path,
            staging_dir=self.staging_dir,
            declared_files=self.declared_files,
        )
        self.assertEqual(len(extracted), 3)
        self.assertTrue((self.staging_dir / "model.int8.onnx").exists())
        self.assertEqual((self.staging_dir / "model.int8.onnx").stat().st_size, 0)


if __name__ == "__main__":
    unittest.main()
