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
| **Citation Accuracy** | $\ge 0.850$ | **`1.0000`** (100% accurate) | **PASS** |
| **Key Fact Coverage** | $\ge 0.750$ | **`0.8235`** | **PASS** |
| **Visit List Score** | $\ge 0.900$ | **`1.0000`** (F1: 1.0, Priority: 1.0) | **PASS** |
| **Primary Evaluator Score** | $> 0.3068$ (Baseline) | **`0.9735`** (+217% over BM25) | **PASS** |
| **Peak RAM Footprint** | $\le 256\text{ MB}$ | **`102.86 MB`** | **PASS** |
| **Median CPU Latency** | $\le 200\text{ ms}$ | **`0.452 ms`** | **PASS** |

---

## 3. Quick Start & Execution Instructions

### A. One-Touch Unified Verification Pipeline
```bash
# Runs Python tests, dev evaluator, Android unit tests, APK compilation, and pre-flight audit:
./verify_all.sh
```

### B. Automated Judge Simulation & Benchmark Comparison
```bash
# Non-interactive automated simulation for judges (<150 ms):
python3 FULL_CODE/cli.py --demo

# Live reproducible head-to-head comparison against official BM25 baseline (+213% improvement):
python3 benchmark_comparison.py

# Interactive console interface:
python3 FULL_CODE/cli.py
```

### C. Zero-Dependency Standalone Browser Showcase & Pitch Deck
```bash
# Open the self-contained offline web demo in any browser:
open web_demo.html

# Open the interactive 10-slide pitch deck in any browser:
open slides.html
```

### D. External Pre-Flight Certification Audit
```bash
# Audits the official submission ZIP archive in an isolated sandbox:
python3 preflight_audit.py
```

### E. Run the Official Evaluator
```bash
# Score development submission against ground truth:
python3 H5_Frontline_Health_Worker_Companion/evaluate.py \
  --submission submission_dev \
  --truth H5_Frontline_Health_Worker_Companion/data/dev_truth.jsonl \
  --out score_dev.json
```

### F. Native Android App (Build, Test & Install)
```bash
cd android_app

# Run 14 Kotlin unit tests
./gradlew test

# Build 7.88 MB standalone release APK (or ./gradlew assembleDebug for debug)
./gradlew assembleRelease

# Install and launch on connected device or emulator
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n org.arogyasathi.app/.MainActivity
```

### G. Automated Android UI Live Tour (ADB)
```bash
# Autonomously navigates all 4 workspaces, tests danger sign escalation & sync, and captures screenshots:
python3 demo_android_tour.py
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
│   └── app/build/outputs/apk/release/    # app-release.apk (7.88 MB release) / debug (12 MB)
├── docs/                                 # Complete documentation suite
│   ├── PROJECT_MANIFEST.md               # Living single source of truth
│   ├── architecture/                     # System & Android architectures + implementation reports
│   └── research/                         # 6 audited reference research documents
├── FULL_CODE/                            # Production Python core engine (Evaluator Score: 0.9735)
│   ├── arogyasathi/                      # Python JEV, Laya, VisitEngine, Protocols, Server
│   │   ├── jev.py                        # JEV deterministic safety kernel
│   │   ├── laya.py                       # Laya BM25 retrieval & synthesis
│   │   ├── protocols.py                  # Protocol leaf parser (107 sections)
│   │   ├── qa_engine.py                  # Unified QA pipeline
│   │   ├── visit_engine.py               # Chandrapuri visit scheduler (09_VIS)
│   │   └── server.py                     # FastAPI Sub-Centre edge sync gateway
│   ├── tests/                            # 17 automated test suites
│   ├── cli.py                            # Interactive console & judge simulation
│   └── run_pipeline.py                   # Pipeline runner & submission generator
├── submission/                           # Scored test submission artifacts
│   ├── H5_answers.jsonl                  # 200 answers mapped 1:1 to test questions
│   ├── H5_visit_list.csv                 # 346 prioritized Chandrapuri visits
│   └── H5_footprint.json                 # Benchmark footprint metadata
├── slides.html                           # Interactive 10-slide pitch presentation viewer
├── web_demo.html                         # Zero-dependency browser showcase
├── demo_android_tour.py                  # Automated live Android UI demonstration & screenshot tour
├── verify_all.sh                         # Unified 5-step verification pipeline
├── preflight_audit.py                    # External archive certification auditor
└── README.md
```

---

## 5. Architectural Preservation Note

This implementation strictly preserves:
- **JEV:** Judicial Evidence Validator as the hard-coded safety and scope governance kernel.
- **Laya:** Multilingual edge language adaptation and retrieval engine.
- **3-Mode Operating Topology:** Mode 1 (Offline On-Device), Mode 2 (Online Server), Mode 3 (Twilio IVR Call).
- **4-Tier RBAC:** Frontline ASHA, Auxiliary Nurse Midwife (ANM), Medical Officer (MO), System Administrator.

