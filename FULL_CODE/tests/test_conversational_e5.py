"""Unit tests for the Multilingual E5 ONNX INT8 Conversational Assistant."""

import pytest
from arogyasathi.conversational_assistant import ArogyasathiConversationalAssistant


@pytest.fixture(scope="module")
def assistant():
    return ArogyasathiConversationalAssistant()


def test_e5_initialization(assistant):
    assert assistant.embedder is not None
    assert assistant.retriever is not None
    assert len(assistant.sections) == 107
    assert len(assistant.patient_engine.patients_by_id) > 0


def test_multilingual_semantic_retrieval(assistant):
    # 1. Hindi query
    res_hi = assistant.chat("bacche ko dast lag gaye hain kya karein", language="hi")
    assert "CHD-1" in res_hi["cited_section_ids"][0]
    assert res_hi["escalate"] is False

    # 2. Telugu query
    res_te = assistant.chat("pillavadiki loose motions avthunnai em cheyali", language="te")
    assert "CHD-1" in res_te["cited_section_ids"][0]
    assert res_te["escalate"] is False


def test_patient_context_and_followup(assistant):
    session_id = "test_pytest_session"
    # Find a pregnant patient
    pregnant_patient = next(p for p in assistant.patient_engine.patients_by_id.values() if p.is_pregnant)

    # Turn 1: Patient query
    q1 = f"What is the ANC checkup schedule for {pregnant_patient.name}?"
    res1 = assistant.chat(q1, session_id=session_id)
    assert res1["patient_context"] == pregnant_patient.name
    assert "ANC-" in res1["cited_section_ids"][0]

    # Turn 2: Follow-up without mentioning name
    q2 = "What should she take for iron supplementation?"
    res2 = assistant.chat(q2, session_id=session_id)
    assert res2["patient_context"] == pregnant_patient.name
    assert len(res2["cited_section_ids"]) > 0


def test_longitudinal_vitals_alerts(assistant):
    session_id = "test_vitals_session"
    pregnant_patient = next(p for p in assistant.patient_engine.patients_by_id.values() if p.is_pregnant)

    # Input critical vitals
    q = f"Check {pregnant_patient.name} whose blood pressure measured 144/94 today."
    res = assistant.chat(q, session_id=session_id, current_vitals={"bp": "144/94", "hb": 6.5})
    assert len(res["trends_detected"]) > 0


def test_safety_escalation_override(assistant):
    # Unconscious child must trigger 100% emergency escalation
    q = "Child is unconscious and cannot feed at all"
    res = assistant.chat(q)
    assert res["escalate"] is True
    assert res["triage_level"] == "EMERGENCY"
    assert "ESC-" in res["cited_section_ids"][0]


def test_scope_refusal(assistant):
    # Surgical cancer inquiry must be refused
    q = "What is the surgical chemotherapy protocol for breast cancer?"
    res = assistant.chat(q)
    assert res["refused"] is True
    assert res["triage_level"] == "OUT_OF_SCOPE"
    assert "SCP-1.2" in res["cited_section_ids"][0]

