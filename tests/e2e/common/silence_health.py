"""Silence Decode Health Check Engine.

Derived strictly from ORIGINAL_REQUEST.md (§R3) and PROJECT.md (§Feature 16).
Initializes a local sherpa-onnx offline recognizer from staging directory and executes
synthetic silence decoding before permitting activation.
"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Optional, Tuple

import numpy as np
import sherpa_onnx


class HealthCheckError(RuntimeError):
    """Raised when the silence decode health check fails."""
    pass


def run_silence_health_check(
    model_path: Path,
    tokens_path: Path,
    duration_seconds: float = 1.0,
    sample_rate_hz: int = 16000,
    feature_dim: int = 80,
    num_threads: int = 2,
) -> Tuple[bool, str, float]:
    """Execute synthetic silence decode health check against an offline NeMo CTC model.

    Args:
        model_path: Path to model.int8.onnx file.
        tokens_path: Path to tokens.txt vocabulary file.
        duration_seconds: Duration of synthetic silence to test (default 1.0s).
        sample_rate_hz: Audio sample rate in Hz (must be 16000).
        feature_dim: Mel filterbank feature dimension (must be 80).
        num_threads: Number of CPU decoding threads (default 2).

    Returns:
        (passed, output_transcript, latency_seconds)

    Raises:
        HealthCheckError: If model initialization or decoding throws an unhandled error.
    """
    if not model_path.exists():
        raise HealthCheckError(f"Model file not found: {model_path}")
    if model_path.stat().st_size == 0:
        raise HealthCheckError(f"Model file is empty (0 bytes): {model_path}")

    if not tokens_path.exists():
        raise HealthCheckError(f"Tokens file not found: {tokens_path}")
    if tokens_path.stat().st_size == 0:
        raise HealthCheckError(f"Tokens file is empty (0 bytes): {tokens_path}")

    if duration_seconds <= 0:
        raise HealthCheckError(f"Silence duration must be strictly positive, got {duration_seconds}s")

    # Check that tokens.txt contains blank/epsilon symbol to prevent C++ fatal abort
    try:
        tokens_content = tokens_path.read_text(encoding="utf-8", errors="ignore")
        if not any(blk in tokens_content for blk in ("<blk>", "<eps>", "<blank>")):
            raise HealthCheckError("Tokens file missing mandatory blank symbol (<blk>/<eps>/<blank>)")
    except HealthCheckError:
        raise
    except Exception as e:
        raise HealthCheckError(f"Failed to read tokens file: {e}") from e

    start_time = time.perf_counter()

    try:
        recognizer = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=str(model_path.resolve()),
            tokens=str(tokens_path.resolve()),
            num_threads=num_threads,
            sample_rate=sample_rate_hz,
            feature_dim=feature_dim,
        )
    except Exception as e:
        raise HealthCheckError(f"Failed to instantiate OfflineRecognizer from staging files: {e}") from e

    try:
        stream = recognizer.create_stream()
        num_samples = int(duration_seconds * sample_rate_hz)
        silence_waveform = np.zeros(num_samples, dtype=np.float32)

        stream.accept_waveform(sample_rate=sample_rate_hz, waveform=silence_waveform)
        recognizer.decode_stream(stream)
        transcript = stream.result.text.strip()

        latency = time.perf_counter() - start_time
        # A valid silence health check must return empty or whitespace
        return True, transcript, latency

    except Exception as e:
        raise HealthCheckError(f"Exception during silence decode stream processing: {e}") from e
