"""Unit tests for Laya (Multilingual Retrieval & Grounding Engine)."""

import pytest
from arogyasathi.protocols import load_protocol_sections
from arogyasathi.laya import LayaLexicon, LayaRetriever, LayaSynthesizer


def test_laya_lexicon_expansion():
    """Verify dialect and regional term expansion across languages."""
    # Telugu terms
    tel_expanded = LayaLexicon.expand_tokens("daggu swasa")
    assert "cough" in tel_expanded
    assert "breathing" in tel_expanded

    # Hindi terms
    hi_expanded = LayaLexicon.expand_tokens("bachche ko dast aur ulti hai")
    assert "diarrhoea" in hi_expanded

    # Protocol acronyms
    anc_expanded = LayaLexicon.expand_tokens("chautha anc checkup")
    assert "anc-1.2" in anc_expanded


def test_laya_retriever_ranking():
    """Verify BM25 retrieves top matching protocol leaf sections."""
    sections = load_protocol_sections()
    retriever = LayaRetriever(sections)

    # Test NCD query
    hits_ncd = retriever.retrieve("BP ke marij ko kitna namak khana chahiye?")
    top_sec = hits_ncd[0][1]
    assert top_sec == "NCD-3.1"

    # Test Immunization query
    hits_imm = retriever.retrieve("Chandrapuri me JE ka tika lagta hai kya?")
    top_secs = [h[1] for h in hits_imm]
    assert "IMM-2.4" in top_secs


def test_laya_synthesizer_constraints():
    """Verify synthesized answer text never exceeds 400 chars and includes section ID."""
    sections = load_protocol_sections()
    query = "bachche ko ORS kitna pilana hai?"
    ans = LayaSynthesizer.synthesize(sections, ["CHD-1.2"], query)

    assert len(ans) <= 400
    assert "CHD-1.2" in ans
    assert len(ans) > 20

