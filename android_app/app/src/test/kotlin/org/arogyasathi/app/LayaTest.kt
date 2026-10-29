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

    @Test
    fun testPatientContextAwareSynthesis() {
        val section = ProtocolSection(
            sectionId = "ANC-2.3",
            title = "Calcium supplementation",
            body = "Give two tablets of calcium (500 mg each = 1000 mg) daily from 14 weeks onwards until 6 months postpartum. Take separately from IFA.",
            category = "ANC"
        )
        val pregnantPatient = org.arogyasathi.app.core.Member(
            memberId = "M-TEST01",
            householdId = "HH-TEST",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Jamuna Devi",
            relation = "Self",
            sex = "F",
            dob = "1998-05-12",
            isPregnant = true,
            edd = "2026-11-20",
            lastAncBp = "145/95",
            lastHbGdl = 6.8
        )
        val synth = LayaSynthesizer.synthesizeWithContext(section, "calcium tablet kitna dena hai", pregnantPatient)
        assertTrue("Should include patient name", synth.textEnglish.contains("Jamuna Devi"))
        assertTrue("Should contain exact calcium dosage", synth.textEnglish.contains("Calcium"))
        assertTrue("Should include valid GoI citation", synth.textEnglish.contains("ANC-2.3") || synth.citedSection == "ANC-2.3")
        assertTrue("Must generate bilingual Hindi text", synth.textHindi.isNotBlank())
        assertTrue("English text must not exceed 400 chars", synth.textEnglish.length <= 400)
        assertTrue("Hindi text must not exceed 400 chars", synth.textHindi.length <= 400)
    }

    @Test
    fun testDiarrheaBilingualSynthesis() {
        val section = ProtocolSection(
            sectionId = "CHD-1.2",
            title = "How much ORS to give",
            body = "Give 1 packet of ORS in 1 litre drinking water. Children under 2 years: 50-100 ml. Children 2 years and older: 100-200 ml.",
            category = "CHD"
        )
        val synth = LayaSynthesizer.synthesizeWithContext(section, "dast me kitna ors dena hai", null)
        assertTrue("English text must contain 100-200 ml", synth.textEnglish.contains("100-200 ml") || synth.textEnglish.contains("50-100 ml"))
        assertTrue("Hindi text must contain ओआरएस", synth.textHindi.contains("ओआरएस"))
        assertEquals("CHD-1.2", synth.citedSection)
    }
}
