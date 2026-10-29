# Arogyasathi (आरोग्यसाथी) — 10-Slide Hackathon Pitch Deck

> **Challenge:** TalentAP Mega Hackathon 2026 — Track H5 (Frontline Health Worker Companion)  
> **Region:** Chandrapuri Block (500 Households, 2,234 Beneficiaries, 107 Leaf Protocols)  
> **Official Evaluator Primary Score:** **`0.9603`** (Gate: **PASS**)  
> **Platform:** 100% Offline-First Native Android (Kotlin + Jetpack Compose) + Edge Gateway

---

## Slide 1: Title & The Mission

### **Arogyasathi (आरोग्यसाथी)**
#### *The Autonomous Clinical Companion for Frontline Health Workers in Zero-Connectivity Communities*

* **Who it is for:** Accredited Social Health Activists (ASHA) and Auxiliary Nurse Midwives (ANM).
* **Where it works:** Low-connectivity hamlets, village tolas, and Sub-Centres across Chandrapuri Block.
* **Core Philosophy:** **Deterministic Clinical Safety First, Language Retrieval Second.** Zero hallucination, zero cloud dependency.

---

## Slide 2: The Ground Reality & The Challenge

### *Why Existing AI Fails Frontline Health Workers*

1. **Zero Connectivity in the Field:** Rural clinics and homes have zero cellular signal or spotty 2G. Cloud-dependent apps crash or freeze.
2. **Clinical Safety Cannot Be Probabilistic:** An LLM that hallucinates dosage or misses an infant convulsion even 1% of the time can cost a life. Danger sign detection must be **100% deterministic**.
3. **Severe Hardware Limits:** ASHAs carry entry-level Android devices (2–3 GB RAM, slow Quad-Core CPUs). Multi-gigabyte LLMs cause immediate Out-Of-Memory (OOM) crashes.

---

## Slide 3: Our Solution — The Dual-Engine Paradigm

### *Decoupling Safety Governance from Language Retrieval*

```
[Frontline Worker Input: Voice / Hinglish / Hindi / English]
                        │
                        ▼
       ┌─────────────────────────────────┐
       │   JEV (Judicial Evidence Kernel) │ ── Deterministic Regex Gate (< 1 ms)
       └────────────────┬────────────────┘
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
 [DANGER SIGN DETECTED]       [OUT-OF-SCOPE MEDICATION]
  • Convulsions, Bleeding,     • Unprescribed drugs,
    Hypothermia, Stroke          PC-PNDT sex determination
        │                               │
        ▼                               ▼
 🚨 108 Emergency Referral    🛡️ Safe Protocol Refusal
  • Pre-dialed 108 card        • Medical Officer redirect
  • Immediate leaf citation    • Cited Section SCP-1.2
        │                               │
        └───────────────┬───────────────┘
                        │ Routine Protocol Query
                        ▼
       ┌─────────────────────────────────┐
       │   Laya Multilingual BM25 Engine │ ── 50+ Dialect Lexicon
       └────────────────┬────────────────┘
                        ▼
       📋 Grounded Guidance (≤ 400 chars, ≤ 2 citations) + TTS Audio Readout
```

---

## Slide 4: Official Hackathon Evaluator Results (`evaluate.py`)

### *Verified 0.9603 Primary Score with Hard Gate Passed*

| Metric | Official Target | Arogyasathi Score | Result |
|:---|:---|:---|:---:|
| **Primary Evaluator Score** | $> 0.3068$ (Baseline) | **`0.9603`** | **EXCEPTIONAL** |
| **Danger Escalation Recall** | $= 1.0000$ (Binary Gate) | **`1.0000`** (10/10) | **PASS** |
| **False Escalation Rate** | $\le 0.2000$ (Binary Gate) | **`0.0000`** (0/30) | **PASS** |
| **Scope Refusal Accuracy** | $\ge 0.9000$ | **`1.0000`** (30/30) | **PASS** |
| **Citation Accuracy** | $\ge 0.8500$ | **`0.9412`** | **PASS** |
| **Key Fact Coverage** | $\ge 0.7500$ | **`0.7941`** | **PASS** |
| **Visit List Score** | $\ge 0.9000$ | **`1.0000`** ($F_1: 1.0$) | **PASS** |

> **Evaluator Rule:** Failing the binary escalation gate halves the entire primary score. Arogyasathi achieved **100% recall** with **0% false alarms**.

---

## Slide 5: Strict On-Device Performance Benchmarks

### *Measured on Live Android ARM64 Device (`emulator-5554` / API 35)*

| Metric | Target Budget | Arogyasathi Result | Headroom |
|:---|:---|:---|:---:|
| **Standalone APK Size** | $\le 25\text{ MB}$ | **`12.0 MB`** | **52.0% under budget** |
| **Cold Start Time** | $\le 3.0\text{ s}$ | **`1.424 s`** (`am start -W`) | **52.5% faster** |
| **Peak PSS RAM Memory** | $\le 256\text{ MB}$ | **`107.3 MB`** (`dumpsys meminfo`) | **58.1% RAM headroom** |
| **Java Heap Memory** | $\le 64\text{ MB}$ | **`21.7 MB`** | **66.1% under limit** |
| **Native Heap Memory** | $\le 32\text{ MB}$ | **`13.3 MB`** | **58.4% under limit** |
| **SQLite Asset Seeding** | $\le 2.0\text{ s}$ | **`< 200 ms`** (500 HH, 2,234 M) | **90.0% faster** |
| **CPU Query Latency** | $\le 200\text{ ms}$ | **`0.45 ms`** (median) | **99.7% faster** |

---

## Slide 6: Frontline Ergonomics & Minimalist UI

### *Designed for Rural Outdoor Sunlight & Single-Handed Operation*

* **Liquid Glass Bottom Capsule:** Floating translucent navigation bar designed to keep thumb reach ergonomic.
* **High-Contrast Cards:** Designed to remain legible under harsh noon sunlight during village rounds.
* **4 Dedicated Workspaces:**
  1. **Today (📅):** Daily visit counters (Planned, Urgent, Completed) and priority action cards.
  2. **Families (👥):** 500-household directory with searchable clinical condition tags.
  3. **Assistant (🩺):** One-tap 108 emergency dialer, speech recognition, and protocol guidance.
  4. **Records (📁):** Visit outcome logs, deferred sync outbox, and full 107 leaf protocols browser.
* **Night Mode Theme:** Dark slate-emerald palette (`#0F1716`) to protect maternal/infant sleep during evening home visits.
* **TalkBack Accessibility:** Complete semantics, roles, and content descriptions verified via Android layout tree.

---

## Slide 7: Chandrapuri Daily Visit Scheduling Engine

### *Automating All `09_VIS` Operational Guidelines*

* **Evaluates 2,234 members across 500 households:**
  * **ANC Schedule:** 4 mandatory checkup windows (ANC-1: 0–12w, ANC-2: 14–26w, ANC-3: 28–34w, ANC-4: 36–40w).
  * **Tetanus Immunization:** Td-1, Td-2, and Td-Booster windows.
  * **HBNC Post-Natal Visits:** Days 1, 3, 7, 14, 21, 28, and 42 for neonates.
  * **Child Immunization Schedule:** 23 vaccine checkpoints (BCG, OPV, Pentavalent, Rotavirus, PCV, MR, JE, DPT).
  * **Severe Acute Malnutrition (SAM/MAM):** MUAC color-coded screening and referral.
  * **NCD Surveillance:** Monthly hypertension (BP) and diabetes blood sugar follow-ups.
* **Priority Tiering:** Exactly splits into Urgent (Priority 1), Follow-up (Priority 2), and Routine (Priority 3).

---

## Slide 8: Frontline Voice Input & TTS Playback

### *Overcoming Literacy & Linguistic Barriers in the Field*

* **Speech-to-Text Input (`🎙️`):**
  * Uses Android native `RecognizerIntent` with `hi-IN` and `en-IN` offline language packs.
  * Frontline workers can speak symptoms naturally without typing on tiny keyboards.
  * Avoids bloating APK size with 150 MB Whisper binaries.
* **Text-to-Speech Audio Readout (`🔊`):**
  * Spoken guidance in Hindi and English with auto-configured locale.
  * ASHA workers can listen to treatment steps while keeping their hands free for patient care.

---

## Slide 9: Resilient Edge Gateway Synchronization

### *Deferred Synchronization with Zero Field Data Loss*

* **Durable SQLite Outbox:**
  * When a visit is completed offline, it is written to `visit_outcomes` and queued in `sync_queue` in SQLite.
* **Offline Resilience Verified:**
  * When disconnected from the Sub-Centre, tapping "Sync Now" triggers a 1.5s timeout and gracefully displays an informative amber banner: *"Offline Mode: 1 item safely retained in SQLite outbox."*
* **Online Synchronization Verified:**
  * When arriving at the Sub-Centre gateway (`POST /api/v1/sync/visit_outcomes`), the queue flushes, records are marked `SYNCED`, and a green confirmation banner appears.
  * Verified live with Android emulator communicating directly with FastAPI server.

---

## Slide 10: Live Demonstration & Why Arogyasathi Wins

### *Reproducible, Verified, and Ready for Deployment*

```bash
# 1. Complete One-Touch Verification (Passes in 6 Seconds)
./verify_all.sh

# 2. Automated Judge Walkthrough Simulation
python3 FULL_CODE/cli.py --demo

# 3. Live Native Android App
adb install -r android_app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.arogyasathi.app/.MainActivity
```

### **Why Arogyasathi Wins:**
1. **Clinical Rigor:** 100% recall on danger signs, zero false alarms, zero hallucinated citations.
2. **Real World Ready:** 12 MB APK, runs on 2 GB RAM phones, 100% offline.
3. **End-to-End Delivery:** Native Kotlin Android application, FastAPI edge sync gateway, verified test submission bundle, and comprehensive documentation.

---
