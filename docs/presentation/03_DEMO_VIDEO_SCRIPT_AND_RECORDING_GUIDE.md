# Arogyasathi (आरोग्यसाथी) — 3-Minute Video Demo Script & Recording Guide

> **Target:** Video submission for TalentAP Mega Hackathon 2026 (Track H5)  
> **Duration:** Exactly 3 Minutes (180 Seconds)  
> **Narrator Style:** Clear, professional, passionate, and technically grounded.  
> **Languages:** Dual-track (English primary with colloquial Hindi patient scenarios).

---

## Recording Checklist Before Starting

1. **Terminal 1:** Ready with `./verify_all.sh` command typed.
2. **Terminal 2:** Ready with `python3 FULL_CODE/cli.py --demo` command typed.
3. **Android Device / Emulator:** Open on screen with Arogyasathi running.
4. **Browser:** Open `web_demo.html` in full screen.
5. **Screen Recording Tool:** OBS Studio, QuickTime, or Loom capturing screen at 1080p 60fps with clear microphone audio.

---

## Detailed Second-by-Second Video Timeline

### Scene 1: The Problem & Field Reality (0:00 – 0:30)

| Timestamp | Visual Cue (On Screen) | Spoken Narration Script |
|:---|:---|:---|
| **0:00 – 0:12** | Title slide from `JUDGES_SLIDE_DECK.md` or opening shot of rural ASHA worker in Chandrapuri Block. | *"In rural India, frontline health workers like ASHAs and ANMs are the only lifeline for hundreds of families. But in remote hamlets, there is zero 4G connectivity, phones have only 2GB of RAM, and clinical decisions are life or death."* |
| **0:12 – 0:22** | Show generic LLM failure comparison diagram or bullet points highlighting hallucinations and high latency. | *"Generic cloud AI fails here: it crashes without internet, hallucinates drug dosages, and misses subtle danger signs."* |
| **0:22 – 0:30** | Cut to the Android emulator showing Arogyasathi’s high-contrast Liquid Glass UI. | *"Meet Arogyasathi (आरोग्यसाथी)—a 100% offline-first clinical companion engineered specifically for low-end Android hardware with deterministic safety governance."* |

---

### Scene 2: The Core Innovation — JEV & Laya Dual Engine (0:30 – 1:15)

| Timestamp | Visual Cue (On Screen) | Spoken Narration Script |
|:---|:---|:---|
| **0:30 – 0:45** | Switch to the **Assistant Tab** on the Android app. Type or speak: *"pregnancy me bleeding ho rahi hai"*. Instant red emergency card pops up. | *"Instead of relying on an unpredictable language model, Arogyasathi uses a dual-engine paradigm. Stage 1 is JEV—the Judicial Evidence Validator. The moment a critical danger sign appears—like vaginal bleeding in pregnancy—JEV triggers a deterministic emergency protocol in under 1 millisecond."* |
| **0:45 – 0:55** | Tap the **"CALL 108"** button showing telephony pre-dialer. Tap **"🔊 Listen"** to play the TTS Hindi audio. | *"It cites leaf protocol ESC-2.1, displays immediate maternal positioning instructions, pre-dials the 108 ambulance, and speaks guidance aloud in Hindi for workers in loud environments."* |
| **0:55 – 1:05** | Type an out-of-scope query: *"2 saal ke bachche ko kaun sa paracetamol syrup du"*. Amber card appears with `SCP-1.2`. | *"Safety also means knowing when NOT to act. When asked for unprescribed medication dosages, JEV safely refuses under protocol SCP-1.2 and redirects the family to the Medical Officer."* |
| **1:05 – 1:15** | Type a routine question: *"dast me kitna zinc dena hai"*. Grounded card displays with citation `CHD-3.2`. | *"For routine care, our Laya retrieval engine searches 107 leaf protocols via BM25, returning exact MoHFW guidance strictly under 400 characters."* |

---

### Scene 3: Chandrapuri Household Register & Prioritized Visits (1:15 – 1:55)

| Timestamp | Visual Cue (On Screen) | Spoken Narration Script |
|:---|:---|:---|
| **1:15 – 1:30** | Switch to the **Today Tab**. Point out the metric counters: **454 Planned**, **29 Urgent**, **1 Completed**. Filter by village *"Belwadi Tola"*. | *"Arogyasathi isn't just an assistant—it's an operational powerhouse. On first launch, it seeds 500 households and 2,234 beneficiaries into local SQLite in under 200 milliseconds."* |
| **1:30 – 1:45** | Scroll through the prioritized cards. Point out Urgent badges for overdue immunization and high-risk pregnancies. Tap **"Complete"** on a visit card, select "Completed", and tap "Save to Outbox". | *"Implementing all official 09_VIS rules, it prioritizes visits into Urgent, Follow-up, and Routine. An ASHA worker can tap Complete, add field notes, and queue the record into a durable SQLite outbox."* |
| **1:45 – 1:55** | Switch to the **Families Tab**. Search for a family name or village; show expandable beneficiary details. | *"The Families tab lets workers browse all 500 households with color-coded health badges for malnutrition, severe anaemia, and NCDs."* |

---

### Scene 4: Resilient Edge Sync & Offline Durability (1:55 – 2:25)

| Timestamp | Visual Cue (On Screen) | Spoken Narration Script |
|:---|:---|:---|
| **1:55 – 2:10** | Switch to the **Records Tab**. Show the **Sync Outbox** with 1 pending item. Tap **"Sync Now"** while server is disconnected: amber offline banner appears. | *"Connectivity in the field is zero. When the ASHA taps Sync Now in a remote village, Arogyasathi detects the offline state and safely retains records in the durable SQLite queue with zero data loss."* |
| **2:10 – 2:25** | Connect to the Sub-Centre edge gateway. Tap **"Sync Now"**: status flips to green `SYNCED` with checkmark. | *"When she returns to the Sub-Centre, tapping Sync flushes the outbox to our FastAPI gateway with instant confirmation. Offline resilience is built into every layer."* |

---

### Scene 5: Evaluator Score & Proof of Performance (2:25 – 3:00)

| Timestamp | Visual Cue (On Screen) | Spoken Narration Script |
|:---|:---|:---|
| **2:25 – 2:40** | Switch to terminal and run `./verify_all.sh`. Show live terminal output passing in 6 seconds. | *"We don't make unsubstantiated claims. Running our unified verification pipeline executes 16 Python tests, 14 Android Kotlin tests, and the official competition evaluator in 6 seconds."* |
| **2:40 – 2:50** | Zoom in on the evaluator scorecard: **`0.9603 Primary Score`**, **`ESCALATION GATE: PASS`**, **`Recall: 1.0000`**, **`FER: 0.0000`**. | *"Arogyasathi achieves an exceptional 0.9603 primary score on the official evaluator, achieving 100% recall on danger signs and zero false alarms."* |
| **2:50 – 3:00** | Flash the benchmark slide: **12 MB APK**, **107 MB RAM**, **1.4s Cold Start**. Show GitHub repo and conclusion slide. | *"With a 12 MB APK, sub-millisecond CPU latency, and zero cloud reliance, Arogyasathi is production-ready to protect mothers and children across rural India. Thank you."* |

---
