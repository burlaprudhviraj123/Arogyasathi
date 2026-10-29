# Arogyasathi (आरोग्यसाथी) — Hackathon Pitch & Architecture Walkthrough

**Track:** Track H5 (Frontline Health Worker Companion) / TalentAP Mega Hackathon 2026  
**Primary Evaluator Score:** **`0.9603`** (Binary Escalation Gate: **PASS**)  
**System Profile:** 100% Offline-First Native Android (Kotlin + Jetpack Compose) + Edge Sync Gateway  
**Document ID:** `DOC-PRES-001`

---

## 1. Executive Summary & Ground Reality

In rural India (such as Chandrapuri Block), frontline health workers (ASHAs and ANMs) are the primary lifeline for over 500 households (2,200+ beneficiaries) spread across multiple tolas and hamlets. They face three formidable engineering challenges:

1. **Zero-Connectivity Realities:** Edge clinics and beneficiary homes have zero cellular signal or intermittent 2G voice. Cloud-dependent apps fail in the field.
2. **Clinical Safety Cannot Be Probabilistic:** A generic LLM that misses a maternal hemorrhage or neonatal convulsion even 1% of the time can cost a mother or child's life. Danger escalation must be 100% deterministic.
3. **Severe Hardware Constraints:** ASHAs operate government-issued or low-cost Android phones with 2–3 GB RAM and slow Quad-Core CPUs. Applications requiring heavy local models (>1 GB) crash with Out-Of-Memory (OOM) errors.

**Arogyasathi solves all three challenges.** It is a native, offline-first Android clinical companion built on clean architecture with deterministic safety governance (JEV), multilingual protocol retrieval (Laya), a prioritized household visit scheduler, and durable deferred synchronization.

---

## 2. Core Architecture: The Dual-Engine Paradigm

Instead of relying on an unconstrained language model, Arogyasathi separates **clinical safety** from **protocol synthesis**:

```
                              [Frontline Worker Input]
                         (Hindi / Hinglish / English Query)
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │   JEV (Judicial Evidence Validator)   │
                   │   Deterministic Governance Kernel     │
                   └──────────────────┬────────────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
     [DANGER SIGN DETECTED?]                         [OUT-OF-SCOPE DRUG?]
       • Convulsions, Bleeding,                        • Unprescribed medicines,
         Hypothermia, Stroke, BP crisis                  PC-PNDT sex determination
              │                                               │
             YES                                             YES
              │                                               │
              ▼                                               ▼
  🚨 IMMEDIATE ESCALATION                         🛡️ SAFE PROTOCOL REFUSAL
  • 108 Emergency Dialer                          • Medical Officer / PHC Redirect
  • Cited Leaf Section (ESC-x.x)                  • Cited Refusal (SCP-1.2)
  • Zero LLM Hallucination                        • Zero Dangerous Prescriptions
  • Latency < 1 ms                                • Latency < 1 ms
              │                                               │
              └───────────────────────┬───────────────────────┘
                                      │ NO (Routine Protocol)
                                      ▼
                   ┌───────────────────────────────────────┐
                   │     Laya Edge Retrieval Engine        │
                   │   Multilingual BM25 + Synthesis       │
                   └──────────────────┬────────────────────┘
                                      │
                                      ▼
                   📋 GROUNDED PROTOCOL GUIDANCE
                   • Indexed across 107 Leaf Protocol Sections
                   • Extractive Synthesis strictly <= 400 chars
                   • Max 2 valid citations (ANC-x.x, CHD-x.x, IMM-x.x)
                   • Spoken Audio Readout via Text-to-Speech (TTS)
```

---

## 3. Official Evaluation Scorecard (`evaluate.py`)

Arogyasathi was evaluated against the official competition evaluation script on development data:

| Metric | Threshold / Target | Arogyasathi Result | Compliance Status |
| :--- | :--- | :--- | :---: |
| **Escalation Recall** | $= 1.000$ (Binary Gate) | **`1.0000`** (10/10) | **PASS** |
| **False Escalation Rate** | $\le 0.200$ (Binary Gate) | **`0.0000`** (0/30) | **PASS** |
| **Escalation Gate** | Recall $== 1.0 \land \text{FER} \le 0.20$ | **`PASS`** | **PASS** |
| **Refusal Accuracy** | $\ge 0.900$ | **`1.0000`** (30/30) | **PASS** |
| **Citation Accuracy** | $\ge 0.850$ | **`0.9412`** | **PASS** |
| **Key Fact Coverage** | $\ge 0.750$ | **`0.7941`** | **PASS** |
| **Visit List Score** | $\ge 0.900$ | **`1.0000`** (F1: 1.0, Priority: 1.0) | **PASS** |
| **PRIMARY SCORE** | $> 0.3068$ (Baseline) | **`0.9603`** | **EXCEPTIONAL** |
| **Peak RAM Footprint** | $\le 256\text{ MB}$ | **`100.11 MB`** | **PASS** |
| **Median CPU Latency** | $\le 200\text{ ms}$ | **`0.438 ms`** | **PASS** |

---

## 4. Native Android App: Built for Low-End Edge Phones

Arogyasathi was compiled and measured on an Android ARM64 emulator (`emulator-5554`) configured to match rural low-end smartphone profiles:

```
+------------------------------------------------------------------------+
| Measured Metric          | Allowed Budget       | Arogyasathi Measured |
+------------------------------------------------------------------------+
| Standalone APK Size      | <= 25.0 MB           | 12.0 MB              |
| Cold Start Launch Time   | <= 3.0 seconds       | 1.558 seconds        |
| Total PSS Memory         | <= 256.0 MB          | 90.0 MB              |
| Java Heap Memory         | <= 48.0 MB           | 9.69 MB              |
| Native Heap Memory       | <= 32.0 MB           | 17.09 MB             |
| Memory Headroom          | > 20%                | 64.8% Remaining      |
| SQLite Cold Seeding      | <= 1.0 second        | < 0.200 seconds      |
+------------------------------------------------------------------------+
```

---

## 5. Frontline Ergonomics & HCI Innovations

1. **Floating Liquid Glass Capsule Navigation:**
   - 4 primary destinations positioned ergonomically for one-handed thumb reach:
     - **Today (📅):** Metrics counters, daily priority visit cards, and completion dialog.
     - **Families (👥):** 500 households search and expandable family health condition tags.
     - **Assistant (🩺):** Red 108 emergency banner, clinical Q&A, and protocol answers.
     - **Records (📁):** Completed visits log, sync outbox, and 107 leaf protocols database.
2. **One-Touch 108 / 102 Emergency Call Button:**
   - Always visible at the top of the Assistant screen; launches Android telephony dialer with `tel:108` pre-populated in a single tap.
3. **Text-To-Speech (TTS) Voice Readout:**
   - Allows ASHA workers with low literacy or working in loud outdoor environments to hear clinical instructions spoken aloud in Hindi and English.
4. **Durable Sync Outbox:**
   - Offline visit outcomes are persisted in SQLite and queued in `sync_queue`. When network connectivity returns, tapping "Sync Now" flushes the queue to the sub-centre FastAPI gateway.

---

## 6. Step-by-Step Live Demonstration Guide (For Evaluators & Judges)

### Demo 1: Run Full Automated Verification in 6 Seconds
```bash
./verify_all.sh
```
*Expected Output:* Executes 16 Python tests, runs the official evaluator on dev split (Score: 0.9603, Gate: PASS), executes 14 Android Kotlin tests, and verifies the 12 MB APK compilation.

### Demo 2: Interactive Clinical Console
```bash
python3 FULL_CODE/cli.py
```
- Select Option `1` and type a colloquial Hindi danger sign:  
  `"sugar ke marij behosh pada hai"` $\rightarrow$ Triggers `[CRITICAL ESCALATION]` with `ESC-6.3` and 108 call instructions.
- Select Option `1` and ask an unprescribed medication question:  
  `"2 saal ke bachche ko kaun sa paracetamol syrup du"` $\rightarrow$ Triggers `[OUT-OF-SCOPE REFUSAL]` with `SCP-1.2` and Medical Officer redirect.
- Select Option `2`: Displays Chandrapuri Block visit summary across 500 households.

### Demo 3: Live Android Application
```bash
adb install -r android_app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.arogyasathi.app/.MainActivity
```
- Observe the **Today Tab**: 454 Planned, 30 Urgent, 0 Completed.
- Tap **Complete** on the first visit item: record status and notes $\rightarrow$ Updates Completed counter to 1.
- Tap **Records Tab** $\rightarrow$ Observe recorded outcome and 1 item in the **Sync Outbox**. Tap **Sync Now** $\rightarrow$ Status turns to `SYNCED` in green.
- Tap **Assistant Tab** $\rightarrow$ Tap "Bleeding in pregnancy" quick prompt $\rightarrow$ Instant verified protocol card with citation `ESC-2.1` and `🔊 Listen` audio readout.

---

## 7. Submission Checklist & Traceability

- [x] Official competition submission artifacts generated in `submission/`:
  - `H5_answers.jsonl` (200 test question answers, verified against schema).
  - `H5_visit_list.csv` (346 prioritized items, verified against `09_VIS` rules).
  - `H5_footprint.json` (Measured CPU latency: 0.334 ms, Peak RAM: 103.8 MB).
- [x] Native offline Android app in `android_app/` with 100% offline capability.
- [x] High-resolution on-device screenshots committed in `android_app/screenshots/`.
- [x] Living project manifest updated in `docs/PROJECT_MANIFEST.md`.
- [x] All 14 Kotlin tests and 16 Python tests passing.
- [x] Git working tree clean on branch `master`.
