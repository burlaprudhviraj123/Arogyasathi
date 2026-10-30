# Sherpa-ONNX and Telugu RNN-T Voice Pack Research

**Status:** Verified implementation research — 2026-10-30  
**Scope:** Offline Telugu speech-to-text for the Android application.

## Verified checkpoint facts

The supplied local checkpoint is:

- Path: `/Users/burlaprudhviraj/Downloads/indicconformer_stt_te_hybrid_rnnt_large.nemo`
- SHA-256: `70aea39388043beae2b06af21df47811d23a743ac5641d7b5c0d20d04fa87a3c`
- Archive size: 499 MB.
- Model target: `nemo.collections.asr.models.hybrid_rnnt_ctc_bpe_models.EncDecHybridRNNTCTCBPEModel`.
- NeMo version recorded in the checkpoint: `1.23.0rc0`.
- Audio configuration: 16,000 Hz sample rate, 80 mel features, 25 ms window, 10 ms stride.
- Decoder: RNN-T decoder with a 5,632-token BPE vocabulary and `blank_as_pad: true`.
- Joint network: RNN-T joint network.

These facts were read from the checkpoint's `model_config.yaml`; they are not inferred from its filename.

## Source: Sherpa-ONNX

Sherpa-ONNX is an offline, local inference runtime with Android and Kotlin bindings. Its NeMo transducer pathway requires a compatible encoder, decoder, joiner, and token file, configured with `model_type = nemo_transducer`.

Sources:

- [Sherpa Android documentation](https://k2-fsa.github.io/sherpa/onnx/android/index.html)
- [Sherpa NeMo transducer documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html)
- [Sherpa Kotlin OfflineRecognizer API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt)
- [Sherpa Android AudioRecord example](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/SherpaOnnx/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt)

## Adopted design

The base APK will not contain a speech model. A downloaded or supervisor-imported Telugu pack will contain:

```text
encoder.int8.onnx
decoder.onnx
joiner.onnx
tokens.txt
pack.json
```

The pack is valid only after file digests, expected metadata, and a local Sherpa initialization health check pass. It will live in app-private storage. The app must not represent a Boolean preference as an installed voice model.

Audio uses Android `AudioRecord` at 16 kHz / mono / PCM-16 and is converted to normalized floats for Sherpa. The transcribed Telugu text enters the existing deterministic `JEV -> Laya` path; no cloud recognizer is used.

## Decisions and constraints

1. The 499 MB NeMo checkpoint must first be exported to functionally equivalent FP32 ONNX components. FP32 output must be validated against known Telugu WAVs before quantization.
2. Quantization targets the large encoder first. Decoder and joiner remain FP32 unless runtime and accuracy validation demonstrate safe quantization.
3. A target of 118 MB is an engineering target, not a verified result. The final claim will use measured pack size and device performance.
4. The current source checkpoint is Telugu-only. Other UI languages remain typed-query capable but cannot claim offline STT until a compatible, validated pack is installed.
5. Android system `RecognizerIntent` must be removed from this feature path to prevent network-backed transcription and false offline claims.

## Required validation before completion

- NeMo FP32 transcript baseline from Telugu test audio.
- Sherpa FP32 component pack transcript parity.
- Sherpa INT8 component pack transcript parity / accepted WER-CER threshold.
- Airplane-mode Android speech capture and transcription using only `AudioRecord` + Sherpa.
- Measured pack size, APK size, first and repeat inference latency, and peak memory on the target device.
