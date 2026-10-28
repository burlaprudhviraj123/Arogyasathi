package org.arogyasathi.app

import org.arogyasathi.app.core.ProtocolSection
import org.arogyasathi.app.data.LocalRepository
import org.arogyasathi.app.laya.LayaLexicon
import org.arogyasathi.app.laya.LayaRetriever
import org.arogyasathi.app.laya.LayaSynthesizer
import org.junit.Assert.*
import org.junit.Test

class LayaTest {

    @Test
    fun testLexiconExpansion() {
        val expanded = LayaLexicon.expand("dast me ors aur zinc kitna")
        assertTrue("Lexicon must expand dast to diarrhoea", expanded.contains("diarrhoea") || expanded.contains("chd-1.1"))
        assertTrue("Lexicon must expand ors", expanded.contains("ors") || expanded.contains("chd-1.2"))
        assertTrue("Lexicon must expand zinc", expanded.contains("zinc") || expanded.contains("chd-1.3"))
    }

    @Test
    fun testBm25RetrievalDiarrhoea() {
        val retriever = LayaRetriever(LocalRepository.sampleProtocols)
        val query = "1 saal ke bachche ko dast me kitna ors dena hai"
        val hits = retriever.retrieve(query, topK = 1)
        assertFalse("Retriever should find relevant section", hits.isEmpty())
        val top = hits.first()
        assertEquals("CHD-1.2", top.sectionId)
    }

    @Test
    fun testBm25RetrievalIfaTablets() {
        val retriever = LayaRetriever(LocalRepository.sampleProtocols)
        val query = "garbhwati mahila ko kitni lal goli ifa dena hai"
        val hits = retriever.retrieve(query, topK = 1)
        assertFalse("Retriever should find relevant section", hits.isEmpty())
        val top = hits.first()
        assertEquals("ANC-2.2", top.sectionId)
    }

    @Test
    fun testGroundedSynthesisLengthConstraint() {
        val section = ProtocolSection(
            sectionId = "CHD-1.2",
            title = "How much ORS to give",
            body = "Up to 2 years: 50 to 100 ml after each loose stool. 2 years or more: 100 to 200 ml. If child vomits, wait 10 minutes then continue slowly. Keep preparing fresh ORS daily. Wash hands with soap.",
            category = "CHD"
        )
        val synthesized = LayaSynthesizer.synthesize(section, "kitna ors dena hai")
        assertNotNull(synthesized)
        assertTrue("Answer text length must be <= 400 characters", synthesized.length <= 400)
        assertTrue("Answer must cite the section ID", synthesized.contains("CHD-1.2"))
    }
}
