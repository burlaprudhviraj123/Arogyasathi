"""Canonical artifact paths, expected byte counts, and SHA-256 digests.

Derived strictly from ORIGINAL_REQUEST.md (§R1), PROJECT.md, and
docs/research/08_OFFLINE_ASR_SOURCE_MODEL_AUDIT.md.
"""

from __future__ import annotations

import os
from pathlib import Path

# Workspace root
REPO_ROOT = Path(__file__).resolve().parents[3]

# Telugu Artifacts (exported on-demand pack in build/exported/te-IN with fallback to assets)
TELUGU_EXPORTED_DIR = REPO_ROOT / "build/exported/te-IN"
TELUGU_ASSETS_DIR = REPO_ROOT / "android_app/app/src/main/assets/voice-packs/te-IN"
TELUGU_DIR = TELUGU_EXPORTED_DIR if TELUGU_EXPORTED_DIR.exists() else TELUGU_ASSETS_DIR
TELUGU_MODEL_PATH = TELUGU_DIR / "model.int8.onnx"
TELUGU_TOKENS_PATH = TELUGU_DIR / "tokens.txt"

TELUGU_EXPECTED_BYTES = 141_139_712
TELUGU_EXPECTED_SHA256 = "a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822"
TELUGU_TOKENS_BYTES = 2_706
TELUGU_TOKENS_SHA256 = "dc0b7bbb9eca8d3c8aebf1eb86e3144c90d692b4008cf5a32d46437b942b23ac"
TELUGU_TOKENS_LINES = 257

# English Artifacts (bundled in APK assets, with fallback to cached HF snapshot)
EN_ASSETS_DIR = REPO_ROOT / "android_app/app/src/main/assets/voice-packs/en-IN"
HF_HUB_EN_DIR = Path.home() / ".cache/huggingface/hub/models--csukuangfj--sherpa-onnx-nemo-ctc-en-conformer-small"
EN_SNAPSHOT_DIR = HF_HUB_EN_DIR / "snapshots/808a1d9d688b060b008b4fb50817e0b1cb51e572"

ENGLISH_DIR = EN_ASSETS_DIR if EN_ASSETS_DIR.exists() else EN_SNAPSHOT_DIR
ENGLISH_MODEL_PATH = ENGLISH_DIR / "model.int8.onnx"
ENGLISH_TOKENS_PATH = ENGLISH_DIR / "tokens.txt"

ENGLISH_EXPECTED_BYTES = 46_419_854
ENGLISH_EXPECTED_SHA256 = "66cfde4ce0b6da9e1a4352e58f0c968f87da23070cf229c81ae0d520cddad2a2"
ENGLISH_TOKENS_BYTES = 11_611
ENGLISH_TOKENS_SHA256 = "1adce7866285e6a4aac6208b120d5b8e4302b7cae5f2ae541c343097773fd988"
ENGLISH_TOKENS_LINES = 1025

# Upper limits and thresholds
MAX_UNCOMPRESSED_ARCHIVE_BYTES = 524_288_000  # 500 MB
PREFLIGHT_STORAGE_MULTIPLIER = 2.5
PREFLIGHT_STORAGE_MIN_BYTES = 524_288_000
