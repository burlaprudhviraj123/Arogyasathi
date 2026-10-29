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

    @Test
    fun testPremManjhiAgeTailoredDiarrhoeaAdvice() {
        val section = ProtocolSection(
            sectionId = "CHD-1.2",
            title = "How much ORS to give",
            body = "Give 1 packet of ORS in 1 litre drinking water. Children under 2 years: 50-100 ml. Children 2 years and older: 100-200 ml.",
            category = "CHD"
        )
        // Prem Manjhi from household_register.csv: born 2022-04-12 (4 years old on 2026-10-26)
        val premManjhi = org.arogyasathi.app.core.Member(
            memberId = "M-R76DUV5C",
            householdId = "HH-014Q8O",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Prem Manjhi",
            relation = "son",
            sex = "M",
            dob = "2022-04-12"
        )
        val synth = LayaSynthesizer.synthesizeWithContext(section, "motions avthunnai", premManjhi)
        assertTrue("Must acknowledge Prem Manjhi", synth.textEnglish.contains("Prem Manjhi"))
        assertTrue("Must calculate and display 4 years", synth.textEnglish.contains("4 years"))
        assertTrue("Must prescribe 100 to 200 ml ORS for a 4-year-old child", synth.textEnglish.contains("100 to 200 ml"))
        assertFalse("Must NOT give under 2 years dose for 4-year-old", synth.textEnglish.contains("50 to 100 ml"))
        assertTrue("Must prescribe 20 mg Zinc for child >=6m", synth.textEnglish.contains("Zinc 20 mg"))
        assertFalse("Must NOT prescribe 10 mg Zinc for 4-year-old", synth.textEnglish.contains("10 mg"))
        assertTrue("Must cite CHD-1.2", synth.textEnglish.contains("CHD-1.2"))
        assertTrue("Length must be <= 400 chars", synth.textEnglish.length <= 400)
    }

    @Test
    fun testTeluguDiarrhoeaQueryRetrieval() {
        val retriever = LayaRetriever(LocalRepository.sampleProtocols)
        val hits = retriever.retrieve("motions avthunnai", topK = 1)
        assertFalse("Retriever should find relevant protocol for Telugu motions query", hits.isEmpty())
        assertTrue("Must retrieve CHD-1 diarrhoea protocol (CHD-1.2 or CHD-1.3)", hits.first().sectionId.startsWith("CHD-1"))
    }

    @Test
    fun testSouthIndianMultilingualRetrieval() {
        val retriever = LayaRetriever(LocalRepository.sampleProtocols)

        // Telugu script
        val teHits = retriever.retrieve("విరేచనాలు", topK = 1)
        assertFalse("Telugu script query should retrieve protocol", teHits.isEmpty())
        assertTrue("Telugu query should match diarrhoea", teHits.first().sectionId.startsWith("CHD-1"))

        // Tamil script
        val taHits = retriever.retrieve("வயிற்றுப்போக்கு", topK = 1)
        assertFalse("Tamil script query should retrieve protocol", taHits.isEmpty())
        assertTrue("Tamil query should match diarrhoea", taHits.first().sectionId.startsWith("CHD-1"))

        // Kannada script
        val knHits = retriever.retrieve("ಭೇದಿ", topK = 1)
        assertFalse("Kannada script query should retrieve protocol", knHits.isEmpty())
        assertTrue("Kannada query should match diarrhoea", knHits.first().sectionId.startsWith("CHD-1"))

        // Malayalam script
        val mlHits = retriever.retrieve("വയറിളക്കം", topK = 1)
        assertFalse("Malayalam script query should retrieve protocol", mlHits.isEmpty())
        assertTrue("Malayalam query should match diarrhoea", mlHits.first().sectionId.startsWith("CHD-1"))
    }

    @Test
    fun testSouthIndianEmergencyPhrasesInJEV() {
        // Tamil unconsciousness & fits
        val taEsc1 = org.arogyasathi.app.jev.JEVEscalate.evaluate("நோயாளிக்கு மயக்கம் வந்துவிட்டது")
        assertNotNull("Tamil unconsciousness must escalate", taEsc1)
        assertEquals("ESC-6.3", taEsc1?.first)

        val taEsc2 = org.arogyasathi.app.jev.JEVEscalate.evaluate("குழந்தைக்கு வலிப்பு")
        assertNotNull("Tamil convulsions must escalate", taEsc2)
        assertEquals("ESC-5.1", taEsc2?.first)

        // Kannada unconsciousness & fits
        val knEsc1 = org.arogyasathi.app.jev.JEVEscalate.evaluate("ರೋಗಿಗೆ ಪ್ರಜ್ಞೆ ಇಲ್ಲ")
        assertNotNull("Kannada unconsciousness must escalate", knEsc1)
        assertEquals("ESC-6.3", knEsc1?.first)

        // Malayalam unconsciousness & fits
        val mlEsc1 = org.arogyasathi.app.jev.JEVEscalate.evaluate("ബോധമില്ല")
        assertNotNull("Malayalam unconsciousness must escalate", mlEsc1)
        assertEquals("ESC-6.3", mlEsc1?.first)

        // Telugu severe bleeding in pregnancy
        val teEsc = org.arogyasathi.app.jev.JEVEscalate.evaluate("గర్భిణికి రక్తస్రావం")
        assertNotNull("Telugu severe pregnancy bleeding must escalate", teEsc)
        assertEquals("ESC-2.1", teEsc?.first)
    }

    @Test
    fun testTeluguEmergencyPhrasesInJEV() {
        val res1 = org.arogyasathi.app.jev.JEVEscalate.evaluate("jhatkalu vasthunnai")
        assertNotNull("Must detect Telugu convulsions as emergency", res1)
        assertEquals("ESC-5.1", res1?.first)

        val res2 = org.arogyasathi.app.jev.JEVEscalate.evaluate("garbhavathi lo raktham paduthondi")
        assertNotNull("Must detect Telugu severe bleeding as emergency", res2)
        assertEquals("ESC-2.1", res2?.first)

        val res3 = org.arogyasathi.app.jev.JEVEscalate.evaluate("spruha ledu")
        assertNotNull("Must detect Telugu unconsciousness as emergency", res3)
        assertEquals("ESC-6.3", res3?.first)
    }

    @Test
    fun testBilateralPittingOedemaTriggersSamReferral() {
        val section = ProtocolSection(
            sectionId = "NUT-1.1",
            title = "MUAC screening",
            body = "Measure MUAC. Check both feet for bilateral pitting oedema. Refer SAM to NRC.",
            category = "NUT"
        )
        val oedemaChild = org.arogyasathi.app.core.Member(
            memberId = "M-OEDEMA01",
            householdId = "HH-01",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Aarav Kumar",
            relation = "Son",
            sex = "M",
            dob = "2024-02-10",
            lastMuacCm = 12.8, // Normal green MUAC, but has oedema
            bilateralPittingOedema = true
        )
        val synth = LayaSynthesizer.synthesizeWithContext(section, "check malnutrition and muac", oedemaChild)
        assertTrue("Must detect bilateral pitting oedema", synth.textEnglish.contains("oedema") || synth.textEnglish.contains("Oedema"))
        assertTrue("Must identify SAM with complications", synth.textEnglish.contains("SAM"))
        assertTrue("Must refer immediately to NRC", synth.textEnglish.contains("NRC"))
        assertTrue("Must cite GoI protocol", synth.textEnglish.contains("NUT-1.1") || synth.textEnglish.contains("ESC-5.3"))
    }

    @Test
    fun testSevereAnaemiaInPregnancyGuidance() {
        val section = ProtocolSection(
            sectionId = "ANC-1.2",
            title = "ANC Vitals and Examination",
            body = "Check Hb at every visit. If Hb < 7.0 g/dL refer for severe anaemia.",
            category = "ANC"
        )
        val anaemicMother = org.arogyasathi.app.core.Member(
            memberId = "M-ANAEMIA01",
            householdId = "HH-02",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Meera Bai",
            relation = "Self",
            sex = "F",
            dob = "2000-01-15",
            isPregnant = true,
            edd = "2026-12-05",
            lastAncBp = "118/76",
            lastHbGdl = 6.4 // Severe Anaemia < 7.0
        )
        val synth = LayaSynthesizer.synthesizeWithContext(section, "ANC follow up vitals check", anaemicMother)
        assertTrue("Must identify severe anaemia", synth.textEnglish.contains("Severe Anaemia") || synth.textEnglish.contains("severe anaemia"))
        assertTrue("Must recommend parenteral iron or blood transfusion referral", synth.textEnglish.contains("refer") || synth.textEnglish.contains("CHC"))
        assertTrue("Must cite ANC-3.1", synth.textEnglish.contains("ANC-3.1"))
    }

    @Test
    fun testAgeCalculatorCategories() {
        // 1. Newborn 15 days old
        val newborn = org.arogyasathi.app.laya.PatientAgeCalculator.calculate("2026-10-11", "2026-10-26")
        assertNotNull(newborn)
        assertTrue("Must be newborn", newborn!!.isNewbornUnderTwoMonths)
        assertTrue("Must be infant under 1", newborn.isInfantUnderOne)

        // 2. Child 4 years 6 months old
        val child = org.arogyasathi.app.laya.PatientAgeCalculator.calculate("2022-04-12", "2026-10-26")
        assertNotNull(child)
        assertTrue("Must be child 2 to 5", child!!.isChildTwoToFive)
        assertTrue("Must be child under 5", child.isChildUnderFive)
        assertEquals(4, child.years)
        assertEquals(6, child.months)

        // 3. Adult 28 years old
        val adult = org.arogyasathi.app.laya.PatientAgeCalculator.calculate("1998-05-12", "2026-10-26")
        assertNotNull(adult)
        assertTrue("Must be adult", adult!!.isAdult)
        assertFalse("Must not be child under 5", adult.isChildUnderFive)
    }
}
