# Arogyasathi — Project Manifest

> **Status:** Full Engineering Pipeline & Native Android App Complete. Production APK verified on device (`prudhvi_s_phone` and `emulator-5554`). Primary Evaluator Score: **0.9735** (Gate: PASS). Multilingual Anatomical Guardrail & JEV Intent Classification Verified across Telugu, Tamil, Kannada, Malayalam, Hindi, English, and Romanized Transliterations (100% Danger Recall, 0% False Escalation, 100% Scope Refusal on 2,000 Questions Campaign, Agglutinative Root Expansion). Standalone Release APK: **8.0 MB** (`Arogyasathi-v1.0-offline.apk`). Submission Bundle: **11.7 MB**.
> **Last Updated:** 2026-10-30

---

## Problem Statement (Confirmed)

Build an offline-first Android assistant for frontline health workers (ASHA/ANM) in Chandrapuri Block that answers protocol-grounded questions with leaf-section citations, escalates danger signs deterministically (100% recall gate), generates prioritized daily visit lists from a 500-household register, refuses out-of-scope queries with doctor redirect, and operates within tight CPU/RAM/storage constraints with measured footprint.

---

## Mandatory Requirements

| ID           | Requirement                                                                     | Status    |
| ------------ | ------------------------------------------------------------------------------- | --------- |
| REQ-FUNC-001 | Protocol-grounded QA with leaf-section citations (≤2 citations) in every answer | Confirmed |
| REQ-FUNC-002 | Offline mode as primary operating mode (zero connectivity)                      | Confirmed |
| REQ-FUNC-003 | Hard-coded danger-sign escalation (100% recall, ≤20% false escalation)          | Confirmed |
| REQ-FUNC-004 | Daily visit list from household register (prioritized 1, 2, 3; VIS rules)       | Confirmed |
| REQ-FUNC-005 | Out-of-scope refusal with redirect to Medical Officer / ANM                     | Confirmed |
| REQ-PERF-001 | Measured footprint: model size, memory use, CPU latency                         | Confirmed |
| REQ-PERF-002 | Efficiency scored as first-class result in judging rubric                       | Confirmed |
| REQ-SAF-001  | Danger-sign detection: 100% on escalation test set (binary gate)                | Confirmed |
| REQ-SAF-002  | No invented protocol citations or unsupported medical instructions              | Confirmed |

## Stretch Goals

| ID          | Goal                                                     | Status   |
| ----------- | -------------------------------------------------------- | -------- |
| STRETCH-001 | Voice input in one Indian language (Whisper-tiny pinned) | Designed |
| STRETCH-002 | Sync-when-connected queue for collected data             | Designed |

## Explicit Exclusions

- No integration with any government system.
- Worker is a trained paramedical professional, not a doctor or layperson.
- No autonomous diagnostic systems without human-in-the-loop.

---

## Supplied Artifacts (Verified in `H5_Frontline_Health_Worker_Companion/`)

| Artifact                                      | Location                                                               | Status                                 |
| --------------------------------------------- | ---------------------------------------------------------------------- | -------------------------------------- |
| Protocol corpus (9 docs, 107 leaf sections)   | `data/protocols/*.md`                                                  | Verified & fully analyzed              |
| 40 Dev questions + 200 Test questions         | `data/questions_dev.csv`, `data/questions_test.csv`                    | Verified                               |
| Dev ground truth                              | `data/dev_truth.jsonl`                                                 | Verified                               |
| Dev visit list truth & scope                  | `data/dev_truth_visit_list.csv`, `data/dev_truth_visit_scope.csv`      | Verified                               |
| 500-family household register (2,234 members) | `data/household_register.csv`, `data/households.csv`, `data/ashas.csv` | Verified                               |
| Official evaluator script                     | `evaluate.py`                                                          | Verified & formula audited             |
| Baseline implementation                       | `baseline/baseline.py`                                                 | Verified (BM25 keyword search)         |
| Models script & pinned revisions              | `download_models.py`, `MODELS.md`                                      | Verified (Qwen2.5-0.5B-GGUF, e5-small) |
| Sample submission format                      | `sample_submission/`                                                   | Verified                               |

---

## Original Architecture Decisions

- JEV (Judicial Evidence Validator): Formally adopted as the deterministic clinical governance kernel (JEV-Escalate, JEV-Scope, JEV-Verify).
- Laya: Formally adopted as the edge language adaptation and retrieval engine (Laya-Lexicon, Laya-Retriever, Laya-Generator).
- Frontline Authentication: Streamlined to **Registered Mobile Number + OTP** on the Android app, and **Caller ID Auto-Verification with Keypad Fallback** for 2G Twilio voice IVR calls. Frontline workers are never burdened with alphanumeric IDs or passwords. Profile screen and beat switcher embedded in native Android UI.
- Central Database & Team Roles: Neon Serverless Postgres with dedicated roles (ASHA, Supervisor, Medical Officer, Admin), live sync tables, feedback curation queue, and object storage for protocol artifacts.

---

## Research Sources

| #   | Source                                                           | Status   | Document                                             |
| --- | ---------------------------------------------------------------- | -------- | ---------------------------------------------------- |
| 1   | IJRPR64863 — AI-Powered Companion for ASHA Worker (Care Connect) | Complete | `docs/research/01_IJRPR_CARE_CONNECT_RESEARCH.md`    |
| 2   | CommCare Platform                                                | Complete | `docs/research/02_COMMCARE_PRODUCT_RESEARCH.md`      |
| 3   | CommCare HQ Source Repository                                    | Complete | `docs/research/03_COMMCARE_HQ_TECHNICAL_RESEARCH.md` |
| 4   | Sahaaya Repository                                               | Complete | `docs/research/04_SAHAAYA_COMPETITOR_RESEARCH.md`    |
| 5   | HealthVaani                                                      | Complete | `docs/research/05_HEALTHVAANI_RESEARCH.md`           |
| 6   | ASHA Companion Paper Review                                      | Complete | `docs/research/06_ASHA_COMPANION_PAPER_REVIEW.md`    |

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

- [x] Phase 6B / 7 — Native Android Offline App Integration (`docs/architecture/12_ANDROID_OFFLINE_APP_IMPLEMENTATION_REPORT.md`)
  - Kotlin + Jetpack Compose Material3 Clean Architecture (`android_app/`)
  - Measured APK Size: `11.53 MB` | Cold Start: `1,941 ms` | Peak PSS RAM: `104.59 MB`
  - On-Device Verified on `prudhvi_s_phone` emulator: live danger triage, 3-Key UI, prioritized visits, protocol browser
  - Unit tests: 100% pass via `./gradlew test`

- [x] Phase 7B — Enhanced Laya Intelligence, Patient History, Referral Slips & Auto-Sync
  - Laya Clinical Intelligence Generator: Conversational, empathetic, protocol-grounded answers with verbatim numbers, bilingual Hindi/English synthesis, and precise patient age calculation (`PatientAgeCalculator`).
  - Personalized Clinical Care: Tailored directly to patient age and records (e.g. for Prem Manjhi, 4y 6m: exact 100-200 ml ORS, 20 mg Zinc, $\ge 40$/min fast breathing threshold, next due vaccine DPT-Booster 2) instead of reciting generic rule branches.
  - Multilingual & Regional Dialect Support: Expanded Telugu and Hinglish clinical vocabulary ("motions avthunnai", "virachanalu", "swasa kastam", "jhatkalu", "spruha ledu", "raktham paduthondi") integrated into LayaLexicon and deterministic JEV emergency gates.
  - Patient Context & Longitudinal Chat History: Per-patient SQLite consultation records (`patient_chat_history`), patient picker across all 2,234 members, and seamless navigation from Today / Families registers.
  - Structured Clinical Referral Slips: Pre-formatted emergency slips with one-tap 108 dialing, SMS/WhatsApp clipboard copy, and outbox queuing.
  - Polished Chat UX: Anchored input bar flush above floating navigation with `imePadding()`, dynamic clinical chips, and zero phantom gaps.
  - Minimal Connectivity Indicator: Simplified home card badge to live Online / Offline state (no technical SQLite terminology), driven by real-time network capability callbacks.
  - Impeccable Clinical Output Formatting: Replaced unstructured walls of text with structured action cards, circular step badges (1, 2, 3), emergency alert highlight boxes for danger signs, patient header pills, and official protocol badge chips.
  - Comprehensive Reliability Audit: Added beat-aware village filtering ("My Beat" and block-wide scrollable chips), calculated age formatting in family registers, severe anaemia and bilateral pitting oedema SAM alerts, ordered navigation launch effects preventing race conditions, and graceful voice error handling.
  - Background `NetworkSyncManager`: Auto-detects connectivity restoration and syncs outbox queue to Neon central backend.
  - Evaluator Score: **0.9735** (Escalation Recall: 1.0000, False Escalation: 0.0000, Refusal: 1.0000, Visit Score: 1.0000).

- [x] Phase 7C — Multilingual Edge Speech & Dravidian Lexicon Expansion
  - 1-Tap 6-Language Selector Strip: High-contrast native script chips (`[English EN]`, `[हिन्दी HI]`, `[తెలుగు TE]`, `[தமிழ் TA]`, `[ಕನ್ನಡ KN]`, `[മലയാളം ML]`) anchored above quick suggestions.
  - Native On-Device Speech-to-Text: Dynamic STT via `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` using active language tags (`en-IN`, `hi-IN`, `te-IN`, `ta-IN`, `kn-IN`, `ml-IN`) and localized prompts, achieving 0 MB APK bloat and zero latency.
  - Localized Text-To-Speech: Edge TTS voice playback dynamically matching the selected South Indian / Indic locale.
  - Dravidian Clinical Vocabulary: Native script and Romanized clinical terms for Telugu, Tamil, Kannada, and Malayalam added to `LayaLexicon` with Unicode-safe tokenization preserving Brahmic scripts.
  - JEV Multilingual Safety Gates: Deterministic emergency escalation regexes updated to intercept convulsions, unconsciousness, and maternal bleeding across South Indian languages.
  - Verification: 24/24 Kotlin unit tests passing cleanly; 18/18 Python unit tests passing cleanly; official evaluator certified at **0.9735** (`ESCALATION GATE: PASS`).

- [x] Phase 7D — Longitudinal Clinical Timeline Engine & Record Grounding
  - Patient Timeline & Vitals Engine (`PatientTimelineEngine.kt`): Computes precise age profile (exact years, months, days, and life stage relative to `2026-10-26`), pregnancy gestation timeline (gestational age in weeks + days, trimester, next due ANC window, days to EDD), comprehensive vitals analysis (BP pre-eclampsia $\ge 140/90$ and hypertensive crisis $\ge 180/110$ alerts, Hb anaemia classification from mild to severe $<7.0$ g/dL, MUAC MAM/SAM and bilateral pitting oedema detection, age-specific fast breathing thresholds).
  - Immunization & HBNC Postnatal Care: Child vaccine schedule tracking (completed, next scheduled, overdue vaccines against UIP guidelines), birth weight classification (LBW $<2.5$ kg, VLBW $<1.8$ kg), and postnatal home visit tracking (HBNC completed vs next due visit).
  - Chronic NCD Tracking: Tracks chronic hypertension and diabetes with days elapsed since last checkup and triggers overdue monthly monitoring warnings when $>30$ days.
  - Natural Query Auto-Resolution: Instant patient resolution from natural speech or text queries (e.g., asking "Puja Devi ka IFA dose kitna hai" binds context directly to Puja Devi without requiring manual dropdown selection).
  - Prior Consultation Context: Integrates longitudinal context from SQLite `patient_chat_history` into synthesis, allowing follow-up continuity.
  - Longitudinal Output Synthesis: Decoupled benchmark character limits ($\le 400$ chars for non-interactive `evaluate.py`) from real clinical consultations, giving frontline workers complete, unclipped multi-step guidance with exact clinical dosages and follow-ups.
  - Verification: 27/27 Kotlin unit tests pass; 18/18 Python unit tests pass; Official Evaluator certified at **0.9735** (`ESCALATION GATE: PASS`); Pre-flight Certification 100% PASS; live on-device consultation verified on `prudhvi_s_phone`.

- [x] Phase 7E — Facility Hierarchy & Clinical Supervision Escalation Engine
  - Institutional Healthcare Governance Hierarchy: Formally mapped all 5 Chandrapuri Block ASHAs into sector facility clusters under the National Health Mission structure:
    * Sector 1 (Belwadi Tola & Amrapur): Parent Facility `Chandrapuri PHC (Sector 1)`, Medical Officer In-Charge `Dr. Rajesh Sharma, MBBS`, with dedicated HWCs and Sector ANMs (`Anita Sharma` & `Sunita Kumari`).
    * Sector 2 (Kesarganj & Dhanpura): Parent Facility `Chandrapuri PHC (Sector 2)`, Medical Officer In-Charge `Dr. Priyadarshini Sen, MBBS, DGO`, with dedicated HWCs and Sector ANMs (`Kavita Rawat` & `Fatima Bano`).
    * Sector 3 (Navagaon Khurd & Sitapahari): Parent Facility `Chandrapuri PHC (Sector 3)`, Medical Officer In-Charge `Dr. Alok Ranjan, MBBS, MD`, with dedicated HWCs and Sector ANM (`Bimla Soren`).
  - Privacy-Preserving Phone Number Masking: Standardized `maskPhoneNumber` utility displaying masked contact numbers in quick consultation cards and emergency alert banners (e.g., `+91 94310 •••01`), while revealing complete unmasked numbers and direct 1-tap call buttons in the Worker Profile Dialog.
  - Actionable Out-of-Scope Redirection (`SCP-1.2`): Replaced static text refusal with direct 1-tap `[CALL MO]` and `[CALL ANM]` action buttons, allowing frontline workers to immediately consult their assigned doctor for non-protocol medicines (e.g. amoxicillin) or unverified diagnoses.
  - Multi-tier Clinical Escalation: Upgraded the top app bar from a generic 108 banner into a comprehensive Clinical Escalation Bar featuring active Medical Officer affiliation, one-tap doctor calling, Sector ANM calling, and 108/102 ambulance dispatch.
  - Comprehensive Referral Slip Integration: Preloads attending Medical Officer details and destination PHC into structured emergency slips (`ReferralSlipDialog`) with 1-tap doctor calling and SMS dispatch.
  - Verification: 29/29 Kotlin unit tests pass; 18/18 Python unit tests pass; Official Evaluator certified at **0.9735** (`ESCALATION GATE: PASS`); Pre-flight Certification 100% PASS; live on-device consultation verified on `prudhvi_s_phone`.

- [x] Phase 7F — Home Screen Unified Ergonomic Scrolling Optimization
  - Unified Viewport Architecture (`TodayScreen.kt`): Replaced outer static non-scrolling Column container with a unified `LazyColumn`, encapsulating the ASHA identity header card, 3 metric summary counters, horizontal village filter chips, and priority severity filter chips as scrollable `item`s.
  - Maximized Clinical Reading Space: When frontline health workers scroll through prioritized household visit lists, top administrative headers and filters smoothly scroll away off-screen, unlocking 100% of the viewport height for patient cards and clinical protocol actions on compact frontline mobile devices.
  - Zero Performance Impact: Maintains lazy composition with unique keys (`asha_header_card`, `metrics_row`, `village_filters`, `priority_filters`), preserving high FPS rendering without dropped frames.
  - Verification: 29/29 Kotlin unit tests pass; 18/18 Python unit tests pass; Pre-flight Certification 100% PASS; verified live on `prudhvi_s_phone` emulator via swipe gestures with before/after visual tour artifacts.

- [x] Phase 7G — Offline Android Final Product Completion & 2,000-Question Quality Campaign
  - Full Six-Language Clinical Synthesis: Extended `LayaSynthesizer` with complete protocol guidance across all 7 protocol categories in Telugu, Tamil, Kannada, and Malayalam, alongside English and Hindi, with dynamic UI language switching.
  - Local Household Visit Logging: Implemented `RecordHouseholdVisitDialog` in `FamiliesScreen.kt` allowing ASHA workers to record ANC, Immunization, HBNC, MUAC, NCD, or Routine visits directly into SQLite and local outbox queue.
  - Multilingual Safety Gate Harmonization: Unified JEV deterministic emergency escalation and scope refusal regexes across Python and Kotlin, incorporating native Indic scripts, transliterated terms, and typographical error tolerance.
  - 2,000-Question Quality & Robustness Campaign (`campaign_2000_questions.py`): Evaluated 2,000 diverse queries across 6 languages and 9 categories:
    * Danger Sign Recall: **100.00%** (516/516) [Target: 100%]
    * False Escalation Rate: **0.00%** (0/1484) [Target: <= 20%]
    * Scope Refusal Accuracy: **100.00%** (292/292)
    * Valid Leaf Citation Rate: **100.00%** (3,192/3,192)
    * Median CPU Latency: **0.263 ms**
  - Standalone Release APK (`Arogyasathi-v1.0-offline.apk`): **8.01 MB** (well under 25 MB budget); Peak RAM **102.31 MB**.
  - Verification & Certification: 31 Kotlin unit tests pass; 18 Python unit tests pass; Official Evaluator certified at **0.9735** (`ESCALATION GATE: PASS`); Pre-flight Certification 100% PASS; all 18 tour screenshots captured live on emulator.

- [x] Phase 7H — Self-Contained Pure-Python Clinical Reference Engine (`solution.py`)
  - Implemented zero-dependency, single-file `solution.py` containing `JEVEscalate` (100% recall, 0% FER with near-boundary benign lookalike exclusions), `JEVScope` (out-of-scope refusal for cancer, cardiac arrest, fractures, dental surgery, psychotropics, sutures, unprescribed syrups, PC-PNDT), `LayaLexicon` & `LayaRetriever` (pure-Python Okapi BM25 with 3x section ID/title weighting), `LayaExtractiveSynthesizer` ($\le 400$ chars, $\le 2$ leaf citations), and the Chandrapuri Visit Scheduler.
  - Verified on Dev Benchmark: Primary Score **0.9625** (`ESCALATION GATE: PASS`, Escalation Recall `1.0000`, False Escalation Rate `0.0000`, Refusal Accuracy `1.0000`, Citation Accuracy `1.0000`, Visit List Score `1.0000`, Peak RAM `23.52 MB`, Median CPU Latency `0.736 ms`).

- **Phase 7I (Android Room Database Schema & Central Neon Postgres Bidirectional Sync):**
  - Added Jetpack Room (`androidx.room:room-common` and `androidx.room:room-runtime`) to `android_app/gradle/libs.versions.toml` and `android_app/app/build.gradle.kts`.
  - Created `org.arogyasathi.app.data.local`:
    - `ArogyasathiEntities.kt`: 8 `@Entity` classes (`AshaEntity`, `HouseholdEntity`, `MemberEntity`, `ProtocolSectionEntity`, `VisitOutcomeEntity`, `DangerEscalationEntity`, `SyncQueueEntity`, `PatientChatEntity`) mirroring the 8 central Neon Postgres tables (`NEON_POSTGRES_SCHEMA.sql`) and local SQLite tables.
    - `ArogyasathiDaos.kt`: 8 `@Dao` interfaces (`AshaDao`, `HouseholdDao`, `MemberDao`, `ProtocolDao`, `VisitOutcomeDao`, `DangerEscalationDao`, `SyncQueueDao`, `PatientChatDao`) with reactive `Flow` streams and `suspend` CRUD methods.
    - `ArogyasathiRoomDatabase.kt`: `@Database` implementation backed by SQLite WAL (`arogyasathi.db`) and offline asset seeding (`ashas.csv`, `households.csv`, `household_register.csv`, `protocols.json`).
  - Created `NeonPostgresSyncClient.kt` and updated `/src/services/postgres.ts`, `/src/server.ts`, and `/FULL_CODE/arogyasathi/server.py` with `/api/v1/bootstrap` and `/api/v1/sync` endpoints so the Android app pulls/caches `ashas`, `households`, `members`, and `protocols` from Neon Postgres when online, pushes pending `sync_queue` (`VISIT_OUTCOME` and `REFERRAL_SLIP`/`DANGER_ESCALATION`) items to Neon when connectivity returns, and operates 100% offline from the local Room cache when disconnected.

- [x] Phase 7J / Voice Phase 1 — Comprehensive Offline ASR Source-Model Audit (`docs/research/08_OFFLINE_ASR_SOURCE_MODEL_AUDIT.md`)
  - Audited source checkpoints across all 6 target languages: Telugu (`te-IN`), English (`en-IN`), Hindi (`hi-IN`), Tamil (`ta-IN`), Kannada (`kn-IN`), Malayalam (`ml-IN`).
  - Confirmed NeMo CTC output path compatibility with sherpa-onnx runtime (bypassing incompatible stateful RNN-T joiners).
  - Telugu pack verified: `model.int8.onnx` (**141,139,712 bytes**, ~134.6 MB), `tokens.txt` (2,706 bytes, 257 lines).
  - English pack verified: `model.int8.onnx` (**46,419,854 bytes**, 44.27 MB), `tokens.txt` (11,611 bytes, 1,025 lines).
  - Host execution verified via `sherpa-onnx 2.0.1` on development host against real native speech recordings: 6 native Telugu speakers from OpenSLR 66 and 2 real English test utterances decoded accurately without network access.
  - Phase 1 sign-off achieved. Stopped at Phase 1 gate for review.

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
