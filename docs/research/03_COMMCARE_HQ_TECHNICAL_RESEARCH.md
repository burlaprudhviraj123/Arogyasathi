# Technical Research: CommCare HQ & Android Client Architecture

> **Document ID:** 03_COMMCARE_HQ_TECHNICAL_RESEARCH  
> **Source:** GitHub (`dimagi/commcare-android`, `dimagi/commcare-core`, `dimagi/commcare-hq`)  
> **Type:** Technical Source Code Audit / Architecture Decomposition  
> **Date Examined:** 2026-10-29  
> **Status:** Completed & Audited

---

## 1. Technical Repositories Examined

1. **`commcare-android`**: Native Android client application (Kotlin/Java, Android SDK).
2. **`commcare-core`**: Shared platform engine implementing the JavaRosa XForm evaluator, CaseXML state engine, and platform-independent business logic.
3. **`commcare-hq`**: Server-side system of record (Python, Django, PostgreSQL, CouchDB/Elasticsearch).

---

## 2. Technical Component Boundaries & Data Protocols

### 2.1 The CaseXML Transaction Protocol
CommCare abstracts all beneficiary operations into atomic **CaseXML** transaction blocks:
- `<create>`: Instantiates a new case GUID with initial case-type and owner ID.
- `<update>`: Modifies specific attributes (e.g. `last_hb_gdl = 8.6`, `last_bp = 140/90`).
- `<index>`: Establishes directed parent-child edges (e.g. linking newborn case `M-CHILD` to mother case `M-MOTHER`).
- `<close>`: Terminates active tracking (e.g. delivery completed, migrated out of village).

**Key Takeaway for Arogyasathi:** Treating patient actions as append-only transaction records ensures that offline edits are deterministic and replayable.

### 2.2 Local Storage Architecture (Android)
- **Engine:** SQLite with optional **SQLCipher** for full database encryption at rest.
- **Storage Isolation:** User databases are partitioned per authenticated worker.
- **Indexing:** Heavily indexes `case_id`, `case_type`, `owner_id`, and status dates to allow sub-50ms query responses across thousands of cases on low-end hardware.

### 2.3 The Sync Token & Delta Restore Protocol
When CommCare reconnects to the network:
1. **Upward Sync (Submissions):** The mobile outbox streams completed XML forms and case updates to the endpoint. The server commits these into PostgreSQL/CouchDB.
2. **Sync Token Handshake:** The server returns a newly generated `sync_token` representing the authoritative state of the user’s universe.
3. **Downward Sync (Restores):** The device requests updates since its last known `sync_token`. The server computes a minimal XML diff (delta) containing only modified cases and reference tables (fixtures).
4. **Idempotency:** Transactions are keyed by client-generated UUIDs; duplicated transmissions due to dropping 2G connections are automatically deduplicated server-side.

---

## 3. Performance & Memory Profiling Observations

- **APK Size:** The `commcare-android` APK typically measures between 15 MB and 30 MB (without ML weights), heavily optimized with ProGuard/R8.
- **Memory Footprint:** The JavaRosa form engine operates strictly within a 32 MB – 64 MB heap budget, making it functional even on ancient Android devices with 512 MB – 1 GB total RAM.
- **CPU Latency:** Form validation and rule execution occur in $<10\text{ ms}$ on mobile ARM processors because rules are compiled bytecode/expression trees rather than neural network operations.

---

## 4. Strengths & Engineering Excellence to Mirror in Arogyasathi

1. **Transaction-Safe Outbox Pattern:**
   - In Arogyasathi’s STRETCH-002 (sync queue), network requests must never be executed synchronously on the UI thread or directly inside form submit handlers.
   - All visit completions and question logs must be committed to an internal SQLite `sync_queue` table with states: `PENDING`, `IN_TRANSIT`, `ACKNOWLEDGED`, `FAILED`.
   - Android’s `WorkManager` must be used to schedule opportunistic background upload when `NetworkCapabilities.NET_CAPABILITY_INTERNET` is satisfied.
2. **Defensive Data Types:** CommCare treats all external dates as ISO-8601 (`YYYY-MM-DD`) and gracefully handles empty strings, non-numeric strings, and placeholder readings (e.g. `0/0` BP readings)—exactly matching the edge cases documented in `09_VIS_daily_visit_list_rules.md`.

---

## 5. Architectural Differences & Additions for Arogyasathi

| Dimension | CommCare Architecture | Arogyasathi Architecture |
|---|---|---|
| **Primary Interaction** | Form wizard & case list | **Conversational QA (Voice/Text) + Prioritized Daily Visit List** |
| **Knowledge Engine** | Hardcoded XForm logic in app XML | **JEV Deterministic Gate + Laya Edge LLM (Qwen2.5-0.5B)** |
| **Inference Hardware** | Standard Java bytecode on Dalvik/ART | **Native C++ / NDK inference via llama.cpp on mobile CPU** |
| **Model Footprint** | 0 MB (No ML models) | **~491 MB GGUF LLM + 384-dim Embeddings** |
| **Safety Assurance** | Form constraint validators | **JEV-Escalate (100% recall danger gate) + JEV-Verify** |

---

## 6. Architectural Decision Records Influenced by this Research

- **ADR-003**: Arogyasathi will implement an SQLite-backed transactional Outbox (`sync_queue`) managed by Android `WorkManager` for asynchronous, resilient two-way synchronization.
- **ADR-004**: The household visit engine will run purely inside local SQLite using optimized SQL queries and Kotlin domain logic, keeping latency under 100ms for 2,200+ members.
