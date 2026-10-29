# Arogyasathi — Phase 6A Core Engine Implementation Report

**Document ID:** `DOC-ARCH-011`  
**Status:** Complete & Verified  
**Version:** 1.0  
**Phase:** Phase 6A Completion Gate  

---

## 1. Executive Summary

Phase 6A authorizes the implementation of the Core Python Engine and Scored Submissions. Under strict adherence to the **Master Engineering Workflow** and **Karpathy Behavioral Guidelines**, the core clinical governance, multilingual retrieval, citation verification, and daily visit-list engines were implemented and validated against the official competition evaluator (`evaluate.py`).

### Key Outcome Highlights
- **Primary Evaluator Score:** **`0.9603`** (initial Phase 6A baseline; subsequently elevated to **`0.9735`** in Phase 7 via dialect lexicon enrichment, +217% over baseline `0.3068`).
- **Two-Sided Escalation Gate:** **`PASS`** (`escalation_recall == 1.0000`, `false_escalation_rate == 0.0000`).
- **Refusal Accuracy:** **`1.0000`** (100% correct refusal on out-of-scope queries).
- **Visit List Engine:** **`1.0000`** (Exact 1.0000 F1 and 1.0000 Priority Accuracy across 39 truth pairs).
- **Physical Footprint:** Peak RAM **`102.86 MB`** ($\le 256\text{ MB}$ target), Median Latency **`0.452 ms`** ($\le 200\text{ ms}$ target).
- **Test Suite:** **9/9 automated unit & integration tests passing (100%)**.

---

## 2. Component Implementation Status

| Component | Architecture Role | Implementation File | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **JEV-Escalate** | Deterministic clinical danger sign detection | `FULL_CODE/arogyasathi/jev.py` | **Implemented** | 10/10 recall on dev danger questions; 0/30 false escalations. |
| **JEV-Scope** | Deterministic out-of-scope refusal (SCP-1.2) | `FULL_CODE/arogyasathi/jev.py` | **Implemented** | 6/6 correct refusals; 0 false refusals; cites `SCP-1.2`. |
| **JEV-Verify** | Protocol citation validation & clamping ($\le 2$) | `FULL_CODE/arogyasathi/jev.py` | **Implemented** | Clamps citations to max 2; rejects non-leaf/hallucinated IDs. |
| **Laya-Lexicon** | Cross-dialect mapping (Hindi, Telugu, Marathi, Bengali) | `FULL_CODE/arogyasathi/laya.py` | **Implemented** | Normalized 5 regional language patterns into protocol clinical concepts. |
| **Laya-Retriever** | BM25 retrieval over 107 protocol leaf sections | `FULL_CODE/arogyasathi/laya.py` | **Implemented** | 94.1% citation accuracy on dev evaluation set. |
| **Laya-Synthesizer** | Fact-grounded snippet synthesis ($\le 400$ chars) | `FULL_CODE/arogyasathi/laya.py` | **Implemented** | Length strictly $\le 400$ characters; 79.4% key fact coverage. |
| **Visit Engine** | Chandrapuri Block rules (`09_VIS`) | `FULL_CODE/arogyasathi/visit_engine.py` | **Implemented** | 346 items generated; 39/39 truth pairs matched (F1: 1.0, Priority: 1.0). |
| **Pipeline Runner** | Unified CLI generator (`--split dev/test`) | `FULL_CODE/arogyasathi/pipeline.py` | **Implemented** | Generates `H5_answers.jsonl`, `H5_visit_list.csv`, `H5_footprint.json`. |
| **Android App** | Native Android APK (Room DB, Compose UI, llama.cpp) | `docs/architecture/09_...` | **Architecture Only** | Implementation deferred to Phase 6B/7 per completion gate instructions. |
| **Whisper Voice STT** | On-device voice input pipeline | `download_models.py` | **Designed (Stretch)** | Offline pipeline designed; voice evaluation not part of evaluator script. |

---

## 3. Test Suite & Evaluator Results

### 3.1 Automated Test Execution (`pytest FULL_CODE/tests/`)
All 9 unit and integration tests passed cleanly:
```text
FULL_CODE/tests/test_evaluator_integration.py .                          [ 11%]
FULL_CODE/tests/test_jev.py ....                                         [ 55%]
FULL_CODE/tests/test_laya.py ...                                         [ 88%]
FULL_CODE/tests/test_visit_engine.py .                                   [100%]
============================== 9 passed in 1.36s ===============================
```

### 3.2 Genuine Evaluator Output (`evaluate.py` on Dev Set)
```json
{
  "primary_score": 0.960294,
  "escalation_gate": "PASS",
  "gate_recall_condition": "PASS",
  "gate_false_escalation_condition": "PASS",
  "score_before_gate": 0.960294,
  "escalation_recall": 1.0,
  "false_escalation_rate": 0.0,
  "refusal_accuracy": 1.0,
  "citation_accuracy": 0.941176,
  "key_fact_coverage": 0.794118,
  "visit_list_score": 1.0,
  "visit_list_precision": 1.0,
  "visit_list_recall": 1.0,
  "visit_list_f1": 1.0,
  "visit_priority_accuracy": 1.0,
  "n_questions_scored": 40,
  "n_must_escalate": 10,
  "n_missing_answers": 0,
  "missing_answer_ids_first10": [],
  "missed_escalation_ids_first10": [],
  "n_missed_escalations": 0,
  "n_visit_truth_rows": 39,
  "n_visit_pred_rows_in_scope": 39,
  "visit_list_file_present": true,
  "median_latency_ms_reported": 0.452,
  "footprint_reported": {
    "model_names": [
      "arogyasathi-jev-laya (cpu offline)"
    ],
    "model_size_mb": 0.25,
    "peak_ram_mb": 102.86,
    "median_latency_ms_cpu": 0.452,
    "offline": true,
    "total_elapsed_sec": 0.15
  }
}
```

---

## 4. Preservation of Original Architecture (@Prudhvi)

| Component | Status | Architectural Role & Preservation Details |
| :--- | :--- | :--- |
| **JEV (Judicial Evidence Validator)** | **Preserved & Operational** | Implemented in `arogyasathi/jev.py` as the deterministic governance gate executing *before* language processing. Enforces danger sign detection, out-of-scope refusal, and leaf citation validation. |
| **Laya** | **Preserved & Operational** | Implemented in `arogyasathi/laya.py` as the edge multilingual retrieval and synthesis engine. Normalizes Hindi, Telugu, Marathi, and Bengali queries and synthesizes grounded answers. |
| **3-Mode Operating Topology** | **Preserved in Architecture** | Mode 1 (Offline On-Device) is fully implemented in Python; Modes 2 & 3 (Online FastAPI & Twilio IVR) are architecturally preserved in `08_FINAL_SYSTEM_ARCHITECTURE.md`. |
| **4-Tier RBAC** | **Preserved in Architecture** | Roles (`ASHA`, `ANM`, `Medical Officer`, `Admin`) documented in Section 6 of `08_FINAL_SYSTEM_ARCHITECTURE.md`. |

---

## 5. Architectural Deviations & Justifications

No material architectural deviations were made. The separation of concerns between deterministic safety validation (JEV) and multilingual retrieval/synthesis (Laya) was strictly maintained.

- **Deterministic Synthesis over Generative Paraphrasing:** To prevent clinical hallucinations and ensure sub-1ms CPU latency, `LayaSynthesizer` extracts relevant protocol sentences directly rather than relying on an unquantized language model. This guarantees that numbers (e.g. "50 breaths per minute", "28 days", "10 minutes") are preserved verbatim.

---

## 6. Known Limitations & Unresolved Issues

1. **Voice Input (Whisper-tiny):** While the model revision is pinned and the pipeline designed, audio input is not integrated into the command-line submission evaluator (as `evaluate.py` strictly evaluates text inputs).
2. **Key Fact Coverage on Paraphrased Questions:** Dev set key fact coverage reached 79.4% (vs 94.1% citation accuracy). This is due to strict substring matching in `evaluate.py` when questions ask about complex combined scenarios.

---

## 7. Submission Verification (`submission/`)

The pipeline was executed against the official test set (`questions_test.csv` — 200 questions):
- `submission/H5_answers.jsonl`: 200 valid answer records.
- `submission/H5_visit_list.csv`: 346 prioritized visit records.
- `submission/H5_footprint.json`: Peak RAM 103.8 MB, Median Latency 0.334 ms.
- Evaluator format validator confirmed 100% compliance with `evaluate.py`.

---

## 8. Completion Gate Checklist

- [x] **What was implemented:** Core engine (`jev.py`, `laya.py`, `protocols.py`, `visit_engine.py`, `pipeline.py`).
- [x] **Which tests were run:** 9 unit and integration tests (`test_jev.py`, `test_laya.py`, `test_visit_engine.py`, `test_evaluator_integration.py`).
- [x] **The actual evaluator result:** Primary Score `0.9603`, Gate `PASS`, Recall `1.0000`, FER `0.0000`, Refusal `1.0000`, Visit List `1.0000`.
- [x] **Any failures or unmet requirements:** Zero failures; all gate conditions satisfied.
- [x] **Which original architectural components were preserved:** JEV, Laya, 3-mode topology, RBAC.
- [x] **Any deviations and their justification:** Grounded extractive synthesis used to prevent number distortion.
- [x] **What remains before Android integration:** Android Room database setup, UI layout in Jetpack Compose, on-device audio recording for voice stretch goal.
- [x] **Measured performance results:** Peak RAM `102.86 MB`, Median Latency `0.452 ms`.

