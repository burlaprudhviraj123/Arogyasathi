"""Tier 1 Feature Coverage: Silence Decode Health Check.

Verifies:
- Offline silence decode sanity test executes via sherpa-onnx against actual model artifacts
- Telugu INT8 model passes 1.0s synthetic silence decode without error
- English INT8 model passes 1.0s synthetic silence decode without error
- Decoding latency is measured and within reasonable bounds (< 5.0 seconds on host CPU)
- Output transcript on pure silence is empty or whitespace
- Corrupted model files are detected and rejected prior to activation
- Missing model/tokens files trigger health check errors
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tests.e2e.common.artifacts import (
    ENGLISH_MODEL_PATH,
    ENGLISH_TOKENS_PATH,
    TELUGU_MODEL_PATH,
    TELUGU_TOKENS_PATH,
)
from tests.e2e.common.silence_health import HealthCheckError, run_silence_health_check


class TestSilenceDecodeHealthFeature(unittest.TestCase):
    """Test suite verifying silence decode health checks against real models."""

    def setUp(self) -> None:
        if not TELUGU_MODEL_PATH.exists():
            self.skipTest("Telugu model not cached locally (hosted online on GitHub releases)")

    def test_telugu_model_silence_health_check_passes(self) -> None:
        """Verify that real Telugu INT8 model passes silence decode health check."""
        self.assertTrue(TELUGU_MODEL_PATH.exists(), f"Telugu model missing at {TELUGU_MODEL_PATH}")
        self.assertTrue(TELUGU_TOKENS_PATH.exists(), f"Telugu tokens missing at {TELUGU_TOKENS_PATH}")

        passed, transcript, latency = run_silence_health_check(
            model_path=TELUGU_MODEL_PATH,
            tokens_path=TELUGU_TOKENS_PATH,
            duration_seconds=1.0,
        )

        self.assertTrue(passed)
        self.assertEqual(transcript, "", f"Expected empty transcript on pure silence, got '{transcript}'")
        self.assertLess(latency, 5.0, f"Health check took unexpectedly long: {latency}s")

    def test_english_model_silence_health_check_passes(self) -> None:
        """Verify that real English INT8 model passes silence decode health check."""
        resolved_model = ENGLISH_MODEL_PATH.resolve()
        resolved_tokens = ENGLISH_TOKENS_PATH.resolve()
        self.assertTrue(resolved_model.exists(), f"English model missing at {resolved_model}")
        self.assertTrue(resolved_tokens.exists(), f"English tokens missing at {resolved_tokens}")

        passed, transcript, latency = run_silence_health_check(
            model_path=resolved_model,
            tokens_path=resolved_tokens,
            duration_seconds=1.0,
        )

        self.assertTrue(passed)
        self.assertEqual(transcript, "", f"Expected empty transcript on pure silence, got '{transcript}'")
        self.assertLess(latency, 5.0, f"English health check took unexpectedly long: {latency}s")

    def test_corrupt_model_fails_health_check(self) -> None:
        """Verify that a corrupt/truncated ONNX file raises HealthCheckError."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            corrupt_model = tmp_path / "corrupt_model.int8.onnx"
            # Write 100 bytes of random noise / invalid ONNX header
            corrupt_model.write_bytes(b"\x00\x01\x02\x03\x04" * 20)

            with self.assertRaises(HealthCheckError):
                run_silence_health_check(
                    model_path=corrupt_model,
                    tokens_path=TELUGU_TOKENS_PATH,
                )

    def test_missing_model_file_fails_health_check(self) -> None:
        """Verify that non-existent model path raises HealthCheckError."""
        fake_model = Path("/tmp/nonexistent_model_123456.onnx")
        with self.assertRaises(HealthCheckError):
            run_silence_health_check(
                model_path=fake_model,
                tokens_path=TELUGU_TOKENS_PATH,
            )

    def test_missing_tokens_file_fails_health_check(self) -> None:
        """Verify that non-existent tokens path raises HealthCheckError."""
        fake_tokens = Path("/tmp/nonexistent_tokens_123456.txt")
        with self.assertRaises(HealthCheckError):
            run_silence_health_check(
                model_path=TELUGU_MODEL_PATH,
                tokens_path=fake_tokens,
            )


if __name__ == "__main__":
    unittest.main()
