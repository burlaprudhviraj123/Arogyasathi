"""Tier 2 Boundary & Corner Cases: Silence Decode Health Check Boundaries.

Verifies:
- Variable duration silence inputs (0.1s, 2.0s)
- Zero duration / 0 samples handling
- Zero-byte empty model file handling
- Zero-byte empty tokens file handling
- Thread scaling boundary conditions (1 vs 4 CPU threads)
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tests.e2e.common.artifacts import TELUGU_MODEL_PATH, TELUGU_TOKENS_PATH
from tests.e2e.common.silence_health import HealthCheckError, run_silence_health_check


class TestSilenceDecodeBoundaries(unittest.TestCase):
    """Test suite for silence decode boundary conditions and stress tests."""

    def setUp(self) -> None:
        if not TELUGU_MODEL_PATH.exists():
            self.skipTest("Telugu model not cached locally (hosted online on GitHub releases)")

    def test_short_silence_duration(self) -> None:
        """Verify that a short 100ms (1,600 samples) silence slice decodes cleanly."""
        passed, transcript, latency = run_silence_health_check(
            model_path=TELUGU_MODEL_PATH,
            tokens_path=TELUGU_TOKENS_PATH,
            duration_seconds=0.1,
        )
        self.assertTrue(passed)
        self.assertEqual(transcript, "")
        self.assertLess(latency, 2.0)

    def test_extended_silence_duration(self) -> None:
        """Verify that standard 1.0s (16,000 samples) silence stream decodes cleanly to empty text."""
        passed, transcript, latency = run_silence_health_check(
            model_path=TELUGU_MODEL_PATH,
            tokens_path=TELUGU_TOKENS_PATH,
            duration_seconds=1.0,
        )
        self.assertTrue(passed)
        self.assertEqual(transcript, "")
        self.assertLess(latency, 4.0)

    def test_zero_duration_silence_raises_health_check_error(self) -> None:
        """Verify that zero duration (0 samples) raises HealthCheckError rather than invoking C++ with empty shape."""
        with self.assertRaises(HealthCheckError):
            run_silence_health_check(
                model_path=TELUGU_MODEL_PATH,
                tokens_path=TELUGU_TOKENS_PATH,
                duration_seconds=0.0,
            )

    def test_zero_byte_model_file_fails_gracefully(self) -> None:
        """Verify that a 0-byte model file raises HealthCheckError without crashing the process."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            zero_model = Path(tmp_dir) / "empty_model.onnx"
            zero_model.touch()

            with self.assertRaises(HealthCheckError):
                run_silence_health_check(
                    model_path=zero_model,
                    tokens_path=TELUGU_TOKENS_PATH,
                )

    def test_zero_byte_tokens_file_fails_gracefully(self) -> None:
        """Verify that a 0-byte tokens file raises HealthCheckError."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            zero_tokens = Path(tmp_dir) / "empty_tokens.txt"
            zero_tokens.touch()

            with self.assertRaises(HealthCheckError):
                run_silence_health_check(
                    model_path=TELUGU_MODEL_PATH,
                    tokens_path=zero_tokens,
                )

    def test_thread_concurrency_boundaries(self) -> None:
        """Verify health check executes with 1 thread and with 4 threads on 1.0s silence."""
        for threads in (1, 4):
            with self.subTest(num_threads=threads):
                passed, transcript, latency = run_silence_health_check(
                    model_path=TELUGU_MODEL_PATH,
                    tokens_path=TELUGU_TOKENS_PATH,
                    duration_seconds=1.0,
                    num_threads=threads,
                )
                self.assertTrue(passed)
                self.assertEqual(transcript, "")


if __name__ == "__main__":
    unittest.main()
