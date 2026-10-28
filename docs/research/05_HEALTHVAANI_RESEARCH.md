# Reference Research: HealthVaani (Wadhwani AI)

> **Document ID:** 05_HEALTHVAANI_RESEARCH  
> **Source:** Wadhwani Institute for Artificial Intelligence / Google AI for Social Good  
> **Type:** Production Voice-First AI Companion for Frontline Public Health Workers  
> **Date Examined:** 2026-10-29  
> **Status:** Completed & Audited

---

## 1. Source Identity and Research Scope

- **Canonical Identity:** HealthVaani (developed by Wadhwani AI in collaboration with MoHFW and Google).
- **Target Audience:** ASHA (Accredited Social Health Activists) and Anganwadi (AWW) workers in rural India.
- **Focus:** Voice-first interaction design, multilingual audio synthesis, regional dialect handling, and frontline worker user experience.

---

## 2. Technical and Operational Findings

### 2.1 Voice-First Interaction Paradigm
1. **The Literacy & Usability Bottleneck:** HealthVaani recognizes that rural frontline workers, while literate, find typing long medical queries or navigating deep digital menus slow and error-prone during field visits.
2. **Audio-In / Audio-Out Architecture:**
   - Workers tap a single microphone button, speak in their natural language or local dialect (Hindi, Hinglish, Marathi, Odia, Telugu), and receive both a concise text card and a clear, spoken audio answer.
   - Answers are distilled into short, actionable bullet points rather than lengthy paragraphs, respecting the worker’s cognitive load while standing on a beneficiary's doorstep.

### 2.2 Model and Pipeline Architecture
- **Speech-to-Text (STT):** Leverages specialized fine-tuned multilingual ASR models capable of transcribing code-mixed Indian languages with medical domain terms (*Hb*, *Penta*, *BP*, *sujan*, *chhaati dhasna*).
- **Knowledge Core:** Trained/grounded on official GoI manuals (NHM ASHA modules, IMNCI guidelines, RBSK protocols).
- **Text-to-Speech (TTS):** Generates naturalistic regional audio playback to verify comprehension.

---

## 3. Strengths of HealthVaani

1. **Unmatched Field Usability:** Eliminates the keyboard as a barrier; high adoption among older ASHA workers who struggle with standard touchscreen keyboards.
2. **Dialect Resilience:** Gracefully parses phonetically mixed phrases that combine Hindi grammatical structures with English medical acronyms (*"3rd ANC checkup kab karwana hai?"*).
3. **Audio Grounding:** Audio playback serves as an educational tool for the beneficiary family, allowing the ASHA to play the authoritative advice aloud.

---

## 4. Limitations in Low-Connectivity Settings

1. **Network Infrastructure Requirements:** In its production deployment, HealthVaani relies heavily on **cloud speech and LLM endpoints** (Google Gemini and cloud speech APIs). If an ASHA loses cellular signal in a remote village, speech transcription and generative audio response fail.
2. **Latency Constraints:** Cloud roundtrips over flaky 2G connections introduce 4 to 12 second latencies, leading workers to believe the app has frozen.
3. **No Offline Longitudinal Register:** HealthVaani functions primarily as an informational companion; it does not maintain an offline household register or generate daily prioritized visit schedules.

---

## 5. Relevance & Strategic Takeaways for Arogyasathi

### Key Innovations to Adopt (Especially for STRETCH-001)
- **Voice-In / Audio-Out UI Pattern**: For Arogyasathi's Android interface, providing a prominent tap-to-speak button with animated recording feedback dramatically improves worker experience.
- **Audio Answers**: Generating localized audio playback of the protocol answer allows workers to listen while checking vitals.
- **On-Device Speech Alternative**: To satisfy Arogyasathi's strict offline requirement, speech recognition cannot use cloud APIs; it must utilize the pinned `whisper-tiny` model (~151 MB safetensors) running via ONNX/TFLite, or Android's on-device `SpeechRecognizer` offline packs.

### Architectural Decision Records Influenced
- **ADR-005**: Arogyasathi will adopt HealthVaani’s high-contrast, voice-first single-card UI design, but will power it through a **100% on-device pipeline** (local Whisper-tiny / local GGUF) to guarantee zero-signal availability.
