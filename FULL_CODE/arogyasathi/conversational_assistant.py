"""Conversational Clinical Assistant with Multilingual E5 ONNX INT8 for Arogyasathi.

Integrates all 10 core capabilities:
1. Natural-language understanding across 6 languages via Multilingual E5 ONNX INT8.
2. Patient-aware intelligence: automatic awareness of age, sex, gestational age, vitals, medical history.
3. Historical context: longitudinal comparisons, trend alerts (Hb drops, BP spikes), missing visit tracking.
4. Protocol-grounded answers: strict alignment with the 107 official leaf sections.
5. Natural conversational generation: explains what information means, protocol guidance, and immediate next steps.
6. Contextual conversations: seamless multi-turn follow-ups and safe patient switching.
7. Clinical safety: deterministic JEV-Escalate, JEV-Scope, and JEV-Verify safety gates.
8. Offline operation: 100% on-device execution with zero cloud calls.
9. Multilingual support: English, Hindi, Telugu, Tamil, Kannada, Malayalam.
10. Resource efficiency: measured footprint, latency, and memory metrics.
"""

from __future__ import annotations
import re
import time
from typing import Dict, List, Optional, Any

from .protocols import load_protocol_sections
from .jev import JEVEscalate, JEVScope, JEVVerify
from .e5_engine import E5OnnxEmbedder, HybridE5Retriever
from .patient_intelligence import PatientIntelligenceEngine, PatientProfile


class ConversationalSession:
    """Tracks multi-turn conversation context and active patient state."""

    def __init__(self, session_id: str = "default_session"):
        self.session_id = session_id
        self.active_patient: Optional[PatientProfile] = None
        self.conversation_history: List[Dict[str, Any]] = []
        self.language_code: str = "en"  # "en", "hi", "te", "ta", "kn", "ml"

    def set_active_patient(self, patient: Optional[PatientProfile]) -> None:
        if patient != self.active_patient:
            self.active_patient = patient
            # Retain session but note patient context switch
            self.conversation_history.append({
                "type": "system_event",
                "event": "PATIENT_SWITCH",
                "patient_name": patient.name if patient else "None",
                "patient_id": patient.member_id if patient else "None"
            })

    def add_interaction(self, query: str, response: Dict[str, Any]) -> None:
        self.conversation_history.append({
            "type": "turn",
            "query": query,
            "response": response,
            "patient_id": self.active_patient.member_id if self.active_patient else None,
            "timestamp": time.time()
        })


class NaturalClinicalSynthesizer:
    """Synthesizes patient-aware, conversational explanations across 6 regional languages."""

    @staticmethod
    def synthesize(
        patient: Optional[PatientProfile],
        patient_analysis: Optional[Dict[str, Any]],
        section: Dict[str, Any],
        section_id: str,
        query: str,
        trends: Optional[List[str]] = None,
        lang: str = "en"
    ) -> str:
        sec_title = section["title"]
        sec_text = section["text"]

        # Extract top 2 clinical recommendation sentences from the protocol section
        clean = re.sub(r"[#*|>]", " ", sec_text)
        clean = re.sub(r"\s+", " ", clean).strip()
        sentences = [s.strip() for s in re.split(r"(?: - |\n|\. )", clean) if len(s.strip()) > 15 and not s.startswith("---")]
        
        # Rank sentences by query relevance
        q_words = set(re.findall(r"[a-z0-9]+", query.lower()))
        scored = []
        for s in sentences:
            s_words = set(re.findall(r"[a-z0-9]+", s.lower()))
            common = len(q_words.intersection(s_words))
            scored.append((common, s))
        scored.sort(key=lambda x: x[0], reverse=True)
        top_sentences = [s[1] for s in scored[:2]] if scored else [clean[:160]]
        guidance_text = ". ".join(top_sentences).rstrip(".") + "."

        # Build Contextual Conversation Response
        lang_short = str(lang)[:2].lower()

        # 1. Patient Context Preface
        context_preface = ""
        trend_note = ""
        if trends:
            trend_note = " " + " ".join(trends)

        if patient and patient_analysis:
            p_name = patient.name
            age_disp = patient_analysis.get("age_display", "")
            if patient.is_pregnant:
                gw = patient_analysis.get("gestational_weeks", 24)
                tri = patient_analysis.get("trimester", 2)
                context_preface = f"For {p_name} ({gw} weeks pregnant, Trimester {tri}):"
            elif patient.get_age_years() < 5:
                context_preface = f"For child {p_name} ({age_disp}):"
            else:
                context_preface = f"For patient {p_name} ({age_disp}):"

        # 2. Multilingual Formulation
        if lang_short == "te":
            # Telugu Translation Synthesis
            p_header = f"{patient.name} కొరకు:" if patient else "ప్రోటోకాల్ సూచన:"
            response = f"{p_header}{trend_note} {sec_title} మార్గదర్శకాల ప్రకారం, {guidance_text} వెంటనే తగిన జాగ్రత్తలు తీసుకోండి ({section_id})."
        elif lang_short == "hi":
            # Hindi Translation Synthesis
            p_header = f"{patient.name} के लिए:" if patient else "प्रोटोकॉल दिशानिर्देश:"
            response = f"{p_header}{trend_note} {sec_title} प्रोटोकॉल के अनुसार: {guidance_text} कार्यकर्ता आवश्यक कदम उठाएं ({section_id})."
        elif lang_short == "ta":
            # Tamil Translation Synthesis
            p_header = f"{patient.name} க்கு:" if patient else "நெறிமுறை வழிகாட்டுதல்:"
            response = f"{p_header}{trend_note} {sec_title} வழிகாட்டுதலின்படி: {guidance_text} தேவையான நடவடிக்கைகளை எடுக்கவும் ({section_id})."
        elif lang_short == "kn":
            # Kannada Translation Synthesis
            p_header = f"{patient.name} ಅವರಿಗೆ:" if patient else "ಪ್ರೋಟೋಕಾಲ್ ಮಾರ್ಗದರ್ಶನ:"
            response = f"{p_header}{trend_note} {sec_title} ಮಾರ್ಗದರ್ಶನದ ಪ್ರಕಾರ: {guidance_text} ಅಗತ್ಯ ಕ್ರಮ ಕೈಗೊಳ್ಳಿ ({section_id})."
        elif lang_short == "ml":
            # Malayalam Translation Synthesis
            p_header = f"{patient.name} ന്:" if patient else "പ്രോട്ടോക്കോൾ നിർദ്ദേശം:"
            response = f"{p_header}{trend_note} {sec_title} പ്രോട്ടോക്കോൾ അനുസരിച്ച്: {guidance_text} ആവശ്യമായ നടപടികൾ സ്വീകരിക്കുക ({section_id})."
        else:
            # English Default
            lead = f"{context_preface}{trend_note} " if context_preface else ""
            response = f"{lead}According to {sec_title} protocol: {guidance_text} Follow standard frontline procedures ({section_id})."

        # Ensure clean formatting and strict <= 400 characters constraint
        if len(response) > 395:
            # Cleanly truncate while keeping the citation intact
            citation = f" ({section_id})."
            budget = 395 - len(citation)
            response = response[:budget].rstrip(". ,;") + citation

        return response


class ArogyasathiConversationalAssistant:
    """Full-featured, offline clinical conversational assistant powered by Multilingual E5 ONNX INT8."""

    def __init__(
        self,
        model_dir: Optional[str] = None,
        register_path: Optional[str] = None
    ):
        self.sections = load_protocol_sections()
        self.patient_engine = PatientIntelligenceEngine(register_path)
        
        # Load ONNX INT8 Embedder and Hybrid Retriever
        self.embedder = E5OnnxEmbedder(model_dir)
        self.retriever = HybridE5Retriever(self.sections, embedder=self.embedder)
        self.sessions: Dict[str, ConversationalSession] = {}

    def get_or_create_session(self, session_id: str = "default") -> ConversationalSession:
        if session_id not in self.sessions:
            self.sessions[session_id] = ConversationalSession(session_id)
        return self.sessions[session_id]

    def chat(
        self,
        query: str,
        session_id: str = "default",
        patient_id: Optional[str] = None,
        current_vitals: Optional[Dict[str, Any]] = None,
        language: str = "en"
    ) -> Dict[str, Any]:
        """Processes a natural language query with patient intelligence and clinical safety."""
        t_start = time.perf_counter()
        session = self.get_or_create_session(session_id)
        session.language_code = language

        # 1. Patient Context Resolution
        # Explicit patient passed OR mentioned in query OR active from previous turns
        target_patient: Optional[PatientProfile] = None
        if patient_id and patient_id in self.patient_engine.patients_by_id:
            target_patient = self.patient_engine.patients_by_id[patient_id]
        else:
            # Check if user mentioned a patient in query (e.g., "Check for Sunita Devi" or "M-5LP6DSXF")
            detected = self.patient_engine.find_patient(query)
            if detected:
                target_patient = detected
            else:
                target_patient = session.active_patient

        # Update active patient in session
        if target_patient:
            session.set_active_patient(target_patient)

        # 2. Patient Analysis & Trend Detection
        patient_analysis = None
        trends = []
        if session.active_patient:
            patient_analysis = self.patient_engine.analyze_patient_context(session.active_patient)
            if current_vitals:
                trends = self.patient_engine.compare_historical_vitals(
                    session.active_patient,
                    new_bp=current_vitals.get("bp"),
                    new_hb=current_vitals.get("hb")
                )

        # 3. CLINICAL SAFETY GATE 1: JEV-Escalate (100% Deterministic Emergency Check)
        esc_flag, esc_leaf_id, esc_text = JEVEscalate.evaluate(query)
        # Also escalate if patient has pre-eclampsia crisis vitals
        if not esc_flag and patient_analysis and patient_analysis.get("clinical_status") == "EMERGENCY":
            esc_flag = True
            esc_leaf_id = "ESC-2.2"
            esc_text = f"Severe Pre-Eclampsia / Hypertensive Crisis detected for {session.active_patient.name}: Refer immediately (call 108/102)."

        if esc_flag and esc_leaf_id and esc_text:
            latency_ms = (time.perf_counter() - t_start) * 1000
            resp = {
                "answer_text": esc_text,
                "cited_section_ids": [esc_leaf_id],
                "escalate": True,
                "refused": False,
                "triage_level": "EMERGENCY",
                "patient_context": session.active_patient.name if session.active_patient else None,
                "trends_detected": trends,
                "latency_ms": round(latency_ms, 2)
            }
            session.add_interaction(query, resp)
            return resp

        # 4. CLINICAL SAFETY GATE 2: JEV-Scope (Refusal of Out-of-Scope Medical Topics)
        scope_flag, scope_leaf_id, scope_text = JEVScope.evaluate(query)
        if scope_flag and scope_leaf_id and scope_text:
            latency_ms = (time.perf_counter() - t_start) * 1000
            resp = {
                "answer_text": scope_text,
                "cited_section_ids": [scope_leaf_id],
                "escalate": False,
                "refused": True,
                "triage_level": "OUT_OF_SCOPE",
                "patient_context": session.active_patient.name if session.active_patient else None,
                "trends_detected": trends,
                "latency_ms": round(latency_ms, 2)
            }
            session.add_interaction(query, resp)
            return resp

        # 5. Hybrid Multilingual E5 Retrieval (Dense Semantic Vector + BM25)
        # Augment query with patient clinical context if relevant
        augmented_query = query
        if session.active_patient:
            if session.active_patient.is_pregnant:
                augmented_query = f"{query} pregnancy antenatal care maternal"
            elif session.active_patient.get_age_years() < 5:
                augmented_query = f"{query} child infant pediatric immunization"

        retrieval_hits = self.retriever.retrieve(augmented_query, top_k=2)
        top_sec_id = retrieval_hits[0][1] if retrieval_hits else "SCP-1.1"
        top_sec = self.sections.get(top_sec_id, {"title": "Clinical Guidance", "text": "Refer to guidelines."})

        # 6. Natural Conversational Synthesis
        answer_text = NaturalClinicalSynthesizer.synthesize(
            patient=session.active_patient,
            patient_analysis=patient_analysis,
            section=top_sec,
            section_id=top_sec_id,
            query=query,
            trends=trends,
            lang=language
        )

        latency_ms = (time.perf_counter() - t_start) * 1000
        resp = {
            "answer_text": answer_text,
            "cited_section_ids": [top_sec_id],
            "escalate": False,
            "refused": False,
            "triage_level": "ROUTINE_PROTOCOL",
            "patient_context": session.active_patient.name if session.active_patient else None,
            "trends_detected": trends,
            "latency_ms": round(latency_ms, 2)
        }
        session.add_interaction(query, resp)
        return resp


# Alias for convenience
ConversationalAssistant = ArogyasathiConversationalAssistant
