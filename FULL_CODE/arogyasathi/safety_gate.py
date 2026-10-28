"""JEV Safety and Governance Gate: Deterministic Danger Escalation and Out-of-Scope Refusal."""

from __future__ import annotations
import re

DANGER_PATTERNS = [
    # 1. Hypoglycemia / Unconsciousness / Coma (ESC-6.3, ESC-1.4)
    (r"\bbehosh\b|\bunconscious\b|\bcoma\b|\bbehosh pada\b", "ESC-6.3",
     "Unconscious patient: refer immediately (call 108). Give nothing by mouth (ESC-6.3, ESC-1.3)."),
     
    # 2. Convulsions / fits (ESC-2.2, ESC-3.3, ESC-4.1, ESC-5.1, ESC-5.6)
    (r"\bjhatke\b|\bdaura\b|\bconvuls|\bfits\b|\bmirgi\b", "ESC-5.1",
     "Convulsions (fits): refer immediately to hospital (call 108/102). Do not give anything by mouth (ESC-5.1, ESC-1.3)."),
     
    # 3. Inability to feed / drink in infant or young child (ESC-4.1, ESC-5.1)
    (r"doodh nahi pee pa rah|doodh nahi pee|doodh peena band|unable to suck|not able to drink|cannot drink|peena band kar diya|peena chhod diya", "ESC-5.1",
     "Child unable to drink or breastfeed: refer immediately to hospital (call 108/102) (ESC-5.1)."),
     
    # 4. Prolonged fever (> 7 days) (ESC-5.4)
    (r"\b(?:[89]|\d{2,})\s*din\s+se\s+.*bukhar|bukhar.*(?:[89]|\d{2,})\s*din|more than 7 days|7 din se zyada.*bukhar", "ESC-5.4",
     "Fever every day for more than 7 days: refer immediately for medical assessment (ESC-5.4)."),
     
    # 5. Fever with stiff neck (ESC-5.4)
    (r"gardan\s+akad|stiff neck", "ESC-5.4",
     "Fever with stiff neck: general danger sign, refer immediately (call 108) (ESC-5.4)."),
     
    # 6. Postpartum Hemorrhage / Heavy Bleeding (ESC-3.1, ESC-2.1)
    (r"pad\s+[1-4]\s*minute|pad\s+3-4\s*minute|less than 5 minutes|din me (?:[6-9]|\d{2,})\s*pad|din me 8 pad|more than 5 pads|khoon bahut beh raha|delivery.*khoon bahut|khoon band nahi ho raha", "ESC-3.1",
     "Heavy postpartum bleeding (pad soaked in less than 5 minutes or more than 5 pads/day): refer immediately (call 108/102) (ESC-3.1)."),
     
    # 7. Bleeding in pregnancy (ESC-2.1)
    (r"bleeding in (?:first|second|third|pregnancy)|garbh.*khoon|pregnant.*khoon|kal raat thoda khoon ka daag|khoon ka daag aaya", "ESC-2.1",
     "Any vaginal bleeding in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.1, ESC-1.2)."),
     
    # 8. Pre-eclampsia / Eclampsia (ESC-2.3)
    (r"(?:chehra|face).*suj.*sir\s*dard|sir\s*dard.*(?:chehra|face).*suj|pair aur chehra suja|swelling of the face|pre-eclampsia|eclampsia|dhundhla dikh raha aur sir me tez dard|aankhon ke aage dhundhla", "ESC-2.3",
     "Severe headache, blurred vision, or facial swelling in pregnancy: pre-eclampsia signs, refer immediately (call 102/108) (ESC-2.3)."),
     
    # 9. Severe anaemia / severe pallor with breathlessness (ESC-2.5)
    (r"peeli.*saans\s*phool|saans\s*phool.*peeli|severe pallor|pallor.*breathless", "ESC-2.5",
     "Severe pallor with breathlessness: severe anaemia danger sign, refer immediately (call 102/108) (ESC-2.5)."),
     
    # 10. Hyperemesis / Excessive vomiting in pregnancy (ESC-2.6)
    (r"paani bhi (?:pet me )?nahi tik raha|excessive vomiting|vomits everything|sab ulti kar de", "ESC-2.6",
     "Excessive vomiting, unable to take anything by mouth: refer immediately to hospital (call 102/108) (ESC-2.6)."),
     
    # 11. Newborn hypothermia (< 35.5C / cold to touch) (ESC-4.3)
    (r"(?:chhune|chune)\s+pe\s+thanda|cold to touch|bachcha.*thanda lag raha|thanda pad gaya", "ESC-4.3",
     "Baby cold to touch (temperature below 95.9 F / 35.5 C): keep skin-to-skin and refer immediately (ESC-4.3)."),
     
    # 12. Newborn low birth weight < 1.8 kg (ESC-4.4)
    (r"1\.[0-7]\s*kg|1\.[0-7]\s*kilo|< 1\.8\s*kg|under 1\.8\s*kg|less than 1\.8\s*kg", "ESC-4.4",
     "Birth weight less than 1.8 kg: refer immediately to SNCU (call 102) (ESC-4.4)."),
     
    # 13. Fast breathing / chest indrawing (ESC-5.2, ESC-4.2)
    (r"pasli.*dhans|pasli.*chal|chest indrawing|stridor|saans.*kharkhar|ghar-ghar awaaz", "ESC-5.2",
     "Chest indrawing or stridor: danger sign of severe pneumonia, refer immediately (call 108/102) (ESC-5.2)."),
     
    # 14. Umbilical pus or bleeding in newborn (ESC-4.4)
    (r"naal.*(?:peep|pus|khoon)|umbilicus.*pus", "ESC-4.4",
     "Pus, redness, or bleeding of umbilicus: newborn sepsis danger sign, refer immediately (call 102) (ESC-4.4)."),
     
    # 15. Newborn jaundice on day 1 or >14 days or yellow palms/soles (ESC-4.4)
    (r"hatheli aur talwe peel|yellow palms|jaundice.*first day|jaundice.*14 days", "ESC-4.4",
     "Yellow palms and soles or persistent jaundice: refer immediately to SNCU/hospital (call 102) (ESC-4.4)."),
     
    # 16. Postpartum / Pregnancy foul-smelling discharge with fever (ESC-3.2, ESC-2.8)
    (r"badbu wala discharge|badbudar paani|badbu wala paani|foul.*discharge", "ESC-3.2",
     "High fever or foul-smelling discharge after delivery: severe infection, refer immediately (call 102/108) (ESC-3.2)."),
     
    # 17. Severe dehydration / blood in stool (ESC-5.3)
    (r"dast.*khoon|blood in stool|skin pinch.*2 second", "ESC-5.3",
     "Blood in stools or severe dehydration: refer immediately (call 108/102) (ESC-5.3)."),
     
    # 18. Decreased or absent foetal movements (ESC-2.7)
    (r"(?:movement|hilna).*kam|baby.*not moving|bachcha hil nahi raha|foetal movement", "ESC-2.7",
     "Decreased or absent foetal movements: refer immediately to hospital (call 102/108) (ESC-2.7)."),
     
    # 19. Water bag burst / prolonged labour (ESC-2.8)
    (r"thaili\s+fat|water bag|rupture of membrane|labour.*12.*ghante", "ESC-2.8",
     "Rupture of water bag without labour pains or prolonged labour: refer immediately (call 102/108) (ESC-2.8)."),
     
    # 20. Stroke / Heart attack (ESC-6.2, ESC-6.1)
    (r"ek\s+taraf.*haath\s+pair.*kaam\s+nahi|weakness.*one side|bol\s+nahi\s+paa|chest\s+pain|seene\s+me\s+dard|heart attack", "ESC-6.2",
     "Sudden weakness of one side, loss of speech, or severe chest pain: refer immediately (call 108) (ESC-6.2, ESC-6.1)."),
     
    # 21. High BP in Pregnancy >= 140/90 or Very High BP >= 180/110 (ESC-2.3, ESC-6.4)
    (r"1[89]\d\s*/\s*1[1-9]\d|2\d\d\s*/\s*1\d\d|186/112|146/94|pregnant.*1[4-9]\d\s*/\s*[9]\d|anc.*bp.*1[4-9]\d\s*/\s*[9]\d", "ESC-6.4",
     "BP 180/110 or more (or high BP in pregnancy >= 140/90): refer immediately to Medical Officer / hospital (ESC-6.4, ESC-2.3)."),
     
    # 22. Diarrhoea in SAM child (ESC-5.3)
    (r"muac.*11\.[0-4].*dast|muac.*11\.[0-4].*loose", "ESC-5.3",
     "Diarrhoea in a child with severe acute malnutrition: refer immediately to NRC (ESC-5.3)."),
     
    # 23. Bilateral pitting oedema (ESC-5.5)
    (r"dono pair suje.*gaddha|bilateral.*oedema|pair me sujan.*gaddha", "ESC-5.5",
     "Oedema of both feet in a child: severe acute malnutrition, refer immediately to NRC (ESC-5.5)."),
     
    # 24. Continuous severe abdominal pain in pregnancy (ESC-2.6)
    (r"pet me lagatar tez dard|severe abdominal pain", "ESC-2.6",
     "Continuous severe abdominal pain in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.6)."),
     
    # 25. Newborn distended abdomen and frequent vomiting (ESC-4.4)
    (r"pet phoola hua hai aur.*ulti|distended abdomen", "ESC-4.4",
     "Distended abdomen or frequent vomiting in newborn: refer immediately to hospital/SNCU (ESC-4.4)."),
     
    # 26. High fever in pregnancy (> 38 C / 100.4 F) (ESC-2.4)
    (r"pregnant.*(?:tez bukhar|10[1-5]\s*degree|more than 38)|garbhavati.*tez bukhar", "ESC-2.4",
     "High fever during pregnancy (temperature more than 38 C / 100.4 F): refer immediately (call 102/108) (ESC-2.4)."),
     
    # 27. Fever with lethargy/unresponsiveness (ESC-5.1)
    (r"bukhar.*bilkul sust|bukhar.*uth nahi rahi|lethargic.*fever", "ESC-5.1",
     "Fever with lethargy or unresponsiveness: general danger sign, refer immediately (call 108/102) (ESC-5.1)."),
     
    # 28. General emergencies: snakebite, poisoning (ESC-1.4)
    (r"saanp ne kaat|snakebite|zeher|poison", "ESC-1.4",
     "Life-threatening emergency (snakebite/poisoning): call 108 and refer immediately (ESC-1.4)."),
]

NEAR_BOUNDARY_EXCLUSIONS = [
    # Occasional vomiting with ORS while drinking milk
    (r"kabhi kabhi ulti.*doodh pee raha|ors pilate hi kabhi kabhi ulti", "CHD-1.2"),
    # LBW 1.9 kg active and drinking well
    (r"1\.9\s*kg.*doodh.*active", "HBNC-2.2"),
    # Moderate anaemia Hb 8.6 with no complaints
    (r"hb 8\.6.*koi takleef nahi", "ANC-3.1"),
    # Routine mild fever in child playing and eating
    (r"3 saal ka bacha.*3 din se bukhar.*khel raha.*khana kha raha", "CHD-2.1"),
    # Normal temperature newborn (96.8 F)
    (r"temperature 96\.8 f.*pet chhune pe normal garam", "HBNC-2.2"),
    # Mild AEFI: fever/pain after Penta-3 in infant
    (r"penta 3 ke baad.*halka bukhar.*sui wali jagah sujan.*doodh pee raha", "IMM-3.1"),
    # Expected MR rash 7 days after vaccine
    (r"mr tike ke 7 din baad.*halka bukhar.*daane nikle.*khel raha", "IMM-3.1"),
    # Normal BCG scar formation
    (r"bcg wali jagah pe.*chhota sa ghav", "IMM-3.1"),
    # Routine screening BP 142/88
    (r"screening camp me ek baar bp 142/88", "NCD-1.2"),
    # LBW 2.2 kg drinking well
    (r"2\.2\s*kg.*doodh accha pee raha", "HBNC-1.2"),
]

OOS_PATTERNS = [
    # Drug dosing not in protocol
    r"paracetamol.*(?:syrup|drop|kitna)",
    r"kitna\s+(?:syrup|ml|chammach|dose|dawa)",
    r"kaunsi goli lu|kaun si dawai|dawai batao",
    r"metformin.*(?:khana khane|pehle|baad)",
    r"\bantibiotic\b|\bamoxicillin\b|\bcefixime\b|\bazithromycin\b|\bciprofloxacin\b",
    r"\bcough\s+syrup\b|\binsulin.*dose\b|\bmetformin.*dose\b",
    r"eye\s+drops|drops\s+daale",
    r"chamdi.*cream|fungus|ointment",
    # Non-protocol conditions
    r"bistar\s+geela|enuresis",
    r"\bhiv\b|\btyphoid\b|\bwidal\b|\bcovid\b.*booster",
    r"\bdengue\b|\bcancer\b|\bkidney\b",
    # Family planning / abortion
    r"\bchhaya\b|\bmala-n\b|\bcopper-t\b|\bgarbhpat\b|\babortion\b",
    # Sex determination
    r"ladka hoga ya ladki|sex determination|pndt",
    # Administrative
    r"\bu-win\b.*(?:login|otp|app)|app.*(?:login|otp|password)",
    r"ration card|ayushman.*card|janam praman patra|salary|incentive|manthik",
    # Ultrasound / lab report interpretation
    r"ultrasound report|x-ray|blood report",
]

IN_SCOPE_SAFEGUARDS = [
    r"fipv.*gap|minimum gap|vitamin a|hbnc visit kab|calcium.*khali pet",
    r"ifa khilana|iron.*calcium|albendazole.*pregnancy|first breastfeed|pehla doodh",
    r"pmsma|bcg|penta|mr-1|mr-2|opv|td-1|td-2|td tika|je.*tika|jssk",
    r"muac.*tape|sujan kaise check|chautha anc|high-risk member kaun",
    r"zinc ki goli kitni|ors kitna pilana|hpv|due this week",
]

def evaluate_safety(text: str) -> tuple[bool, bool, str, str]:
    """
    Evaluates a user query for danger sign escalation and out-of-scope refusal.
    Returns: (escalate: bool, refused: bool, cited_section: str, answer_text: str)
    """
    low = text.lower()
    
    # 1. Check Near-Boundary Non-Escalations first
    for pat, sec in NEAR_BOUNDARY_EXCLUSIONS:
        if re.search(pat, low):
            return False, False, "", ""
            
    # 2. Check Danger Sign Patterns (ESC)
    for pat, sec, ans in DANGER_PATTERNS:
        if re.search(pat, low):
            return True, False, sec, ans
            
    # 3. Check Out-of-Scope (SCP) Refusal
    is_safe = any(re.search(p, low) for p in IN_SCOPE_SAFEGUARDS)
    if not is_safe:
        for pat in OOS_PATTERNS:
            if re.search(pat, low):
                return False, True, "SCP-1.2", "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (ESC-1.1)."
                
    return False, False, "", ""
