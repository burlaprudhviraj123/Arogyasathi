# Arogyasathi (आरोग्यसाथी) — Frontline Health-Worker Companion

> **An offline-first, protocol-grounded clinical companion for ASHA and ANM workers in low-connectivity rural environments (Chandrapuri Block).**  
> *TalentAP Mega Hackathon 2026 — Track H5 Frontline Health Worker Companion*

---

## 1. System Overview

Arogyasathi implements a deterministic clinical governance and retrieval architecture designed for low-resource Android edge devices (2–3 GB RAM, no GPU):
- **JEV (Judicial Evidence Validator):** Deterministic danger-sign escalation kernel (`JEVEscalate`), out-of-scope refusal guard (`JEVScope`), and citation validation (`JEVVerify`).
- **Laya:** Edge multilingual engine supporting Hindi (Devanagari & Hinglish), Telugu, Tamil, Kannada, Malayalam, and Bengali (`LayaLexicon`, `LayaRetriever`, `LayaSynthesizer`).
- **Chandrapuri Visit Engine:** Prioritized daily visit list scheduler over a 500-household register (2,234 members) following all block counting and priority conventions (`09_VIS`).
- **Floating Capsule Navigation Dock:** Framer Motion spring-morphing active tab indicator, dark notification badges, and tactile active tab scaling.
- **Dynamic Database Architecture:** Zero hardcoded worker profiles, sub-centre IDs, or village lists—every field is queried directly and dynamically from SQLite/Room and backed by Neon Postgres.

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

### A. Run Core Python Test Suite (29 Automated Tests)
```bash
# Run all unit, integration, and safety tests:
pytest
```

### B. Automated Judge Simulation & CLI Interface
```bash
# Non-interactive automated simulation for judges (<150 ms):
python3 FULL_CODE/cli.py --demo

# Interactive console interface:
python3 FULL_CODE/cli.py
```

### C. Run the Official Evaluator
```bash
# Score submission against ground truth:
python3 H5_Frontline_Health_Worker_Companion/evaluate.py \
  --submission submission \
  --truth H5_Frontline_Health_Worker_Companion/data/dev_truth.jsonl \
  --out score_dev.json
```

### D. Native Android App (Build, Test & Install)
```bash
cd android_app

# Run 24 Kotlin unit tests:
./gradlew testDebugUnitTest

# Build standalone debug APK:
./gradlew assembleDebug

# Install and launch on connected Android device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.arogyasathi.app/.MainActivity
```

---

## 4. Key Android Architecture Highlights

### A. Floating Capsule Navigation Dock
- **Stadium Capsule Bar:** Clean floating pill dock (`RoundedCornerShape(percent = 50)`) with ambient drop shadow.
- **Gliding Spring Pill:** Morphing background indicator with Framer Motion spring physics (`dampingRatio = 0.78f`, `stiffness = 380f`) that glides effortlessly between tabs.
- **Tactile Micro-Interactions:** Subtle icon bounce scale (`1.08f`) and dark notification pill counter badges.

### B. Dynamic Database-Derived Information (Zero Hardcoding)
- **Profile & Credential Sourcing:** Worker identity (`name`, `phone`, `sub_centre`, `sub_centre_id`, `parent_phc`, `mo_name`, `mo_phone`, `anm_name`, `anm_phone`) is queried directly from SQLite (`ashas` table) and Room DAOs.
- **Dynamic Village Coverage:** Coverage areas are computed via `SELECT DISTINCT village FROM households`, automatically adapting when households change without static in-memory lists.
- **Sub-Centre ID Integration:** Explicit Sub-Centre IDs (e.g. `SC-BWT-101`, `SC-AMP-102`) mapped and displayed across Profile Hero cards, Facility Supervision hierarchies, and Settings.
- **Pixel-Perfect 50/50 Symmetry:** Quick info cards feature balanced column weights (`weight(1f)`), full-height intrinsic dividers (`IntrinsicSize.Min`), and vertically aligned baselines.

---

## 5. Repository Structure

```text
Arogyasathi/
├── android_app/                          # Native offline Android application (Kotlin + Jetpack Compose)
│   ├── app/src/main/assets/              # Bundled offline assets (protocols.json, households.csv)
│   ├── app/src/main/kotlin/org/arogyasathi/app/
│   │   ├── core/Models.kt                # Domain models: Member, Household, ProtocolSection, AshaProfile, VisitItem
│   │   ├── jev/JEV.kt                    # JEV deterministic danger escalation & scope refusal in Kotlin
│   │   ├── laya/Laya.kt                  # Laya BM25 retrieval & multilingual synthesis in Kotlin
│   │   ├── visit/VisitEngine.kt          # Chandrapuri Block scheduler in Kotlin (all 09_VIS rules)
│   │   ├── sync/NeonPostgresSyncClient.kt# Central Neon Postgres cloud synchronization client
│   │   ├── data/
│   │   │   ├── ArogyasathiDbHelper.kt    # SQLite database helper (seed, query, dynamic profiles & villages)
│   │   │   ├── LocalRepository.kt        # Repository coordinating queries, outcomes, and sync outbox
│   │   │   └── local/ArogyasathiRoomDatabase.kt # Room database DAOs & entity mappers
│   │   └── ui/
│   │       ├── Theme.kt                  # Clinical teal & slate color tokens
│   │       ├── BottomNavigation.kt       # Floating capsule bottom navbar with spring sliding indicator
│   │       ├── TodayScreen.kt            # Tab 0: Daily visits, priority filters, outcome completion
│   │       ├── FamiliesScreen.kt         # Tab 1: Household register, village chips, expandable member cards
│   │       ├── AssistantScreen.kt        # Tab 2: 108 emergency dialer, clinical Q&A, STT / TTS voice consultation
│   │       ├── RecordsScreen.kt          # Tab 3: Outcomes history, sync outbox, protocol browser
│   │       ├── SettingsScreen.kt         # Tab 4: Language picker, offline voice packs, profile switcher
│   │       ├── AshaProfileScreen.kt      # Slide-out ASHA Profile & clinical hierarchy supervision card
│   │       └── MainScreen.kt             # Root navigation container
│   ├── app/src/test/kotlin/              # 24 automated Android unit tests
│   └── app/build/outputs/apk/debug/      # app-debug.apk
├── docs/                                 # Audited technical documentation & architecture records
├── FULL_CODE/                            # Production Python core engine (Evaluator Score: 0.9735)
│   ├── arogyasathi/                      # Python JEV, Laya, VisitEngine, Protocols, Edge Gateway
│   ├── tests/                            # 29 automated test suites (pytest)
│   ├── cli.py                            # Interactive console & judge simulation
│   └── run_pipeline.py                   # Submission generator pipeline
├── submission/                           # Scored test submission artifacts
│   ├── H5_answers.jsonl                  # 200 answers mapped 1:1 to test questions
│   ├── H5_visit_list.csv                 # 346 prioritized Chandrapuri visits
│   └── H5_footprint.json                 # Benchmark footprint metadata
├── pytest.ini                            # Pytest configuration
├── README.md                             # Project overview and technical specification
└── .gitignore                            # Clean repo ignore rules
```

---

## 6. Architectural Preservation Note

This implementation strictly preserves:
- **JEV:** Judicial Evidence Validator as the hard-coded safety and scope governance kernel.
- **Laya:** Multilingual edge language adaptation and retrieval engine.
- **3-Mode Operating Topology:** Mode 1 (Offline On-Device), Mode 2 (Online Server), Mode 3 (Twilio IVR Call).
- **4-Tier RBAC:** Frontline ASHA, Auxiliary Nurse Midwife (ANM), Medical Officer (MO), System Administrator.
