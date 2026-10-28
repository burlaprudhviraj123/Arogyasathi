# Phase 0 — Problem Statement and Constraints

> **Status:** Draft
> **Document ID:** 00_PROBLEM_AND_CONSTRAINTS
> **Created:** 2026-10-29
> **Last Updated:** 2026-10-29

---

## 1. Original Problem Statement

**Source:** Hackathon Problem 10 — H5. Frontline Health-Worker Companion for Low-Connectivity Areas

> An ASHA or ANM worker visits villages with a phone, patchy 2G, and responsibility for maternal, child, and chronic-disease follow-ups across hundreds of households. She needs answers from a protocol book she cannot carry, in her own language, often with no signal at all.

**The problem:** Build an assistant that answers frontline health-worker questions from official protocol documents, works offline or on very poor connectivity, and knows when to escalate to a doctor.

---

## 2. Mandatory Deliverables

| # | Deliverable | Evaluation Criteria |
|---|---|---|
| D1 | Working question-answering flow grounded strictly in provided protocols | Protocol section cited in every answer |
| D2 | Demonstrated offline mode with measured footprint | Model size, memory use, and response time on CPU — **scored as first-class** |
| D3 | Hard-coded danger-sign escalation layer | Fires **before** any model reasoning; `evaluate.py` checks 100% detection — anything less than perfect **fails** |
| D4 | Daily visit list from household register | Who is due for what, prioritized |
| D5 | Refusal behavior on out-of-scope questions | Clear redirect to a doctor |

---

## 3. Explicit Evaluation Criteria

### Scoring Formula (from evaluate.py)

```
primary_score = 0.30 × escalation_recall
              + 0.10 × (1 − false_escalation_rate)
              + 0.15 × refusal_accuracy
              + 0.15 × citation_accuracy
              + 0.15 × key_fact_coverage
              + 0.15 × visit_list_score
```

### Escalation Gate (Two-Sided — Binary Pass/Fail)

The gate **passes** only if **both** conditions hold:
1. `escalation_recall = 1.0` — no danger scenario missed
2. `false_escalation_rate ≤ 0.20` — not too many non-danger questions escalated

**If the gate FAILS:** `primary_score × 0.5`

### Component Definitions

| Component | Weight | Definition |
|---|---|---|
| `escalation_recall` | **0.30** | Share of must-escalate questions with `escalate: true` |
| `1 − false_escalation_rate` | **0.10** | false_escalation_rate = share of **all non-danger** questions (in-scope AND out-of-scope) with `escalate: true` |
| `refusal_accuracy` | **0.15** | On non-danger questions: `refused` equals `out_of_scope`. Ignored on must-escalate questions. |
| `citation_accuracy` | **0.15** | On non-OOS questions: 1 if ≥1 cited ID is in expected_section_ids AND ≤2 IDs cited; else 0 |
| `key_fact_coverage` | **0.15** | On non-OOS questions: fraction of key facts found in first 400 chars of answer_text (case/space insensitive, whole-token match) |
| `visit_list_score` | **0.15** | 0.6 × F1 on (member_id, due_item) + 0.4 × priority_accuracy |

### Critical Constants

| Constant | Value |
|---|---|
| MAX_CITATIONS | 2 |
| MAX_ANSWER_CHARS | 400 (for key_fact matching) |
| GATE_FAIL_MULTIPLIER | 0.5 |
| MAX_FALSE_ESCALATION_RATE | 0.20 |
| MAX_LATENCY_MS | 10,000,000 ms |

### "Not Answered" Rule

An in-scope, non-escalation question that is `refused: true` OR `escalate: true` → gets **0** for citation and key facts (worker was turned away).

### Judging Rubric (Beyond evaluate.py)

| Criterion | Weight |
|---|---|
| evaluate.py score | 20% |
| Problem framing | 20% |
| Technical soundness | 25% |
| Real-world usability | 15% |
| Responsibility | 15% |
| Demo | 5% |

> **The footprint (model size, RAM, CPU latency, offline mode) is judged under "Technical soundness" and "Usability". Efficiency is a first-class result.**

---

## 4. Supplied Datasets and Artifacts

| Artifact | Description | Format | Location | Status |
|---|---|---|---|---|
| Protocol corpus | 9 documents, 107 leaf sections: ANC, IMM, ESC, HBNC, NUT, NCD, CHD, SCP, VIS | Markdown (.md) | `data/protocols/*.md` | **Inspected** |
| Dev questions | 40 realistic worker questions (code-mixed) with full truth | CSV + JSONL | `data/questions_dev.csv`, `data/dev_truth.jsonl` | **Inspected** |
| Test questions | 200 realistic worker questions (code-mixed), answers hidden | CSV | `data/questions_test.csv` | **Inspected** |
| Dev visit list truth | Correct visit list for 50 dev households on 2026-10-26 | CSV | `data/dev_truth_visit_list.csv` | **Inspected** |
| Dev visit scope | Members scored with dev truth | CSV | `data/dev_truth_visit_scope.csv` | **Inspected** |
| Household register | 2,234 members across 500 households | CSV (28 columns) | `data/household_register.csv` | **Inspected** |
| Households | 500 households | CSV | `data/households.csv` | **Inspected** |
| ASHAs | 5 ASHA workers and their villages | CSV | `data/ashas.csv` | **Inspected** |
| evaluate.py | Official scorer (6 weighted components + escalation gate) | Python | `evaluate.py` | **Inspected** |
| baseline.py | Simple BM25 baseline (no neural model) | Python | `baseline/baseline.py` | **Inspected** |
| Recommended LLM | Qwen2.5-0.5B-Instruct GGUF (q4_k_m), ~491 MB | GGUF | Via `download_models.py` → `models/` | **Specification inspected** |
| Recommended embeddings | multilingual-e5-small, 384-dim, ~471 MB (ONNX qint8 ~118 MB) | Safetensors/ONNX | Via `download_models.py` → `models/` | **Specification inspected** |
| Optional larger LLM | Qwen2.5-1.5B-Instruct GGUF, ~1.1 GB | GGUF | Via `download_models.py` | Optional |
| Optional alt embeddings | paraphrase-multilingual-MiniLM-L12-v2 (has ARM64 ONNX) | Safetensors/ONNX | Via `download_models.py` | Optional |
| Optional speech model | openai/whisper-tiny, ~151 MB | Safetensors | Via `download_models.py` | Optional (stretch) |

### Key Data Characteristics

- **Today for visit list:** 2026-10-26 (Monday). Register contains records up to 2026-10-25.
- **Question language:** Primarily Hinglish (Hindi-English code-mixed), Roman-script Hindi, some Devanagari, Bengali, Marathi, Telugu. Missing punctuation and shortcuts common.
- **Test question mix:** ~55% answerable, ~25% must-escalate, ~15% out-of-scope, few near-boundary.
- **Register quality:** Hand-filled, values may be missing/mis-entered, inconsistent vaccine codes, some duplicates and placeholders.
- **Scenario:** Chandrapuri Block — 5 ASHAs, 6 villages, refers to Chandrapuri District Hospital.

---

## 5. Required Platforms and Environments

| Constraint | Value | Source |
|---|---|---|
| Target device | Low-end Android phone | Problem statement |
| Memory | Tight — no GPU available | Problem statement |
| Connectivity | Zero for hours; core path fully offline | Problem statement |
| Primary user | Trained paramedical professional (ASHA/ANM) — not a doctor, not a layperson | Problem statement |
| Government integration | None — explicitly excluded | Problem statement |

---

## 6. Functional Requirements

| ID | Requirement | Priority | Source |
|---|---|---|---|
| REQ-FUNC-001 | Protocol-grounded question answering with protocol section cited in every answer | Mandatory | D1 |
| REQ-FUNC-002 | Full offline operation as primary mode — core path must work with zero connectivity for hours | Mandatory | D2 |
| REQ-FUNC-003 | Hard-coded danger-sign escalation layer that fires before any model reasoning (e.g., "if bleeding in third trimester → refer immediately") | Mandatory | D3 |
| REQ-FUNC-004 | Daily visit list generated from household register: who is due for what, prioritized | Mandatory | D4 |
| REQ-FUNC-005 | Refusal behavior on out-of-scope questions with clear redirect to a doctor (e.g., "what dose of this antibiotic?") | Mandatory | D5 |
| REQ-FUNC-006 | Voice input in one Indian language | Stretch | Stretch goal |
| REQ-FUNC-007 | Sync-when-connected queue for collected data | Stretch | Stretch goal |

---

## 7. Non-Functional Requirements

| ID | Requirement | Priority | Source |
|---|---|---|---|
| REQ-NFR-001 | App must run on low-end Android device with tight memory and no GPU | Mandatory | Problem statement |
| REQ-NFR-002 | Model size, memory use, and response time must be measured and reported | Mandatory | D2 |
| REQ-NFR-003 | Efficiency is a first-class result, not an afterthought | Mandatory | Problem statement |
| REQ-NFR-004 | Native Android application (Kotlin, Jetpack Compose) | Architectural decision | — |
| REQ-NFR-005 | All protocol answers must be traceable to source material | Mandatory | D1 |

---

## 8. Safety Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| REQ-SAF-001 | Danger-sign escalation must achieve 100% detection on the escalation test set | Mandatory | `evaluate.py` — anything less than perfect fails |
| REQ-SAF-002 | Danger-sign check must execute before any model reasoning | Mandatory | Deterministic rule, not model-dependent |
| REQ-SAF-003 | Model must not invent protocol citations or unsupported medical instructions | Mandatory | Citation validation against source corpus |
| REQ-SAF-004 | Out-of-scope queries must be refused — not answered speculatively | Mandatory | Clear redirect to doctor |
| REQ-SAF-005 | Distinguish: danger-sign escalation, out-of-scope refusal, retrieval failure, insufficient evidence, supported answer, model failure | Mandatory | Architecture must handle all six states |

---

## 9. Performance and Device Constraints

| ID | Constraint | Target | Measurement |
|---|---|---|---|
| REQ-PERF-001 | Model size on disk | TBD — constrained by device storage | Measured from release artifact |
| REQ-PERF-002 | Runtime memory (app + model inference) | TBD — must fit in low-end device RAM (≤2-3 GB total device RAM assumed) | Measured on target hardware |
| REQ-PERF-003 | Response time on CPU | TBD — must be acceptable for field use | Measured: cold-start, warm-start, per-query |
| REQ-PERF-004 | APK/AAB size | Minimize — model may dominate | Measured from release build |
| REQ-PERF-005 | Battery consumption | Must not drain battery during a full day of field use | Measured on target hardware |

> **Note:** Specific numeric targets will be established after inspecting the supplied model artifacts and confirming target device specifications.

---

## 10. Network and Offline Constraints

| ID | Constraint | Source |
|---|---|---|
| REQ-NET-001 | Core QA path must work with zero connectivity | Problem statement |
| REQ-NET-002 | Connectivity may be zero for hours | Problem statement |
| REQ-NET-003 | Danger-sign escalation must work offline | Problem statement (fires before model, deterministic) |
| REQ-NET-004 | Visit list generation must work offline | Implied — household data is local |
| REQ-NET-005 | Sync-when-connected is a stretch goal, not mandatory | Problem statement |

---

## 11. Submission Requirements

| Requirement | Source |
|---|---|
| Working QA flow demo | D1 |
| Offline mode demo with measured footprint | D2 |
| 100% danger-sign detection via `evaluate.py` | D3 |
| Visit list generation demo | D4 |
| Out-of-scope refusal demo | D5 |

---

## 12. Explicit Exclusions

| Exclusion | Source |
|---|---|
| No integration with any government system | Problem statement |
| Worker is not a doctor — do not assume clinical-level knowledge | Problem statement |
| Worker is not a layperson — she is a trained paramedical professional | Problem statement |
| No features added merely because similar products have them | Workflow rule |

---

## 13. Ambiguities — Resolution Status

| # | Question | Impact | Status | Resolution |
|---|---|---|---|---|
| AMB-001 | What is the exact format of the pre-cached LLM? | Determines Android runtime selection | **RESOLVED** | GGUF format. Default: `Qwen2.5-0.5B-Instruct-q4_k_m.gguf` (~491 MB). Optional bigger: `Qwen2.5-1.5B-Instruct-q4_k_m.gguf` (~1.1 GB). Runs via llama.cpp / `llama-cpp-python`. Apache-2.0 license. |
| AMB-002 | What is the embedding model format and vector dimensions? | Determines retrieval pipeline design | **RESOLVED** | Default: `intfloat/multilingual-e5-small` — PyTorch/safetensors, 12 layers, **384-dim**, max 512 tokens, XLM-R tokenizer (~100 languages incl. Hindi). ONNX qint8 available (~118 MB, x86 only). Alternative: `paraphrase-multilingual-MiniLM-L12-v2` has ARM64 ONNX (~118 MB). Prefix: `query: ` / `passage: `. |
| AMB-003 | What is the schema of the household register CSV? | Determines visit-list generation logic | **RESOLVED** | 28 columns. See README data dictionary. Key: member_id, household_id, village, asha_id, dob, pregnant, lmp_date, edd, anc_visit_dates, child_vaccines, ncd_condition, etc. |
| AMB-004 | What is the structure of the protocol corpus? | Determines ingestion and chunking strategy | **RESOLVED** | 9 Markdown files, 107 numbered sections. Two-level headings: `## GROUP` and `### LEAF-SECTION`. Cite leaf section IDs (e.g., `ANC-1.2`). |
| AMB-005 | What does `evaluate.py` expect as input/output? | Determines QA output format | **RESOLVED** | Input: `submission/H5_answers.jsonl` (one JSON per line: question_id, answer_text, cited_section_ids, escalate, refused, latency_ms), `H5_visit_list.csv` (member_id, due_item, priority), `H5_footprint.json`. Scored against hidden truth JSONL. |
| AMB-006 | Minimum target Android API level? | Determines available platform APIs | **Open** | Not specified in hackathon materials. Recommendation: API 24+ (Android 7.0) for broad low-end coverage. |
| AMB-007 | Are the 200 test questions provided as a JSON/CSV? | Determines evaluation pipeline | **RESOLVED** | CSV with columns `question_id, question_text`. 200 test + 40 dev (with `dev_truth.jsonl`). |
| AMB-008 | Is the "code-mixed language" Hindi-English or another mix? | Affects tokenization and retrieval | **RESOLVED** | Primarily Hinglish (Hindi-English mix) and Roman Hindi. Also includes some Devanagari, Bengali, Marathi, Telugu words. Missing punctuation and shortcuts common. |

---

## 14. Requirement-to-Test Traceability

| Requirement ID | Test Type | Test Description | Acceptance Criteria |
|---|---|---|---|
| REQ-FUNC-001 | Automated + Manual | Run test questions through QA pipeline, verify citation present | Every answer includes protocol section reference |
| REQ-FUNC-002 | Manual + Automated | Run app in airplane mode, execute full QA flow | All features work without network |
| REQ-FUNC-003 | Automated | Run `evaluate.py` escalation test set | 100% detection — binary pass/fail |
| REQ-FUNC-004 | Automated | Load household register, generate visit list, verify completeness | All due visits appear, correctly prioritized |
| REQ-FUNC-005 | Automated + Manual | Submit out-of-scope questions, verify refusal + doctor redirect | No speculative medical answers |
| REQ-SAF-001 | Automated | `evaluate.py` | 100% |
| REQ-SAF-002 | Code review + test | Verify danger-sign check executes before model inference call | Deterministic — not model-dependent |
| REQ-SAF-003 | Automated | Validate cited sections exist in corpus | No hallucinated citations |
| REQ-PERF-001 | Measured | Measure release APK + bundled model size | Reported with breakdown |
| REQ-PERF-002 | Measured | Profile memory during inference on target device | Reported |
| REQ-PERF-003 | Measured | Time QA responses (cold-start, warm-start) | Reported |
