package org.arogyasathi.app.laya

import org.arogyasathi.app.core.ProtocolSection
import kotlin.math.ln

object LayaLexicon {
    private val lexicon = mapOf(
        "dast" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "motion" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "motions" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "ors" to "ors packet 100 200 ml 50 100 ml 10 minutes wait chd-1.2",
        "zinc" to "zinc 14 days 20 mg 10 mg 6 months chd-1.3",
        "bukhar" to "fever 38 100.4 chd-2.1 chd-2.2",
        "taap" to "fever chd-2.1 chd-2.2",
        "tap" to "fever chd-2.1 chd-2.2",
        "saans" to "breaths per minute 50 breaths 40 breaths 60 breaths fast breathing chd-3.1",
        "swasa" to "breaths per minute 50 breaths 40 breaths fast breathing chd-3.1",
        "daggu" to "cough fast breathing chd-3.1",
        "khansi" to "cough fast breathing chd-3.1",
        "tika" to "vaccine immunisation session imm-1.1 imm-2",
        "teeka" to "vaccine immunisation imm-1.1 imm-2",
        "tike" to "vaccine immunisation imm-1.1 imm-2",
        "penta" to "penta pentavalent till one year of age 6 10 14 weeks imm-2.2",
        "bcg" to "bcg till one year birth 0.1 ml imm-2.1",
        "mr" to "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
        "khasra" to "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
        "dpt" to "dpt booster 16 24 months 5 6 years 7 years imm-2.4 imm-2.5",
        "vitamin a" to "vitamin a 9 doses every 6 months 1 lakh 2 lakh iu imm-2.4",
        "je" to "chandrapuri not a je-endemic district no je imm-2.4",
        "td" to "td tetanus diphtheria pregnancy anc-2.5 imm-4.1",
        "ifa" to "red ifa tablet 180 days 100 mg iron anc-2.2 hbnc-3.1",
        "lal goli" to "red ifa tablet 180 days iron anc-2.2 hbnc-3.1",
        "calcium" to "calcium two tablets a day 500 mg 6 months anc-2.3 hbnc-3.1",
        "hb" to "haemoglobin 7.0 9.9 11.0 moderate mild severe anc-3.1",
        "khoon ki kami" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "edd" to "expected date of delivery lmp 280 days 9 months 7 days anc-1.1",
        "pmsma" to "pmsma 9th of every month doctor checkup anc-1.3",
        "hbnc" to "hbnc visit schedule institutional 6 home 7 days 1 3 7 14 21 28 42 hbnc-1.1",
        "nehlana" to "bathe bathing first 48 hours 2000 g hbnc-2.2",
        "nehlayen" to "bathe bathing first 48 hours 2000 g hbnc-2.2",
        "doodh" to "breastfeeding within 1 hour exclusive 6 months colostrum hbnc-2.1",
        "muac" to "muac 6 59 months 11.5 12.5 cm yellow red green nut-1.1 nut-1.2",
        "oedema" to "bilateral pitting oedema both feet dent pit nut-1.1",
        "sujan" to "bilateral pitting oedema both feet dent pit nut-1.1",
        "sam" to "sam severe acute malnutrition nrc 11.5 cm nut-2.1",
        "mam" to "mam moderate acute malnutrition 11.5 12.4 cm nut-2.3",
        "ncd" to "hypertension diabetes screening 30 years cbac ncd-1.1",
        "bp" to "blood pressure 140 90 hypertension monthly ncd-1.2 ncd-2.1",
        "sugar" to "diabetes random blood sugar 200 mg dl ncd-1.3",
        "salt" to "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
        "namak" to "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
        "exercise" to "physical activity 30 minutes 5 days a week 150 minutes ncd-3.1",
        "overdue" to "grace period 28 days overdue vis-1.3"
    )

    fun expand(text: String): List<String> {
        val raw = Regex("[a-zA-Z0-9]+").findAll(text.lowercase()).map { it.value }.toList()
        val out = raw.toMutableList()
        val joined = " " + raw.joinToString(" ") + " "
        for ((k, v) in lexicon) {
            if (k.contains(" ") && joined.contains(" $k ")) {
                out.addAll(v.split(" "))
            } else if (k in raw) {
                out.addAll(v.split(" "))
            }
        }
        return out
    }
}

class LayaRetriever(private val sections: List<ProtocolSection>) {
    private val docTokens = sections.map { sec ->
        LayaLexicon.expand("${sec.sectionId} ${sec.title} ${sec.title} ${sec.title} ${sec.body}")
    }
    private val docCount = sections.size
    private val avgDocLen = docTokens.map { it.size }.average()
    private val docFreqs = mutableMapOf<String, Int>().apply {
        for (tokens in docTokens) {
            for (t in tokens.toSet()) {
                this[t] = (this[t] ?: 0) + 1
            }
        }
    }

    fun retrieve(query: String, topK: Int = 2): List<ProtocolSection> {
        val qTokens = LayaLexicon.expand(query)
        val k1 = 1.5
        val b = 0.75

        val scored = sections.indices.map { i ->
            val dTokens = docTokens[i]
            val dLen = dTokens.size
            val tfMap = mutableMapOf<String, Int>()
            for (t in dTokens) tfMap[t] = (tfMap[t] ?: 0) + 1

            var score = 0.0
            for (qt in qTokens) {
                val tf = tfMap[qt] ?: continue
                val df = docFreqs[qt] ?: 0
                val idf = ln(1.0 + (docCount - df + 0.5) / (df + 0.5))
                val denom = tf + k1 * (1.0 - b + b * (dLen / avgDocLen))
                score += idf * (tf * (k1 + 1.0)) / denom
            }
            Pair(score, sections[i])
        }.sortedByDescending { it.first }

        return scored.take(topK).map { it.second }
    }
}

object LayaSynthesizer {
    fun synthesize(section: ProtocolSection, query: String): String {
        val clean = section.body.replace(Regex("[#*|>]"), " ").replace(Regex("\\s+"), " ").trim()
        val chunks = clean.split(Regex("( - |\\n|\\. )")).map { it.trim() }.filter { it.length > 5 && !it.startsWith("---") }
        val qTokens = LayaLexicon.expand(query).toSet()

        val scoredChunks = chunks.map { chunk ->
            val cWords = Regex("[a-zA-Z0-9]+").findAll(chunk.lowercase()).map { it.value }.toSet()
            val matchCount = qTokens.intersect(cWords).size
            Pair(matchCount, chunk)
        }.sortedByDescending { it.first }

        val selected = mutableListOf<String>()
        var curLen = 0
        for ((_, chunk) in scoredChunks) {
            if (curLen + chunk.length + 2 <= 380) {
                selected.add(chunk)
                curLen += chunk.length + 2
            }
            if (curLen >= 260) break
        }

        val baseText = if (selected.isNotEmpty()) selected.joinToString(". ") else clean.take(380)
        val suffix = " (${section.sectionId})."
        return if (baseText.length + suffix.length <= 400) {
            baseText + suffix
        } else {
            baseText.take(400 - suffix.length) + suffix
        }
    }
}

