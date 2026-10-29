package org.arogyasathi.app.laya

import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.PatientConsultation
import org.arogyasathi.app.core.ProtocolSection
import kotlin.math.ln

data class PatientAgeInfo(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalMonths: Int,
    val totalDays: Int,
    val ageDisplay: String,
    val isChildUnderTwo: Boolean,
    val isChildTwoToFive: Boolean,
    val isChildUnderFive: Boolean,
    val isInfantUnderOne: Boolean,
    val isUnderSixMonths: Boolean,
    val isNewbornUnderTwoMonths: Boolean,
    val isAdult: Boolean
)

object PatientAgeCalculator {
    fun calculate(dobStr: String, refDate: String = "2026-10-26"): PatientAgeInfo? {
        try {
            val dParts = dobStr.trim().split("-").map { it.toInt() }
            val rParts = refDate.trim().split("-").map { it.toInt() }
            if (dParts.size != 3 || rParts.size != 3) return null

            var y = rParts[0] - dParts[0]
            var m = rParts[1] - dParts[1]
            var d = rParts[2] - dParts[2]

            if (d < 0) {
                m -= 1
                d += 30
            }
            if (m < 0) {
                y -= 1
                m += 12
            }

            val totalDays = y * 365 + m * 30 + d
            val totalMonths = y * 12 + m

            val ageDisplay = when {
                totalDays <= 60 -> "$totalDays days"
                totalMonths < 24 -> "$totalMonths months"
                else -> "$y years${if (m > 0) " $m months" else ""}"
            }

            return PatientAgeInfo(
                years = y,
                months = m,
                days = d,
                totalMonths = totalMonths,
                totalDays = totalDays,
                ageDisplay = ageDisplay,
                isChildUnderTwo = totalMonths < 24,
                isChildTwoToFive = totalMonths in 24..60,
                isChildUnderFive = y < 5,
                isInfantUnderOne = totalMonths < 12,
                isUnderSixMonths = totalMonths < 6,
                isNewbornUnderTwoMonths = totalDays <= 60,
                isAdult = y >= 18
            )
        } catch (e: Exception) {
            return null
        }
    }
}

object LayaLexicon {
    private val lexicon = mapOf(
        // Diarrhoea & ORS / Zinc (Hindi, Telugu, Hinglish, English)
        "dast" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "motion" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "motions" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "motions avthunnai" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "avthunnai" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "virachanalu" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "neellu neelluga" to "diarrhoea loose motions watery chd-1.1 chd-1.2",
        "ors" to "ors packet 100 200 ml 50 100 ml 10 minutes wait chd-1.2",
        "zinc" to "zinc 14 days 20 mg 10 mg 6 months chd-1.3",
        "kadupu noppi" to "abdominal pain chd-1.1",
        "vanthulu" to "vomiting vomit chd-1.2",
        "vanthulu avthunnai" to "vomiting vomit chd-1.2",

        // Fever & Respiratory / Pneumonia
        "bukhar" to "fever 38 100.4 chd-2.1 chd-2.2",
        "taap" to "fever chd-2.1 chd-2.2",
        "tap" to "fever chd-2.1 chd-2.2",
        "jwaram" to "fever chd-2.1 chd-2.2",
        "saans" to "breaths per minute 50 breaths 40 breaths 60 breaths fast breathing chd-3.1",
        "swasa" to "breaths per minute 50 breaths 40 breaths fast breathing chd-3.1",
        "swasa kastam" to "breaths fast breathing pneumonia chd-3.1 esc-5.2",
        "daggu" to "cough fast breathing chd-3.1",
        "khansi" to "cough fast breathing chd-3.1",
        "pasli" to "chest indrawing esc-5.2",

        // Immunization & Vaccines
        "tika" to "vaccine immunisation session imm-1.1 imm-2",
        "teeka" to "vaccine immunisation imm-1.1 imm-2",
        "tike" to "vaccine immunisation imm-1.1 imm-2",
        "tika eppudu" to "vaccine due schedule imm-2",
        "penta" to "penta pentavalent till one year of age 6 10 14 weeks imm-2.2",
        "bcg" to "bcg till one year birth 0.1 ml imm-2.1",
        "mr" to "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
        "khasra" to "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
        "dpt" to "dpt booster 16 24 months 5 6 years 7 years imm-2.4 imm-2.5",
        "vitamin a" to "vitamin a 9 doses every 6 months 1 lakh 2 lakh iu imm-2.4",
        "je" to "chandrapuri not a je-endemic district no je imm-2.4",
        "td" to "td tetanus diphtheria pregnancy anc-2.5 imm-4.1",

        // Maternal & ANC
        "pregnant" to "pregnancy antenatal anc-1.1 anc-1.2",
        "garbh" to "pregnancy antenatal anc-1.1 anc-1.2",
        "garbhvati" to "pregnancy antenatal anc-1.1 anc-1.2",
        "garbhavathi" to "pregnancy antenatal anc-1.1 anc-1.2",
        "anc" to "antenatal checkup minimum four anc anc-1.2",
        "ifa" to "red ifa tablet 180 days 100 mg iron anc-2.2 hbnc-3.1",
        "lal goli" to "red ifa tablet 180 days iron anc-2.2 hbnc-3.1",
        "calcium" to "calcium two tablets a day 500 mg 6 months anc-2.3 hbnc-3.1",
        "hb" to "haemoglobin 7.0 9.9 11.0 moderate mild severe anc-3.1",
        "khoon ki kami" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "edd" to "expected date of delivery lmp 280 days 9 months 7 days anc-1.1",
        "pmsma" to "pmsma 9th of every month doctor checkup anc-1.3",

        // Newborn & HBNC
        "hbnc" to "hbnc visit schedule institutional 6 home 7 days 1 3 7 14 21 28 42 hbnc-1.1",
        "nehlana" to "bathe bathing first 48 hours 2000 g hbnc-2.2",
        "nehlayen" to "bathe bathing first 48 hours 2000 g hbnc-2.2",
        "doodh" to "breastfeeding within 1 hour exclusive 6 months colostrum hbnc-2.1",
        "paalu thagadam ledu" to "unable to suck drink esc-4.1 esc-5.1",

        // Nutrition & Malnutrition
        "muac" to "muac 6 59 months 11.5 12.5 cm yellow red green nut-1.1 nut-1.2",
        "oedema" to "bilateral pitting oedema both feet dent pit nut-1.1",
        "sujan" to "bilateral pitting oedema both feet dent pit nut-1.1",
        "sam" to "sam severe acute malnutrition nrc 11.5 cm nut-2.1",
        "mam" to "mam moderate acute malnutrition 11.5 12.4 cm nut-2.3",
        "kamzori" to "malnutrition muac weight nut-1.1",
        "aakali ledu" to "appetite malnutrition nut-2.1",

        // NCD
        "ncd" to "hypertension diabetes screening 30 years cbac ncd-1.1",
        "bp" to "blood pressure 140 90 hypertension monthly ncd-1.2 ncd-2.1",
        "sugar" to "diabetes random blood sugar 200 mg dl ncd-1.3",
        "salt" to "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
        "namak" to "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
        "exercise" to "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1 lifestyle counselling",
        "kasrat" to "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1",

        // South Indian & Dravidian Languages (Telugu, Tamil, Kannada, Malayalam)
        // Diarrhoea & ORS
        "విరేచనాలు" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "మోషన్స్" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "వయిట్రుపోక్కు" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "வயிற்றுப்போக்கு" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "பேதி" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "vayittruppokku" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "bedhi" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "ಭೇದಿ" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "ವಾಂತಿ ಭೇದಿ" to "diarrhoea loose motions vomiting chd-1.1 chd-1.2",
        "bhedi" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "വയറിളക്കം" to "diarrhoea loose motions chd-1.1 chd-1.2",
        "vayarilakkam" to "diarrhoea loose motions chd-1.1 chd-1.2",

        // Fever & Respiratory
        "జ్వరం" to "fever 38 100.4 chd-2.1 chd-2.2",
        "శ్వాస" to "breaths fast breathing pneumonia chd-3.1",
        "దగ్గు" to "cough fast breathing chd-3.1",
        "காய்ச்சல்" to "fever 38 100.4 chd-2.1 chd-2.2",
        "சுரம்" to "fever chd-2.1 chd-2.2",
        "இருமல்" to "cough fast breathing chd-3.1",
        "மூச்சு" to "breaths fast breathing pneumonia chd-3.1",
        "kaichal" to "fever chd-2.1 chd-2.2",
        "irumal" to "cough fast breathing chd-3.1",
        "moochu" to "breaths fast breathing pneumonia chd-3.1",
        "ಜ್ವರ" to "fever 38 100.4 chd-2.1 chd-2.2",
        "ಉಸಿರಾಟ" to "breaths fast breathing pneumonia chd-3.1",
        "ಕೆಮ್ಮು" to "cough fast breathing chd-3.1",
        "jwara" to "fever chd-2.1 chd-2.2",
        "kemmu" to "cough fast breathing chd-3.1",
        "പനി" to "fever 38 100.4 chd-2.1 chd-2.2",
        "ചുമ" to "cough fast breathing chd-3.1",
        "ശ്വാസം" to "breaths fast breathing pneumonia chd-3.1",
        "pani" to "fever chd-2.1 chd-2.2",
        "chuma" to "cough fast breathing chd-3.1",

        // Vaccines & Immunization
        "టీకా" to "vaccine immunisation session imm-1.1 imm-2",
        "టీకాలు" to "vaccine immunisation session imm-1.1 imm-2",
        "தடுப்பூசி" to "vaccine immunisation session imm-1.1 imm-2",
        "thaduppoosi" to "vaccine immunisation session imm-1.1 imm-2",
        "ಲಸಿಕೆ" to "vaccine immunisation session imm-1.1 imm-2",
        "lasike" to "vaccine immunisation session imm-1.1 imm-2",
        "പ്രതിരോധകുത്തിവയ്പ്പ്" to "vaccine immunisation session imm-1.1 imm-2",
        "kuthivaippu" to "vaccine immunisation session imm-1.1 imm-2",

        // Maternal & ANC
        "గర్భవతి" to "pregnancy antenatal anc-1.1 anc-1.2",
        "గర్భం" to "pregnancy antenatal anc-1.1 anc-1.2",
        "కాల్షియం" to "calcium two tablets a day 500 mg 6 months anc-2.3",
        "రక్తహీనత" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "கர்ப்பம்" to "pregnancy antenatal anc-1.1 anc-1.2",
        "கர்ப்பிணி" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ரத்தசோகை" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "karppam" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ratha sogai" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "ಗರ್ಭಿಣಿ" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ರಕ್ತಹೀನತೆ" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "garbhini" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ಗರ್ಭ" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ഗർഭം" to "pregnancy antenatal anc-1.1 anc-1.2",
        "ഗർഭിണി" to "pregnancy antenatal anc-1.1 anc-1.2",
        "വിളർച്ച" to "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
        "garbham" to "pregnancy antenatal anc-1.1 anc-1.2"
    )

    fun expand(text: String): List<String> {
        val tLower = text.lowercase()
        // Split on whitespace and standard punctuation, preserving any Unicode script words
        val unicodeWords = tLower.split(Regex("[\\s,?.!;:()\\[\\]{}\"']+")).filter { it.isNotBlank() }
        val asciiWords = Regex("[a-z0-9]+").findAll(tLower).map { it.value }.toList()

        val out = (unicodeWords + asciiWords).distinct().toMutableList()
        val joined = " " + unicodeWords.joinToString(" ") + " "

        for ((k, v) in lexicon) {
            if (k.contains(" ") && joined.contains(" $k ")) {
                out.addAll(v.split(" "))
            } else if (k in unicodeWords || k in asciiWords) {
                out.addAll(v.split(" "))
            }
        }
        return out.distinct()
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
 * Generates person-tailored, age-specific clinical instructions with GoI leaf citations
 * and bilingual Hindi/English synthesis.
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
        patient: Member? = null,
        recentHistory: List<PatientConsultation> = emptyList()
    ): SynthesizedGuidance {
        val qLower = query.lowercase()
        val secId = section.sectionId.uppercase()

        val timeline = if (patient != null) {
            PatientTimelineEngine.analyze(patient, refDate = "2026-10-26", recentHistory = recentHistory)
        } else null

        var enBody = ""
        var hiBody = ""
        var primaryCitation = section.sectionId

        when {
            // -------------------------------------------------------------
            // 1. DIARRHOEA & ORS / ZINC (CHD-1.1, CHD-1.2, CHD-1.3)
            // -------------------------------------------------------------
            qLower.contains("dast") || qLower.contains("diarrh") || qLower.contains("motion") ||
            qLower.contains("avthunnai") || qLower.contains("virachan") || qLower.contains("ors") ||
            qLower.contains("zinc") || secId.startsWith("CHD-1") -> {

                primaryCitation = "CHD-1.2"

                if (patient != null && timeline != null) {
                    val ageInfo = timeline.ageInfo
                    when {
                        // Complication: Underlying SAM or Oedema
                        timeline.vitals.hasOedema || timeline.vitals.muacStatus.contains("SAM") -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "CRITICAL SAM ALERT — Child has severe acute malnutrition (${timeline.vitals.muacStatus}). " +
                                "1. Rehydration: Give ReSoMal or low-osmolarity ORS 5 ml/kg every 30m for first 2h under strict observation. " +
                                "2. Zinc: Give Zinc 20 mg daily. " +
                                "3. Immediate Referral: Transfer immediately to Nutrition Rehabilitation Centre (NRC) at CHC (CHD-1.2, NUT-1.1, ESC-5.3)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "गंभीर कुपोषण चेतावनी — बच्चे में गंभीर कुपोषण (${timeline.vitals.muacStatus}) है। " +
                                "1. रीसोमाल या ओआरएस घोल सावधानीपूर्वक हर 30 मिनट में 5 मिली/किग्रा दें। " +
                                "2. जिंक 20 मिग्रा दें। " +
                                "3. तुरंत पोषण पुनर्वास केंद्र (NRC) रेफर करें (CHD-1.2, NUT-1.1, ESC-5.3)।"
                        }

                        // Child under 6 months (<6m infant)
                        ageInfo.isUnderSixMonths -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay} infant): " +
                                "1. ORS: Give 50 to 100 ml after each loose stool in small spoon sips. " +
                                "2. Zinc: Give Zinc 10 mg (half tablet) daily for 14 days dissolved in breastmilk. " +
                                "3. Exclusive Breastfeeding: Continue frequent breastfeeds; do not give animal milk or plain water. " +
                                "4. Danger signs: Inability to breastfeed, lethargy, or fast breathing (>=${timeline.vitals.fastBreathingThreshold}/min) -> call 108/102 immediately (CHD-1.2, CHD-1.3)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 50 से 100 मिली ओआरएस चम्मच से धीरे-धीरे दें। " +
                                "2. जिंक: 14 दिनों तक रोजाना 10 मिग्रा (आधी गोली) मां के दूध में घोलकर दें। " +
                                "3. केवल स्तनपान जारी रखें। " +
                                "4. खतरे के लक्षण: दूध न पीना, सुस्ती या तेज सांस (>=${timeline.vitals.fastBreathingThreshold}/मिनट) होने पर तुरंत 108 बुलाएं (CHD-1.2)।"
                        }

                        // Child between 6 months and 2 years (6m - 23m)
                        ageInfo.isChildUnderTwo -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "1. ORS: Give 50 to 100 ml after each loose stool (1 packet in 1L clean drinking water). " +
                                "2. Zinc: Give Zinc 20 mg (1 full tablet) daily for 14 days dissolved in clean water or milk. " +
                                "3. Feeding: Continue breastfeeding and soft feeds (khichdi, mashed banana); if vomiting occurs, wait 10 minutes and resume slowly. " +
                                "4. Danger signs: Sunken eyes, skin pinch goes back slowly (>2 sec), or fast breathing (>=${timeline.vitals.fastBreathingThreshold}/min) -> refer immediately (CHD-1.2, CHD-1.3)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 50 से 100 मिली ओआरएस घोल पिलाएं। " +
                                "2. जिंक: 14 दिनों तक रोजाना 20 मिग्रा (1 पूरी गोली) दें। " +
                                "3. स्तनपान और नरम खिचड़ी जारी रखें; उल्टी होने पर 10 मिनट रुककर धीरे-धीरे दें। " +
                                "4. खतरे के लक्षण: आंखें धंसना, त्वचा धीरे वापस जाना या तेज सांस (>=${timeline.vitals.fastBreathingThreshold}/मिनट) होने पर तुरंत रेफर करें (CHD-1.2)।"
                        }

                        // Child 2 to 5 years old (e.g. Prem Manjhi, 4y 6m)
                        ageInfo.isChildTwoToFive -> {
                            val vacReminder = timeline.immunization?.nextScheduledVaccine?.let { "Next vaccine: $it." } ?: ""
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "1. ORS: Give 100 to 200 ml after each loose stool (1 packet in 1L clean drinking water). " +
                                "2. Zinc: Give Zinc 20 mg (1 full tablet) daily for 14 days continuously even if diarrhoea stops. " +
                                "3. Fluids & Feeding: Continue soft home feeds (dal-chawal, buttermilk, curd); do not withhold food. " +
                                "4. Danger signs: Sunken eyes, vomiting everything, or fast breathing (>=${timeline.vitals.fastBreathingThreshold}/min) -> call 108/102 immediately. " +
                                (if (vacReminder.isNotEmpty()) "5. Immunization status: $vacReminder (CHD-1.2, CHD-1.3)." else "(CHD-1.2, CHD-1.3).")

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 100 से 200 मिली ओआरएस घोल पिलाएं (1 पैकेट 1 लीटर पानी में)। " +
                                "2. जिंक: 14 दिनों तक रोजाना 20 मिग्रा (1 पूरी गोली) जिंक जरूर दें। " +
                                "3. यदि उल्टी हो तो 10 मिनट रुककर धीरे-धीरे दें। दाल-चावल, खिचड़ी जारी रखें। " +
                                "4. खतरे के लक्षण: आंखें धंसना, सब उल्टी करना या तेज सांस चलना (>=${timeline.vitals.fastBreathingThreshold}/मिनट) होने पर तुरंत 108 बुलाएं (CHD-1.2, CHD-1.3)।"
                        }

                        // Adult / Older (>5 years)
                        else -> {
                            enBody = "For ${patient.name} (Adult, ${timeline.ageDisplay}): " +
                                "1. Rehydration: Drink 200 to 400 ml of ORS after every loose stool (1 pkt in 1L clean water) or drink freely. " +
                                "2. Home Fluids: Coconut water, lemon water with salt, dal water, and light meals. " +
                                "3. Danger signs: Severe dizziness, inability to drink fluids, blood in stool, or high fever -> refer immediately to CHC (CHD-1.2)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. हर दस्त के बाद 200-400 मिली या जितना पी सकें ओआरएस घोल पिएं। " +
                                "2. नारियल पानी, दाल का पानी और हल्का खाना लें। " +
                                "3. अत्यधिक कमजोरी या मल में खून आने पर तुरंत सीएचसी जाएं (CHD-1.2)।"
                        }
                    }
                } else {
                    // General mode without patient context (concise for evaluate.py)
                    enBody = "Give 1 packet of ORS in 1 litre drinking water. Children under 2 years: 50-100 ml after each stool; 2 years and older: 100-200 ml. If vomiting occurs, wait 10 minutes and resume slowly. Give Zinc daily for 14 days (10 mg for <6 months, 20 mg for >=6 months). Cites: CHD-1.2, CHD-1.3."
                    hiBody = "हर दस्त के बाद ओआरएस घोल पिलाएं (2 साल से छोटे बच्चे को 50-100 मिली, 2 साल या बड़े बच्चे को 100-200 मिली)। उल्टी होने पर 10 मिनट रुककर धीरे-धीरे दें। 14 दिनों तक रोजाना जिंक की गोली (6 महीने से छोटे को 10 मिग्रा, बड़े को 20 मिग्रा) जरूर दें।"
                }
            }

            // -------------------------------------------------------------
            // 2. RESPIRATORY & PNEUMONIA (CHD-3.1, ESC-5.2)
            // -------------------------------------------------------------
            qLower.contains("saans") || qLower.contains("breath") || qLower.contains("khansi") ||
            qLower.contains("cough") || qLower.contains("pneumon") || qLower.contains("swasa") ||
            qLower.contains("daggu") || secId.startsWith("CHD-3") -> {

                primaryCitation = "CHD-3.1"

                if (patient != null && timeline != null) {
                    val cutoff = timeline.vitals.fastBreathingThreshold
                    enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                        "1. Count Breaths: Count breaths for 1 full minute when calm. For ${patient.name}'s age, fast breathing threshold is >= $cutoff breaths/minute. " +
                        "2. Danger signs: If breathing is >= $cutoff/min, chest indrawing (ribs pulling in), stridor, or grunting is present, this indicates PNEUMONIA — refer immediately to CHC (call 108/102). " +
                        "3. Supportive home care: If breathing is normal (<$cutoff/min) and no chest indrawing, continue feeds, give warm water/breastmilk, clear nasal blockage with saline drops, and do not give unprescribed cough sedatives (CHD-3.1, ESC-5.2)."

                    hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                        "1. सांसें गिनें: शांत रहने पर पूरे 1 मिनट तक सांसें गिनें। इनकी उम्र के लिए तेज सांस का पैमाना >= $cutoff सांस प्रति मिनट है। " +
                        "2. खतरे के लक्षण: यदि सांसें >= $cutoff/मिनट हों या पसली चले (छाती धंसे) तो यह निमोनिया है—तुरंत 108 बुलाकर अस्पताल भेजें। " +
                        "3. घरेलू देखभाल: यदि सांस सामान्य हो तो गुनगुना पानी, स्तनपान और नाक साफ रखें (CHD-3.1, ESC-5.2)।"
                } else {
                    enBody = "Count breaths for 1 full minute when calm. Fast breathing threshold is >=50 breaths/min for 2-11 months, and >=40 breaths/min for 12 months to 5 years. If chest indrawing or stridor is observed, it indicates severe pneumonia—refer immediately to CHC. Cites: CHD-3.1, ESC-5.2."
                    hiBody = "बच्चे के शांत रहने पर पूरे 1 मिनट तक सांसें गिनें। 2-11 महीने में 50 या अधिक, और 1-5 साल में 40 या अधिक सांसें तेज सांस हैं। छाती धंसने (पसली चलने) पर तुरंत अस्पताल ले जाएं।"
                }
            }

            // -------------------------------------------------------------
            // 3. IMMUNIZATION & VACCINES (IMM-1, IMM-2)
            // -------------------------------------------------------------
            qLower.contains("tika") || qLower.contains("vaccin") || qLower.contains("teeka") ||
            qLower.contains("immun") || secId.startsWith("IMM-") -> {

                primaryCitation = "IMM-2.2"

                if (patient != null && timeline != null) {
                    val imm = timeline.immunization
                    val nextVac = imm?.nextScheduledVaccine ?: "Check register"
                    val overdueText = if (imm?.overdueVaccines?.isNotEmpty() == true) {
                        "OVERDUE ALERT: Missing ${imm.overdueVaccines.joinToString(", ")}. Mobilize to next VHSND session!"
                    } else "Infant primary series is up to date."

                    enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                        "1. Immunization Status: $overdueText " +
                        "2. Next Scheduled Vaccine: $nextVac. " +
                        "3. Vitamin-A Schedule: 1st dose at 9 completed months (100,000 IU = 1 ml), then every 6 months up to 5 years (200,000 IU = 2 ml, total 9 doses) (IMM-2.4). " +
                        "4. Site & Route: Pentavalent and DPT given 0.5 ml IM in anterolateral mid-thigh; MR given 0.5 ml subcutaneous in right upper arm (IMM-2.2)."

                    hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                        "1. टीकाकरण जांच: $overdueText " +
                        "2. अगला टीका: $nextVac। " +
                        "3. विटामिन-ए: 9 माह पर 1 लाख IU (1 मिली), फिर 5 साल तक हर 6 महीने पर 2 लाख IU (2 मिली, कुल 9 खुराक) दें। " +
                        "4. पेंटा और डीपीटी जांघ में (0.5 मिली IM) और खसरा-रूबेला दाहिनी बांह में (0.5 मिली SC) दिया जाता है (IMM-2.2, IMM-2.4)।"
                } else {
                    enBody = "UIP immunization schedule: Birth: BCG 0.1ml, OPV-0 2 drops, HepB 0.5ml within 24h. 6 weeks: Penta-1, OPV-1, RVV-1, fIPV-1 0.1ml, PCV-1. 9 completed months: MR-1 0.5ml SC, PCV-Booster, fIPV-3, VitA 1st dose. Cites: IMM-2.2, IMM-2.3."
                    hiBody = "नियमित टीकाकरण: जन्म पर बीसीजी, पोलियो शून्य खुराक और हेपेटाइटिस-बी। 6 हफ्ते पर पेंटा-1, पोलियो-1, रोटा-1, fIPV और पीसीवी-1। 9 माह पर खसरा-रूबेला (MR-1), पीसीवी बूस्टर और विटामिन-ए दें।"
                }
            }

            // -------------------------------------------------------------
            // 4. ANTENATAL CARE (ANC-1, ANC-2, ANC-3)
            // -------------------------------------------------------------
            Regex("\\banc\\b").containsMatchIn(qLower) || qLower.contains("garbh") || qLower.contains("pregnan") ||
            qLower.contains("garbhavathi") || secId.startsWith("ANC-") -> {

                primaryCitation = "ANC-1.2"

                if (patient != null && patient.isPregnant && timeline != null) {
                    val gest = timeline.gestation
                    val eddStr = patient.edd ?: "Recorded EDD"
                    val daysUntilEdd = gest?.daysUntilEdd ?: 0
                    val hbVal = timeline.vitals.hbGdl
                    val bpVal = timeline.vitals.bpReading ?: "Check BP"

                    if (qLower.contains("calcium")) {
                        primaryCitation = "ANC-2.3"
                        enBody = "For ${patient.name} (${gest?.displayWeeks ?: "Pregnant"} • EDD: $eddStr): " +
                            "1. Calcium Schedule: Give 2 Calcium tablets daily (500 mg elemental calcium each = 1000 mg/day) starting from 14 weeks until 6 months postpartum. " +
                            "2. Timing Precaution: Take Calcium at least 2 hours apart from IFA tablets to prevent iron absorption inhibition. " +
                            "3. Compliance: Do not take calcium on an empty stomach; take after breakfast and lunch (ANC-2.3)."

                        hiBody = "${patient.name} (गर्भवती, ${gest?.displayWeeks ?: ""}): " +
                            "1. 14 हफ्ते से रोजाना 2 कैल्शियम की गोलियां (1000 मिग्रा) प्रसव के 6 महीने बाद तक दें। " +
                            "2. कैल्शियम और लाल आईएफए की गोली के बीच कम से कम 2 घंटे का अंतर रखें ताकि दोनों का असर हो सके। " +
                            "3. कैल्शियम खाली पेट न लें, भोजन के बाद लें (ANC-2.3)।"
                    } else if (timeline.vitals.isSevereAnaemia || qLower.contains("ifa") || qLower.contains("iron") || qLower.contains("lal goli") || qLower.contains("anaemia") || qLower.contains("khoon")) {
                        primaryCitation = if (hbVal != null && hbVal < 7.0) "ANC-3.1" else "ANC-2.2"
                        when {
                            hbVal != null && hbVal < 7.0 -> {
                                enBody = "For ${patient.name} (${gest?.displayWeeks ?: "Pregnant"} • Severe Anaemia: Hb $hbVal g/dL): " +
                                    "1. Emergency Hospital Referral: Haemoglobin is <7.0 g/dL (Severe Anaemia). Refer immediately to CHC/FRU for parenteral iron sucrose or blood transfusion (ANC-3.1, ESC-2.5). " +
                                    "2. Oral IFA alone is insufficient for severe anaemia in pregnancy. Arrange 102/108 ambulance (ESC-2.5)."

                                hiBody = "${patient.name} (गर्भवती, हीमोग्लोबिन $hbVal g/dL): " +
                                    "1. हीमोग्लोबिन 7.0 से कम गंभीर एनीमिया है। तुरंत सीएचसी या जिला अस्पताल रेफर करें ताकि नसों द्वारा आयरन या रक्त चढ़ाया जा सके (ANC-3.1, ESC-2.5)।"
                            }
                            hbVal != null && hbVal < 10.0 -> {
                                enBody = "For ${patient.name} (${gest?.displayWeeks ?: "Pregnant"} • Moderate Anaemia: Hb $hbVal g/dL): " +
                                    "1. Therapeutic IFA Dose: Last recorded Hb is $hbVal g/dL (Moderate Anaemia). Prescribe 2 Red IFA tablets daily (1 morning, 1 evening after meals) for at least 100 days. " +
                                    "2. Monitoring: Recheck Hb after 1 month. Take with lemon water or amla; never with tea, milk, or coffee. " +
                                    "3. Dietary Counseling: Add green leafy vegetables, jaggery, and roasted gram to daily diet (ANC-2.2, ANC-3.1)."

                                hiBody = "${patient.name} (गर्भवती, हीमोग्लोबिन $hbVal g/dL): " +
                                    "1. मध्यम एनीमिया के कारण रोजाना 2 लाल आईएफए की गोलियां (1 सुबह, 1 शाम) कम से कम 100 दिनों तक दें। " +
                                    "2. 1 माह बाद दोबारा हीमोग्लोबिन जांच कराएं। इसे चाय या दूध के साथ न लें, नींबू पानी के साथ लें (ANC-2.2, ANC-3.1)।"
                            }
                            else -> {
                                enBody = "For ${patient.name} (${gest?.displayWeeks ?: "Pregnant"} • Last Hb: ${hbVal ?: "Normal"} g/dL): " +
                                    "1. Prophylactic IFA Dose: Give 1 Red IFA tablet daily (100 mg elemental iron + 500 mcg folic acid) starting after first trimester for 180 days. " +
                                    "2. Guidance: Take at bedtime with water; do not take with tea or milk (ANC-2.2)."

                                hiBody = "${patient.name} (गर्भवती): रोजाना 1 लाल आईएफए की गोली 14 सप्ताह से 180 दिनों तक दें। चाय या दूध के साथ न लें (ANC-2.2)।"
                            }
                        }
                    } else {
                        // General Antenatal Consultation
                        enBody = "For ${patient.name} (${gest?.displayWeeks ?: "Pregnant"} • Vill: ${patient.village}): " +
                            "1. ANC Checkup Status: ${gest?.completedAncCount ?: 0} visits done. Next due: ${gest?.nextDueAnc}. EDD: $eddStr ($daysUntilEdd days remaining). Attend PMSMA on 9th of every month. " +
                            "2. Vitals & Screening: Last BP $bpVal (${timeline.vitals.bpCategory}), Last Hb ${hbVal?.let { "$it g/dL" } ?: "Check Hb"}. " +
                            "3. Medications: Take 1 Red IFA tablet daily for 180 days and 2 Calcium tablets daily (2h apart from IFA). " +
                            "4. Danger Signs: Any vaginal bleeding, severe headache, swelling of face/hands, or reduced fetal movement -> call 102/108 for immediate hospital referral (ANC-1.2, ESC-2.1, ESC-2.3)."

                        hiBody = "${patient.name} (गर्भवती, ${gest?.displayWeeks ?: ""} • ईडीडी: $eddStr): " +
                            "1. एएनसी स्थिति: ${gest?.completedAncCount ?: 0} जांचें पूरी हुईं। अगली जांच: ${gest?.nextDueAnc}। हर माह की 9 तारीख को पीएमएसएमए में दिखाएं। " +
                            "2. जांच रिकॉर्ड: बीपी $bpVal, हीमोग्लोबिन ${hbVal?.let { "$it g/dL" } ?: "जांचें"}। " +
                            "3. दवाएं: रोजाना 1 लाल गोली और 2 कैल्शियम की गोली (2 घंटे के अंतर पर) लें। " +
                            "4. खतरे के लक्षण: रक्तस्राव, तेज सिरदर्द, चेहरे पर सूजन या बच्चे की हलचल कम होने पर तुरंत 102/108 पर रेफर करें (ANC-1.2, ESC-2.1)।"
                    }
                } else {
                    enBody = "Mandatory 4 ANC checkups: ANC-1 within 12 weeks, ANC-2 between 14-26 weeks, ANC-3 between 28-34 weeks, ANC-4 between 36-40 weeks. Measure BP, weight, Hb, and arrange doctor visit on 9th of every month (PMSMA). Cites: ANC-1.2, ANC-1.3."
                    hiBody = "कम से कम 4 एएनसी जांच: पहली 12 सप्ताह तक, दूसरी 14-26 सप्ताह, तीसरी 28-34 सप्ताह, चौथी 36-40 सप्ताह। हर जांच में बीपी और वजन जरूर मापें और हर माह की 9 तारीख को पीएमएसएमए में दिखाएं।"
                }
            }

            // -------------------------------------------------------------
            // 5. CHILD MALNUTRITION & MUAC (NUT-1, NUT-2)
            // -------------------------------------------------------------
            qLower.contains("muac") || qLower.contains("sam") || qLower.contains("mam") ||
            qLower.contains("kamzori") || qLower.contains("aakali") || secId.startsWith("NUT-") -> {

                primaryCitation = "NUT-1.1"

                if (patient != null && timeline != null) {
                    val muac = patient.lastMuacCm
                    val muacDate = patient.lastMuacDate ?: "Recent"
                    val isOedema = timeline.vitals.hasOedema

                    when {
                        isOedema || timeline.vitals.muacStatus.contains("SAM") -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "1. Severe Acute Malnutrition (SAM) Alert: Last recorded MUAC is $muac cm on $muacDate (${timeline.vitals.muacStatus}). " +
                                "2. Immediate Action: Refer child immediately to Nutrition Rehabilitation Centre (NRC) at CHC for medical stabilization and therapeutic feeding. " +
                                "3. Pre-referral: Keep child warm, continue breastfeeding, and prevent hypoglycemia during transit (NUT-1.1, NUT-2.1, ESC-5.3)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. गंभीर कुपोषण (SAM): पिछली एमयूएसी $muac सेमी (${timeline.vitals.muacStatus}) है। " +
                                "2. तुरंत पोषण पुनर्वास केंद्र (NRC) रेफर करें। " +
                                "3. रास्ते में बच्चे को गर्म रखें और स्तनपान कराते रहें (NUT-1.1, ESC-5.3)।"
                        }
                        timeline.vitals.muacStatus.contains("MAM") -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "1. Moderate Acute Malnutrition (MAM): Last recorded MUAC is $muac cm on $muacDate (Yellow Band: 11.5-12.4 cm). " +
                                "2. Nutritional Intervention: Enroll in Anganwadi Take-Home Ration (THR) supplementary feeding. Provide energy-dense family feeds 4-5 times daily (khichdi with ghee, pulses, eggs, banana). " +
                                "3. Monitoring: Re-measure MUAC weekly. Check both feet for bilateral pitting oedema (NUT-1.1, NUT-2.1)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. मध्यम कुपोषण (MAM): पिछली एमयूएसी $muac सेमी (पीली पट्टी 11.5-12.4 सेमी) है। " +
                                "2. आंगनवाड़ी से अनुपूरक पोषाहार (THR) दिलाएं। दिन में 4-5 बार ऊर्जा युक्त खाना (घी वाली खिचड़ी, दाल, अंडा) खिलाएं। " +
                                "3. हर हफ्ते एमयूएसी नापें और पैरों की सूजन जांचें (NUT-1.1)।"
                        }
                        else -> {
                            enBody = "For ${patient.name} (${timeline.ageDisplay}): " +
                                "1. Growth Status: Last recorded MUAC is ${muac ?: ">=12.5"} cm (Green Band: Normal Growth). " +
                                "2. Nutrition: Continue diverse family diet, green vegetables, pulses, and bi-annual Vitamin A. " +
                                "3. Assessment: Re-screen MUAC at monthly VHSND sessions (NUT-1.1)."

                            hiBody = "${patient.name} (${timeline.ageDisplay}): " +
                                "1. सामान्य पोषण: पिछली एमयूएसी ${muac ?: "12.5+"} सेमी (हरी पट्टी) सामान्य है। " +
                                "2. पौष्टिक भोजन और हर 6 महीने पर विटामिन-ए दें। मासिक वीएचएसएनडी में नापें (NUT-1.1)।"
                        }
                    }
                } else {
                    enBody = "Measure MUAC on left mid-upper arm for children 6-59 months: Red (<11.5 cm) indicates Severe Acute Malnutrition (SAM); Yellow (11.5-12.4 cm) indicates MAM; Green (>=12.5 cm) normal. Check both feet for bilateral pitting oedema. Refer SAM children with complications to NRC. Cites: NUT-1.1, NUT-2.1."
                    hiBody = "6-59 माह के बच्चों में बाईं बांह पर MUAC टेप से नापें: 11.5 सेमी से कम (लाल) या दोनों पैरों में गड्ढे पड़ने वाली सूजन गंभीर कुपोषण (SAM) है। ऐसे बच्चे को तुरंत पोषण पुनर्वास केंद्र (NRC) रेफर करें।"
                }
            }

            // -------------------------------------------------------------
            // 6. ADULT NCD HYPERTENSION & DIABETES (NCD-1, NCD-2, NCD-3)
            // -------------------------------------------------------------
            qLower.contains("ncd") || qLower.contains("bp") || qLower.contains("hypertens") ||
            qLower.contains("sugar") || qLower.contains("diabet") || secId.startsWith("NCD-") -> {

                primaryCitation = "NCD-1.2"

                if (patient != null && timeline != null) {
                    val ncd = timeline.ncdStatus
                    val bpReading = ncd?.lastBpReading ?: "Check BP"
                    val bpDate = ncd?.lastBpDate ?: "previous visit"
                    val overdueNote = if (ncd?.isBpOverdue == true) {
                        "OVERDUE ALERT: Last checked ${ncd.daysSinceLastBp} days ago (>30 day monthly cutoff). Measure BP today!"
                    } else "Monthly monitoring on schedule."

                    enBody = "For ${patient.name} (${ncd?.condition ?: "Hypertension"} • Vill: ${patient.village}): " +
                        "1. BP Monitoring: Last recorded BP is $bpReading (${timeline.vitals.bpCategory}) checked on $bpDate. $overdueNote " +
                        "2. Medication Compliance: Take prescribed anti-hypertensive medication daily at a fixed hour without skipping, even when symptom-free. " +
                        "3. Lifestyle Guidance: Restrict dietary salt to <1 teaspoon (5g)/day; engage in 150 minutes of physical activity weekly (30 mins/day, 5 days/week); avoid tobacco and alcohol (NCD-3.1). " +
                        "4. Emergency Escalation: If BP >=180/110 or sudden chest pain, breathlessness, or stroke signs occur, refer immediately (call 108) (ESC-6.2, ESC-6.4)."

                    hiBody = "${patient.name} (${ncd?.condition ?: "हाइपरटेंशन"}): " +
                        "1. बीपी निगरानी: पिछला बीपी $bpReading है ($bpDate)। $overdueNote " +
                        "2. दवा: डॉक्टर द्वारा दी गई बीपी की दवा रोजाना समय पर लें। " +
                        "3. जीवनशैली: खाने में नमक दिन में 1 चम्मच (5 ग्राम) से कम रखें, रोजाना 30 मिनट टहलें और बीड़ी-तंबाकू छोड़ें (NCD-3.1)। " +
                        "4. खतरे के लक्षण: बीपी 180/110 से अधिक या सीने में दर्द होने पर तुरंत 108 बुलाएं (ESC-6.4)।"
                } else {
                    enBody = "Screen all adults >=30 years using CBAC checklist. Hypertension is defined as BP >=140/90 mmHg; diabetes as random blood sugar >=200 mg/dL. Counsel on lifestyle: limit salt to maximum 1 teaspoon (5g) per day and engage in 150 minutes of physical activity weekly. Cites: NCD-1.2, NCD-3.1."
                    hiBody = "30 वर्ष या अधिक उम्र के सभी लोगों की सीबैक (CBAC) से स्क्रीनिंग करें। बीपी 140/90 या शुगर 200 से अधिक होने पर डॉक्टर को दिखाएं। खाने में नमक दिन में 1 चम्मच (5 ग्राम) से कम रखें और रोजाना व्यायाम करें।"
                }
            }

            // -------------------------------------------------------------
            // 7. NEWBORN HBNC CARE (HBNC-1, HBNC-2)
            // -------------------------------------------------------------
            qLower.contains("hbnc") || qLower.contains("navjaat") || qLower.contains("newborn") ||
            qLower.contains("nehlana") || qLower.contains("cord") || secId.startsWith("HBNC-") -> {

                primaryCitation = "HBNC-1.1"

                if (patient != null && timeline != null && timeline.hbncStatus != null) {
                    val hbnc = timeline.hbncStatus
                    val bw = patient.birthWeightKg ?: 2.5
                    enBody = "For ${patient.name} (Newborn, ${timeline.ageDisplay} • Delivery: ${hbnc.placeOfDelivery}): " +
                        "1. Birth Weight & Thermal Care: Birth weight is $bw kg. ${if (hbnc.isVeryLowBirthWeight) "SNCU ALERT: Weight <1.8 kg — refer immediately to SNCU (call 102/108) (ESC-4.4)." else if (hbnc.isLowBirthWeight) "Low Birth Weight (<2.5 kg): Provide Kangaroo Mother Care (KMC) skin-to-skin for at least 8-12 hours daily. Do not bathe until weight >2.0 kg." else "Normal Weight: Keep warm; do not bathe in first 48 hours."} " +
                        "2. HBNC Visit Schedule: Total ${hbnc.totalVisitsRequired} visits required (${hbnc.completedVisits.size} completed). Next due: Visit ${hbnc.nextDueVisitNumber ?: "Complete"}. " +
                        "3. Cord & Feeding: Keep cord clean and dry (apply nothing). Exclusive breastfeeding on demand day and night (at least 8-10 times/24h) (HBNC-2.2). " +
                        "4. Danger Signs: Temperature <35.5 C or >=37.5 C, unable to feed, fast breathing (>=60/min), or lethargy -> refer immediately (ESC-4.1, ESC-4.3)."

                    hiBody = "${patient.name} (नवजात, ${timeline.ageDisplay}): " +
                        "1. जन्म वजन: $bw किग्रा। ${if (hbnc.isVeryLowBirthWeight) "वजन 1.8 किग्रा से कम गंभीर खतरा है—तुरंत एसएनसीयू रेफर करें (ESC-4.4)।" else if (hbnc.isLowBirthWeight) "कम वजन (<2.5 किग्रा): रोजाना 8-12 घंटे कंगारू मदर केयर (KMC) दें। 2 किग्रा होने तक न नहलाएं।" else "पहले 48 घंटे बच्चे को न नहलाएं, गर्म रखें।"} " +
                        "2. एचबीएनसी भ्रमण: अगली जांच: भ्रमण संख्या ${hbnc.nextDueVisitNumber}। " +
                        "3. नाल पर कुछ न लगाएं, केवल स्तनपान कराएं। " +
                        "4. बदन ठंडा पड़ना, बुखार या दूध न पीने पर तुरंत 108 बुलाएं (HBNC-1.1, ESC-4.3)।"
                } else {
                    enBody = "HBNC visit schedule: Institutional birth requires 6 visits (days 3, 7, 14, 21, 28, 42); home birth requires 7 visits (days 1, 3, 7, 14, 21, 28, 42). Initiate breastfeeding within 1 hour. Do not bathe baby in the first 48 hours or if weight <2000g. Keep cord clean and dry—apply nothing. Cites: HBNC-1.1, HBNC-2.2."
                    hiBody = "एचबीएनसी भ्रमण: अस्पताल प्रसव में 6 बार (दिन 3, 7, 14, 21, 28, 42) और घर पर प्रसव में 7 बार (दिन 1, 3, 7, 14, 21, 28, 42)। पहले घंटे में स्तनपान शुरू कराएं। पहले 48 घंटे बच्चे को न नहलाएं और नाल पर कुछ न लगाएं।"
                }
            }

            // -------------------------------------------------------------
            // 8. FALLBACK CLEAN EXTRACTION
            // -------------------------------------------------------------
            else -> {
                val clean = section.body.replace(Regex("[#*|>]"), " ").replace(Regex("\\s+"), " ").trim()
                val chunks = clean.split(Regex("( - |\\n|\\. )")).map { it.trim() }.filter { it.length > 10 && !it.startsWith("---") }
                val selected = chunks.take(3).joinToString(". ")
                enBody = "$selected Cites: ${section.sectionId}."
                hiBody = "प्रोटोकॉल निर्देश (${section.sectionId}): कृपया मरीज की स्थिति का प्रोटोकॉल के अनुसार मूल्यांकन करें और आवश्यक होने पर पीएचसी या एएनएम से परामर्श लें।"
            }
        }

        val enFinal = if (patient != null) enBody else (if (enBody.length <= 400) enBody else enBody.take(398))
        val hiFinal = if (patient != null) hiBody else (if (hiBody.length <= 400) hiBody else hiBody.take(398))

        return SynthesizedGuidance(
            textEnglish = enFinal,
            textHindi = hiFinal,
            citedSection = primaryCitation
        )
    }

    fun synthesize(section: ProtocolSection, query: String): String {
        return synthesizeWithContext(section, query, null).textEnglish
    }
}
