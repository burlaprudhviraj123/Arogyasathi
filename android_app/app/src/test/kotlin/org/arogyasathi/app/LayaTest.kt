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
        val generalSynth = LayaSynthesizer.synthesizeWithContext(section, "calcium tablet kitna dena hai", null)
        assertTrue("General benchmark English text must not exceed 400 chars", generalSynth.textEnglish.length <= 400)
        assertTrue("General benchmark Hindi must not exceed 400 chars", generalSynth.textHindi.length <= 400)
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
        val generalSynth = LayaSynthesizer.synthesizeWithContext(section, "dast me kitna ors dena hai", null)
        assertTrue("General benchmark answer length must be <= 400 chars", generalSynth.textEnglish.length <= 400)
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

    @Test
    fun testPujaDeviGestationAndAnaemiaAnalysis() {
        val pujaDevi = org.arogyasathi.app.core.Member(
            memberId = "M-KRE3GIWS",
            householdId = "HH-02GGF7",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Puja Devi",
            relation = "wife",
            sex = "F",
            dob = "2006-08-04",
            isPregnant = true,
            lmpDate = "2026-03-25",
            edd = "2026-12-30",
            ancVisitDates = listOf("2026-06-05", "2026-09-22", "2026-10-20"),
            lastAncBp = "111/66",
            lastHbGdl = 8.2,
            tdBoosterEligible = true,
            tdDoses = listOf("Td-BST:2026-06-05")
        )

        val timeline = org.arogyasathi.app.laya.PatientTimelineEngine.analyze(pujaDevi, "2026-10-26")
        assertNotNull("Gestation timeline must be computed", timeline.gestation)
        assertEquals(30, timeline.gestation!!.gestationalWeeks)
        assertEquals(3, timeline.gestation!!.trimester)
        assertEquals("Moderate Anaemia (7.0-9.9 g/dL) — 2 IFA Tablets Daily", timeline.vitals.anaemiaSeverity)

        val section = ProtocolSection("ANC-2.2", "IFA tablets", "Give IFA tablets", "ANC")
        val synth = LayaSynthesizer.synthesizeWithContext(section, "IFA kitna dena hai", pujaDevi)
        assertTrue("Must prescribe 2 Red IFA tablets for moderate anaemia", synth.textEnglish.contains("2 Red IFA tablets"))
        assertTrue("Must specify morning and evening doses", synth.textEnglish.contains("1 morning, 1 evening") || synth.textEnglish.contains("100 days"))
        assertTrue("Must include citation", synth.textEnglish.contains("ANC-2.2") || synth.textEnglish.contains("ANC-3.1"))
    }

    @Test
    fun testDurgaDeviNcdOverdueTracking() {
        val durgaDevi = org.arogyasathi.app.core.Member(
            memberId = "M-Z55MVUP8",
            householdId = "HH-02UAAZ",
            village = "Amrapur",
            ashaId = "ASHA-096P",
            name = "Durga Devi",
            relation = "wife",
            sex = "F",
            dob = "1994-01-06",
            ncdCondition = "HTN",
            lastBpCheckDate = "2026-09-06",
            lastBpReading = "138/86"
        )

        val timeline = org.arogyasathi.app.laya.PatientTimelineEngine.analyze(durgaDevi, "2026-10-26")
        assertNotNull("NCD status must be computed", timeline.ncdStatus)
        assertEquals("HTN", timeline.ncdStatus!!.condition)
        assertEquals(50, timeline.ncdStatus!!.daysSinceLastBp)
        assertTrue("BP check must be flagged as overdue (>30 days)", timeline.ncdStatus!!.isBpOverdue)

        val section = ProtocolSection("NCD-1.2", "Hypertension", "Monitor BP", "NCD")
        val synth = LayaSynthesizer.synthesizeWithContext(section, "BP follow up guidance", durgaDevi)
        assertTrue("Must flag overdue check in days", synth.textEnglish.contains("OVERDUE ALERT") || synth.textEnglish.contains("50 days"))
        assertTrue("Must include salt restriction", synth.textEnglish.contains("1 teaspoon") || synth.textEnglish.contains("5g"))
        assertTrue("Must include physical activity advice", synth.textEnglish.contains("150 minutes"))
    }

    @Test
    fun testSamWithOedemaEmergencyDiarrhoeaAlert() {
        val samChild = org.arogyasathi.app.core.Member(
            memberId = "M-SAM01",
            householdId = "HH-01",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Raju Kumar",
            relation = "son",
            sex = "M",
            dob = "2024-02-10",
            lastMuacCm = 11.2,
            lastMuacDate = "2026-10-15",
            bilateralPittingOedema = true
        )

        val section = ProtocolSection("CHD-1.2", "ORS", "Give ORS", "CHD")
        val synth = LayaSynthesizer.synthesizeWithContext(section, "dast ho raha hai", samChild)
        assertTrue("Must flag critical SAM alert", synth.textEnglish.contains("CRITICAL SAM ALERT") || synth.textEnglish.contains("SAM"))
        assertTrue("Must recommend ReSoMal or careful low-osmolarity ORS", synth.textEnglish.contains("ReSoMal") || synth.textEnglish.contains("5 ml/kg"))
        assertTrue("Must refer immediately to NRC", synth.textEnglish.contains("NRC") || synth.textEnglish.contains("Nutrition Rehabilitation Centre"))
    }

    @Test
    fun testPhoneNumberMasking() {
        val masked = org.arogyasathi.app.core.maskPhoneNumber("+91 94310 82101")
        assertEquals("+91 94310 •••01", masked)

        val profile = org.arogyasathi.app.core.AshaProfile(
            ashaId = "ASHA-RRR5",
            name = "Kalavati Kumari",
            phone = "+91 98765 43220",
            villages = listOf("Belwadi Tola"),
            subCentre = "Belwadi Tola HWC",
            parentPhc = "Chandrapuri PHC (Sector 1)",
            medicalOfficerName = "Dr. Rajesh Sharma, MBBS",
            medicalOfficerRole = "Medical Officer In-Charge",
            medicalOfficerPhone = "+91 94310 82101",
            anmName = "Anita Sharma",
            anmRole = "Designated Sector ANM",
            anmPhone = "+91 98350 41102"
        )

        assertEquals("+91 94310 •••01", profile.maskedMoPhone())
        assertEquals("+91 98350 •••02", profile.maskedAnmPhone())
        assertEquals("Chandrapuri PHC (Sector 1)", profile.parentPhc)
        assertEquals("Dr. Rajesh Sharma, MBBS", profile.medicalOfficerName)
    }

    @Test
    fun testReferralSlipMedicalOfficerLinkage() {
        val slip = org.arogyasathi.app.core.ReferralSlip(
            patientId = "M-TEST01",
            patientName = "Puja Devi",
            ageGender = "22y / Female",
            village = "Belwadi Tola",
            householdId = "HH-TEST",
            suspectedCondition = "Severe Pre-eclampsia",
            dangerSigns = listOf("ESC-2.3"),
            vitalSigns = "BP: 160/110 mmHg",
            protocolCitation = "ESC-2.3",
            referredFacility = "Chandrapuri PHC (Sector 1)",
            medicalOfficerName = "Dr. Rajesh Sharma, MBBS",
            medicalOfficerPhone = "+91 94310 82101"
        )

        assertEquals("Dr. Rajesh Sharma, MBBS", slip.medicalOfficerName)
        assertEquals("+91 94310 82101", slip.medicalOfficerPhone)
        assertEquals("Chandrapuri PHC (Sector 1)", slip.referredFacility)
        assertEquals("+91 94310 •••01", org.arogyasathi.app.core.maskPhoneNumber(slip.medicalOfficerPhone))
    }

    @Test
    fun testSixLanguageGuidanceSynthesis() {
        val sec = ProtocolSection(
            sectionId = "CHD-1.2",
            title = "ORS Preparation & Administration",
            body = "Give 1 packet of ORS in 1 litre drinking water. Children under 2 years: 50-100 ml after each stool.",
            category = "CHD"
        )

        val synth = LayaSynthesizer.synthesizeWithContext(sec, "child has diarrhoea motions")
        assertEquals("CHD-1.2", synth.citedSection)
        assertTrue(synth.textEnglish.contains("ORS"))
        assertTrue(synth.textHindi.contains("ओआरएस"))
        assertTrue(synth.textTelugu.contains("ఓఆర్ఎస్") || synth.textTelugu.contains("విరేచనం"))
        assertTrue(synth.textTamil.contains("ORS") || synth.textTamil.contains("வயிற்றுப்போக்கு"))
        assertTrue(synth.textKannada.contains("ಓಆರ್‌ಎಸ್") || synth.textKannada.contains("ಬೇದಿ"))
        assertTrue(synth.textMalayalam.contains("ഒആർഎസ്") || synth.textMalayalam.contains("വയറിളക്ക"))

        // Language extractor method verification
        assertEquals(synth.textEnglish, synth.getTextForLanguage("en-IN"))
        assertEquals(synth.textHindi, synth.getTextForLanguage("hi-IN"))
        assertEquals(synth.textTelugu, synth.getTextForLanguage("te-IN"))
        assertEquals(synth.textTamil, synth.getTextForLanguage("ta-IN"))
        assertEquals(synth.textKannada, synth.getTextForLanguage("kn-IN"))
        assertEquals(synth.textMalayalam, synth.getTextForLanguage("ml-IN"))
    }

    @Test
    fun testSixLanguageAssistantResultResolution() {
        val result = org.arogyasathi.app.data.AssistantResult(
            answer = "General protocol guidance in English (CHD-1.2).",
            citedSection = "CHD-1.2",
            isEscalation = false,
            isRefusal = false,
            triageLevel = "ROUTINE_PROTOCOL",
            answerHindi = "हिन्दी में प्रोटोकॉल मार्गदर्शन (CHD-1.2)।",
            answerTelugu = "తెలుగులో ప్రోటోకాల్ మార్గదర్శకం (CHD-1.2).",
            answerTamil = "தமிழில் மருத்துவ நெறிமுறை (CHD-1.2).",
            answerKannada = "ಕನ್ನಡದಲ್ಲಿ ಪ್ರೋಟೋಕಾಲ್ ಮಾರ್ಗದರ್ಶನ (CHD-1.2).",
            answerMalayalam = "മലയാളത്തിൽ ക്ലിനിക്കൽ പ്രോട്ടോക്കോൾ (CHD-1.2)."
        )

        assertEquals("General protocol guidance in English (CHD-1.2).", result.getAnswerForLanguage("en"))
        assertEquals("हिन्दी में प्रोटोकॉल मार्गदर्शन (CHD-1.2)।", result.getAnswerForLanguage("hi"))
        assertEquals("తెలుగులో ప్రోటోకాల్ మార్గదర్శకం (CHD-1.2).", result.getAnswerForLanguage("te"))
        assertEquals("தமிழில் மருத்துவ நெறிமுறை (CHD-1.2).", result.getAnswerForLanguage("ta"))
        assertEquals("ಕನ್ನಡದಲ್ಲಿ ಪ್ರೋಟೋಕಾಲ್ ಮಾರ್ಗದರ್ಶನ (CHD-1.2).", result.getAnswerForLanguage("kn"))
        assertEquals("മലയാളത്തിൽ ക്ലിനിക്കൽ പ്രോട്ടോക്കോൾ (CHD-1.2).", result.getAnswerForLanguage("ml"))
    }

    @Test
    fun testAnatomicalIntentAndScopeGuardrails() {
        val nonProtocolQueries = listOf(
            "bleeeding from nose",
            "chevu nunchi blood ostundhi",
            "chevu lo nunchi raktham",
            "ముక్కు నుంచి రక్తస్రావం",
            "daant me dard",
            "pallu noppi",
            "kaan se khoon",
            "kaalu virigindi",
            "gontu noppi",
            "kalla noppi"
        )

        for (q in nonProtocolQueries) {
            val scp = org.arogyasathi.app.jev.JEVScope.evaluate(q)
            assertNotNull("JEVScope must catch out-of-scope organ symptom: $q", scp)
            assertEquals("SCP-1.2", scp?.first)

            val esc = org.arogyasathi.app.jev.JEVEscalate.evaluate(q)
            assertNull("Non-pregnancy bleeding or organ symptoms must NOT falsely escalate: $q", esc)
        }

        // Legitimate obstetric danger signs MUST still escalate
        val escPregnancy = org.arogyasathi.app.jev.JEVEscalate.evaluate("8 mahine ki pregnant h, subah se thoda khoon aa raha neeche se, dard nahi h")
        assertNotNull("Pregnancy vaginal bleeding must escalate", escPregnancy)
        assertEquals("ESC-2.1", escPregnancy?.first)

        val escPph = org.arogyasathi.app.jev.JEVEscalate.evaluate("abhi ghar pe delivery hui, khoon bahut beh raha, pad 3-4 minute me bhar ja raha")
        assertNotNull("Heavy postpartum bleeding must escalate", escPph)
        assertEquals("ESC-3.1", escPph?.first)

        // Dravidian obstetric emergency
        val escTelugu = org.arogyasathi.app.jev.JEVEscalate.evaluate("garbhavati sthri ki rakthasravam")
        assertNotNull("Telugu obstetric bleeding must escalate", escTelugu)
        assertEquals("ESC-2.1", escTelugu?.first)
    }

    @Test
    fun testNaturalLanguageMultilingualQueries() {
        val retriever = LayaRetriever(LocalRepository.sampleProtocols)

        // 1. Child Diarrhoea & ORS in Telugu / Hindi
        val hitsDiarrhoea = retriever.retrieve("pillavadiki loose motions avthunnai em cheyali", topK = 1)
        assertFalse(hitsDiarrhoea.isEmpty())
        assertTrue("Must retrieve diarrhoea section", hitsDiarrhoea.first().sectionId.startsWith("CHD-1"))

        // 2. Child Pneumonia & Respiratory in Hindi
        val hitsResp = retriever.retrieve("bacche ko bukhar aur khansi hai", topK = 1)
        assertFalse(hitsResp.isEmpty())
        assertTrue("Must retrieve respiratory or fever section", hitsResp.first().sectionId.startsWith("CHD-") || hitsResp.first().sectionId.startsWith("ESC-5"))

        // 3. Early ANC Checkup in Hindi
        val hitsAnc = retriever.retrieve("garbhavati ka pehla checkup kab karwaye", topK = 1)
        assertFalse(hitsAnc.isEmpty())
        assertTrue("Must retrieve ANC checkup section", hitsAnc.first().sectionId.startsWith("ANC-1"))

        // 4. Bathing Newborn in Telugu
        val hitsBathing = retriever.retrieve("snanam eppudu cheyali bidda ki", topK = 1)
        assertFalse(hitsBathing.isEmpty())
        assertEquals("HBNC-2.2", hitsBathing.first().sectionId)

        // 5. Pentavalent Vaccine in Telugu
        val hitsPenta = retriever.retrieve("penta tika eppudu veyali", topK = 1)
        assertFalse(hitsPenta.isEmpty())
        assertEquals("IMM-2.2", hitsPenta.first().sectionId)

        // 6. 9 Months Vaccine Schedule
        val hits9m = retriever.retrieve("9 months baby vaccine list", topK = 1)
        assertFalse(hits9m.isEmpty())
        assertEquals("IMM-2.3", hits9m.first().sectionId)

        // 7. ORS Preparation
        val hitsMakeOrs = retriever.retrieve("how to make ors at home", topK = 1)
        assertFalse(hitsMakeOrs.isEmpty())
        assertEquals("CHD-1.1", hitsMakeOrs.first().sectionId)
    }
}
