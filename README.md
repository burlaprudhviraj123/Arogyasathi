# Arogyasathi (आरोग्यसाथी) — Frontline Health-Worker Companion

> **An offline-first, protocol-grounded clinical companion for ASHA and ANM workers in low-connectivity rural environments (Chandrapuri Block).**  
> *TalentAP Mega Hackathon 2026 — Track H5 Frontline Health Worker Companion*

---

## 1. System Overview

Arogyasathi implements a deterministic clinical governance and retrieval architecture designed for low-resource Android edge devices (2–3 GB RAM, no GPU):
- **JEV (Judicial Evidence Validator):** Deterministic danger-sign escalation kernel (`JEVEscalate`), out-of-scope refusal guard (`JEVScope`), and citation validation (`JEVVerify`).
- **Laya:** Edge multilingual engine supporting Hindi (Devanagari & Hinglish), Marathi, Telugu, and Bengali (`LayaLexicon`, `LayaRetriever`, `LayaSynthesizer`).
- **Chandrapuri Visit Engine:** Prioritized daily visit list scheduler over a 500-household register (2,234 members) following all block counting and priority conventions (`09_VIS`).

---

## 2. Evaluation Results (Official `evaluate.py`)

| Metric | Target / Gate | Measured Result | Status |
| :--- | :--- | :--- | :--- |
| **Escalation Recall** | $= 1.000$ (Binary Gate) | **`1.0000`** (10/10) | **PASS** |
| **False Escalation Rate** | $\le 0.200$ (Binary Gate) | **`0.0000`** (0/30) | **PASS** |
| **Escalation Gate** | Recall $== 1.0 \land \text{FER} \le 0.20$ | **`PASS`** | **PASS** |
| **Refusal Accuracy** | $\ge 0.900$ | **`1.0000`** (30/30) | **PASS** |
| **Citation Accuracy** | $\ge 0.850$ | **`0.9412`** | **PASS** |
| **Key Fact Coverage** | $\ge 0.750$ | **`0.7941`** | **PASS** |
| **Visit List Score** | $\ge 0.900$ | **`1.0000`** (F1: 1.0, Priority: 1.0) | **PASS** |
| **Primary Evaluator Score** | $> 0.3068$ (Baseline) | **`0.9603`** | **PASS** |
| **Peak RAM Footprint** | $\le 256\text{ MB}$ | **`102.86 MB`** | **PASS** |
| **Median CPU Latency** | $\le 200\text{ ms}$ | **`0.452 ms`** | **PASS** |

---

## 3. Quick Start & Execution Instructions

### A. Run Automated Unit & Integration Tests
```bash
# Run all 9 test suites
PYTHONPATH=FULL_CODE python3 -m pytest FULL_CODE/tests/
```

### B. Generate Submissions via CLI
```bash
# Generate development submission
python3 FULL_CODE/run_pipeline.py --split dev --out submission_dev

# Generate final competition test submission
python3 FULL_CODE/run_pipeline.py --split test --out submission
```

### C. Run the Official Evaluator
```bash
# Score the development submission
python3 H5_Frontline_Health_Worker_Companion/evaluate.py \
  --submission submission_dev \
  --truth H5_Frontline_Health_Worker_Companion/data/dev_truth.jsonl \
  --out score.json
```

### D. Native Android App (Build, Test & Install)
```bash
cd android_app

# Run all 14 Kotlin unit tests
./gradlew test

# Build debug APK (12 MB package size)
./gradlew assembleDebug

# Install on connected device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch Arogyasathi on Android
adb shell am start -n org.arogyasathi.app/.MainActivity
```

---

## 4. Repository Structure

```text
Arogyasathi/
├── android_app/                          # Native offline Android application (Kotlin + Compose)
│   ├── app/src/main/assets/              # Bundled offline assets (protocols.json, households.csv, household_register.csv)
│   ├── app/src/main/kotlin/org/arogyasathi/app/
│   │   ├── core/Models.kt                # Domain models: Member, Household, ProtocolSection, VisitItem, SyncItem
│   │   ├── jev/JEV.kt                    # JEV deterministic danger escalation & scope refusal in Kotlin
│   │   ├── laya/Laya.kt                  # Laya BM25 retrieval & synthesis in Kotlin
│   │   ├── visit/VisitEngine.kt          # Chandrapuri Block scheduler in Kotlin (all 09_VIS rules)
│   │   ├── data/
│   │   │   ├── ArogyasathiDbHelper.kt    # SQLite database helper (500 households, 2234 members, 107 protocols)
│   │   │   └── LocalRepository.kt        # Repository coordinating queries, outcomes, sync outbox
│   │   └── ui/
│   │       ├── Theme.kt                  # Clinical Liquid Glass palette & typography
│   │       ├── BottomNavigation.kt       # Floating Liquid Glass bottom capsule navbar
│   │       ├── TodayScreen.kt            # Tab 0: Daily visits, metrics counters, outcome completion dialog
│   │       ├── FamiliesScreen.kt         # Tab 1: Household search, village filter chips, expandable cards
│   │       ├── AssistantScreen.kt        # Tab 2: 108 emergency dialer, clinical Q&A, Text-to-Speech audio
│   │       ├── RecordsScreen.kt          # Tab 3: Outcomes history, sync outbox with retry, protocol browser
│   │       └── MainScreen.kt             # Root navigation container
│   ├── app/src/test/kotlin/              # 14 automated unit tests (JEV, Laya, VisitEngine)
│   └── app/build/outputs/apk/debug/      # app-debug.apk (12 MB standalone APK)
├── docs/                                 # Complete documentation suite
│   ├── PROJECT_MANIFEST.md               # Living single source of truth
│   ├── architecture/                     # System & Android architectures + implementation reports
│   └── research/                         # 6 audited reference research documents
├── FULL_CODE/                            # Production Python core engine (Evaluator Score: 0.9603)
│   ├── arogyasathi/                      # Python JEV, Laya, VisitEngine, Protocols
│   └── tests/                            # 9 comprehensive tests
├── submission/                           # Final evaluated test submission
│   ├── H5_answers.jsonl
│   ├── H5_visit_list.csv
│   └── H5_footprint.json
└── README.md
│   │   ├── jev.py                        # JEV governance kernel
│   │   ├── laya.py                       # Laya retrieval & synthesis
│   │   ├── protocols.py                  # Protocol leaf parser
│   │   ├── qa_engine.py                  # Unified QA pipeline
│   │   ├── visit_engine.py               # Chandrapuri visit engine
│   │   └── pipeline.py                   # Submission generator
│   ├── tests/                            # 9 comprehensive tests
│   └── run_pipeline.py                   # CLI entrypoint
├── submission/                           # Final evaluated test submission
│   ├── H5_answers.jsonl
│   ├── H5_visit_list.csv
│   └── H5_footprint.json
└── README.md
```

---

## 5. Architectural Preservation Note

This implementation strictly preserves:
- **JEV:** Judicial Evidence Validator as the hard-coded safety and scope governance kernel.
- **Laya:** Multilingual edge language adaptation and retrieval engine.
- **3-Mode Operating Topology:** Mode 1 (Offline On-Device), Mode 2 (Online Server), Mode 3 (Twilio IVR Call).
- **4-Tier RBAC:** Frontline ASHA, Auxiliary Nurse Midwife (ANM), Medical Officer (MO), System Administrator.

