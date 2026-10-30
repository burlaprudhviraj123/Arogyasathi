package org.arogyasathi.app.laya

import org.arogyasathi.app.core.ProtocolSection
import java.text.Normalizer
import java.util.Locale

data class LayaV2Config(
    val minimumBm25Score: Double = 0.15,
    val minimumEvidenceTerms: Int = 1,
    val maximumSections: Int = 2,
    val maximumQueryCharacters: Int = 2_000
) {
    init {
        require(minimumBm25Score.isFinite() && minimumBm25Score >= 0.0)
        require(minimumEvidenceTerms > 0)
        require(maximumSections > 0)
        require(maximumQueryCharacters > 0)
    }
}

data class LayaRankedSection(
    val section: ProtocolSection,
    val score: Double,
    val matchedTerms: Set<String>,
    val titleMatches: Int,
    val exactPhraseMatch: Boolean,
    val categoryMatch: Boolean
)

data class LayaCitation(
    val sectionId: String,
    val sourceFilename: String,
    val heading: String
) {
    fun format(): String = "$sourceFilename — $heading ($sectionId)"
}

data class LayaV2Response(
    val answer: String,
    val sections: List<ProtocolSection>,
    val citations: List<LayaCitation>,
    val abstained: Boolean,
    val retrievalScore: Double
)

object LayaProtocolSources {
    private val sourceByCategory = mapOf(
        "ANC" to "01_ANC_antenatal_care.md",
        "IMM" to "02_IMM_immunisation_schedule.md",
        "ESC" to "03_ESC_danger_signs_refer_immediately.md",
        "HBNC" to "04_HBNC_newborn_and_postnatal_care.md",
        "NUT" to "05_NUT_malnutrition_screening.md",
        "NCD" to "06_NCD_hypertension_diabetes.md",
        "CHD" to "07_CHD_diarrhoea_fever_breathing.md",
        "SCP" to "08_SCP_scope_and_redirect.md",
        "VIS" to "09_VIS_daily_visit_list_rules.md"
    )

    fun filenameFor(section: ProtocolSection): String =
        section.sourceFilename.takeIf { it.isNotBlank() }
            ?: sourceByCategory[section.category.uppercase(Locale.ROOT)]
            ?: ""
}

object LayaQueryNormalizer {
    private val canonicalWordForms = mapOf(
        "vaccines" to "vaccine",
        "1st" to "first",
        "2nd" to "second",
        "3rd" to "third",
        "4th" to "fourth",
        "weeks" to "week",
        "months" to "month",
        "days" to "day",
        "children" to "child",
        "babies" to "baby",
        "doses" to "dose",
        "tablets" to "tablet",
        "sessions" to "session"
    )

    private val spellingAndAbbreviationAliases = linkedMapOf(
        "दस्त" to "dast diarrhoea loose motions",
        "ओआरएस" to "ors",
        "टीका" to "tika teeka vaccine immunisation session",
        "टीकाकरण" to "tikakaran immunisation sessions due list",
        "गर्भवती" to "garbhwati pregnancy antenatal",
        "बुखार" to "bukhar fever",
        "सांस" to "saans breathing",
        "విరేచనాలు" to "virachanalu diarrhoea loose motions",
        "ఓఆర్ఎస్" to "ors",
        "టీకా" to "tika vaccine immunisation session",
        "గర్భిణి" to "garbhini pregnancy antenatal",
        "జ్వరం" to "jwaram fever",
        "శ్వాస" to "swasa breathing",
        "diarrhea" to "diarrhoea",
        "chautha" to "fourth",
        "chauthi" to "fourth",
        "hafte" to "week",
        "hafta" to "week",
        "hafton" to "weeks",
        "dusri" to "second",
        "doosri" to "second",
        "poochna" to "ask mother postnatal check",
        "puchna" to "ask mother postnatal check",
        "पूछना" to "ask mother postnatal check",
        "nehlana" to "bathe bathing",
        "nahana" to "bathe bathing",
        "ulti" to "vomit vomiting",
        "je" to "je endemic district",
        "immunization" to "immunisation",
        "immunizations" to "immunisation",
        "vaccination" to "vaccine",
        "vaccinations" to "vaccine",
        "bp" to "bp blood pressure",
        "b p" to "bp blood pressure",
        "high bp" to "high bp high blood pressure",
        "ifa" to "ifa iron folic acid",
        "iron folic" to "ifa iron folic acid",
        "loose motion" to "loose motions diarrhoea",
        "loose motions" to "loose motions diarrhoea",
        "loose stool" to "loose stools diarrhoea",
        "loose stools" to "loose stools diarrhoea",
        "breathlessness" to "breathlessness difficulty breathing",
        "fast breathing" to "rapid breathing",
        "high sugar" to "diabetes blood sugar",
        "sugar level" to "blood sugar"
    )

    private val questionAndActionWords = setOf(
        "how", "what", "when", "where", "why", "which", "can", "should", "do", "does",
        "kitna", "kitni", "kitne", "kaise", "kab", "kya", "kahan", "kyun", "batao",
        "dena", "de", "kare", "karna", "ela", "eppudu", "entha", "emiti", "cheppandi",
        "कितना", "कितनी", "कितने", "बताएं", "करें", "எப்படி", "எப்போது"
    )

    private val stopWords = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "to", "of", "for", "in", "on",
        "and", "or", "with", "my", "me", "this", "that", "please", "tell", "give", "use",
        "hai", "hain", "ka", "ki", "ke", "ko", "mein", "aur", "kya", "kaise", "kab",
        "kitna", "kitni", "kitne", "kare", "karna", "dena", "lo", "ela", "eppudu",
        "emiti", "cheppandi"
    )

    data class NormalizedQuery(
        val text: String,
        val originalTerms: Set<String>,
        val expandedTerms: Set<String>,
        val hasQuestionOrActionCue: Boolean
    )

    fun normalize(query: String): NormalizedQuery {
        val normalized = Normalizer.normalize(query, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        var canonical = normalized
        spellingAndAbbreviationAliases.forEach { (alias, replacement) ->
            canonical = canonical.replace(
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])"),
                replacement
            )
        }

        val originalTerms = tokens(canonical)
            .filterNot { it in stopWords || it.matches(Regex("\\d+(?:\\.\\d+)?")) }
            .toSet()
        val expandedTerms = LayaLexicon.expand(canonical)
            .flatMap(::tokens)
            .filterNot(::isNonEvidenceToken)
            .toSet()
        val cue = query.contains("?") ||
            tokens(normalized).any { it in questionAndActionWords } ||
            normalized.contains("ఎలా") ||
            normalized.contains("ఎప్పుడు") ||
            normalized.contains("क्या") ||
            normalized.contains("कैसे") ||
            normalized.contains("कब") ||
            normalized.contains("कितना") ||
            normalized.contains("कितनी") ||
            normalized.contains("कितने") ||
            normalized.contains("ఎంత") ||
            normalized.contains("చేయాలి")

        return NormalizedQuery(canonical, originalTerms, expandedTerms, cue)
    }

    fun tokens(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+(?:\\.\\p{N}+)?")
            .findAll(text.lowercase(Locale.ROOT))
            .map { canonicalWordForms[it.value] ?: it.value }
            .toList()

    fun isNonEvidenceToken(token: String): Boolean =
        token in stopWords ||
            token.matches(Regex("\\d+(?:\\.\\d+)?")) ||
            token.matches(Regex("[a-z]{2,4}-\\d+(?:\\.\\d+)?"))
}

class LayaV2Engine(
    private val sections: List<ProtocolSection>,
    private val config: LayaV2Config = LayaV2Config()
) {
    private val retriever = LayaRetriever(sections)
    private val categorySignals = mapOf(
        "ANC" to setOf("pregnancy", "pregnant", "antenatal", "anc", "lmp", "edd", "maternal", "garbh", "ifa", "folic", "calcium", "trimester"),
        "IMM" to setOf("immunisation", "vaccine", "vaccination", "tika", "teeka", "bcg", "penta", "opv", "mr"),
        "ESC" to setOf("danger", "emergency", "refer", "referral", "unconscious", "convulsions", "bleeding", "fit"),
        "HBNC" to setOf("newborn", "newborns", "hbnc", "breastfeeding", "umbilicus", "cord", "birth", "mother", "maa", "jachcha", "postnatal"),
        "NUT" to setOf("nutrition", "malnutrition", "muac", "sam", "mam", "oedema", "edema"),
        "NCD" to setOf("hypertension", "diabetes", "blood", "pressure", "sugar", "ncd", "bp"),
        "CHD" to setOf("diarrhoea", "diarrhea", "ors", "zinc", "fever", "cough", "breathing", "vomiting", "child", "pneumonia"),
        "VIS" to setOf("visit", "schedule", "overdue", "household", "due")
    )

    fun answer(query: String): LayaV2Response {
        if (query.length > config.maximumQueryCharacters) return abstention()
        val normalized = LayaQueryNormalizer.normalize(query)
        if (normalized.originalTerms.isEmpty() ||
            (normalized.originalTerms.size < 2 && !normalized.hasQuestionOrActionCue)
        ) {
            return abstention()
        }

        val intents = detectCategories(normalized.originalTerms)
        val ranked = retriever.retrieveRanked(
            normalized = normalized,
            topK = maxOf(config.maximumSections * 4, 8),
            categoryFilter = intents
        )
        val evidence = ranked.filter { hit ->
            hit.score >= config.minimumBm25Score &&
                hit.matchedTerms.size >= config.minimumEvidenceTerms &&
                (hit.matchedTerms.size >= 2 ||
                    (normalized.hasQuestionOrActionCue && hit.titleMatches > 0))
        }
        if (evidence.isEmpty()) return abstention()

        val selected = mutableListOf<LayaRankedSection>()
        val coveredTerms = mutableSetOf<String>()
        evidence.forEach { hit ->
            if (selected.size >= config.maximumSections) return@forEach
            val newTerms = hit.matchedTerms - coveredTerms
            if (selected.isEmpty() || newTerms.isNotEmpty() ||
                hasConflictingDoseEvidence(selected + hit, normalized.text)
            ) {
                selected += hit
                coveredTerms += hit.matchedTerms
            }
        }
        if (hasConflictingDoseEvidence(selected, normalized.text)) return abstention()

        val citations = selected.mapNotNull { hit ->
            val filename = LayaProtocolSources.filenameFor(hit.section)
            if (filename.isBlank()) return@mapNotNull null
            LayaCitation(
                sectionId = hit.section.sectionId,
                sourceFilename = filename,
                heading = extractHeading(hit.section)
            )
        }
        if (citations.size != selected.size) return abstention()

        val answer = selected.zip(citations).joinToString("\n\n") { (hit, citation) ->
            val sourceText = extractRelevantBlocks(hit)
            "$sourceText\n\nSource: ${citation.format()}"
        }

        return LayaV2Response(
            answer = answer,
            sections = selected.map { it.section },
            citations = citations,
            abstained = false,
            retrievalScore = selected.firstOrNull()?.score ?: 0.0
        )
    }

    private fun detectCategories(terms: Set<String>): Set<String> {
        val explicitCategories = terms.intersect(setOf("anc", "imm", "esc", "hbnc", "nut", "ncd", "chd"))
        if (explicitCategories.isNotEmpty()) return explicitCategories.map(String::uppercase).toSet()

        val matched = categorySignals.mapValues { (_, vocabulary) -> terms.count { it in vocabulary } }
            .filterValues { it > 0 }
        if (matched.isEmpty()) return emptySet()
        if ("overdue" in terms && "VIS" in matched) return setOf("VIS")
        val best = matched.values.maxOrNull() ?: return emptySet()
        return matched.filterValues { it == best }.keys
    }

    private fun extractHeading(section: ProtocolSection): String {
        val heading = section.body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#") }
            ?.replace(Regex("^#{1,6}\\s*"), "")
            ?.replaceFirst(Regex("^[A-Z]{2,4}-\\d+(?:\\.\\d+)?\\s*"), "")
            ?.trim()
        return heading?.takeIf { it.isNotBlank() } ?: section.title
    }

    private fun extractRelevantBlocks(hit: LayaRankedSection): String {
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()
        fun saveBlock() {
            if (current.isNotEmpty()) {
                blocks += current.joinToString("\n").trim()
                current.clear()
            }
        }

        hit.section.body.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                saveBlock()
            } else if (!trimmed.startsWith("#")) {
                current += trimmed
            }
        }
        saveBlock()

        val relevant = blocks.mapIndexed { index, block ->
            val blockTerms = LayaQueryNormalizer.tokens(block).toSet()
            val overlap = blockTerms.intersect(hit.matchedTerms).size
            Triple(index, overlap, block)
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.second }.thenBy { it.first })
            .take(3)
            .map { it.third }
        return (relevant.ifEmpty { blocks }).joinToString("\n\n").trim()
    }

    private fun hasConflictingDoseEvidence(
        selected: List<LayaRankedSection>,
        query: String
    ): Boolean {
        val doseAnchors = setOf(
            "zinc", "ors", "ifa", "iron", "folic", "calcium",
            "insulin", "metformin", "amlodipine", "paracetamol", "amoxicillin"
        )
        val queryDoseAnchors = LayaQueryNormalizer.tokens(query).toSet().intersect(doseAnchors)
        if (queryDoseAnchors.isEmpty() ||
            Regex("\\b(?:how many|kitne|kitni)\\s+(?:days?|din|weeks?|haft[a-z]*)\\b")
                .containsMatchIn(query)
        ) return false
        val asksForAmount = Regex(
            "\\b(dose|dosage|how much|mg|mcg|ml|kitna|kitni|kitne)\\b|कितना|कितनी|कितने|खुराक|मात्रा|ఎంత|మోతాదు"
        )
            .containsMatchIn(query)
        if (!asksForAmount || selected.size < 2) return false

        fun facts(text: String): Map<String, Set<Double>> {
            val matches = Regex(
                "(?i)(\\d+(?:\\.\\d+)?)\\s*(mg|mcg|ml|litres?|liters?|l)\\b"
            ).findAll(text)
            return matches.groupBy(
                keySelector = { it.groupValues[2].lowercase(Locale.ROOT).removeSuffix("s") },
                valueTransform = { it.groupValues[1].toDouble() }
            ).mapValues { (_, values) -> values.toSet() }
        }

        for (leftIndex in selected.indices) {
            for (rightIndex in leftIndex + 1 until selected.size) {
                val left = selected[leftIndex]
                val right = selected[rightIndex]
                val sharedTerms = left.matchedTerms.intersect(right.matchedTerms)
                val sharedDoseAnchors = sharedTerms.intersect(doseAnchors)
                if (queryDoseAnchors.isNotEmpty()) {
                    if (sharedDoseAnchors.intersect(queryDoseAnchors).isEmpty()) continue
                } else if (sharedTerms.isEmpty()) {
                    continue
                }
                val leftFacts = facts(left.section.body)
                val rightFacts = facts(right.section.body)
                if (leftFacts.any { (unit, values) ->
                        val otherValues = rightFacts[unit].orEmpty()
                        otherValues.isNotEmpty() && values != otherValues
                    }
                ) return true
            }
        }
        return false
    }

    private fun abstention() = LayaV2Response(
        answer = "The approved protocols do not provide enough information to answer this question safely. Please consult your ANM or the Medical Officer at the PHC. If there is an emergency or danger sign, refer immediately.",
        sections = emptyList(),
        citations = emptyList(),
        abstained = true,
        retrievalScore = 0.0
    )
}
