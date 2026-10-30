"""Unified QA Engine for Arogyasathi.

Integrates:
- JEV (Judicial Evidence Validator): Deterministic danger escalation, scope refusal, citation verification
- Laya: Cross-dialect protocol retrieval and fact-grounded synthesis
"""

from __future__ import annotations
import re
import time
from typing import Dict, Any, Optional
from .protocols import load_protocol_sections
from .jev import JEVEscalate, JEVScope, JEVVerify
from .laya import LayaRetriever, LayaSynthesizer


def is_anatomically_incompatible(query: str, section_id: str) -> bool:
    """Detects whether a query concerns non-community organs or mismatched anatomy."""
    q = query.lower()

    # Legitimate pediatric cold signs permitted by protocols (CHD-3.1 non-severe cold)
    if "runny nose" in q or "running nose" in q:
        pass
    elif re.search(r"\b(?:bleed|ble{1,2}ding|blood|raktham|khoon).*from.*(?:nose|naak|mookku|mukku|mooku)\b|\bnosebleed\b|\bepistaxis\b|\bnaak se khoon\b|\bnose\b|\bnaak\b|\bmookku\b|\bmukku\b|\bmooku\b|\bnaasi\b", q):
        return True

    # 1. Non-protocol body areas not covered in ASHA community protocols
    non_protocol_organs = [
        r"\bear\b|\bears\b|\bkaan\b|\bchevi\b|\bchevu\b|\bchevulu\b|\bcevu\b|\bcevi\b|\bkaadhu\b|\bkevi\b|\bkivi\b|చెవు|చెవి",
        r"\btooth\b|\bteeth\b|\bdaant\b|\bdant\b|\bpal\b|\bpallu\b|\bhallu\b|\bgum\b|\bdentist\b|పళ్ళు|పన్ను",
        r"\bthroat\b|\bgala\b|\bthonda\b|\bgontu\b|\bgonthu\b|\btonsil\b|గొంతు",
        r"\burine\b|\bpeshab\b|\bmutram\b|\bmootram\b|\bmoothram\b|\bkidney\b|\bstone\b|\bpathri\b|మూత్రం",
        r"\bfracture\b|\bbone\b|\bhaddi\b|\belumbu\b|\bjoint\b|\bghutn[a-z]*\b|\bknee\b|\byemuka\b|\bemuka\b|\bvirigindi\b|విరిగింది",
        r"\bskin rash\b|\bkhujli\b|\bitching\b|\bringworm\b|\bfungal\b|\babscess\b|\bgajji\b|\bduradha\b|దురద|గజ్జి",
        r"\bburn\b|\bjal gaya\b|\btheekayam\b|\bkaalindhi\b|కాలింది",
        r"\beye\b|\beyes\b|\baankh\b|\bankh\b|\baankhon\b|\bkallu\b|\bkannu\b|\bkanla\b|\bkannil\b|కళ్ళు|కన్ను",
    ]
    if any(re.search(pat, q) for pat in non_protocol_organs):
        return True

    # 2. Maternal vaginal bleeding protocol (ESC-2.1 / ESC-3.1) without any maternal/pregnancy context
    if section_id.startswith("ESC-2.1") or section_id.startswith("ESC-3.1"):
        maternal_words = [
            r"\bpregnan[a-z]*\b", r"\bgarbh[a-z]*\b", r"\btrimester\b", r"\bvagina[a-z]*\b",
            r"\bmahila\b", r"\baurat\b", r"\bprasav\b", r"\bfoetal\b", r"\bfetal\b",
            r"\bdelivery\b", r"\bpad\b", r"\bpostpartum\b", r"\bpregnancy\b",
            r"\bnela\b", r"\bnelalu\b", r"\bthalli\b", r"\bbidda\b"
        ]
        if not any(re.search(w, q) for w in maternal_words):
            return True

    return False


class QAEngine:
    """The central Question-Answering engine running on-device with zero cloud calls."""

    def __init__(self, sections: Optional[Dict[str, Dict[str, Any]]] = None):
        if sections is None:
            sections = load_protocol_sections()
        self.sections = sections
        self.retriever = LayaRetriever(sections)
        self.verifier = JEVVerify(set(sections.keys()))

    def answer(self, question_id: str, question_text: str) -> Dict[str, Any]:
        """
        Processes a single question text and returns the exact dictionary format
        mandated by evaluate.py:
        {
            "question_id": str,
            "answer_text": str (max 400 chars),
            "cited_section_ids": list[str] (max 2 ids),
            "escalate": bool,
            "refused": bool,
            "latency_ms": float
        }
        """
        t0 = time.perf_counter_ns()

        # Step 1: JEV-Escalate (Deterministic Danger Sign Gate)
        esc, esc_sec, esc_ans = JEVEscalate.evaluate(question_text)
        if esc:
            t1 = time.perf_counter_ns()
            return {
                "question_id": question_id,
                "answer_text": (esc_ans or "")[:400],
                "cited_section_ids": [esc_sec] if esc_sec else ["ESC-1.1"],
                "escalate": True,
                "refused": False,
                "latency_ms": round((t1 - t0) / 1e6, 3),
            }

        # Step 2: JEV-Scope (Deterministic Out-of-Scope Refusal Gate)
        ref, ref_sec, ref_ans = JEVScope.evaluate(question_text)
        if ref:
            t1 = time.perf_counter_ns()
            return {
                "question_id": question_id,
                "answer_text": (ref_ans or "")[:400],
                "cited_section_ids": [ref_sec] if ref_sec else ["SCP-1.2"],
                "escalate": False,
                "refused": True,
                "latency_ms": round((t1 - t0) / 1e6, 3),
            }

        # Step 3: Laya-Retriever (Protocol Leaf Retrieval)
        hits = self.retriever.retrieve(question_text, top_k=2)
        candidate_ids = [h[1] for h in hits]

        # Step 3b: Semantic Anatomical Compatibility Guardrail
        top_sec = candidate_ids[0] if candidate_ids else "SCP-1.1"
        if is_anatomically_incompatible(question_text, top_sec):
            t1 = time.perf_counter_ns()
            return {
                "question_id": question_id,
                "answer_text": "This condition is outside frontline community protocols. Please refer the beneficiary to the Medical Officer at the PHC (SCP-1.2).",
                "cited_section_ids": ["SCP-1.2"],
                "escalate": False,
                "refused": True,
                "latency_ms": round((t1 - t0) / 1e6, 3),
            }

        # Step 4: JEV-Verify (Citation Validation)
        verified_citations = self.verifier.validate_citations(candidate_ids)

        # Step 5: Laya-Synthesizer (Fact-Grounded Answer Text)
        answer_text = LayaSynthesizer.synthesize(
            self.sections, verified_citations, question_text
        )

        t1 = time.perf_counter_ns()
        return {
            "question_id": question_id,
            "answer_text": answer_text[:400],
            "cited_section_ids": verified_citations,
            "escalate": False,
            "refused": False,
            "latency_ms": round((t1 - t0) / 1e6, 3),
        }
