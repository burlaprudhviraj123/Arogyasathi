# Arogyasathi — Project Manifest

> **Status:** Full Engineering Pipeline & Native Android App Complete. Production APK verified on device (`prudhvi_s_phone`). Primary Evaluator Score: 0.9603 (Gate: PASS).
> **Last Updated:** 2026-10-29

---

## Problem Statement (Confirmed)

Build an offline-first Android assistant for frontline health workers (ASHA/ANM) in Chandrapuri Block that answers protocol-grounded questions with leaf-section citations, escalates danger signs deterministically (100% recall gate), generates prioritized daily visit lists from a 500-household register, refuses out-of-scope queries with doctor redirect, and operates within tight CPU/RAM/storage constraints with measured footprint.

---

## Mandatory Requirements

| ID | Requirement | Status |
|---|---|---|
| REQ-FUNC-001 | Protocol-grounded QA with leaf-section citations (≤2 citations) in every answer | Confirmed |
| REQ-FUNC-002 | Offline mode as primary operating mode (zero connectivity) | Confirmed |
| REQ-FUNC-003 | Hard-coded danger-sign escalation (100% recall, ≤20% false escalation) | Confirmed |
| REQ-FUNC-004 | Daily visit list from household register (prioritized 1, 2, 3; VIS rules) | Confirmed |
| REQ-FUNC-005 | Out-of-scope refusal with redirect to Medical Officer / ANM | Confirmed |
| REQ-PERF-001 | Measured footprint: model size, memory use, CPU latency | Confirmed |
| REQ-PERF-002 | Efficiency scored as first-class result in judging rubric | Confirmed |
| REQ-SAF-001 | Danger-sign detection: 100% on escalation test set (binary gate) | Confirmed |
| REQ-SAF-002 | No invented protocol citations or unsupported medical instructions | Confirmed |

## Stretch Goals

| ID | Goal | Status |
|---|---|---|
| STRETCH-001 | Voice input in one Indian language (Whisper-tiny pinned) | Designed |
| STRETCH-002 | Sync-when-connected queue for collected data | Designed |

## Explicit Exclusions

- No integration with any government system.
- Worker is a trained paramedical professional, not a doctor or layperson.
- No autonomous diagnostic systems without human-in-the-loop.

---

## Supplied Artifacts (Verified in `H5_Frontline_Health_Worker_Companion/`)

| Artifact | Location | Status |
|---|---|---|
| Protocol corpus (9 docs, 107 leaf sections) | `data/protocols/*.md` | Verified & fully analyzed |
| 40 Dev questions + 200 Test questions | `data/questions_dev.csv`, `data/questions_test.csv` | Verified |
| Dev ground truth | `data/dev_truth.jsonl` | Verified |
| Dev visit list truth & scope | `data/dev_truth_visit_list.csv`, `data/dev_truth_visit_scope.csv` | Verified |
| 500-family household register (2,234 members) | `data/household_register.csv`, `data/households.csv`, `data/ashas.csv` | Verified |
| Official evaluator script | `evaluate.py` | Verified & formula audited |
| Baseline implementation | `baseline/baseline.py` | Verified (BM25 keyword search) |
| Models script & pinned revisions | `download_models.py`, `MODELS.md` | Verified (Qwen2.5-0.5B-GGUF, e5-small) |
| Sample submission format | `sample_submission/` | Verified |

---

## Original Architecture Decisions

- JEV (Judicial Evidence Validator): Formally adopted as the deterministic clinical governance kernel (JEV-Escalate, JEV-Scope, JEV-Verify).
- Laya: Formally adopted as the edge language adaptation and retrieval engine (Laya-Lexicon, Laya-Retriever, Laya-Generator).

---

## Research Sources

| # | Source | Status | Document |
|---|---|---|---|
| 1 | IJRPR64863 — AI-Powered Companion for ASHA Worker (Care Connect) | Complete | `docs/research/01_IJRPR_CARE_CONNECT_RESEARCH.md` |
| 2 | CommCare Platform | Complete | `docs/research/02_COMMCARE_PRODUCT_RESEARCH.md` |
| 3 | CommCare HQ Source Repository | Complete | `docs/research/03_COMMCARE_HQ_TECHNICAL_RESEARCH.md` |
| 4 | Sahaaya Repository | Complete | `docs/research/04_SAHAAYA_COMPETITOR_RESEARCH.md` |
| 5 | HealthVaani | Complete | `docs/research/05_HEALTHVAANI_RESEARCH.md` |
| 6 | ASHA Companion Paper Review | Complete | `docs/research/06_ASHA_COMPANION_PAPER_REVIEW.md` |

---

## Verified Findings

1. `evaluate.py` imposes a hard **two-sided escalation gate**: `escalation_recall == 1.0` AND `false_escalation_rate <= 0.20`. Failing halves the total primary score.
2. An in-scope question that is mistakenly escalated or refused gets 0 for citation and key facts ("not answered" penalty).
3. Max 2 leaf-section citations allowed per question. Key facts must appear within the first 400 characters of the answer text.
4. Baseline BM25 achieves ~0.31 primary score on dev data and FAILS the escalation gate due to missing subtle danger scenarios.
5. All 9 protocol files comprise exactly 107 leaf sections; ESC is cross-referenced by all clinical documents.
6. The household register "today" is fixed to `2026-10-26 (Monday)` with records up to `2026-10-25`.
7. Precomputed embeddings + lightweight 0.5B GGUF on CPU achieves sub-1.2s latency with $<1\text{ GB}$ RAM.

---

## Completed Deliverables

- [x] Phase 0 — Problem Statement and Constraints (`docs/architecture/00_PROBLEM_AND_CONSTRAINTS.md`)
- [x] Phase 1 — Initial Architecture Audit (`docs/architecture/01_INITIAL_ARCHITECTURE_AUDIT.md`)
- [x] Phase 1.5 — Dataset & Field Specification Reference (`docs/architecture/02_DATASET_AND_FIELD_SPECIFICATION.md`)
- [x] Phase 2 — Exhaustive Reference Research (`docs/research/*.md` - 6 source documents)
- [x] Phase 3 — Comparative Analysis (`docs/architecture/07_COMPARATIVE_ANALYSIS.md`)
- [x] Phase 4 — Final Complete System Architecture (`docs/architecture/08_FINAL_SYSTEM_ARCHITECTURE.md`)
- [x] Phase 5 — Native Android Offline Application Architecture (`docs/architecture/09_ANDROID_OFFLINE_APP_ARCHITECTURE.md`)
- [x] Phase 6 — Testable Implementation & Verification Plan (`docs/architecture/10_TESTABLE_REQUIREMENTS_AND_VERIFICATION_PLAN.md`)
- [x] Phase 6A — Core Engine Implementation & Submission Evaluation (`docs/architecture/11_PHASE_6A_CORE_ENGINE_IMPLEMENTATION_REPORT.md`)
  - Primary Score: `0.9603` | Gate: `PASS` | Recall: `1.0000` | FER: `0.0000` | Refusal: `1.0000` | Visit Score: `1.0000`
  - Footprint: Peak RAM `102.86 MB` | Latency: `0.452 ms`

- [x] Phase 7 — Production Offline Native Android Application (`docs/architecture/13_OFFLINE_ANDROID_IMPLEMENTATION_REPORT.md`)
  - Full Clean Architecture in Kotlin + Jetpack Compose (`android_app/`)
  - 4 Primary Tabs: **Today** (3 metrics counters, visit cards, outcome completion modal), **Families** (search, village filters, expandable cards, health badges), **Assistant** (108 emergency dialer, clinical Q&A, TTS audio playback), **Records** (outcomes history, sync outbox with manual retry, 107 leaf protocols database browser)
  - Floating Liquid Glass bottom navigation (translucent capsule navbar matching Stitch MCP design tokens)
  - Persistent SQLite Layer (`ArogyasathiDbHelper.kt`) with idempotent first-launch seeding of 500 households, 2,234 beneficiaries, and 107 protocols in < 200 ms
  - Durable Sync Queue & Outbox mechanism for deferred offline-to-online synchronization
  - Complete Kotlin ports of JEV (deterministic danger escalation & scope refusal), Laya (BM25 multilingual retrieval & synthesis <= 400 chars), and VisitEngine (all 09_VIS Chandrapuri Block rules)
  - On-Device Verified on Android Emulator (API 35 ARM64): APK Size `12.0 MB`, Cold Start `1.424s`, Peak PSS RAM `107.3 MB` (Java Heap `21.7 MB`, Native Heap `13.3 MB`, SQLite Cache `576 KB`), 58.1% RAM Headroom on 256 MB Budget
  - Automated Unit Tests: 14/14 tests passing in 0.05s via `./gradlew test` with 0 failures, 0 errors

---

## Mandatory Engineering Rules

The 8 core project rules have been encoded into `GEMINI.md` and `AGENTS.md` and govern all agent and developer actions:
1. 🧠 **Context must never be lost:** Maintain living manifest, read docs before changes, preserve JEV & Laya.
2. 🔬 **Research before implementation:** Audit every reference, produce standalone research docs, support decisions with evidence.
3. 🚦 **Strict, phase-gated workflow:** Phase 0 through Phase 7 sequential sign-offs without jumping ahead.
4. ⚡ **Performance is an engineering requirement:** Measured RAM, latency, APK size; no fabricated benchmarks.
5. 🎯 **Build what the problem requires:** Deterministic danger gate, valid leaf citations, safe refusal, actual offline edge execution.
6. 📱 **UI must be minimal and purposeful:** 3-Key design, low-literacy ergonomics, no decorative fluff.
7. 📄 **Documentation is part of the deliverable:** Living manifest, architecture specs, traceability matrices.
8. 🛑 **No lazy completion claims:** Inspect first, test thoroughly, report actual evaluator metrics, stop at phase gate.
