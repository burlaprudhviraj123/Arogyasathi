# Arogyasathi — Testable Implementation & Verification Plan (Phase 6)

**Document ID:** `DOC-ARCH-010`  
**Status:** Living Verification Specification  
**Version:** 1.0  
**Phase:** Phase 6 (Testable Requirements & Verification Plan)  

---

## 1. Executive Summary & Verification Philosophy

Arogyasathi is a clinical and operational companion for frontline healthcare workers (ASHAs/ANMs) operating in zero-connectivity environments. In accordance with the **Master Engineering Workflow** and the **Karpathy Behavioral Guidelines**:
- **Zero Premature Implementation:** No code is written without explicit user authorization and pre-defined verification criteria.
- **Goal-Driven Execution:** Every functional component has an associated, automated test harness with deterministic pass/fail gates.
- **Safety Precedence:** A safety failure (e.g. missing a maternal hemorrhage or neonatal convulsion) immediately fails the system, cutting the evaluation score by 50% via the two-sided escalation gate.

---

## 2. Requirement-to-Test Traceability Matrix

| Requirement ID | Specification | Metric / Target | Test Suite | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-SAF-001** | Maternal Danger Sign Detection | 100% Recall on obstetric emergencies | `tests/test_safety_gate.py::test_maternal_danger` | Recall == 1.000 |
| **REQ-SAF-002** | Neonatal Danger Sign Detection | 100% Recall on neonatal emergencies | `tests/test_safety_gate.py::test_neonatal_danger` | Recall == 1.000 |
| **REQ-SAF-003** | Pediatric IMNCI Danger Detection | 100% Recall on chest indrawing, stridor, lethargy | `tests/test_safety_gate.py::test_pediatric_danger` | Recall == 1.000 |
| **REQ-SAF-004** | Adult Acute Danger Detection | 100% Recall on stroke (BE FAST), MI, crisis HTN | `tests/test_safety_gate.py::test_adult_danger` | Recall == 1.000 |
| **REQ-SAF-005** | False Escalation Suppression | Prevent referral hospital overload | `tests/test_safety_gate.py::test_false_escalation` | FER $\le$ 0.200 (Target 0.00) |
| **REQ-SAF-006** | Out-of-Scope Scope Guard (SCP-1.2) | Deterministic refusal of unprescribed meds & admin | `tests/test_safety_gate.py::test_scope_refusal` | Accuracy == 1.000 |
| **REQ-OPS-001** | ANC Visit Schedule Computation | Exact alignment with MoHFW 4-ANC schedule | `tests/test_visit_engine.py::test_anc_visits` | Exact match with truth |
| **REQ-OPS-002** | HBNC Postnatal Schedule Engine | 6 institutional visits, 7 home delivery visits | `tests/test_visit_engine.py::test_hbnc_visits` | Exact match with truth |
| **REQ-OPS-003** | National Immunization Catch-up | Complete schedule + 28-day overdue rules | `tests/test_visit_engine.py::test_imm_schedule` | Exact match with truth |
| **REQ-OPS-004** | High-Risk Priority Stratification | Severe anaemia, HTN, SAM elevated to HIGH/CRITICAL | `tests/test_visit_engine.py::test_priority_rules` | Priority Accuracy $\ge$ 0.95 |
| **REQ-QA-001** | Protocol Leaf Citation Fidelity | $\le$ 2 leaf citations; at least one matches expected | `tests/test_qa_engine.py::test_citations` | Citation Accuracy $\ge$ 0.85 |
| **REQ-QA-002** | Key Clinical Fact Coverage | Fact tokens within first 400 characters | `tests/test_qa_engine.py::test_key_facts` | Key Fact Coverage $\ge$ 0.85 |
| **REQ-PERF-001** | On-Device Memory Footprint | Total Peak RAM under full load | `tests/test_performance.py::test_ram_footprint` | Peak RSS $\le$ 256 MB |
| **REQ-PERF-002** | Query & Visit Engine Latency | Local CPU inference & database search | `tests/test_performance.py::test_latency` | Median Latency $\le$ 200 ms |
| **REQ-PERF-003** | Complete Offline Edge Execution | Zero network calls / socket connections | `tests/test_offline.py::test_no_network` | 0 socket calls allowed |

---

## 3. Two-Sided Escalation Gate Verification Harness

The competition evaluator enforces a strict two-sided gate:
$$\text{Gate} = \begin{cases} \text{PASS} & \text{if } \text{escalation\_recall} == 1.0 \land \text{false\_escalation\_rate} \le 0.20 \\ \text{FAIL} & \text{otherwise} \end{cases}$$

### 3.1 Positive Recall Harness (`must_escalate == True`)
Every danger sign specified in `03_ESC_danger_signs_refer_immediately.md` must trigger `escalate = True`:
1. **Obstetric:**
   - Antepartum / intrapartum / postpartum hemorrhage (`ESC-2.1`, `ESC-3.1`).
   - Eclampsia / convulsions / severe pre-eclampsia headache + visual blur + facial swelling (`ESC-2.2`, `ESC-2.3`).
   - Decreased or absent fetal movements (`ESC-2.7`).
   - Premature rupture of membranes / leaking water bag without labour pains (`ESC-2.8`).
   - Prolonged labour $> 12$ hours (`ESC-2.8`).
   - Puerperal sepsis (foul lochia + fever within 42 days) (`ESC-3.2`).
2. **Neonatal:**
   - Inability to suck / feed (`ESC-4.1`).
   - Hypothermia ($< 35.5^\circ\text{C}$ / cold to touch) (`ESC-4.3`).
   - Tachypnea ($\ge 60\text{ bpm}$) or severe chest indrawing (`ESC-4.2`).
   - Umbilical redness/pus or $\ge 10$ pustules (`ESC-4.4`).
   - Jaundice appearing in first 24 hours of life (`ESC-4.4`).
3. **Childhood (IMNCI):**
   - General danger signs: convulsions, lethargic/unconscious, vomits everything, unable to drink (`ESC-5.1`).
   - Stridor or chest indrawing (`ESC-5.2`).
   - Fever lasting $> 7$ days or with stiff neck (`ESC-5.4`).
   - Severe dehydration with sunken eyes / skin pinch $> 2\text{s}$ (`ESC-5.3`).
4. **Adult Acute:**
   - Hypertensive crisis (BP $\ge 180/110\text{ mmHg}$) (`ESC-6.4`).
   - Stroke signs: unilateral facial droop, arm weakness, slurred speech (`ESC-6.2`).
   - Acute myocardial infarction: crushing central retrosternal pain with diaphoresis (`ESC-6.1`).
   - Hypoglycemic crisis with altered sensorium (`ESC-6.3`).

### 3.2 False Escalation Control Harness (`must_escalate == False`)
Non-danger questions must **never** trigger emergency 108/102 escalation:
- Mild childhood diarrhea drinking fluids normally (`CHD-1.2`).
- Routine vaccination minor adverse events: mild fever, injection site soreness (`IMM-3.1`).
- Normal physiological pregnancy symptoms: mild pedal edema in evening without headache (`ANC-1.4`).
- Routine follow-up queries or administrative inquiries (`SCP-1.2`).

---

## 4. Operational Visit Engine Verification Harness

### 4.1 Evaluation Dataset
- **Household Register:** 500 households, 2,234 individuals (`data/household_register.csv`).
- **Dev Truth Set:** 39 target members in scope (`data/dev_truth_visit_list.csv`).
- **Scoring Formulas:**
  $$\text{F1} = \frac{2 \cdot P \cdot R}{P + R}, \quad \text{where } P = \frac{\text{hits}}{\text{predicted}}, R = \frac{\text{hits}}{\text{truth}}$$
  $$\text{Priority Accuracy} = \frac{\sum \mathbb{I}(\text{pred\_priority} == \text{truth\_priority})}{\max(|\text{truth}|, |\text{pred}|)}$$
  $$\text{Visit Score} = 0.60 \cdot \text{F1} + 0.40 \cdot \text{Priority Accuracy}$$

### 4.2 Edge Case Test Battery
1. **Window Calculation:** Confirm upcoming visit window of 6 days (`VIS-1.3`).
2. **Overdue Threshold:** Confirm grace period cutoff of exactly 28 days (`VIS-1.3`).
3. **Institutional vs Home HBNC:** Verify 6-visit institutional vs 7-visit home delivery day sequences.
4. **Immunization Catch-up:** Verify minimum dose gaps (4 weeks for Penta, 2 months for PCV, 8 weeks for fIPV) and confirm doses are never restarted from dose 1.
5. **High-Risk Elevation:** Verify that severe anaemia (Hb $< 7.0$), pregnancy hypertension ($\ge 140/90$), and SAM children automatically escalate to `HIGH` priority.

---

## 5. Performance & Resource Verification Plan

### 5.1 Measurement Harness (`tests/test_performance.py`)
- **Peak RSS RAM:** Measured via `resource.getrusage(resource.RUSAGE_SELF).ru_maxrss`.
  - macOS reports bytes; Linux reports kilobytes. The harness normalizes to megabytes (MB).
  - Target: $\le 128\text{ MB}$ typical, $\le 256\text{ MB}$ ceiling.
- **Inference Latency:** Measured via `time.perf_counter_ns()` across 100 queries.
  - Target: Median $\le 10\text{ ms}$ on CPU for retrieval; $\le 50\text{ ms}$ total query latency.
- **Disk Footprint:**
  - Codebase + protocols + indices $\le 5\text{ MB}$.
  - No external model weights exceeding 50 MB; purely deterministic offline lookup with BM25 indexing.

---

## 6. Verification Sign-Off Checklist Before Implementation Authorization

- [x] Phase 0: Problem Statement and Constraints extracted without assumption (`00_PROBLEM_AND_CONSTRAINTS.md`).
- [x] Phase 1: Initial Architecture audited faithfully preserving @Prudhvi's 3-mode design, RBAC, and Jev/Laya (`01_INITIAL_ARCHITECTURE_AUDIT.md`).
- [x] Phase 2: All 6 research documents audited and summarized (`docs/research/01` to `06`).
- [x] Phase 3: Comprehensive Comparative Analysis completed (`07_COMPARATIVE_ANALYSIS.md`).
- [x] Phase 4: Final Authoritative System Architecture documented (`08_FINAL_SYSTEM_ARCHITECTURE.md`).
- [x] Phase 5: Native Android Offline Application Architecture documented (`09_ANDROID_OFFLINE_APP_ARCHITECTURE.md`).
- [x] Phase 6: Testable Implementation & Verification Plan established (`10_TESTABLE_REQUIREMENTS_AND_VERIFICATION_PLAN.md`).
- [ ] **Implementation Authorization:** Awaiting explicit authorization from USER before modifying or finalizing production application code.
