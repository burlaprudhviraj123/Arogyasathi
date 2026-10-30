#!/usr/bin/env python3
"""Multilingual IndicConformer CTC Pack Exporter for Arogyasathi.

Exports AI4Bharat IndicConformer hybrid checkpoints to quantized INT8
NeMo-CTC ONNX models for sherpa-onnx, supporting Hindi (hi), Kannada (kn),
Malayalam (ml), Tamil (ta), and Telugu (te). Generates 257-line tokens.txt,
preserves ConvASRDecoder CTC head slicing, injects ONNX metadata, and
packages the resulting assets into standard Schema v1 voice packs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
from typing import Any

# Optional / Environment-dependent dependencies
try:
    import torch
    import torch.nn as nn
    _TORCH_AVAILABLE = True
except ImportError:
    torch = None
    _TORCH_AVAILABLE = False

    class _MockModule:
        def __init__(self, *args: Any, **kwargs: Any) -> None:
            pass

        def eval(self) -> _MockModule:
            return self

        def __call__(self, *args: Any, **kwargs: Any) -> Any:
            return self.forward(*args, **kwargs)

    class _MockNN:
        Module = _MockModule

    nn = _MockNN()

try:
    import onnx
    _ONNX_AVAILABLE = True
except ImportError:
    onnx = None
    _ONNX_AVAILABLE = False

try:
    from onnxruntime.quantization import QuantType, quantize_dynamic
    _ORT_AVAILABLE = True
except ImportError:
    QuantType = None
    quantize_dynamic = None
    _ORT_AVAILABLE = False

try:
    from nemo.collections.asr.models import ASRModel
    _NEMO_AVAILABLE = True
except ImportError:
    ASRModel = None
    _NEMO_AVAILABLE = False


SUPPORTED_LANGUAGES: dict[str, dict[str, Any]] = {
    "hi": {
        "language_code": "hi",
        "language_tag": "hi-IN",
        "language_name": "Hindi",
        "display_name": "Hindi (हिन्दी)",
        "ctc_offset": 1536,
        "vocab_size": 256,
        "hf_model_id": "ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large",
    },
    "kn": {
        "language_code": "kn",
        "language_tag": "kn-IN",
        "language_name": "Kannada",
        "display_name": "Kannada (ಕನ್ನಡ)",
        "ctc_offset": 1792,
        "vocab_size": 256,
        "hf_model_id": "ai4bharat/indicconformer_stt_kn_hybrid_ctc_rnnt_large",
    },
    "ml": {
        "language_code": "ml",
        "language_tag": "ml-IN",
        "language_name": "Malayalam",
        "display_name": "Malayalam (മലയാളം)",
        "ctc_offset": 2560,
        "vocab_size": 256,
        "hf_model_id": "ai4bharat/indicconformer_stt_ml_hybrid_ctc_rnnt_large",
    },
    "ta": {
        "language_code": "ta",
        "language_tag": "ta-IN",
        "language_name": "Tamil",
        "display_name": "Tamil (தமிழ்)",
        "ctc_offset": 4864,
        "vocab_size": 256,
        "hf_model_id": "ai4bharat/indicconformer_stt_ta_hybrid_ctc_rnnt_large",
    },
    "te": {
        "language_code": "te",
        "language_tag": "te-IN",
        "language_name": "Telugu",
        "display_name": "Telugu (తెలుగు)",
        "ctc_offset": 5120,
        "vocab_size": 256,
        "hf_model_id": "ai4bharat/indicconformer_stt_te_hybrid_rnnt_large",
    },
}

DEFAULT_SAMPLE_RATE = 16000
DEFAULT_FEATURE_DIM = 80


class SlicedIndicConformerCtcWrapper(nn.Module):
    """Wraps Conformer-Large encoder and slices language-specific CTC projection.

    The multilingual CTC head has 5,632 classes (22 languages * 256 BPE classes)
    plus 1 final blank class (class 5,632). This module extracts the target language
    slice and concatenates it with the shared blank token before applying log_softmax.
    """

    def __init__(
        self,
        encoder: Any,
        ctc_decoder: Any,
        language_offset: int,
        vocab_size: int = 256,
    ) -> None:
        super().__init__()
        self.encoder = encoder
        self.ctc_layer = getattr(ctc_decoder, "decoder_layers", [None])[0] if hasattr(ctc_decoder, "decoder_layers") else ctc_decoder
        self.language_offset = language_offset
        self.vocab_size = vocab_size

    def forward(self, audio_signal: Any, length: Any) -> Any:
        if not _TORCH_AVAILABLE:
            raise RuntimeError("PyTorch is required to execute forward pass of SlicedIndicConformerCtcWrapper")
        # sherpa's NeMo-CTC bridge transposes [B, T, 80] to [B, 80, T].
        encoded, encoded_len = self.encoder(audio_signal=audio_signal, length=length)
        aggregate_logits = self.ctc_layer(encoded).transpose(1, 2)
        # Sliced language block followed by the shared blank token
        lang_logits = aggregate_logits[
            ..., self.language_offset : self.language_offset + self.vocab_size
        ]
        blank_logits = aggregate_logits[..., -1:]
        return torch.log_softmax(torch.cat((lang_logits, blank_logits), dim=-1), dim=-1)


def sha256_file(path: Path) -> str:
    """Compute lowercase hex SHA-256 digest of a file in 1 MB chunks."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().lower()


def write_tokens(model: Any, language_code: str, output_path: Path) -> int:
    """Extract 256 BPE tokens for the language and append trailing blank."""
    if language_code not in model.tokenizer.tokenizers_dict:
        raise RuntimeError(f"Checkpoint does not contain tokenizer assets for '{language_code}'")
    tokenizer = model.tokenizer.tokenizers_dict[language_code]
    vocab_size = len(tokenizer.vocab)
    if vocab_size != 256:
        raise RuntimeError(f"Expected 256 tokens for {language_code}, but found {vocab_size}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as dest:
        for token_id in range(vocab_size):
            token_str = tokenizer.ids_to_tokens([token_id])[0]
            dest.write(f"{token_str} {token_id}\n")
        dest.write(f"<blk> {vocab_size}\n")
    return vocab_size


def add_metadata(model_path: Path, properties: dict[str, str]) -> None:
    """Inject sherpa-onnx compatible metadata properties into ONNX graph."""
    if not _ONNX_AVAILABLE:
        raise RuntimeError("ONNX library is required to inject model metadata")
    model = onnx.load(str(model_path))
    del model.metadata_props[:]
    for key, value in properties.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = str(value)
    onnx.save(model, str(model_path))


def export_indicconformer_pack(
    checkpoint_path: str | Path,
    language: str,
    output_dir: str | Path,
    version: str = "1.0.0",
    opset_version: int = 17,
    per_channel_quant: bool = True,
    keep_unquantized: bool = False,
    package: bool = True,
    package_dir: str | Path | None = None,
    overwrite: bool = False,
) -> tuple[Path, Path | None]:
    """Export a multilingual IndicConformer checkpoint for a target language.

    Returns:
        tuple of (exported_dir, packaged_archive_path_or_none)
    """
    if not _TORCH_AVAILABLE:
        raise RuntimeError("PyTorch is required for export. Please run in an environment with torch installed.")
    if not _NEMO_AVAILABLE:
        raise RuntimeError("NeMo toolkit is required for checkpoint loading. Please run in an environment with nemo-toolkit installed.")
    if not _ONNX_AVAILABLE:
        raise RuntimeError("ONNX is required for model export. Please run in an environment with onnx installed.")
    if not _ORT_AVAILABLE:
        raise RuntimeError("onnxruntime is required for quantization. Please install onnxruntime.")

    lang_code = language.strip().lower()
    if lang_code not in SUPPORTED_LANGUAGES:
        valid_langs = sorted(SUPPORTED_LANGUAGES.keys())
        raise ValueError(f"Unsupported language '{language}'. Supported languages: {valid_langs}")

    lang_config = SUPPORTED_LANGUAGES[lang_code]
    checkpoint = Path(checkpoint_path).resolve()
    destination = Path(output_dir).resolve()

    if not checkpoint.is_file():
        raise FileNotFoundError(f"Checkpoint file not found: {checkpoint}")

    if destination.exists() and not overwrite:
        raise FileExistsError(f"Output directory already exists: {destination}. Use --overwrite to replace.")

    destination.mkdir(parents=True, exist_ok=True)

    try:
        checkpoint_hash = sha256_file(checkpoint)
        print(f"Loading NeMo checkpoint from {checkpoint}...")
        model = ASRModel.restore_from(str(checkpoint), map_location="cpu")
        model.eval().freeze()

        expected_target = "nemo.collections.asr.models.hybrid_rnnt_ctc_bpe_models.EncDecHybridRNNTCTCBPEModel"
        if getattr(model.cfg, "target", None) != expected_target:
            raise RuntimeError(f"Unexpected model target: {getattr(model.cfg, 'target', None)}")

        if lang_code not in model.tokenizer.tokenizers_dict:
            raise RuntimeError(f"Checkpoint does not contain tokenizer assets for '{lang_code}'")

        # 1. Write tokens.txt
        tokens_path = destination / "tokens.txt"
        vocab_size = write_tokens(model, lang_code, tokens_path)
        print(f"Extracted {vocab_size} tokens + blank to {tokens_path}")

        # 2. Export ONNX graph
        features = torch.randn(1, DEFAULT_FEATURE_DIM, 200, dtype=torch.float32)
        feature_lengths = torch.tensor([200], dtype=torch.int64)
        language_offset = lang_config["ctc_offset"]

        raw_onnx_path = destination / "model.onnx"
        print(f"Exporting ONNX graph (opset {opset_version}, offset {language_offset})...")
        wrapper = SlicedIndicConformerCtcWrapper(
            model.encoder, model.ctc_decoder, language_offset, vocab_size
        ).eval()

        torch.onnx.export(
            wrapper,
            (features, feature_lengths),
            str(raw_onnx_path),
            input_names=["audio_signal", "length"],
            output_names=["log_probs"],
            dynamic_axes={
                "audio_signal": {0: "batch", 2: "frames"},
                "length": {0: "batch"},
                "log_probs": {0: "batch", 1: "encoded_frames"},
            },
            opset_version=opset_version,
        )

        # 3. Add metadata properties
        metadata = {
            "vocab_size": str(vocab_size),
            "normalize_type": str(model.cfg.preprocessor.normalize),
            "subsampling_factor": str(model.cfg.encoder.subsampling_factor),
            "model_type": "nemo_ctc",
            "version": version,
            "model_author": "AI4Bharat",
            "license": "MIT",
            "language": lang_config["language_name"],
            "language_tag": lang_config["language_tag"],
            "sample_rate": str(DEFAULT_SAMPLE_RATE),
            "source_checkpoint_sha256": checkpoint_hash,
        }
        add_metadata(raw_onnx_path, metadata)

        # 4. Quantize to INT8
        quantized_onnx_path = destination / "model.int8.onnx"
        print(f"Quantizing ONNX model to INT8 (per_channel={per_channel_quant})...")
        quantize_dynamic(
            model_input=str(raw_onnx_path),
            model_output=str(quantized_onnx_path),
            weight_type=QuantType.QUInt8,
            per_channel=per_channel_quant,
        )

        if not keep_unquantized:
            raw_onnx_path.unlink()

        # 5. Optional packaging
        archive_path = None
        if package:
            pkg_out_dir = Path(package_dir).resolve() if package_dir else destination.parent / "voice-packs"
            tools_dir = Path(__file__).resolve().parent
            if str(tools_dir) not in sys.path:
                sys.path.insert(0, str(tools_dir))
            
            from package_voice_pack import package_voice_pack

            source_desc = f"AI4Bharat IndicConformer {lang_config['language_name']} hybrid RNN-T large"
            zip_p, _, _, _ = package_voice_pack(
                model_path=quantized_onnx_path,
                tokens_path=tokens_path,
                language_tag=lang_config["language_tag"],
                display_name=lang_config["display_name"],
                version=version,
                source=source_desc,
                source_checkpoint_sha256=checkpoint_hash,
                output_dir=pkg_out_dir,
                overwrite=overwrite,
            )
            archive_path = zip_p
            print(f"Packaged archive created at {archive_path}")

        return destination, archive_path

    except BaseException:
        shutil.rmtree(destination, ignore_errors=True)
        raise


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export AI4Bharat IndicConformer hybrid checkpoints to sherpa-onnx NeMo-CTC packs."
    )
    parser.add_argument("--model", required=True, type=Path, help="Path to upstream .nemo checkpoint.")
    parser.add_argument(
        "--lang",
        required=True,
        type=str,
        choices=list(SUPPORTED_LANGUAGES.keys()),
        help=f"Target language code: {list(SUPPORTED_LANGUAGES.keys())}",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory to write exported model and tokens. Default: build/exported/<lang>",
    )
    parser.add_argument("--version", type=str, default="1.0.0", help="Pack version (default: 1.0.0).")
    parser.add_argument("--opset", type=int, default=17, help="ONNX opset version (default: 17).")
    parser.add_argument(
        "--per-channel",
        action="store_true",
        default=True,
        help="Use per-channel quantization (default: True).",
    )
    parser.add_argument(
        "--keep-unquantized",
        action="store_true",
        help="Keep unquantized model.onnx alongside model.int8.onnx.",
    )
    parser.add_argument(
        "--package",
        action="store_true",
        default=True,
        help="Package exported files into standard .zip voice pack (default: True).",
    )
    parser.add_argument(
        "--no-package",
        dest="package",
        action="store_false",
        help="Do not package exported files into .zip.",
    )
    parser.add_argument(
        "--package-dir",
        type=Path,
        default=None,
        help="Output directory for .zip voice pack. Default: build/voice-packs",
    )
    parser.add_argument("--overwrite", "-f", action="store_true", help="Overwrite existing output directories.")
    return parser.parse_args()


def main() -> int:
    try:
        args = parse_arguments()
        out_dir = args.output_dir or Path(f"build/exported/{args.lang}")
        pkg_dir = args.package_dir or Path("build/voice-packs")
        dest, archive = export_indicconformer_pack(
            checkpoint_path=args.model,
            language=args.lang,
            output_dir=out_dir,
            version=args.version,
            opset_version=args.opset,
            per_channel_quant=args.per_channel,
            keep_unquantized=args.keep_unquantized,
            package=args.package,
            package_dir=pkg_dir,
            overwrite=args.overwrite,
        )
        print("Export completed successfully.")
        print(f"Export directory: {dest}")
        if archive:
            print(f"Package archive:  {archive}")
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
