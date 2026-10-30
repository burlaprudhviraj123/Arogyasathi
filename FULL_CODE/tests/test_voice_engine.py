"""Unit tests for Arogyasathi Offline Voice Consultation Engine."""

import pytest
from arogyasathi.voice_engine import OfflineVoiceTranscriber, VoiceConsultationEngine
from arogyasathi.conversational_assistant import ConversationalAssistant


@pytest.fixture(scope="module")
def voice_engine():
    assistant = ConversationalAssistant()
    return VoiceConsultationEngine(assistant=assistant)


def test_telugu_voice_emergency_escalation(voice_engine):
    """Verifies that a spoken Telugu emergency phrase triggers immediate JEV escalation."""
    # Spoken audio transcription of bleeding in pregnancy
    res = voice_engine.consult_from_voice(
        audio_input="గర్భధారణలో రక్తస్రావం అవుతోంది",
        language="te"
    )
    assert res["escalate"] is True
    assert res["triage_level"] == "EMERGENCY"
    assert "ESC-2.1" in res["cited_section_ids"]
    assert res["call_doctor_required"] is True
    assert "అత్యవసర హెచ్చరిక" in res["tts_audio_text"] or "108" in res["tts_audio_text"]


def test_telugu_voice_phonetic_transcription(voice_engine):
    """Verifies phonetic/transliterated spoken dialect mapped to Telugu clinical query."""
    res = voice_engine.consult_from_voice(
        audio_input="motions avthunnai ors dose",
        language="te"
    )
    assert "విరేచనాలకు" in res["transcription"] or "ఓఆర్ఎస్" in res["transcription"]
    assert res["escalate"] is False
    assert len(res["cited_section_ids"]) > 0
    assert "SEC-4.2" in res["cited_section_ids"] or "ORS" in res["answer_text"]


def test_voice_refusal_out_of_scope(voice_engine):
    """Verifies spoken surgical/cancer questions are refused and direct doctor call is flagged."""
    res = voice_engine.consult_from_voice(
        audio_input="సిజేరియన్ సర్జరీ ఆపరేషన్ ఖర్చు ఎంత",
        language="te"
    )
    assert res["refused"] is True
    assert res["call_doctor_required"] is True
    assert "వైద్యాధికారిని సంప్రదించండి" in res["tts_audio_text"] or "Medical Officer" in res["answer_text"]


def test_hindi_voice_consultation(voice_engine):
    """Verifies Hindi spoken danger sign triggers immediate JEV escalation."""
    res = voice_engine.consult_from_voice(
        audio_input="तीसरी तिमाही में तेज रक्तस्राव हो रहा है",
        language="hi"
    )
    assert res["escalate"] is True
    assert "ESC-2.1" in res["cited_section_ids"]
    assert "आपातकालीन चेतावनी" in res["tts_audio_text"] or "108" in res["tts_audio_text"]


def test_voice_engine_footprint_reported(voice_engine):
    """Verifies edge model size and RAM footprint conform to < 150 MB device budget."""
    res = voice_engine.consult_from_voice(
        audio_input="రక్తహీనత మరియు ఐరన్ మాత్రలు",
        language="te"
    )
    fp = res["voice_engine_footprint"]
    assert fp["model_size_mb"] <= 150.0
    assert fp["target_ram_mb"] <= 200.0
    assert fp["latency_ms"] >= 0.0
