"""Unified QA Engine for Arogyasathi.

Integrates:
- JEV (Judicial Evidence Validator): Deterministic danger escalation, scope refusal, citation verification
- Laya: Cross-dialect protocol retrieval and fact-grounded synthesis
"""

from __future__ import annotations
import time
from typing import Dict, Any, Optional
from .protocols import load_protocol_sections
from .jev import JEVEscalate, JEVScope, JEVVerify
from .laya import LayaRetriever, LayaSynthesizer


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
