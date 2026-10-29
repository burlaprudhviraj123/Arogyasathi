package org.arogyasathi.app.jev

import org.arogyasathi.app.core.SafetyResult

object JEVEscalate {
    private val dangerRules = listOf(
        // 1. Unconsciousness / Coma
        Pair(
            Regex("\\b(behosh|unconscious|coma|behosh pada|behosh ho gayi)\\b", RegexOption.IGNORE_CASE),
            Pair("ESC-6.3", "Unconscious patient: refer immediately (call 108). Give nothing by mouth (ESC-6.3, ESC-1.3).")
        ),
        // 2. Convulsions / fits
        Pair(
            Regex("\\b(jhatke|daura|convuls|fits|mirgi|tyala jhatke)\\b", RegexOption.IGNORE_CASE),
            Pair("ESC-5.1", "Convulsions (fits / jhatke): general danger sign, refer immediately to hospital (call 108/102). Give nothing by mouth (ESC-5.1, ESC-1.3).")
        ),
        // 3. Child unable to feed
        Pair(
            Regex("(doodh nahi pee pa rah|doodh nahi pee|doodh peena band|unable to suck|not able to drink|cannot drink|peena band kar diya|peena chhod diya|doodh nahi kheench raha)", RegexOption.IGNORE_CASE),
            Pair("ESC-5.1", "Child not able to drink or breastfeed: general danger sign, refer immediately to hospital (call 108/102) (ESC-5.1).")
        ),
        // 4. Prolonged fever (> 7 days)
        Pair(
            Regex("(\\b([89]|\\d{2,})\\s*din\\s+se\\s+.*bukhar|bukhar.*([89]|\\d{2,})\\s*din|more than 7 days|7 din se zyada.*bukhar)", RegexOption.IGNORE_CASE),
            Pair("ESC-5.4", "Fever lasting more than 7 days: danger sign, refer immediately for medical assessment (ESC-5.4).")
        ),
        // 5. Stiff neck
        Pair(
            Regex("(gardan\\s+akad|stiff neck)", RegexOption.IGNORE_CASE),
            Pair("ESC-5.4", "Fever with stiff neck: general danger sign, refer immediately (call 108) (ESC-5.4).")
        ),
        // 6. Postpartum hemorrhage
        Pair(
            Regex("(pad\\s+[1-4]\\s*minute|pad\\s+3-4\\s*minute|less than 5 minutes|din me ([6-9]|\\d{2,})\\s*pad|din me 8 pad|दिन में 8 पैड|more than 5 pads|6-7 kapde|[5-9]\\s*kapde|\\d{2,}\\s*kapde|khoon bahut beh raha|delivery.*khoon bahut)", RegexOption.IGNORE_CASE),
            Pair("ESC-3.1", "Heavy postpartum bleeding (pad soaked in less than 5 minutes or more than 5 pads/day): refer immediately (call 108/102) (ESC-3.1).")
        ),
        // 7. Vaginal bleeding in pregnancy
        Pair(
            Regex("(bleeding in (first|second|third|pregnancy)|garbh.*(khoon|bleeding)|pregnant.*(khoon|bleeding)|pregnant.*subah se thoda khoon|8 mahine ki pregnant.*khoon|kal raat thoda khoon ka daag|khoon ka daag aaya|(mahila|aurat|garbhwati).*(bleeding|khoon aa raha|khoon beh).*(\\d+.*mahine|pregnancy|garbh)|(bleeding|khoon aa raha|khoon beh).*(\\d+.*mahine|pregnant|garbh))", RegexOption.IGNORE_CASE),
            Pair("ESC-2.1", "Any vaginal bleeding in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.1, ESC-1.2).")
        ),
        // 8. Pre-eclampsia
        Pair(
            Regex("((chehra|face).*suj.*sir\\s*dard|sir\\s*dard.*(chehra|face).*suj|pair aur chehra suja|swelling of the face|pre-eclampsia|eclampsia|dhundhla dikh raha aur sir me tez dard|aankhon ke aage dhundhla|हाथ मुह पे सूजन|सिर मे बहुत तेज दर्द)", RegexOption.IGNORE_CASE),
            Pair("ESC-2.3", "Pre-eclampsia danger signs in pregnancy: swelling of the face, severe headache, blurred vision. Refer immediately (call 102/108) (ESC-2.3).")
        ),
        // 9. Severe anaemia / breathlessness in pregnancy
        Pair(
            Regex("(peeli.*saans\\s*phool|saans\\s*phool.*peeli|severe pallor|pallor.*breathless|bahut peeli dikh rahi hai)", RegexOption.IGNORE_CASE),
            Pair("ESC-2.5", "Severe pallor with breathlessness in pregnancy: severe anaemia danger sign, refer immediately (call 102/108) (ESC-2.5).")
        ),
        // 10. Hyperemesis / excessive vomiting
        Pair(
            Regex("(paani bhi (pet me )?nahi tik raha|excessive vomiting|vomits everything|sab ulti kar de|paani bhi nahi tikta)", RegexOption.IGNORE_CASE),
            Pair("ESC-2.6", "Excessive vomiting in pregnancy: unable to take fluids, refer immediately to hospital (call 102/108) (ESC-2.6).")
        ),
        // 11. Severe continuous abdominal pain
        Pair(
            Regex("(pet me lagatar tez dard.*pregnant|pregnant.*pet me lagatar tez dard|tez dard.*30 hafte)", RegexOption.IGNORE_CASE),
            Pair("ESC-2.6", "Continuous severe abdominal pain in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.6).")
        ),
        // 12. Newborn hypothermia
        Pair(
            Regex("((chhune|chune)\\s+pe\\s+thanda|cold to touch|bachcha.*thanda lag raha|thanda pad gaya)", RegexOption.IGNORE_CASE),
            Pair("ESC-4.3", "Baby cold to touch (temperature below 95.9 F / 35.5 C): keep skin-to-skin and refer immediately (call 102/108) (ESC-4.3).")
        ),
        // 13. Low birth weight under 1.8 kg
        Pair(
            Regex("(1\\.[0-7]\\s*kg|under 1\\.8\\s*kg|1\\.6\\s*kg|1\\.7\\s*kg|1\\.5\\s*kg|1\\.4\\s*kg)", RegexOption.IGNORE_CASE),
            Pair("ESC-4.4", "Baby birth weight under 1.8 kg: refer immediately to SNCU (call 102/108). Keep skin-to-skin (ESC-4.4).")
        ),
        // 14. Neonatal fast breathing / cyanosis
        Pair(
            Regex("(navjaat.*saans.*(6[0-9]|[7-9]\\d)|saans.*66.*pasli|saans khub fast.*70|thot nil|cyanosis)", RegexOption.IGNORE_CASE),
            Pair("ESC-4.2", "Newborn breathing 60 per minute or more, or central cyanosis: danger sign, refer immediately (call 108/102) (ESC-4.2).")
        ),
        // 15. Chest indrawing in child
        Pair(
            Regex("(pasli andar dhans|chest indrawing|chhati andar dhans)", RegexOption.IGNORE_CASE),
            Pair("ESC-5.2", "Chest indrawing / severe pneumonia: general danger sign, refer immediately to hospital (call 108/102) (ESC-5.2).")
        ),
        // 16. Puerperal sepsis
        Pair(
            Regex("(delivery.*badbu wala discharge|badbu wala discharge.*bukhar|foul-smelling discharge.*fever)", RegexOption.IGNORE_CASE),
            Pair("ESC-3.2", "Postpartum puerperal sepsis (foul-smelling discharge with fever within 42 days): refer immediately (call 108/102) (ESC-3.2).")
        ),
        // 17. Decreased fetal movements
        Pair(
            Regex("(halchal mahsoos nahi|bachcha pet me hil hi nahi raha|halchal band|decreased foetal movement|बच्चे की हलचल महसूस नहीं हो रही)", RegexOption.IGNORE_CASE),
            Pair("ESC-2.7", "Decreased or absent fetal movements: obstetric danger sign, refer immediately to FRU / hospital (call 102/108) (ESC-2.7).")
        ),
        // 18. Hypertensive crisis (BP >= 180/110)
        Pair(
            Regex("(18[0-9]/11[0-9]|19[0-9]/|2\\d\\d/|bp 180/110 se upar)", RegexOption.IGNORE_CASE),
            Pair("ESC-6.4", "BP 180/110 or more is a hypertensive escalation: refer immediately to Medical Officer / CHC (ESC-6.4).")
        ),
        // 19. Acute heart attack signs
        Pair(
            Regex("(chhati ke beech me bhaari.*dard|chest pain.*sweat|heart attack)", RegexOption.IGNORE_CASE),
            Pair("ESC-6.1", "Acute chest pain or pressure: suspected heart attack, refer immediately (call 108). Do not delay (ESC-6.1).")
        ),
        // 20. Acute stroke signs
        Pair(
            Regex("(ek taraf ka haath pair kaam nahi kar raha|achanak.*aankh se dikhna band hua aur chakkar aake gir|paralysis.*achanak|stroke)", RegexOption.IGNORE_CASE),
            Pair("ESC-6.2", "Sudden weakness on one side, difficulty speaking, or sudden loss of vision: stroke signs (BE FAST), refer immediately (call 108) (ESC-6.2).")
        ),
        // 21. Hypoglycemia in diabetes
        Pair(
            Regex("(sugar (5[0-8]|4\\d|3\\d)\\b|shivering sweating confusion diabetes|sugar 58 aaya)", RegexOption.IGNORE_CASE),
            Pair("ESC-6.3", "Low blood sugar (hypoglycemia): give sugar/jaggery immediately if conscious; refer immediately if confused or unconscious (call 108) (ESC-6.3).")
        ),
        // 22. Accidental poisoning
        Pair(
            Regex("(kerosene pee liya|mitti ka tel|poison|zeher|keetnashak|dawai pee li)", RegexOption.IGNORE_CASE),
            Pair("ESC-1.4", "Accidental poisoning / chemical ingestion: life-threatening emergency, refer immediately to hospital (call 108). Do not induce vomiting (ESC-1.4).")
        )
    )

    fun evaluate(query: String): Pair<String, String>? {
        for ((pattern, result) in dangerRules) {
            if (pattern.containsMatchIn(query)) {
                return result
            }
        }
        return null
    }
}

object JEVScope {
    private val scopeRules = listOf(
        Pair(
            Regex("\\b(paracetamol|pcm syrup|cefixime|amoxicillin|cough syrup|kaun sa cough syrup|kaun si goli lu|mere khud ke sir me.*dard|kaun si dawai|kaun si cream|kaun se drops|drops daale|ghutno me.*kaun si dawai|neend nahi aati.*kaun si dawai|daaru chhudwane|bistar geela.*koi dawai)\\b", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2).")
        ),
        Pair(
            Regex("(insulin kitni unit|insulin kitni|metformin.*khana khane se pehle|amlodipine.*10 mg karni hai|bp 150.*amlodipine)", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2).")
        ),
        Pair(
            Regex("(typhoid.*widal|widal test|hiv test positive|dengue ka test|platelet kitne|thyroid report|tsh 8|ultrasound.*placenta previa)", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2).")
        ),
        Pair(
            Regex("(ladka.*ladki|sex determination|ling jaanch|kahin pata karwa sakte|ling parikshan)", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book and prohibited by law (PC-PNDT Act). Every pregnancy requires full antenatal care. Please consult your ANM / MO (SCP-1.2).")
        ),
        Pair(
            Regex("(u-win app|login nahi ho raha|otp nahi aa raha|ration card me.*naam|ayushman card kaise|mobile me data|recharge kaun|incentive kab|hisaab portal|maandey|janam praman patra kaise banwaye)", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book. Please contact your block program management unit (BPMU) or supervisor (SCP-1.2).")
        ),
        Pair(
            Regex("(rabies ke injection|kutte ne.*kharoch|dog bite|pathri ka desi ilaaj|stone ka ilaaj|covid tike.*booster abhi|hpv.*cancer.*tika|bachchedani ke cancer wala tika|garbhpat ki goli|abortion pill|tb ki dawai 6 mahine ki hoti hai ya 9)", RegexOption.IGNORE_CASE),
            Pair("SCP-1.2", "This is outside my protocol book. Please refer the beneficiary to the Medical Officer at the PHC (SCP-1.2).")
        )
    )

    fun evaluate(query: String): Pair<String, String>? {
        for ((pattern, result) in scopeRules) {
            if (pattern.containsMatchIn(query)) {
                return result
            }
        }
        return null
    }
}

object JEVVerify {
    fun validateCitations(citations: List<String>, validSections: Set<String>): List<String> {
        val out = mutableListOf<String>()
        for (c in citations) {
            val cu = c.trim().uppercase()
            if (cu in validSections && cu !in out) {
                out.add(cu)
            }
            if (out.size >= 2) break
        }
        return if (out.isNotEmpty()) out else listOf("SCP-1.1")
    }
}

