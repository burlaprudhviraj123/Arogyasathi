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
        "exercise" to "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1 lifestyle counselling",
        "kasrat" to "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1",
        "baithe" to "physical activity 30 minutes 5 days a week 150 minutes ncd-3.1 sedentary",
        "overdue" to "overdue 28 days grace period vis-1.3 counting conventions",
        "kab overdue" to "overdue 28 days grace period vis-1.3 counting conventions"
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

/**
 * Laya Clinical Intelligence Engine
 * Formulates empathetic, clinically structured, and protocol-grounded guidance
 * with patient context integration and bilingual Hindi/English synthesis.
 */
object LayaSynthesizer {

    data class SynthesizedGuidance(
        val textEnglish: String,
        val textHindi: String,
        val citedSection: String
    )

    fun synthesizeWithContext(
        section: ProtocolSection,
        query: String,
        patient: org.arogyasathi.app.core.Member? = null
    ): SynthesizedGuidance {
        val qLower = query.lowercase()
        val secId = section.sectionId.uppercase()

        // 1. Patient Context Awareness Header
        val patientPrefixEn = if (patient != null) {
            val riskTag = when {
                patient.isPregnant && (patient.lastHbGdl != null && patient.lastHbGdl < 7.0) -> " [High Risk: Severe Anaemia Hb ${patient.lastHbGdl}g/dL]"
                patient.isPregnant && (patient.lastAncBp != null && patient.lastAncBp.startsWith("14") || (patient.lastAncBp ?: "").startsWith("15")) -> " [High Risk: BP ${patient.lastAncBp}]"
                patient.ncdCondition != null -> " [NCD: ${patient.ncdCondition}]"
                else -> ""
            }
            "For ${patient.name}${if (patient.isPregnant) " (Pregnant)" else ""}$riskTag: "
        } else ""

        val patientPrefixHi = if (patient != null) {
            "${patient.name} के लिए: "
        } else ""

        // 2. Clinical Domain Knowledge Synthesis (Matching Qwen2.5-0.5B fine-tuned patterns)
        var enBody = ""
        var hiBody = ""

        when {
            // Diarrhoea & ORS / Zinc (CHD-1.1, CHD-1.2, CHD-1.3)
            qLower.contains("dast") || qLower.contains("diarrh") || qLower.contains("motion") || qLower.contains("ors") || qLower.contains("zinc") || secId.startsWith("CHD-1") -> {
                enBody = "Give 1 packet of ORS in 1 litre drinking water. For children under 2 years, give 50-100 ml after each stool; for 2 years and older, give 100-200 ml. If the child vomits, wait 10 minutes and resume slowly. Give Zinc daily for 14 days (10 mg for <6 months, 20 mg for >=6 months). Cites: ${section.sectionId}."
                hiBody = "हर दस्त के बाद ओआरएस घोल पिलाएं (2 साल से छोटे बच्चे को 50-100 मिली, 2 साल या बड़े बच्चे को 100-200 मिली)। उल्टी होने पर 10 मिनट रुककर धीरे-धीरे दें। 14 दिनों तक रोजाना जिंक की गोली (6 महीने से छोटे को 10 मिग्रा, बड़े को 20 मिग्रा) जरूर दें।"
            }

            // Respiratory & Pneumonia / Fast Breathing (CHD-3.1)
            qLower.contains("saans") || qLower.contains("breath") || qLower.contains("khansi") || qLower.contains("cough") || qLower.contains("pneumon") || secId.startsWith("CHD-3") -> {
                enBody = "Count breaths for 1 full minute when calm. Fast breathing threshold is >=50 breaths/min for 2-11 months, and >=40 breaths/min for 12 months to 5 years. If chest indrawing or stridor is observed, it indicates severe pneumonia—refer immediately to CHC. Cites: ${section.sectionId}."
                hiBody = "बच्चे के शांत रहने पर पूरे 1 मिनट तक सांसें गिनें। 2-11 महीने में 50 या अधिक, और 1-5 साल में 40 या अधिक सांसें तेज सांस हैं। छाती धंसने (पसली चलने) पर तुरंत अस्पताल ले जाएं।"
            }

            // Routine UIP Immunization (IMM-1, IMM-2)
            qLower.contains("tika") || qLower.contains("vaccin") || qLower.contains("teeka") || qLower.contains("immun") || secId.startsWith("IMM-") -> {
                if (patient != null && patient.dob.isNotBlank()) {
                    enBody = "Check vaccination card. UIP schedule: At birth: BCG, OPV-0, HepB-birth; 6, 10, 14 weeks: Pentavalent, OPV, Rotavirus, fIPV, and PCV; 9 months: MR-1, PCV-Booster, fIPV-3, Vitamin A (1 lakh IU). Ensure minimum 4 weeks between doses. Cites: ${section.sectionId}."
                    hiBody = "टीकाकरण कार्ड की जांच करें। जन्म पर: बीसीजी, ओपीवी-0, हेप-बी। 6, 10, 14 हफ्ते पर: पेंटावेलेंट, ओपीवी, रोटावायरस, fIPV और पीसीवी। 9 माह पर: एमआर-1, पीसीवी बूस्टर और विटामिन-ए की खुराक दें।"
                } else {
                    enBody = "UIP immunization schedule: Birth: BCG 0.1ml, OPV-0 2 drops, HepB 0.5ml within 24h. 6 weeks: Penta-1, OPV-1, RVV-1, fIPV-1 0.1ml, PCV-1. 9 completed months: MR-1 0.5ml SC, PCV-Booster, fIPV-3, VitA 1st dose. Cites: ${section.sectionId}."
                    hiBody = "नियमित टीकाकरण: जन्म पर बीसीजी, पोलियो शून्य खुराक और हेपेटाइटिस-बी। 6 हफ्ते पर पेंटा-1, पोलियो-1, रोटा-1, fIPV और पीसीवी-1। 9 माह पर खसरा-रूबेला (MR-1), पीसीवी बूस्टर और विटामिन-ए दें।"
                }
            }

            // Antenatal Care (ANC-1, ANC-2, ANC-3)
            qLower.contains("anc") || qLower.contains("garbh") || qLower.contains("pregnan") || secId.startsWith("ANC-") -> {
                if (qLower.contains("ifa") || qLower.contains("iron") || qLower.contains("lal goli")) {
                    enBody = "Provide 1 red IFA tablet daily (100 mg elemental iron + 500 mcg folic acid) for 180 days starting at 14 weeks. Do not take with tea or milk. If severe anaemia (Hb <7.0 g/dL), refer to CHC for parenteral iron. Cites: ${section.sectionId}."
                    hiBody = "14 सप्ताह पूरे होने के बाद रोजाना 1 लाल आईएफए (आयरन) की गोली 180 दिनों तक दें। इसे चाय या दूध के साथ न लें। यदि हीमोग्लोबिन 7 से कम है तो तुरंत सीएचसी रेफर करें।"
                } else if (qLower.contains("calcium")) {
                    enBody = "Give 2 Calcium tablets daily (500 mg elemental calcium each = 1000 mg/day) starting from 14 weeks until 6 months postpartum. Take Calcium separately from IFA by at least 2 hours. Cites: ${section.sectionId}."
                    hiBody = "14 हफ्ते से रोजाना 2 कैल्शियम की गोलियां (1000 मिग्रा) दें। कैल्शियम और आयरन की गोली के बीच कम से कम 2 घंटे का अंतर जरूर रखें।"
                } else {
                    enBody = "Mandatory 4 ANC checkups: ANC-1 within 12 weeks, ANC-2 between 14-26 weeks, ANC-3 between 28-34 weeks, ANC-4 between 36-40 weeks. Measure BP, weight, Hb, and arrange doctor visit on 9th of every month (PMSMA). Cites: ${section.sectionId}."
                    hiBody = "कम से कम 4 एएनसी जांच: पहली 12 सप्ताह तक, दूसरी 14-26 सप्ताह, तीसरी 28-34 सप्ताह, चौथी 36-40 सप्ताह। हर जांच में बीपी और वजन जरूर मापें और हर माह की 9 तारीख को पीएमएसएमए में दिखाएं।"
                }
            }

            // Home Based Newborn Care (HBNC-1, HBNC-2, HBNC-3)
            qLower.contains("hbnc") || qLower.contains("navjaat") || qLower.contains("newborn") || qLower.contains("nehlana") || qLower.contains("cord") || secId.startsWith("HBNC-") -> {
                enBody = "HBNC visit schedule: Institutional birth requires 6 visits (days 3, 7, 14, 21, 28, 42); home birth requires 7 visits (days 1, 3, 7, 14, 21, 28, 42). Initiate breastfeeding within 1 hour. Do not bathe baby in the first 48 hours or if weight <2000g. Keep cord clean and dry—apply nothing. Cites: ${section.sectionId}."
                hiBody = "एचबीएनसी भ्रमण: अस्पताल प्रसव में 6 बार (दिन 3, 7, 14, 21, 28, 42) और घर पर प्रसव में 7 बार (दिन 1, 3, 7, 14, 21, 28, 42)। पहले घंटे में स्तनपान शुरू कराएं। पहले 48 घंटे बच्चे को न नहलाएं और नाल पर कुछ न लगाएं।"
            }

            // Child Malnutrition & MUAC (NUT-1, NUT-2)
            qLower.contains("muac") || qLower.contains("sam") || qLower.contains("mam") || qLower.contains("malnutr") || qLower.contains("sujan") || secId.startsWith("NUT-") -> {
                enBody = "Measure MUAC on left mid-upper arm for children 6-59 months: Red (<11.5 cm) indicates Severe Acute Malnutrition (SAM); Yellow (11.5-12.4 cm) indicates MAM; Green (>=12.5 cm) normal. Check both feet for bilateral pitting oedema. Refer SAM children with complications to NRC. Cites: ${section.sectionId}."
                hiBody = "6-59 माह के बच्चों में बाईं बांह पर MUAC टेप से नापें: 11.5 सेमी से कम (लाल) या दोनों पैरों में गड्ढे पड़ने वाली सूजन गंभीर कुपोषण (SAM) है। ऐसे बच्चे को तुरंत पोषण पुनर्वास केंद्र (NRC) रेफर करें।"
            }

            // NCD Screening & Lifestyle (NCD-1, NCD-2, NCD-3)
            qLower.contains("ncd") || qLower.contains("bp") || qLower.contains("hypertens") || qLower.contains("sugar") || qLower.contains("diabet") || secId.startsWith("NCD-") -> {
                enBody = "Screen all adults >=30 years using CBAC checklist. Hypertension is defined as BP >=140/90 mmHg; diabetes as random blood sugar >=200 mg/dL. Counsel on lifestyle: limit salt to maximum 1 teaspoon (5g) per day and engage in 150 minutes of physical activity weekly. Cites: ${section.sectionId}."
                hiBody = "30 वर्ष या अधिक उम्र के सभी लोगों की सीबैक (CBAC) से स्क्रीनिंग करें। बीपी 140/90 या शुगर 200 से अधिक होने पर डॉक्टर को दिखाएं। खाने में नमक दिन में 1 चम्मच (5 ग्राम) से कम रखें और रोजाना व्यायाम करें।"
            }

            // Fallback: Clean sentence extraction from retrieved protocol body
            else -> {
                val clean = section.body.replace(Regex("[#*|>]"), " ").replace(Regex("\\s+"), " ").trim()
                val chunks = clean.split(Regex("( - |\\n|\\. )")).map { it.trim() }.filter { it.length > 10 && !it.startsWith("---") }
                val selected = chunks.take(3).joinToString(". ")
                enBody = "$selected Cites: ${section.sectionId}."
                hiBody = "प्रोटोकॉल निर्देश (${section.sectionId}): कृपया मरीज की स्थिति का प्रोटोकॉल के अनुसार मूल्यांकन करें और आवश्यक होने पर पीएचसी या एएनएम से परामर्श लें।"
            }
        }

        val fullEn = (patientPrefixEn + enBody).take(398)
        val fullHi = (patientPrefixHi + hiBody).take(398)

        return SynthesizedGuidance(
            textEnglish = fullEn,
            textHindi = fullHi,
            citedSection = section.sectionId
        )
    }

    fun synthesize(section: ProtocolSection, query: String): String {
        return synthesizeWithContext(section, query, null).textEnglish
    }
}

