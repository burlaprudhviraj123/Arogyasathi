"""Tier 1 Feature Coverage: Exact Byte Counts and SHA-256 Digests.

Verifies:
- Telugu INT8 model measured at exactly 141,139,712 bytes (~134.6 MB)
- Telugu SHA-256 digest: a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822
- Telugu tokens: 2,706 bytes, 257 lines, SHA-256 dc0b7bbb9eca8d3c8aebf1eb86e3144c90d692b4008cf5a32d46437b942b23ac
- English INT8 model measured at exactly 46,419,854 bytes (~44.27 MB)
- English SHA-256 digest: 66cfde4ce0b6da9e1a4352e58f0c968f87da23070cf229c81ae0d520cddad2a2
- English tokens: 11,611 bytes, 1,025 lines, SHA-256 1adce7866285e6a4aac6208b120d5b8e4302b7cae5f2ae541c343097773fd988
- Rejection of legacy 118 MB claims and altered hashes
"""

from __future__ import annotations

import unittest
from pathlib import Path

from tests.e2e.common.artifacts import (
    ENGLISH_EXPECTED_BYTES,
    ENGLISH_EXPECTED_SHA256,
    ENGLISH_MODEL_PATH,
    ENGLISH_TOKENS_BYTES,
    ENGLISH_TOKENS_LINES,
    ENGLISH_TOKENS_PATH,
    ENGLISH_TOKENS_SHA256,
    TELUGU_EXPECTED_BYTES,
    TELUGU_EXPECTED_SHA256,
    TELUGU_MODEL_PATH,
    TELUGU_TOKENS_BYTES,
    TELUGU_TOKENS_LINES,
    TELUGU_TOKENS_PATH,
    TELUGU_TOKENS_SHA256,
)
from tests.e2e.common.helpers import compute_sha256


class TestExactBytesAndHashesFeature(unittest.TestCase):
    """Test suite verifying exact byte counts and SHA-256 digests against real artifacts."""

    def test_telugu_model_file_exists_and_exact_byte_count(self) -> None:
        """Verify Telugu INT8 model exists on disk and has exact size 141,139,712 bytes."""
        self.assertTrue(TELUGU_MODEL_PATH.exists(), f"Telugu model missing at {TELUGU_MODEL_PATH}")
        actual_bytes = TELUGU_MODEL_PATH.stat().st_size
        self.assertEqual(
            actual_bytes,
            TELUGU_EXPECTED_BYTES,
            f"Telugu model byte mismatch: expected {TELUGU_EXPECTED_BYTES}, got {actual_bytes}",
        )

    def test_telugu_model_sha256_exact_digest(self) -> None:
        """Verify Telugu INT8 model SHA-256 hash matches the authoritative checkpoint digest."""
        actual_sha256 = compute_sha256(TELUGU_MODEL_PATH)
        self.assertEqual(
            actual_sha256,
            TELUGU_EXPECTED_SHA256,
            f"Telugu model SHA-256 mismatch: expected {TELUGU_EXPECTED_SHA256}, got {actual_sha256}",
        )

    def test_telugu_tokens_file_exact_bytes_and_hash(self) -> None:
        """Verify Telugu tokens.txt byte size (2,706 B), line count (257), and SHA-256."""
        self.assertTrue(TELUGU_TOKENS_PATH.exists(), f"Telugu tokens missing at {TELUGU_TOKENS_PATH}")
        actual_bytes = TELUGU_TOKENS_PATH.stat().st_size
        self.assertEqual(actual_bytes, TELUGU_TOKENS_BYTES)

        actual_sha256 = compute_sha256(TELUGU_TOKENS_PATH)
        self.assertEqual(actual_sha256, TELUGU_TOKENS_SHA256)

        with TELUGU_TOKENS_PATH.open("r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
        self.assertEqual(len(lines), TELUGU_TOKENS_LINES)
        self.assertTrue(lines[-1].startswith("<blk>"), "Last token in tokens.txt must be blank token <blk>")

    def test_english_model_file_exists_and_exact_byte_count(self) -> None:
        """Verify English INT8 model exists on disk and has exact size 46,419,854 bytes."""
        # Check either snapshot or direct blob target
        resolved_path = ENGLISH_MODEL_PATH.resolve()
        self.assertTrue(resolved_path.exists(), f"English model missing at {ENGLISH_MODEL_PATH}")
        actual_bytes = resolved_path.stat().st_size
        self.assertEqual(
            actual_bytes,
            ENGLISH_EXPECTED_BYTES,
            f"English model byte mismatch: expected {ENGLISH_EXPECTED_BYTES}, got {actual_bytes}",
        )

    def test_english_model_sha256_exact_digest(self) -> None:
        """Verify English INT8 model SHA-256 matches authoritative digest."""
        resolved_path = ENGLISH_MODEL_PATH.resolve()
        actual_sha256 = compute_sha256(resolved_path)
        self.assertEqual(
            actual_sha256,
            ENGLISH_EXPECTED_SHA256,
            f"English model SHA-256 mismatch: expected {ENGLISH_EXPECTED_SHA256}, got {actual_sha256}",
        )

    def test_english_tokens_file_exact_bytes_and_hash(self) -> None:
        """Verify English tokens.txt byte size (11,611 B), line count (1,025), and SHA-256."""
        resolved_path = ENGLISH_TOKENS_PATH.resolve()
        self.assertTrue(resolved_path.exists(), f"English tokens missing at {ENGLISH_TOKENS_PATH}")
        actual_bytes = resolved_path.stat().st_size
        self.assertEqual(actual_bytes, ENGLISH_TOKENS_BYTES)

        actual_sha256 = compute_sha256(resolved_path)
        self.assertEqual(actual_sha256, ENGLISH_TOKENS_SHA256)

        with resolved_path.open("r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
        self.assertEqual(len(lines), ENGLISH_TOKENS_LINES)
        self.assertTrue(lines[-1].startswith("<blk>"), "Last token in English tokens.txt must be <blk>")

    def test_legacy_placeholder_values_strictly_prohibited(self) -> None:
        """Verify that legacy 118 MB values (123,731,968 B) are rejected."""
        legacy_118mb_bytes = 118 * 1024 * 1024
        self.assertNotEqual(TELUGU_EXPECTED_BYTES, legacy_118mb_bytes)
        self.assertNotEqual(ENGLISH_EXPECTED_BYTES, legacy_118mb_bytes)


if __name__ == "__main__":
    unittest.main()
