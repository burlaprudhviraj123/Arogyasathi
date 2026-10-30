# Arogyasathi Single-File Clinical Engine (`solution.py`)

Deliver a self-contained, zero-dependency, pure-Python implementation (`solution.py`) of the Arogyasathi clinical governance kernel (**JEV**), dual cross-dialect retrieval and extractive QA engine (**Laya**), and the **Chandrapuri Block Daily Visit Scheduler**, equipped with a comprehensive 4-part CLI demonstration suite and full compatibility with the official hackathon evaluator.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed during Phase 1 alignment and govern the implementation:

- **Confirmed Decision 1 (Data & Protocol Loading)**: Load the 107 official clinical protocol leaf sections and the 500-household register dynamically from `H5_Frontline_Health_Worker_Companion/data` (or `android_app/app/src/main/assets/protocols.json`), while bundling a comprehensive built-in fallback corpus so `solution.py` executes cleanly even if moved to an isolated directory.
- **Confirmed Decision 2 (CLI Execution Behavior)**: Running `python3 solution.py` with no arguments executes the 4-part clinical demonstration suite (Standard Protocol Queries, Emergency Danger Escalations, Benign Lookalikes, and Out-of-Scope Refusals) plus a Visit Scheduler verification summary. Passing `--generate-submission` or `--evaluate` generates the full submission artifacts (`H5_answers.jsonl`, `H5_visit_list.csv`, `H5_footprint.json`) and runs the official scoring verification.
- **Confirmed Decision 3 (Pure Standard Library)**: Implement all CSV parsing, BM25 ranking, regex safety gates, latency/memory profiling, and median calculations using 100% Python standard library modules (`re`, `math`, `csv`, `json`, `statistics`, `datetime`, `collections`, `resource`, `time`, `pathlib`, `argparse`) with zero external dependencies (`pandas` and `numpy` are not required).

---

## 1. Overview & Core Concept

- **What It Does**: `solution.py` serves as the standalone, auditable reference engine for frontline health workers (ASHAs and ANMs). It intercepts life-threatening maternal, neonatal, pediatric, and adult danger signs with 100% deterministic recall, refuses out-of-scope medical or prescription queries with safe redirection to the Primary Health Centre (PHC) Medical Officer, retrieves protocol-grounded clinical facts across English, Hindi (Devanagari & Hinglish), Marathi, Telugu, Tamil, Kannada, and Malayalam using BM25 + clinical lexicon expansion, and computes prioritized daily household visit lists.
- **Target Audience / Persona**: Frontline ASHA workers, ANM supervisors, and clinical safety auditors evaluating offline rural healthcare decision support systems.
- **Key Value**: Guarantees zero hallucinated medical advice by combining hard-coded emergency safety gates (`ESC-*`), strict scope boundaries (`SCP-1.2`), and direct extractive sentence selection strictly capped at $\le 400$ characters and $\le 2$ leaf-section citations per response.

---

## 2. User Experience & CLI Output Design

- **Key User Flows**:
  1. **Default Demonstration Mode (`python3 solution.py`)**:
     - Initializes the protocol corpus (107 leaf sections across 9 official NHM/ASHA modules) and builds the in-memory BM25 index with $3\times$ section ID and title weighting.
     - Executes the **4-Part Clinical Verification Suite**:
       - *Part 1: Standard Protocol Queries* (e.g., ORS & Zinc dosage for an 8-month infant with diarrhoea, ANC iron/folic acid schedule, newborn breastfeeding & cord care).
       - *Part 2: Danger Signs Requiring Emergency Escalation* (e.g., convulsions/`daura`/`jhatke`, chest indrawing/`pasli chalna`, inability to breastfeed/`doodh nahi pee raha`/`paalu thagadam ledu`, lethargy/`behosh`, sunken eyes/skin pinch $>2\text{s}$, pregnancy vaginal bleeding/`rakthasravam`, pre-eclampsia severe headache/blurred vision, newborn hypothermia/jaundice within 24h).
       - *Part 3: Benign Lookalikes That Must NOT Escalate* (e.g., mild fever after Pentavalent vaccination where infant feeds well, normal fetal kicks, routine immunization schedule queries, occasional vomiting when starting ORS while active).
       - *Part 4: Out-of-Scope Clinical Queries Requiring Medical Officer Refusal* (e.g., adult cancer treatment, bone fractures, dental surgery, antibiotic/paracetamol syrup dosing, psychotropic prescriptions, surgical sutures, fetal sex determination).
     - Executes the **Chandrapuri Block Visit Scheduler** on the household register (or built-in representative cohort) and prints summary counts by priority tier (Priority 1 High-Risk/Overdue, Priority 2 Routine Due Today, Priority 3 Upcoming/Monitoring) along with sample prioritized records.
  2. **Submission & Evaluation Mode (`python3 solution.py --generate-submission --split dev`)**:
     - Processes `questions_dev.csv` or `questions_test.csv`, writes `H5_answers.jsonl`, `H5_visit_list.csv`, and `H5_footprint.json`, and prints measured CPU latency and peak RSS memory.
- **Visual Formatting & Terminal Readability**:
  - Clean, structured ASCII tables and status badges (`[ESCALATE: TRUE]`, `[REFUSED: TRUE]`, `[IN-SCOPE QA]`, `[PASS]`) with explicit character count and citation count telemetry on every output row.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Deterministic Regex Kernel (JEV) Ahead of Retrieval**
  - *Chosen Approach*: Evaluate `JEVEscalate` and `JEVScope` (augmented with near-boundary benign exclusions and anatomical compatibility guards) strictly before BM25 retrieval.
  - *Why*: Keyword overlap alone confuses danger signs with routine care (e.g., "fever for 8 days" vs "fever after vaccine") and misroutes non-community anatomy (e.g., "ear bleeding" or "nosebleed" matching maternal vaginal bleeding `ESC-2.1`). Running deterministic multi-dialect pattern matching first guarantees $100\%$ escalation recall and $0.00\%$ false escalation rate.
  - *Alternatives Considered*: Pure BM25 classification or generative LLM prompting — rejected due to hallucination risk, non-deterministic outputs, and failure on the strict two-sided escalation gate.

- **Decision 2: Extractive Sentence Selection with Key-Fact Density Scoring (Laya)**
  - *Chosen Approach*: Split matched protocol leaf sections into atomic clinical statements, rank sentences by token and lexicon overlap with the query (boosting numeric dosages, age cutoffs, and duration units), and assemble the top 2–3 sentences with the leaf citation ID `(SEC-X.Y)` strictly truncated within 400 characters.
  - *Why*: Eliminates any possibility of fabricated dosages while maximizing key-fact coverage within the evaluator's 400-character window.
  - *Alternatives Considered*: Returning raw full sections (exceeds 400-character limit and dilutes early key facts) or abstractive summarization (violates zero-hallucination constraint).

- **Decision 3: Zero-Dependency Standard Library Implementation**
  - *Chosen Approach*: Replace `pandas` and `numpy` with Python's built-in `csv.DictReader`, `csv.writer`, `statistics.median`, `math.log`, and `collections.Counter`.
  - *Why*: Ensures sub-millisecond startup, minimal RAM footprint, and guaranteed portability across any Python 3 environment.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Arogyasathi Engine (solution.py)                     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
         ┌─────────────────────────────┴─────────────────────────────┐
         ▼                                                           ▼
┌─────────────────────────────────────────┐       ┌─────────────────────────────────────┐
│      1. Clinical QA Pipeline            │       │  2. Chandrapuri Visit Scheduler     │
│      (JEV + Laya Architecture)          │       │     (09_VIS Protocol Rules)         │
└────────────────────┬────────────────────┘       └──────────────────┬──────────────────┘
                     │                                               │
                     ▼                                               ▼
┌─────────────────────────────────────────┐       ┌─────────────────────────────────────┐
│ Step A: Near-Boundary Benign Guard      │       │ • Parse Household Register CSV      │
│ • Protects mild AEFI, active feeding,   │       │ • Fixed Reference Date: 2026-10-26  │
│   normal kicks from false escalation    │       │ • Evaluate High-Risk Flags (VIS-5.1)│
└────────────────────┬────────────────────┘       │   - Pregnant Hb<7.0 or BP>=140/90   │
                     │                            │   - Newborn <=42d, SAM MUAC<11.5cm  │
                     ▼                            │   - Uncontrolled HTN/DM BP>=140/90  │
┌─────────────────────────────────────────┐       ├─────────────────────────────────────┤
│ Step B: JEV-Escalate (Danger Kernel)    │       │ • Compute Due & Overdue Windows:    │
│ • Multi-dialect regex (EN/HI/MR/TE/TA)  │       │   - ANC-1..4, Td-1/2/BST, HRP       │
│ • 100% Recall Gate -> Cite ESC-*        │       │   - HBNC Home (1..42) / Inst (3..42)│
│ • Immediate 108/102 Ambulance Referral  │       │   - UIP Vaccines (BCG..DPT-B2)      │
└────────────────────┬────────────────────┘       │   - Monthly MUAC & NCD Follow-ups   │
                     │ (If no danger sign)        ├─────────────────────────────────────┤
                     ▼                            │ • Assign Priority 1, 2, or 3        │
┌─────────────────────────────────────────┐       │ • Deduplicate (member_id, due_item) │
│ Step C: JEV-Scope (Refusal Kernel)      │       └─────────────────────────────────────┘
│ • Adult cancer, fractures, dental,      │
│   antibiotics/syrups, sutures, PC-PNDT  │
│ • Refuse cleanly -> Cite SCP-1.2 & MO   │
└────────────────────┬────────────────────┘
                     │ (If in scope)
                     ▼
┌─────────────────────────────────────────┐
│ Step D: Laya Dual Retrieval Engine      │
│ • LayaLexicon expands colloquial terms  │
│ • Pure Python BM25 (3x Title/ID weight) │
│ • Anatomical Compatibility Guardrail    │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ Step E: JEV-Verify & Extractive QA      │
│ • Validate leaf IDs (Max 2 citations)   │
│ • Extract top 2-3 factual sentences     │
│ • Enforce strict <= 400 char constraint │
└─────────────────────────────────────────┘
```

### Data Model & State

- **Protocol Leaf Corpus (`Dict[str, ProtocolSection]`)**:
  - Keyed by normalized leaf section ID (e.g., `CHD-1.2`, `ANC-2.1`, `ESC-5.1`, `SCP-1.2`).
  - Stores `section_id`, `title`, `text`, and `filename`.
  - Loaded from `H5_Frontline_Health_Worker_Companion/data/protocols/*.md` with fallback to embedded core protocol definitions.
- **Laya BM25 Index (`LayaRetriever`)**:
  - Tokenizes and expands every document using `LAYA_LEXICON` (mapping colloquial village terms like `dast`, `ghol`, `bukhar`, `taap`, `tika`, `kamjor`, `wazan`, `pasli`, `behosh` to canonical clinical concepts and section IDs).
  - Applies $3\times$ repetition weighting to section IDs and titles before computing term frequencies (`tf`), document frequencies (`df`), and Okapi BM25 scores ($k_1 = 1.5, b = 0.75$).
- **QA Response Contract (`Dict[str, Any]`)**:
  - `question_id` (`str`), `answer_text` (`str`, $\le 400$ chars), `cited_section_ids` (`List[str]`, $1\text{–}2$ valid leaf IDs), `escalate` (`bool`), `refused` (`bool`), `latency_ms` (`float`).
- **Visit List Item Contract (`Dict[str, Any]`)**:
  - `member_id` (`str`), `due_item` (`str`), `priority` (`int` in $\{1, 2, 3\}$).

### Interactive Component & State Mapping

- **JEV-Escalate Handler**:
  - First checks `NEAR_BOUNDARY_EXCLUSIONS` so benign lookalikes (e.g., mild fever after vaccination where child is breastfeeding, normal fetal movements, mild dehydration managed with ORS) never trigger false escalation.
  - Then evaluates deterministic regex rules covering all required danger signs (convulsions/fits, chest indrawing/stridor, inability to feed, lethargy/unconsciousness, severe dehydration/sunken eyes/skin pinch $>2\text{s}$, pregnancy/postpartum haemorrhage, pre-eclampsia headache/blurred vision, cord pus/bleeding, newborn hypothermia $<35.5^\circ\text{C}$, jaundice within 24 hours, hypertensive crisis $\ge 180/110$, stroke/MI, poisoning).
  - Returns `escalate=True, refused=False` with immediate 102/108 referral text and `ESC-*` leaf citation.
- **JEV-Scope Handler**:
  - Matches out-of-scope medical queries (cancer, cardiac arrest management beyond immediate referral, bone fractures, dental surgery, psychotropic medications, surgical sutures, unprescribed allopathic drug/syrup dosing, lab report interpretation, PC-PNDT sex determination, administrative portal/incentive issues) and anatomical mismatches.
  - Returns `escalate=False, refused=True, cited_section_ids=["SCP-1.2"]` with clear refusal and redirection to the PHC Medical Officer / ANM.
- **Laya Extractive Synthesizer**:
  - Splits the top-ranked protocol section into clean factual sentences, scores each sentence against expanded query tokens, selects the top 2–3 sentences, and appends the leaf citation `(SEC-ID)` while guaranteeing `len(answer_text) <= 400`.
- **Chandrapuri Visit Scheduler**:
  - Evaluates each household member against `09_VIS_daily_visit_list_rules.md` (with support for both standard register columns and simplified member dicts) to output prioritized visits (`Priority 1`, `Priority 2`, `Priority 3`).
