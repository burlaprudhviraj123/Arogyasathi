package org.arogyasathi.app

import org.arogyasathi.app.core.ProtocolSection
import org.arogyasathi.app.jev.JEVEscalate
import org.arogyasathi.app.jev.JEVScope
import org.arogyasathi.app.laya.LayaQueryNormalizer
import org.arogyasathi.app.laya.LayaV2Config
import org.arogyasathi.app.laya.LayaV2Engine
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class LayaV2Test {
    private val protocols = listOf(
        ProtocolSection(
            "CHD-1.1",
            "Preparing ORS",
            "### CHD-1.1 Preparing ORS\n- Mix 1 packet of ORS in 1 litre of clean drinking water. Use within 24 hours.",
            "CHD",
            "07_CHD_diarrhoea_fever_breathing.md"
        ),
        ProtocolSection(
            "CHD-1.2",
            "ORS and zinc for diarrhoea",
            "### CHD-1.2 ORS and zinc for diarrhoea\n- Children under 2 years: give 50 to 100 ml after each loose stool. Give Zinc 10 mg daily for 14 days if under 6 months; 20 mg daily if 6 months or older.",
            "CHD",
            "07_CHD_diarrhoea_fever_breathing.md"
        ),
        ProtocolSection(
            "IMM-2.2",
            "Vaccines at 6, 10 and 14 weeks",
            "### IMM-2.2 Vaccines at 6, 10 and 14 weeks\n- Penta-1, OPV-1, RVV-1, fIPV-1 and PCV-1 are due at 6 weeks.",
            "IMM",
            "02_IMM_immunisation_schedule.md"
        ),
        ProtocolSection(
            "ANC-2.2",
            "Iron and folic acid",
            "### ANC-2.2 Iron and folic acid\n- Give one IFA tablet daily during pregnancy.",
            "ANC",
            "01_ANC_antenatal_care.md"
        ),
        ProtocolSection(
            "SCP-1.2",
            "Not covered — redirect to the doctor",
            "### SCP-1.2 Not covered — redirect to the doctor\n- Ask the Medical Officer or ANM about questions outside these protocols.",
            "SCP",
            "08_SCP_scope_and_redirect.md"
        )
    )

    @Test
    fun englishAndParaphrasedQuestionsReturnSourceEvidence() {
        val engine = LayaV2Engine(protocols)
        val direct = engine.answer("How do I prepare ORS?")
        val paraphrase = engine.answer("How should I mix an ORS packet?")

        assertFalse(direct.abstained)
        assertFalse(paraphrase.abstained)
        assertEquals("CHD-1.1", direct.citations.first().sectionId)
        assertTrue(direct.answer.contains("1 packet of ORS in 1 litre"))
        assertTrue(direct.answer.contains("07_CHD_diarrhoea_fever_breathing.md"))
        assertTrue(direct.answer.contains("Preparing ORS (CHD-1.1)"))
    }

    @Test
    fun hindiTeluguAndHinglishTermsNormalizeToProtocolTerms() {
        val engine = LayaV2Engine(protocols)
        val hindi = engine.answer("बच्चे को दस्त होने पर कितना ओआरएस दें?")
        val telugu = engine.answer("పిల్లవాడికి విరేచనాలు వస్తే ఎంత ఓఆర్ఎస్ ఇవ్వాలి?")
        val hinglish = engine.answer("bacche ko dast me zinc kitna dena hai?")

        assertFalse("Hindi question should retrieve a protocol", hindi.abstained)
        assertFalse("Telugu question should retrieve a protocol", telugu.abstained)
        assertFalse("Hinglish question should retrieve a protocol", hinglish.abstained)
        assertTrue(hindi.citations.any { it.sectionId == "CHD-1.2" })
        assertTrue(telugu.citations.any { it.sectionId == "CHD-1.2" })
        assertTrue(hinglish.citations.any { it.sectionId == "CHD-1.2" })
        assertTrue(LayaQueryNormalizer.normalize("B.P.").expandedTerms.contains("pressure"))
    }

    @Test
    fun vaccineIntentFiltersIrrelevantProtocolCategories() {
        val answer = LayaV2Engine(protocols).answer("When is the first vaccine due?")

        assertFalse(answer.abstained)
        assertEquals("IMM-2.2", answer.citations.first().sectionId)
        assertTrue(answer.citations.all { it.sourceFilename == "02_IMM_immunisation_schedule.md" })
    }

    @Test
    fun multiTopicQuestionCanCiteMultipleRetrievedSections() {
        val answer = LayaV2Engine(protocols).answer("How do I prepare ORS and what zinc dose is written?")

        assertFalse(answer.abstained)
        assertTrue(answer.citations.any { it.sectionId == "CHD-1.1" })
        assertTrue(answer.citations.any { it.sectionId == "CHD-1.2" })
        assertTrue(answer.answer.contains("07_CHD_diarrhoea_fever_breathing.md"))
    }

    @Test
    fun ambiguousUnsupportedAndUncitableQuestionsAbstain() {
        val engine = LayaV2Engine(protocols)

        val ambiguous = engine.answer("ORS")
        val unsupported = engine.answer("When is the village council meeting?")
        val untraceable = LayaV2Engine(
            listOf(ProtocolSection("XYZ-1.1", "unknown", "### XYZ-1.1 unknown\nRelevant clinical fact.", "XYZ"))
        ).answer("What is the clinical fact?")

        assertTrue(ambiguous.abstained)
        assertTrue(unsupported.abstained)
        assertTrue(untraceable.abstained)
        assertTrue(ambiguous.citations.isEmpty())
    }

    @Test
    fun emergencyAndOutOfScopeGatesRemainDeterministic() {
        assertNotNull(JEVEscalate.evaluate("The patient is unconscious"))
        assertNotNull(JEVScope.evaluate("Which paracetamol dose should I give?"))
        assertNotNull(JEVScope.evaluate("What should I do about dengue?"))
    }

    @Test
    fun conflictingDosageEvidenceTriggersAbstention() {
        val conflicting = protocols + ProtocolSection(
            "CHD-1.3",
            "Zinc dose",
            "### CHD-1.3 Zinc dose\n- Give Zinc 30 mg daily for 14 days.",
            "CHD",
            "07_CHD_diarrhoea_fever_breathing.md"
        )
        val answer = LayaV2Engine(conflicting).answer("How much zinc dose should I give in mg?")

        assertTrue(answer.abstained)
        assertTrue(answer.citations.isEmpty())
    }

    @Test
    fun retrievalLatencyAndRecallAreMeasuredForRepresentativeQueries() {
        val engine = LayaV2Engine(protocols)
        val cases = listOf(
            "How do I prepare ORS?" to "CHD-1.1",
            "When is the first vaccine due?" to "IMM-2.2",
            "How much IFA is given during pregnancy?" to "ANC-2.2"
        )
        var correct = 0
        val elapsed = mutableListOf<Long>()
        cases.forEach { (query, expectedId) ->
            val startedAt = System.nanoTime()
            val answer = engine.answer(query)
            elapsed += (System.nanoTime() - startedAt) / 1_000_000L
            if (answer.citations.any { it.sectionId == expectedId }) correct++
            assertFalse("Expected evidence for: $query", answer.abstained)
        }

        assertEquals(1.0, correct.toDouble() / cases.size, 0.0)
        assertTrue("All questions must cite a source", cases.all { (query, _) ->
            engine.answer(query).citations.isNotEmpty()
        })
        assertTrue("Median local latency should stay below 250 ms", elapsed.sorted()[elapsed.size / 2] < 250L)
    }

    @Test
    fun sourceMarkdownCorpusSupportsTraceableMultilingualRetrieval() {
        val sourceFiles = listOf(
            "01_ANC_antenatal_care.md",
            "02_IMM_immunisation_schedule.md",
            "03_ESC_danger_signs_refer_immediately.md",
            "04_HBNC_newborn_and_postnatal_care.md",
            "05_NUT_malnutrition_screening.md",
            "06_NCD_hypertension_diabetes.md",
            "07_CHD_diarrhoea_fever_breathing.md",
            "08_SCP_scope_and_redirect.md",
            "09_VIS_daily_visit_list_rules.md"
        )
        val sourceSections = sourceFiles.flatMap(::parseMarkdown)
        assertEquals("All 107 source leaf sections should be indexed", 107, sourceSections.size)

        val engine = LayaV2Engine(sourceSections)
        val hinglishZincQuery = "bacche ko dast me zinc kitna dena hai?"
        assertNull("A protocol-supported zinc question must not be blocked by scope rules", JEVScope.evaluate(hinglishZincQuery))
        val zincResponse = engine.answer(hinglishZincQuery)
        assertFalse("Expected zinc guidance for $hinglishZincQuery", zincResponse.abstained)
        assertTrue(
            "Expected the zinc section in citations for $hinglishZincQuery; got ${zincResponse.citations}",
            zincResponse.citations.any { it.sectionId == "CHD-1.3" }
        )
        assertTrue(zincResponse.answer.contains("14 days"))
        assertTrue(zincResponse.answer.contains("10 mg"))
        assertTrue(zincResponse.answer.contains("20 mg"))

        val cases = listOf(
            "How do I prepare ORS?" to "CHD-1.1",
            "बच्चे को दस्त होने पर कितना ओआरएस दें?" to "CHD-1.2",
            "పిల్లవాడికి విరేచనాలు వస్తే ఎంత ఓఆర్ఎస్ ఇవ్వాలి?" to "CHD-1.2",
            hinglishZincQuery to "CHD-1.3",
            "Which vaccines are due at six weeks?" to "IMM-2.2",
            "పిల్లలకు టీకా ఎప్పుడు ఇవ్వాలి?" to "IMM-1.1",
            "बच्चे का टीकाकरण कब है?" to "IMM-1.2"
        )
        val latencyMs = mutableListOf<Long>()
        cases.forEach { (query, expectedSection) ->
            val startedAt = System.nanoTime()
            val response = engine.answer(query)
            latencyMs += (System.nanoTime() - startedAt) / 1_000_000L
            assertFalse("Expected source evidence for $query", response.abstained)
            assertTrue(
                "Expected $expectedSection in citations for $query; got ${response.citations}",
                response.citations.any { it.sectionId == expectedSection }
            )
            assertTrue(
                "Citation must use the source Markdown filename",
                response.citations.all { citation ->
                    sourceFiles.contains(citation.sourceFilename)
                }
            )
        }
        val medianLatencyMs = latencyMs.sorted()[latencyMs.size / 2]
        println(
            "Laya V2 markdown-corpus metrics: Recall@3=1.0, citation accuracy=1.0, " +
                "median JVM latency=${medianLatencyMs}ms"
        )
        assertTrue("Markdown-corpus median JVM latency should stay below 250 ms", medianLatencyMs < 250L)
    }

    private fun parseMarkdown(filename: String): List<ProtocolSection> {
        val input = javaClass.classLoader?.getResourceAsStream(filename)
            ?: error("Missing source protocol test resource: $filename")
        val lines = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readLines() }
        val sectionHeader = Regex("^###\\s+([A-Z]{2,4}-\\d+(?:\\.\\d+)?)\\s*(.*)$")
        val sections = mutableListOf<ProtocolSection>()
        var currentId: String? = null
        var currentTitle = ""
        var currentBody = mutableListOf<String>()

        fun saveCurrent() {
            val id = currentId ?: return
            sections += ProtocolSection(
                sectionId = id,
                title = currentTitle,
                body = currentBody.joinToString("\n").trim(),
                category = id.substringBefore('-'),
                sourceFilename = filename
            )
            currentId = null
            currentBody = mutableListOf()
        }

        lines.forEach { line ->
            val match = sectionHeader.matchEntire(line)
            when {
                match != null -> {
                    saveCurrent()
                    currentId = match.groupValues[1]
                    currentTitle = match.groupValues[2].trim()
                    currentBody = mutableListOf(line)
                }
                Regex("^#{1,2}\\s").containsMatchIn(line) && currentId != null -> saveCurrent()
                currentId != null -> currentBody += line
            }
        }
        saveCurrent()
        return sections
    }
}
