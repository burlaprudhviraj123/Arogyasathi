#!/usr/bin/env python3
"""Export the supplied Telugu IndicConformer hybrid checkpoint for sherpa-onnx.

This is deliberately model-specific.  AI4Bharat's checkpoint has a
multilingual aggregate tokenizer. Its RNN-T predictor is stateful, while
sherpa-onnx's offline-transducer ABI requires a stateless decoder. The
checkpoint also has a trained Telugu-selectable CTC head, so this exporter
uses that head without changing the acoustic model or fabricating predictor
state. It produces the 256 Telugu BPE tokens plus blank and writes an offline
sherpa-onnx NeMo-CTC pack.

Run in the AI4Bharat NeMo v2 environment:

  python tools/export_telugu_indicconformer_sherpa_pack.py \
    --model /path/to/indicconformer_stt_te_hybrid_rnnt_large.nemo \
    --output /path/to/te-IN-indicconformer-ctc-int8

The output is not approved for Android until ``validate_telugu_sherpa_pack.py``
has tested it against a Telugu WAV, and until it has passed Android device
validation in airplane mode.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

import onnx
import torch
from nemo.collections.asr.models import ASRModel
from onnxruntime.quantization import QuantType, quantize_dynamic


LANGUAGE = "te"
SAMPLE_RATE = 16_000


class TeluguCtcWrapper(torch.nn.Module):
    """Freeze the aggregate CTC head to Telugu's 256 tokens plus blank."""

    def __init__(
        self, encoder: torch.nn.Module, ctc_decoder: torch.nn.Module,
        language_offset: int, vocab_size: int,
    ) -> None:
        super().__init__()
        self.encoder = encoder
        self.ctc_layer = ctc_decoder.decoder_layers[0]
        self.language_offset = language_offset
        self.vocab_size = vocab_size

    def forward(
        self, audio_signal: torch.Tensor, length: torch.Tensor
    ) -> tuple[torch.Tensor, torch.Tensor]:
        # sherpa's NeMo-CTC bridge transposes [B, T, 80] to [B, 80, T].
        encoded, encoded_len = self.encoder(
            audio_signal=audio_signal, length=length
        )
        aggregate_logits = self.ctc_layer(encoded).transpose(1, 2)
        # The checkpoint stores language blocks followed by the shared blank.
        telugu_logits = aggregate_logits[
            ..., self.language_offset : self.language_offset + self.vocab_size
        ]
        blank_logits = aggregate_logits[..., -1:]
        return torch.log_softmax(torch.cat((telugu_logits, blank_logits), dim=-1), dim=-1)


def add_metadata(filename: Path, properties: dict[str, str]) -> None:
    model = onnx.load(str(filename))
    del model.metadata_props[:]
    for key, value in properties.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = str(value)
    onnx.save(model, str(filename))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_tokens(model: ASRModel, output: Path) -> int:
    tokenizer = model.tokenizer.tokenizers_dict[LANGUAGE]
    size = len(tokenizer.vocab)
    with output.open("w", encoding="utf-8") as destination:
        for token_id in range(size):
            destination.write(f"{tokenizer.ids_to_tokens([token_id])[0]} {token_id}\n")
        destination.write(f"<blk> {size}\n")
    return size


def export(args: argparse.Namespace) -> None:
    checkpoint = Path(args.model).resolve()
    destination = Path(args.output).resolve()
    if not checkpoint.is_file():
        raise SystemExit(f"Missing checkpoint: {checkpoint}")
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite existing directory: {destination}")

    destination.mkdir(parents=True)
    try:
        model = ASRModel.restore_from(str(checkpoint), map_location="cpu")
        model.eval().freeze()
        if model.cfg.target != "nemo.collections.asr.models.hybrid_rnnt_ctc_bpe_models.EncDecHybridRNNTCTCBPEModel":
            raise RuntimeError(f"Unexpected model target: {model.cfg.target}")
        if LANGUAGE not in model.tokenizer.tokenizers_dict:
            raise RuntimeError("Checkpoint does not contain Telugu tokenizer assets")

        vocab_size = write_tokens(model, destination / "tokens.txt")
        if vocab_size != 256:
            raise RuntimeError(f"Expected 256 Telugu BPE tokens; found {vocab_size}")

        # The bridge supplies NeMo's expected [batch, 80, frames] tensor.
        features = torch.randn(1, 80, 200, dtype=torch.float32)
        feature_lengths = torch.tensor([200], dtype=torch.int64)
        language_offset = model.tokenizer.token_id_offset[LANGUAGE]
        torch.onnx.export(
            TeluguCtcWrapper(
                model.encoder, model.ctc_decoder, language_offset, vocab_size
            ).eval(),
            (features, feature_lengths),
            str(destination / "model.onnx"),
            input_names=["audio_signal", "length"],
            output_names=["log_probs"],
            dynamic_axes={
                "audio_signal": {0: "batch", 2: "frames"},
                "length": {0: "batch"},
                "log_probs": {0: "batch", 1: "encoded_frames"},
            },
            opset_version=17,
        )

        metadata = {
            "vocab_size": str(vocab_size),
            "normalize_type": str(model.cfg.preprocessor.normalize),
            "subsampling_factor": str(model.cfg.encoder.subsampling_factor),
            "model_type": "nemo_ctc",
            "version": "1",
            "model_author": "AI4Bharat",
            "license": "MIT",
            "language": "Telugu",
            "language_tag": "te-IN",
            "sample_rate": str(SAMPLE_RATE),
            "source_checkpoint_sha256": sha256(checkpoint),
        }
        add_metadata(destination / "model.onnx", metadata)

        quantize_dynamic(
            str(destination / "model.onnx"),
            str(destination / "model.int8.onnx"),
            weight_type=QuantType.QUInt8,
            per_channel=True,
        )
        (destination / "model.onnx").unlink()

        manifest = {
            "schema_version": 1,
            "language": "te-IN",
            "model_type": "nemo_ctc",
            "sample_rate_hz": SAMPLE_RATE,
            "source": "AI4Bharat IndicConformer Telugu hybrid RNN-T large",
            "source_checkpoint_sha256": sha256(checkpoint),
            "files": {
                item.name: {"bytes": item.stat().st_size, "sha256": sha256(item)}
                for item in sorted(destination.iterdir())
                if item.is_file()
            },
            "validation": {"status": "NOT_VALIDATED"},
        }
        (destination / "pack.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    except BaseException:
        shutil.rmtree(destination, ignore_errors=True)
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", required=True)
    export(parser.parse_args())
