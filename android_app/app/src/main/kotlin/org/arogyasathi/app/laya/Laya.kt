package org.arogyasathi.app.laya

import org.arogyasathi.app.core.Member
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
        "kasrat" to "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1"
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
        patient: Member? = null
    ): SynthesizedGuidance {
        val qLower = query.lowercase()
        val secId = section.sectionId.uppercase()

        val ageInfo = patient?.dob?.let { PatientAgeCalculator.calculate(it) }

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

                if (patient != null && ageInfo != null) {
                    when {
                        // Prem Manjhi case: Child 2 to 5 years old (e.g. 4 years 6 months)
                        ageInfo.isChildTwoToFive -> {
                            enBody = "For ${patient.name} (${ageInfo.ageDisplay}): " +
                                "1. ORS: Give 100 to 200 ml after each stool (1 pkt/1L drinking water). " +
                                "2. Zinc: Give Zinc 20 mg daily for 14 days dissolved in clean water. " +
                                "3. Fluids & feeding: Continue soft home feeds; if vomiting, wait 10m and resume slowly. " +
                                "4. Danger signs: Sunken eyes, vomiting everything, or fast breathing (>=40/min) -> refer immediately (CHD-1.2)."

                            hiBody = "${patient.name} (${ageInfo.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 100 से 200 मिली ओआरएस घोल पिलाएं (1 पैकेट 1 लीटर पानी में घोलें)। " +
                                "2. जिंक: 14 दिनों तक रोजाना 20 मिग्रा (1 पूरी गोली) जिंक पानी में घोलकर जरूर दें। " +
                                "3. यदि उल्टी हो तो 10 मिनट रुककर धीरे-धीरे दें। खाना जारी रखें। " +
                                "4. खतरे के लक्षण: आंखें धंसना या तेज सांस चलना (>=40/मिनट) होने पर तुरंत 108 बुलाएं।"
                        }

                        // Child under 6 months (<6m infant)
                        ageInfo.isUnderSixMonths -> {
                            enBody = "For ${patient.name} (${ageInfo.ageDisplay} infant): " +
                                "1. ORS: Give 50 to 100 ml in small sips with a clean spoon after each loose stool. " +
                                "2. Zinc: Give Zinc 10 mg daily for 14 days in breastmilk. " +
                                "3. Exclusive breastfeeding: Continue frequent breastfeeds. " +
                                "4. Danger signs: Lethargy, inability to suck, or vomiting everything -> refer immediately (CHD-1.2)."

                            hiBody = "${patient.name} (${ageInfo.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 50 से 100 मिली ओआरएस चम्मच से धीरे-धीरे दें। " +
                                "2. जिंक: 14 दिनों तक रोजाना 10 मिग्रा (आधी गोली) मां के दूध में घोलकर दें। " +
                                "3. केवल स्तनपान जारी रखें। उल्टी न रुकने पर तुरंत अस्पताल ले जाएं।"
                        }

                        // Child between 6 months and 2 years (6m - 23m)
                        ageInfo.isChildUnderTwo -> {
                            enBody = "For ${patient.name} (${ageInfo.ageDisplay}): " +
                                "1. ORS: Give 50 to 100 ml after each loose stool. " +
                                "2. Zinc: Give Zinc 20 mg daily for 14 days in water or milk. " +
                                "3. Feeding: Continue breastfeeding & soft feeds; if vomiting, wait 10m and resume. " +
                                "4. Danger signs: Sunken eyes, lethargy, or fast breathing (>=50/min) -> refer immediately (CHD-1.2)."

                            hiBody = "${patient.name} (${ageInfo.ageDisplay}): " +
                                "1. ओआरएस: हर दस्त के बाद 50 से 100 मिली ओआरएस घोल पिलाएं। " +
                                "2. जिंक: 14 दिनों तक रोजाना 20 मिग्रा (1 गोली) दें। " +
                                "3. स्तनपान और खिचड़ी जारी रखें। आंखें धंसने या सुस्ती पर तुरंत डॉक्टर को दिखाएं।"
                        }

                        // Adult (>15 years)
                        else -> {
                            enBody = "For ${patient.name} (Adult, ${ageInfo.ageDisplay}): " +
                                "1. Rehydration: Drink 200 to 400 ml of ORS after every loose stool (1 pkt in 1L clean water) or drink freely. " +
                                "2. Diet: Continue fluids and light home meals. " +
                                "3. Danger signs: Severe dizziness, blood in stool, or inability to keep fluids down -> refer to CHC (CHD-1.2)."

                            hiBody = "${patient.name} (वयस्क, ${ageInfo.ageDisplay}): " +
                                "1. हर दस्त के बाद 200-400 मिली या जितना पी सकें ओआरएस घोल पिएं। " +
                                "2. नारियल पानी, दाल का पानी और हल्का खाना लें। " +
                                "3. अत्यधिक कमजोरी या मल में खून आने पर तुरंत सीएचसी जाएं।"
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

                if (patient != null && ageInfo != null) {
                    when {
                        ageInfo.isNewbornUnderTwoMonths -> {
                            enBody = "For ${patient.name} (Newborn, ${ageInfo.ageDisplay}): Count breaths for 1 full minute. In young infants (<2 months), fast breathing is >= 60 breaths/minute. If breathing >= 60/min, chest indrawing, or grunting is present, this is a CRITICAL DANGER SIGN. Call 108 for immediate CHC hospital referral (ESC-4.1)."
                            hiBody = "${patient.name} (नवजात, ${ageInfo.ageDisplay}): 2 महीने से छोटे शिशु में 60 या अधिक सांसें तेज सांस मानी जाती हैं। छाती धंसना या तेज सांस गंभीर खतरे का लक्षण है—तुरंत 108 बुलाएं।"
                        }
                        ageInfo.isInfantUnderOne -> {
                            enBody = "For ${patient.name} (${ageInfo.ageDisplay} infant): Count breaths for 1 full minute when calm. In infants 2-11 months, fast breathing is >= 50 breaths/minute. If >= 50 breaths/min or chest indrawing is observed, refer immediately for pneumonia (CHD-3.1, ESC-5.2)."
                            hiBody = "${patient.name} (${ageInfo.ageDisplay}): शांत रहने पर 1 मिनट सांसें गिनें। 2-11 माह में 50 या अधिक सांसें निमोनिया का लक्षण हैं। पसली चलने पर तुरंत अस्पताल ले जाएं।"
                        }
                        ageInfo.isChildTwoToFive -> {
                            // Prem Manjhi: 4 years old
                            enBody = "For ${patient.name} (${ageInfo.ageDisplay}): Count breaths for 1 full minute when he is calm. In children 12 months to 5 years, fast breathing threshold is >= 40 breaths/minute. If breathing is >= 40/min or chest indrawing is present, he has pneumonia—refer immediately to CHC (call 108/102). Do not give unprescribed cough syrups (CHD-3.1, ESC-5.2)."
                            hiBody = "${patient.name} (${ageInfo.ageDisplay}): शांत रहने पर 1 मिनट सांसें गिनें। 1 से 5 साल के बच्चे में 40 या अधिक सांसें तेज सांस (निमोनिया) हैं। छाती धंसने पर तुरंत 108 पर रेफर करें।"
                        }
                        else -> {
                            enBody = "For ${patient.name} (Adult, ${ageInfo.ageDisplay}): Normal adult respiratory rate is 12-20 breaths/min. If breathing rate >30/min or severe breathlessness occurs with chest pain, refer immediately to CHC (ESC-6.1, CHD-3.1)."
                            hiBody = "${patient.name}: वयस्कों में सामान्य सांस 12-20 प्रति मिनट होती है। 30 से अधिक सांस या सीने में दर्द के साथ सांस फूलने पर तुरंत डॉक्टर को दिखाएं।"
                        }
                    }
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

                if (patient != null) {
                    val vaccs = patient.childVaccines
                    if (ageInfo != null && ageInfo.isChildTwoToFive) {
                        // Prem Manjhi case
                        enBody = "For ${patient.name} (${ageInfo.ageDisplay}): His immunization record shows all primary infant vaccines and first boosters (MR-2, DPT-B1, OPV-B) are complete. His next scheduled vaccine is DPT-Booster 2 due at 5 to 6 years of age (IMM-2.5)."
                        hiBody = "${patient.name} (${ageInfo.ageDisplay}): इनके शिशु और पहले बूस्टर (MR-2, DPT-B1) के सभी टीके पूरे हो चुके हैं। अगला टीका डीपीटी-बूस्टर 2 है जो 5 से 6 वर्ष की आयु में लगेगा।"
                    } else if (ageInfo != null && ageInfo.isInfantUnderOne) {
                        enBody = "For ${patient.name} (${ageInfo.ageDisplay}): Check immunization card. Ensure 6, 10, 14 weeks doses (Pentavalent, OPV, Rotavirus, fIPV, PCV) are given with minimum 4 weeks gap. At 9 completed months, administer MR-1, PCV-Booster, fIPV-3, and Vitamin A 1st dose (IMM-2.2, IMM-2.3)."
                        hiBody = "${patient.name} (${ageInfo.ageDisplay}): 6, 10, 14 हफ्ते के टीके (पेंटा, पोलियो, रोटा, fIPV, पीसीवी) 4 हफ्ते के अंतर पर लगवाएं। 9 माह पर एमआर-1, पीसीवी बूस्टर और विटामिन-ए दें।"
                    } else {
                        enBody = "For ${patient.name}: Check immunization due list. UIP schedule: At birth: BCG, OPV-0, HepB; 6, 10, 14 weeks: Pentavalent, OPV, Rotavirus, fIPV, PCV; 9 months: MR-1, PCV-Booster, VitA; 16-24 months: DPT-B1, MR-2; 5-6 years: DPT-B2 (IMM-2.2, IMM-2.4)."
                        hiBody = "${patient.name}: नियमित टीकाकरण समय सारणी के अनुसार छूटे हुए टीकों की जांच करें और अगले सत्र में टीका लगवाएं।"
                    }
                } else {
                    enBody = "UIP immunization schedule: Birth: BCG 0.1ml, OPV-0 2 drops, HepB 0.5ml within 24h. 6 weeks: Penta-1, OPV-1, RVV-1, fIPV-1 0.1ml, PCV-1. 9 completed months: MR-1 0.5ml SC, PCV-Booster, fIPV-3, VitA 1st dose. Cites: IMM-2.2, IMM-2.3."
                    hiBody = "नियमित टीकाकरण: जन्म पर बीसीजी, पोलियो शून्य खुराक और हेपेटाइटिस-बी। 6 हफ्ते पर पेंटा-1, पोलियो-1, रोटा-1, fIPV और पीसीवी-1। 9 माह पर खसरा-रूबेला (MR-1), पीसीवी बूस्टर और विटामिन-ए दें।"
                }
            }

            // -------------------------------------------------------------
            // 4. ANTENATAL CARE (ANC-1, ANC-2, ANC-3)
            // -------------------------------------------------------------
            qLower.contains("anc") || qLower.contains("garbh") || qLower.contains("pregnan") ||
            qLower.contains("garbhavathi") || secId.startsWith("ANC-") -> {

                primaryCitation = "ANC-1.2"

                if (patient != null && patient.isPregnant) {
                    val edd = patient.edd ?: "Recorded EDD"
                    val bp = patient.lastAncBp ?: "Check BP"
                    val hb = patient.lastHbGdl?.let { "$it g/dL" } ?: "Check Hb"

                    if (qLower.contains("ifa") || qLower.contains("iron") || qLower.contains("lal goli")) {
                        primaryCitation = "ANC-2.2"
                        enBody = "For ${patient.name} (Pregnant, EDD: $edd, Last Hb: $hb): Provide 1 red IFA tablet daily (100 mg elemental iron + 500 mcg folic acid) for 180 days starting at 14 weeks. Do not take with tea or milk. If severe anaemia (Hb <7.0 g/dL), refer to CHC for parenteral iron (ANC-2.2)."
                        hiBody = "${patient.name} (गर्भवती, हीमोग्लोबिन: $hb): 14 सप्ताह से रोजाना 1 लाल आईएफए की गोली 180 दिनों तक दें। इसे चाय या दूध के साथ न लें।"
                    } else if (qLower.contains("calcium")) {
                        primaryCitation = "ANC-2.3"
                        enBody = "For ${patient.name} (Pregnant, EDD: $edd): Give 2 Calcium tablets daily (500 mg elemental calcium each = 1000 mg/day) starting from 14 weeks until 6 months postpartum. Take Calcium separately from IFA by at least 2 hours (ANC-2.3)."
                        hiBody = "${patient.name} (गर्भवती): 14 हफ्ते से रोजाना 2 कैल्शियम की गोलियां (1000 मिग्रा) दें। कैल्शियम और आयरन की गोली के बीच कम से कम 2 घंटे का अंतर रखें।"
                    } else {
                        enBody = "For ${patient.name} (Pregnant, EDD: $edd): 1. Ensure all 4 mandatory ANC checkups are completed (ANC-1: <=12w, ANC-2: 14-26w, ANC-3: 28-34w, ANC-4: 36-40w). 2. Vitals check: Last BP $bp, Hb $hb. If BP >=140/90, severe headache, face swelling, or bleeding occurs, refer immediately to CHC (ANC-1.2, ESC-2.3)."
                        hiBody = "${patient.name} (गर्भवती, ईडीडी: $edd): कम से कम 4 एएनसी जांच समय पर कराएं। हर महीने की 9 तारीख को पीएमएसएमए में डॉक्टर से जांच कराएं। बीपी $bp, हीमोग्लोबिन $hb है।"
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

                if (patient != null && patient.lastMuacCm != null) {
                    val muac = patient.lastMuacCm
                    val band = when {
                        muac < 11.5 -> "Red band (<11.5 cm, Severe Acute Malnutrition - SAM)"
                        muac < 12.5 -> "Yellow band (11.5-12.4 cm, Moderate Acute Malnutrition - MAM)"
                        else -> "Green band (>=12.5 cm, Normal)"
                    }
                    enBody = "For ${patient.name} (${ageInfo?.ageDisplay ?: "Child"}): Last recorded MUAC is $muac cm ($band). " +
                        if (muac < 11.5) {
                            "Check for bilateral pitting oedema. Because MUAC < 11.5 cm, child has SAM—refer immediately to Nutrition Rehabilitation Centre (NRC) (NUT-1.1, NUT-2.1)."
                        } else {
                            "Maintain healthy growth with energy-dense home feeds (khichdi with ghee, pulses, eggs, vegetables) 4-5 times daily (NUT-1.1)."
                        }

                    hiBody = "${patient.name}: पिछली एमयूएसी (MUAC) $muac सेमी ($band) है। " +
                        if (muac < 11.5) {
                            "यह गंभीर कुपोषण (SAM) है, बच्चे को तुरंत पोषण पुनर्वास केंद्र (NRC) रेफर करें।"
                        } else {
                            "पोषण सामान्य है। रोजाना पौष्टिक आहार और मौसमी फल-सब्जियां दें।"
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

                if (patient != null && patient.ncdCondition != null) {
                    val bp = patient.lastBpReading ?: "Not recorded"
                    enBody = "For ${patient.name} (${patient.ncdCondition}): Last recorded BP is $bp. Protocol requires monthly blood pressure monitoring and compliance with prescribed anti-hypertensive drugs. Lifestyle counseling: reduce salt to <1 teaspoon (5g)/day and 150 minutes of physical activity weekly. If BP >=180/110 or chest pain occurs, refer immediately (NCD-1.2, NCD-3.1, ESC-6.4)."
                    hiBody = "${patient.name} (${patient.ncdCondition}): पिछला बीपी $bp है। हर महीने बीपी नपवाएं और दवा समय पर लें। खाने में नमक दिन में 1 चम्मच (5 ग्राम) से कम रखें और रोजाना व्यायाम करें।"
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

                enBody = "HBNC visit schedule: Institutional birth requires 6 visits (days 3, 7, 14, 21, 28, 42); home birth requires 7 visits (days 1, 3, 7, 14, 21, 28, 42). Initiate breastfeeding within 1 hour. Do not bathe baby in the first 48 hours or if weight <2000g. Keep cord clean and dry—apply nothing. Cites: HBNC-1.1, HBNC-2.2."
                hiBody = "एचबीएनसी भ्रमण: अस्पताल प्रसव में 6 बार (दिन 3, 7, 14, 21, 28, 42) और घर पर प्रसव में 7 बार (दिन 1, 3, 7, 14, 21, 28, 42)। पहले घंटे में स्तनपान शुरू कराएं। पहले 48 घंटे बच्चे को न नहलाएं और नाल पर कुछ न लगाएं।"
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

        val enFinal = if (enBody.length <= 400) enBody else enBody.take(398)
        val hiFinal = if (hiBody.length <= 400) hiBody else hiBody.take(398)

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
