"""Unit tests for JEV (Judicial Evidence Validator)."""

import json
import pytest
from pathlib import Path
from arogyasathi.jev import JEVEscalate, JEVScope, JEVVerify

DATA_DIR = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data"


def test_jev_escalate_dev_truth():
    """Verify 100% recall on dev danger questions."""
    truth_file = DATA_DIR / "dev_truth.jsonl"
    with open(truth_file, encoding="utf-8") as f:
        dev_truth = [json.loads(line) for line in f]

    import pandas as pd
    q_df = pd.read_csv(DATA_DIR / "questions_dev.csv")
    q_map = dict(zip(q_df["question_id"], q_df["question_text"]))

    esc_questions = [q for q in dev_truth if q.get("must_escalate")]
    assert len(esc_questions) == 10

    for q in esc_questions:
        qid = q["question_id"]
        qtext = q_map[qid]
        esc, sec, ans = JEVEscalate.evaluate(qtext)
        assert esc is True, f"Failed to escalate danger question {qid}: {qtext}"
        assert sec is not None, f"Missing section for danger question {qid}"
        assert sec.upper() in [e.upper() for e in q["expected_section_ids"]], (
            f"Expected section {q['expected_section_ids']} but got {sec} for {qid}"
        )


def test_jev_escalate_false_positive_control():
    """Verify zero false escalations on non-danger questions in dev set."""
    truth_file = DATA_DIR / "dev_truth.jsonl"
    with open(truth_file, encoding="utf-8") as f:
        dev_truth = [json.loads(line) for line in f]

    import pandas as pd
    q_df = pd.read_csv(DATA_DIR / "questions_dev.csv")
    q_map = dict(zip(q_df["question_id"], q_df["question_text"]))

    non_esc_questions = [q for q in dev_truth if not q.get("must_escalate")]
    assert len(non_esc_questions) == 30

    false_esc = []
    for q in non_esc_questions:
        qid = q["question_id"]
        qtext = q_map[qid]
        esc, sec, ans = JEVEscalate.evaluate(qtext)
        if esc:
            false_esc.append((qid, qtext))

    assert len(false_esc) == 0, f"False escalations detected: {false_esc}"


def test_jev_scope_refusal():
    """Verify out-of-scope queries are deterministically refused with SCP-1.2."""
    truth_file = DATA_DIR / "dev_truth.jsonl"
    with open(truth_file, encoding="utf-8") as f:
        dev_truth = [json.loads(line) for line in f]

    import pandas as pd
    q_df = pd.read_csv(DATA_DIR / "questions_dev.csv")
    q_map = dict(zip(q_df["question_id"], q_df["question_text"]))

    oos_questions = [q for q in dev_truth if q.get("out_of_scope")]
    assert len(oos_questions) == 6

    for q in oos_questions:
        qid = q["question_id"]
        qtext = q_map[qid]
        ref, sec, ans = JEVScope.evaluate(qtext)
        assert ref is True, f"Failed to refuse out-of-scope question {qid}: {qtext}"
        assert sec == "SCP-1.2"


def test_jev_verify_citations():
    """Verify citation sanitizer clamps citations to max 2 verified leaf sections."""
    valid_sections = {"ANC-1.1", "ANC-1.2", "ESC-2.3", "IMM-2.1"}
    verifier = JEVVerify(valid_sections)

    # Over-length list clamped to 2
    sanitized = verifier.validate_citations(["ANC-1.1", "ANC-1.2", "ESC-2.3"])
    assert len(sanitized) == 2
    assert sanitized == ["ANC-1.1", "ANC-1.2"]

    # Invalid citations filtered
    sanitized2 = verifier.validate_citations(["INVALID-9.9", "ANC-1.1"])
    assert sanitized2 == ["ANC-1.1"]

