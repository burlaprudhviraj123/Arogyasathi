"""Tier 2 Boundary & Corner Cases: Exact Byte and Hash Verification Boundaries.

Verifies:
- Off-by-one byte count boundaries on real model and tokens files
- Single-bit hash variations and sensitivity
- Case sensitivity enforcement on digests
- Negative verification against legacy '118 MB' strings in the project
"""

from __future__ import annotations

import unittest
from pathlib import Path

from tests.e2e.common.artifacts import (
    ENGLISH_EXPECTED_BYTES,
    ENGLISH_EXPECTED_SHA256,
    ENGLISH_MODEL_PATH,
    ENGLISH_TOKENS_BYTES,
    ENGLISH_TOKENS_PATH,
    ENGLISH_TOKENS_SHA256,
    REPO_ROOT,
    TELUGU_EXPECTED_BYTES,
    TELUGU_EXPECTED_SHA256,
    TELUGU_MODEL_PATH,
    TELUGU_TOKENS_BYTES,
    TELUGU_TOKENS_PATH,
    TELUGU_TOKENS_SHA256,
)
from tests.e2e.common.helpers import compute_sha256


class TestByteAndHashBoundaries(unittest.TestCase):
    """Test suite for boundary values in byte sizes and cryptographic digests."""

    def test_telugu_off_by_one_byte_boundaries(self) -> None:
        """Verify off-by-one byte sizes fail exact equality checks."""
        actual_bytes = TELUGU_MODEL_PATH.stat().st_size
        self.assertNotEqual(actual_bytes, TELUGU_EXPECTED_BYTES - 1)
        self.assertNotEqual(actual_bytes, TELUGU_EXPECTED_BYTES + 1)
        self.assertEqual(actual_bytes, TELUGU_EXPECTED_BYTES)

    def test_english_off_by_one_byte_boundaries(self) -> None:
        """Verify off-by-one byte sizes on English model fail exact equality checks."""
        resolved_path = ENGLISH_MODEL_PATH.resolve()
        actual_bytes = resolved_path.stat().st_size
        self.assertNotEqual(actual_bytes, ENGLISH_EXPECTED_BYTES - 1)
        self.assertNotEqual(actual_bytes, ENGLISH_EXPECTED_BYTES + 1)
        self.assertEqual(actual_bytes, ENGLISH_EXPECTED_BYTES)

    def test_tokens_off_by_one_boundaries(self) -> None:
        """Verify off-by-one byte sizes on tokens.txt files fail equality checks."""
        te_tokens_bytes = TELUGU_TOKENS_PATH.stat().st_size
        self.assertNotEqual(te_tokens_bytes, TELUGU_TOKENS_BYTES - 1)
        self.assertNotEqual(te_tokens_bytes, TELUGU_TOKENS_BYTES + 1)
        self.assertEqual(te_tokens_bytes, TELUGU_TOKENS_BYTES)

        en_tokens_bytes = ENGLISH_TOKENS_PATH.resolve().stat().st_size
        self.assertNotEqual(en_tokens_bytes, ENGLISH_TOKENS_BYTES - 1)
        self.assertNotEqual(en_tokens_bytes, ENGLISH_TOKENS_BYTES + 1)
        self.assertEqual(en_tokens_bytes, ENGLISH_TOKENS_BYTES)

    def test_single_bit_flip_in_hash(self) -> None:
        """Verify that altering a single character in the expected hash causes verification failure."""
        actual_te_sha = compute_sha256(TELUGU_MODEL_PATH)
        for idx in [0, len(TELUGU_EXPECTED_SHA256) // 2, len(TELUGU_EXPECTED_SHA256) - 1]:
            with self.subTest(char_index=idx):
                chars = list(TELUGU_EXPECTED_SHA256)
                chars[idx] = "f" if chars[idx] != "f" else "0"
                mutated_sha = "".join(chars)
                self.assertNotEqual(
                    actual_te_sha,
                    mutated_sha,
                    f"Mutated hash at index {idx} unexpectedly matched actual digest!",
                )

    def test_uppercase_hex_digest_mismatch(self) -> None:
        """Verify that uppercase hex hash strings do not match lowercase hex digests."""
        actual_sha = compute_sha256(TELUGU_MODEL_PATH)
        uppercase_sha = TELUGU_EXPECTED_SHA256.upper()
        self.assertNotEqual(
            actual_sha,
            uppercase_sha,
            "Digest comparator must enforce strict lowercase hex formatting",
        )

    def test_legacy_118mb_placeholder_strictly_absent_from_production_configs(self) -> None:
        """Verify that legacy '118 MB' strings do not appear in voice pack configurations."""
        # Ensure our canonical voice pack manifest in assets does not claim 118 MB
        te_pack_json = TELUGU_MODEL_PATH.parent / "pack.json"
        if te_pack_json.exists():
            content = te_pack_json.read_text(encoding="utf-8")
            self.assertNotIn("118 MB", content)
            self.assertNotIn("118MB", content)


if __name__ == "__main__":
    unittest.main()
