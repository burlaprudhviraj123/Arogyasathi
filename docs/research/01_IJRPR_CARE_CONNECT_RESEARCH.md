# Reference Research: AI-Powered Companion for ASHA Worker (Care Connect)

> **Document ID:** 01_IJRPR_CARE_CONNECT_RESEARCH  
> **Source:** International Journal of Research Publication and Reviews (IJRPR), Vol 7, Issue 5, pp 3951–3957, May 2026  
> **DOI:** https://doi.org/10.55248/gengpi.07.0526.a1211  
> **Authors:** V. Biradar, V. Borse, A. Birajdar, Dr. M. Kshirsagar (JSPM University, Pune)  
> **Status:** Completed & Audited

---

## 1. Source Identity and Research Scope

- **Canonical Document:** `IJRPR64863.pdf` located in project root.
- **Title:** *AI-Powered Companion for Asha Worker* (Project Name: *Care Connect*).
- **Research Scope:** Comprehensive audit of the Care Connect full-stack mHealth architecture, offline storage strategy, multilingual voice input, rule-based risk classification, and AI assistant integration to extract lessons for Arogyasathi.

---

## 2. Source-Derived Facts

1. **Target User Context:** Over 1 million ASHA workers in India face sporadic 2G/zero connectivity in rural field visits, language barriers (English-heavy apps fail), and a lack of real-time clinical decision support.
2. **Field Realities:** In Madhya Pradesh, 61% of ASHAs reported connectivity failures during at least half of weekly home visits (Sharma et al., 2020); 34% of home visits in low-resource deployments occur in zero connectivity (Fraser et al., 2019).
3. **Voice UI Impact:** Field studies (Acharya et al., 2019) demonstrated comprehension was 68% greater in mother tongue vs. Hindi, and 91% greater vs. English. Voice navigation dramatically reduces data entry latency for semi-literate users.
4. **Care Connect Tech Stack:**
   - **Frontend:** React 18 + Vite (mobile-first web app / PWA-ready), Tailwind CSS, Framer Motion, Recharts.
   - **Backend:** Node.js with Express 5, PostgreSQL with Drizzle ORM, OpenAPI 3.1 with Orval.
   - **Voice Input:** Web Speech API (`SpeechRecognition`) in Chrome on Android for 6 languages (en-IN, hi-IN, mr-IN, ta-IN, te-IN, kn-IN).
   - **Offline Storage:** Browser `localStorage` using key `'offline patients'` and `navigator.onLine` listener.
   - **AI Model:** Cloud-hosted OpenAI GPT-5.2 integration via API.

---

## 3. Implemented vs. Planned Functionality in Care Connect

| Feature | Paper Status | Verification & Critical Assessment |
|---|---|---|
| **Patient Registration & Form Input** | Implemented | Form-based entry for Name, Age, Village, Hb, BP, Pregnancy status, Symptoms. |
| **Rule-Based Risk Classification** | Implemented | Hardcoded thresholds: Hb <8 (High), Hb 8-10 (Med), Pregnant+Hb <9 (High), BP >140/90 (High), BP 130-140/85-90 (Med). |
| **UIP Vaccine & ANC Reminders** | Implemented | Schedules standard UIP vaccines (BCG, OPV, Penta, RVV, fIPV, MR, DPT) and 4 ANC visits. |
| **Offline Synchronization Queue** | Implemented (Proof-of-Concept) | Saves records to `localStorage` when offline; loops and POSTs sequentially when online. |
| **Multilingual Voice-to-Field** | Implemented (Web Speech API) | Dictates into specific form fields; global voice input parses single spoken string. |
| **AI Decision Support** | Implemented via Cloud API | Calls OpenAI GPT-5.2 with culturally aware system prompt. **Fails completely offline.** |
| **ABHA ID / Biometrics / SMS Alerts** | Planned (Future Work) | Not implemented. |

---

## 4. Strengths of Care Connect

1. **Context-Driven Human Factors:** Clear recognition that rural frontline health workers cannot use text-heavy, English-first, desktop-style EHR interfaces.
2. **Deterministic Risk Stratification:** Risk levels (High/Medium/Normal) are computed via explicit rules rather than being delegated to an unpredictable language model.
3. **Transparency in Offline Status:** Provides an explicit offline sync counter showing pending records to build worker trust.

---

## 5. Critical Limitations & Flaws for Low-Connectivity Environments

1. **Fatal Offline AI Flaw:** The "AI Health Assistant" relies on OpenAI GPT-5.2 cloud API. In a zero-signal village, the assistant is entirely unavailable—violating Arogyasathi’s core requirement of 100% offline edge QA.
2. **Fragile Offline Storage:** Browser `localStorage` is synchronous, capped at ~5MB, vulnerable to browser cache eviction, and incapable of relational joins required for complex visit scheduling across 500 households (2,234 members).
3. **Unvalidated Citations:** Care Connect's GPT assistant does not ground its output in a specific, verified protocol corpus and does not output verifiable leaf-section citations.
4. **Web Speech API Dependency:** Chrome's implementation of Web Speech API on many Android devices defaults to Google server-side speech recognition, failing when mobile data is disabled.
5. **Lack of Strict Danger-Sign Gate:** Care Connect relies on gentle UI badges rather than an uncompromising, binary danger-sign referral engine (e.g. 1008/102 escalation with first-aid instructions).

---

## 6. Relevance & Actionable Decisions for Arogyasathi

### Features to Adopt
- **Rule-based clinical risk stratification**: Reinforces the design of **JEV-Escalate** for deterministic vital sign checks (BP $\ge 140/90$, Hb $<7$ g/dL).
- **Sync counter UX**: Displaying clear pending queue indicators for worker confidence.
- **Multilingual voice dictation concept**: High relevance for Arogyasathi’s STRETCH-001 goal, but using an offline on-device model (`whisper-tiny` or Android on-device RecognizerIntent) rather than cloud Web Speech.

### Architecture Ideas to Reject
- ❌ **Cloud LLM APIs (OpenAI)**: Must be rejected; Arogyasathi mandates on-device quantized models (`Qwen2.5-0.5B-GGUF` via CPU).
- ❌ **Browser `localStorage`**: Must be replaced with native Android **Room / SQLite** with transactional integrity.
- ❌ **Web-only PWA shell**: A native Android APK (Kotlin + Jetpack Compose) provides direct access to CPU cores, native memory management, and background sync workers.

---

## 7. Decision Implications for Arogyasathi

- **ADR-001**: Arogyasathi will implement a native Android application using SQLite/Room for local storage and llama.cpp for on-device GGUF execution, rejecting cloud-dependent PWA architectures.
- **ADR-002**: JEV will incorporate and expand Care Connect's risk thresholds into a formal, deterministic pre-inference gate covering all 107 leaf protocol rules.
