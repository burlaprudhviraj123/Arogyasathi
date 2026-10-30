#!/usr/bin/env python3
"""Unit tests for Multilingual IndicConformer CTC Pack Exporter.

Tests:
1. Supported languages table and audited ConvASRDecoder CTC offsets.
2. Token extraction format (256 BPE classes + <blk> 256 = 257 lines).
3. SlicedIndicConformerCtcWrapper initialization and tensor slicing logic.
4. CLI argument parsing and validation.
5. Error handling on unsupported languages or corrupted tokenizers.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import MagicMock

from tools.export_indicconformer_pack import (
    SUPPORTED_LANGUAGES,
    SlicedIndicConformerCtcWrapper,
    parse_arguments,
    sha256_file,
    write_tokens,
)


class TestExportIndicConformerPack(unittest.TestCase):
    """Unit test cases for export_indicconformer_pack."""

    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="arogya_test_export_"))

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_supported_languages_and_audited_offsets(self) -> None:
        """Verify all 5 Indic languages have exact audited ConvASRDecoder CTC offsets."""
        expected_offsets = {
            "hi": 1536,
            "kn": 1792,
            "ml": 2560,
            "ta": 4864,
            "te": 5120,
        }
        self.assertEqual(set(SUPPORTED_LANGUAGES.keys()), set(expected_offsets.keys()))

        for lang, expected_offset in expected_offsets.items():
            info = SUPPORTED_LANGUAGES[lang]
            self.assertEqual(info["language_code"], lang)
            self.assertEqual(info["language_tag"], f"{lang}-IN")
            self.assertEqual(info["ctc_offset"], expected_offset)
            self.assertEqual(info["vocab_size"], 256)
            self.assertIn(lang, info["hf_model_id"])
            self.assertTrue(len(info["display_name"]) > 0)

    def test_token_file_generation_257_lines(self) -> None:
        """Verify write_tokens extracts 256 BPE tokens + 1 trailing blank token (257 lines)."""
        # Create mock ASRModel and tokenizer
        mock_tokenizer = MagicMock()
        mock_tokenizer.vocab = [f"t_{i}" for i in range(256)]
        mock_tokenizer.ids_to_tokens.side_effect = lambda ids: [f"token_{ids[0]}"]

        mock_model = MagicMock()
        mock_model.tokenizer.tokenizers_dict = {"te": mock_tokenizer}

        out_tokens = self.temp_dir / "tokens.txt"
        vocab_size = write_tokens(mock_model, "te", out_tokens)

        self.assertEqual(vocab_size, 256)
        self.assertTrue(out_tokens.is_file())

        lines = out_tokens.read_text(encoding="utf-8").strip().splitlines()
        self.assertEqual(len(lines), 257)
        self.assertEqual(lines[0], "token_0 0")
        self.assertEqual(lines[255], "token_255 255")
        self.assertEqual(lines[256], "<blk> 256")

    def test_token_generation_rejects_invalid_vocab_size(self) -> None:
        """Reject tokenizer whose vocabulary does not equal 256."""
        mock_tokenizer = MagicMock()
        mock_tokenizer.vocab = [f"t_{i}" for i in range(100)]  # invalid size
        mock_model = MagicMock()
        mock_model.tokenizer.tokenizers_dict = {"hi": mock_tokenizer}

        with self.assertRaises(RuntimeError) as ctx:
            write_tokens(mock_model, "hi", self.temp_dir / "bad_tokens.txt")
        self.assertIn("Expected 256 tokens", str(ctx.exception))

    def test_token_generation_rejects_missing_language(self) -> None:
        """Reject when target language tokenizer is not present in checkpoint."""
        mock_model = MagicMock()
        mock_model.tokenizer.tokenizers_dict = {}

        with self.assertRaises(RuntimeError) as ctx:
            write_tokens(mock_model, "ta", self.temp_dir / "ta_tokens.txt")
        self.assertIn("does not contain tokenizer assets", str(ctx.exception))

    def test_sliced_ctc_wrapper_initialization(self) -> None:
        """Verify SlicedIndicConformerCtcWrapper initializes layers and offsets."""
        mock_encoder = MagicMock()
        mock_decoder = MagicMock()
        mock_decoder_layer = MagicMock()
        mock_decoder.decoder_layers = [mock_decoder_layer]

        wrapper = SlicedIndicConformerCtcWrapper(
            encoder=mock_encoder,
            ctc_decoder=mock_decoder,
            language_offset=1536,
            vocab_size=256,
        )

        self.assertEqual(wrapper.language_offset, 1536)
        self.assertEqual(wrapper.vocab_size, 256)
        self.assertEqual(wrapper.ctc_layer, mock_decoder_layer)

    def test_sha256_file(self) -> None:
        """Verify sha256_file produces matching lowercase hex digest."""
        test_file = self.temp_dir / "sample.bin"
        content = b"TESTING_FILE_SHA256_DIGEST_CHUNK_READING_ABC"
        test_file.write_bytes(content)

        expected_hash = hashlib.sha256(content).hexdigest().lower()
        self.assertEqual(sha256_file(test_file), expected_hash)

    def test_cli_argument_parsing(self) -> None:
        """Verify CLI parses required flags and options."""
        import sys
        from unittest.mock import patch

        test_args = [
            "export_indicconformer_pack.py",
            "--model", "/tmp/dummy.nemo",
            "--lang", "te",
            "--output-dir", "/tmp/out_te",
            "--no-package",
            "--opset", "17",
        ]
        with patch.object(sys, "argv", test_args):
            args = parse_arguments()
            self.assertEqual(str(args.model), "/tmp/dummy.nemo")
            self.assertEqual(args.lang, "te")
            self.assertEqual(str(args.output_dir), "/tmp/out_te")
            self.assertFalse(args.package)
            self.assertEqual(args.opset, 17)

    def test_cli_rejects_unsupported_language(self) -> None:
        """Verify CLI parser rejects unsupported language choice."""
        import sys
        from unittest.mock import patch

        test_args = [
            "export_indicconformer_pack.py",
            "--model", "/tmp/dummy.nemo",
            "--lang", "fr",
        ]
        with patch.object(sys, "argv", test_args):
            with self.assertRaises(SystemExit):
                parse_arguments()


if __name__ == "__main__":
    unittest.main()
