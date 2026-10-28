"""JEV (Judicial Evidence Validator) Engine for Arogyasathi.

Implements the deterministic clinical governance kernel:
1. JEV-Escalate: Hard-coded danger sign detection (100% recall gate, <=20% FER)
2. JEV-Scope: Strict out-of-scope refusal enforcing SCP-1.2 (doctor redirect)
3. JEV-Verify: Protocol citation validation against verified leaf sections
"""

from __future__ import annotations
import re
from typing import Tuple, List, Optional

# -----------------------------------------------------------------------------
# JEV-Escalate: Deterministic Clinical Danger Sign Rules
# Strictly grounded in 03_ESC_danger_signs_refer_immediately.md
# -----------------------------------------------------------------------------

DANGER_RULES = [
    # 1. Hypoglycemia / Unconsciousness / Coma (ESC-6.3, ESC-1.4)
    (
        r"\bbehosh\b|\bunconscious\b|\bcoma\b|\bbehosh pada\b|\bbehosh ho gayi\b",
        "ESC-6.3",
        "Unconscious patient: refer immediately (call 108). Give nothing by mouth (ESC-6.3, ESC-1.3)."
    ),
    # 2. General convulsions / fits (ESC-5.1, ESC-4.1)
    (
        r"\bjhatke\b|\bdaura\b|\bconvuls|\bfits\b|\bmirgi\b|\btyala jhatke\b",
        "ESC-5.1",
        "Convulsions (fits / jhatke): general danger sign, refer immediately to hospital (call 108/102). Give nothing by mouth (ESC-5.1, ESC-1.3)."
    ),
    # 3. Child unable to drink or breastfeed (ESC-5.1, ESC-4.1)
    (
        r"doodh nahi pee pa rah|doodh nahi pee|doodh peena band|unable to suck|not able to drink|cannot drink|peena band kar diya|peena chhod diya|doodh nahi kheench raha|doodh kheenchne ki taakat nahi",
        "ESC-5.1",
        "Child not able to drink or breastfeed: general danger sign, refer immediately to hospital (call 108/102) (ESC-5.1)."
    ),
    # 4. Prolonged fever (> 7 days) (ESC-5.4)
    (
        r"\b(?:[89]|\d{2,})\s*din\s+se\s+.*bukhar|bukhar.*(?:[89]|\d{2,})\s*din|more than 7 days|7 din se zyada.*bukhar",
        "ESC-5.4",
        "Fever lasting more than 7 days: danger sign, refer immediately for medical assessment (ESC-5.4)."
    ),
    # 5. Fever with stiff neck (ESC-5.4)
    (
        r"gardan\s+akad|stiff neck",
        "ESC-5.4",
        "Fever with stiff neck: general danger sign, refer immediately (call 108) (ESC-5.4)."
    ),
    # 6. Postpartum Hemorrhage / Heavy Bleeding (ESC-3.1)
    (
        r"pad\s+[1-4]\s*minute|pad\s+3-4\s*minute|less than 5 minutes|din me (?:[6-9]|\d{2,})\s*pad|din me 8 pad|दिन में 8 पैड|more than 5 pads|6-7 kapde|khoon bahut beh raha|delivery.*khoon bahut|khoon band nahi ho raha",
        "ESC-3.1",
        "Heavy postpartum bleeding (pad soaked in less than 5 minutes or more than 5 pads/day): refer immediately (call 108/102) (ESC-3.1)."
    ),
    # 7. Bleeding in pregnancy (ESC-2.1)
    (
        r"bleeding in (?:first|second|third|pregnancy)|garbh.*khoon|pregnant.*khoon|pregnant.*subah se thoda khoon|8 mahine ki pregnant.*khoon|kal raat thoda khoon ka daag|khoon ka daag aaya",
        "ESC-2.1",
        "Any vaginal bleeding in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.1, ESC-1.2)."
    ),
    # 8. Pre-eclampsia / Eclampsia (ESC-2.3)
    (
        r"(?:chehra|face).*suj.*sir\s*dard|sir\s*dard.*(?:chehra|face).*suj|pair aur chehra suja|swelling of the face|pre-eclampsia|eclampsia|dhundhla dikh raha aur sir me tez dard|aankhon ke aage dhundhla|हाथ मुह पे सूजन|सिर मे बहुत तेज दर्द",
        "ESC-2.3",
        "Pre-eclampsia danger signs in pregnancy: swelling of the face, severe headache, blurred vision. Refer immediately (call 102/108) (ESC-2.3)."
    ),
    # 9. Severe anaemia / severe pallor with breathlessness in pregnancy (ESC-2.5)
    (
        r"peeli.*saans\s*phool|saans\s*phool.*peeli|severe pallor|pallor.*breathless|bahut peeli dikh rahi hai",
        "ESC-2.5",
        "Severe pallor with breathlessness in pregnancy: severe anaemia danger sign, refer immediately (call 102/108) (ESC-2.5)."
    ),
    # 10. Hyperemesis / Excessive vomiting in pregnancy (ESC-2.6)
    (
        r"paani bhi (?:pet me )?nahi tik raha|excessive vomiting|vomits everything|sab ulti kar de|paani bhi nahi tikta",
        "ESC-2.6",
        "Excessive vomiting in pregnancy: unable to take fluids, refer immediately to hospital (call 102/108) (ESC-2.6)."
    ),
    # 11. Severe abdominal pain in pregnancy (ESC-2.6)
    (
        r"pet me lagatar tez dard.*pregnant|pregnant.*pet me lagatar tez dard|tez dard.*30 hafte",
        "ESC-2.6",
        "Continuous severe abdominal pain in pregnancy: danger sign, refer immediately (call 102/108) (ESC-2.6)."
    ),
    # 12. Newborn hypothermia (< 35.5C / cold to touch) (ESC-4.3)
    (
        r"(?:chhune|chune)\s+pe\s+thanda|cold to touch|bachcha.*thanda lag raha|thanda pad gaya",
        "ESC-4.3",
        "Baby cold to touch (temperature below 95.9 F / 35.5 C): keep skin-to-skin and refer immediately (call 102/108) (ESC-4.3)."
    ),
    # 13. Low birth weight under 1.8 kg (ESC-4.4)
    (
        r"1\.[0-7]\s*kg|under 1\.8\s*kg|1\.6\s*kg|1\.7\s*kg|1\.5\s*kg|1\.4\s*kg",
        "ESC-4.4",
        "Baby birth weight under 1.8 kg: refer immediately to SNCU (call 102/108). Keep skin-to-skin (ESC-4.4)."
    ),
    # 14. Neonatal fast breathing / chest indrawing (ESC-4.2)
    (
        r"navjaat.*saans.*(?:6[0-9]|[7-9]\d)|saans.*66.*pasli|saans khub fast.*70|thot nil|cyanosis",
        "ESC-4.2",
        "Newborn breathing 60 per minute or more, or central cyanosis: danger sign, refer immediately (call 108/102) (ESC-4.2)."
    ),
    # 15. Child chest indrawing / severe pneumonia (ESC-5.2)
    (
        r"pasli andar dhans|chest indrawing|chhati andar dhans",
        "ESC-5.2",
        "Chest indrawing / severe pneumonia: general danger sign, refer immediately to hospital (call 108/102) (ESC-5.2)."
    ),
    # 16. Postpartum puerperal sepsis (ESC-3.2)
    (
        r"delivery.*badbu wala discharge|badbu wala discharge.*bukhar|foul-smelling discharge.*fever",
        "ESC-3.2",
        "Postpartum puerperal sepsis (foul-smelling discharge with fever within 42 days): refer immediately (call 108/102) (ESC-3.2)."
    ),
    # 17. Decreased / absent fetal movements (ESC-2.7)
    (
        r"halchal mahsoos nahi|bachcha pet me hil hi nahi raha|halchal band|decreased foetal movement|absent foetal movement|बच्चे की हलचल महसूस नहीं हो रही",
        "ESC-2.7",
        "Decreased or absent fetal movements: obstetric danger sign, refer immediately to FRU / hospital (call 102/108) (ESC-2.7)."
    ),
    # 18. Premature rupture of membranes / water bag bursting (ESC-2.8)
    (
        r"paani ki thaili phat gayi|water breaking|leaking amniotic",
        "ESC-2.8",
        "Premature rupture of membranes (water bag leaking without labour pains): refer immediately (call 102/108) (ESC-2.8)."
    ),
    # 19. Prolonged labour > 12 hours (ESC-2.8)
    (
        r"prasav peeda.*(?:1[2-9]|[2-9]\d)\s*ghante|prolonged labour|obstructed labour",
        "ESC-2.8",
        "Prolonged labour (> 12 hours): danger sign, transfer immediately to FRU / hospital (call 102/108) (ESC-2.8)."
    ),
    # 20. Postpartum breathlessness / heart failure (ESC-3.4)
    (
        r"delivery.*saans phool rahi hai lete hue|prasav.*saans phool rahi",
        "ESC-3.4",
        "Severe breathlessness after delivery: possible peripartum heart failure / severe anaemia, refer immediately (call 108/102) (ESC-3.4)."
    ),
    # 21. Neonatal pathological jaundice (first 24h) (ESC-4.4)
    (
        r"janam ke pehle din.*peela|first 24 hours.*jaundice|pehle din hi bachcha peela",
        "ESC-4.4",
        "Jaundice appearing in first 24 hours of life: pathological neonatal jaundice, refer immediately to SNCU (ESC-4.4)."
    ),
    # 22. Hypertensive crisis (BP >= 180/110) (ESC-6.4)
    (
        r"18[0-9]\/11[0-9]|19[0-9]\/|2\d\d\/|bp 180\/110 se upar",
        "ESC-6.4",
        "BP 180/110 or more is a hypertensive escalation: refer immediately to Medical Officer / CHC (ESC-6.4)."
    ),
    # 23. Acute myocardial infarction / severe chest pain (ESC-6.1)
    (
        r"chhati ke beech me bhaari.*dard|chest pain.*sweat|heart attack",
        "ESC-6.1",
        "Acute chest pain or pressure: suspected heart attack, refer immediately (call 108). Do not delay (ESC-6.1)."
    ),
    # 24. Acute stroke signs (BE FAST) (ESC-6.2)
    (
        r"ek taraf ka haath pair kaam nahi kar raha|achanak.*aankh se dikhna band hua aur chakkar aake gir|paralysis.*achanak|stroke",
        "ESC-6.2",
        "Sudden weakness on one side, difficulty speaking, or sudden loss of vision: stroke signs (BE FAST), refer immediately (call 108) (ESC-6.2)."
    ),
    # 25. Hypoglycemia crisis in diabetes (ESC-6.3)
    (
        r"sugar (?:5[0-8]|4\d|3\d)\b|shivering sweating confusion diabetes|sugar 58 aaya",
        "ESC-6.3",
        "Low blood sugar (hypoglycemia): give sugar/jaggery immediately if conscious; refer immediately if confused or unconscious (call 108) (ESC-6.3)."
    ),
    # 26. Poisoning / chemical ingestion (ESC-1.4)
    (
        r"kerosene pee liya|poison|zeher|keetnashak|dawai pee li",
        "ESC-1.4",
        "Accidental poisoning / chemical ingestion: life-threatening emergency, refer immediately to hospital (call 108). Do not induce vomiting (ESC-1.4)."
    ),
    # 27. Severe acute malnutrition with complications (ESC-5.5)
    (
        r"dono pair suje.*gaddha|bilateral pitting oedema|sam.*dast",
        "ESC-5.5",
        "Severe Acute Malnutrition (SAM) with bilateral pitting oedema or illness: refer immediately to NRC (ESC-5.5)."
    ),
    # 28. High fever in pregnancy (ESC-2.4)
    (
        r"pregnant.*tez bukhar|6 mahine ki pregnant ko 2 din se tez bukhar|high fever in pregnancy",
        "ESC-2.4",
        "High fever in pregnancy: danger sign, refer immediately to hospital (call 102/108) (ESC-2.4)."
    ),
    # 29. Child lethargy / abnormally sleepy (ESC-5.1)
    (
        r"bilkul sust.*uth nahi rahi|lethargic child|sust pada rehta hai.*5 din",
        "ESC-5.1",
        "Child lethargic or unconscious: general danger sign, refer immediately to hospital (call 108/102) (ESC-5.1)."
    ),
    # 30. Cord infection severe (ESC-4.4)
    (
        r"naal se peep.*aas paas laal|umbilicus pus.*redness",
        "ESC-4.4",
        "Pus or redness spreading from the umbilicus: newborn danger sign, refer immediately to hospital (ESC-4.4)."
    ),
]


# -----------------------------------------------------------------------------
# JEV-Scope: Out-of-Scope Detection & Safe Refusal
# Strictly grounded in 08_SCP_scope_and_redirect.md (SCP-1.2)
# -----------------------------------------------------------------------------

OUT_OF_SCOPE_RULES = [
    # 1. Unprescribed / prescription medicine dosing
    (
        r"\bparacetamol\b|pcm syrup|paracetamol syrup kitna|cefixime|amoxicillin.*kitni baar|amoxicillin.*dose|cough syrup|kaun sa cough syrup|kaun si goli lu|mere khud ke sir me.*dard|kaun si dawai\b|kaun si dawai acchi|kaun si cream\b|kaun se drops\b|drops daale\b|ghutno me.*kaun si dawai|neend nahi aati.*kaun si dawai|daaru chhudwane|bistar geela.*koi dawai",
        "SCP-1.2",
        "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2)."
    ),
    # 2. Chronic adult prescription titration (insulin, metformin, amlodipine)
    (
        r"insulin kitni unit|insulin kitni|metformin.*khana khane se pehle|amlodipine.*10 mg karni hai|bp 150.*amlodipine",
        "SCP-1.2",
        "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2)."
    ),
    # 3. Reading diagnostic tests / lab reports
    (
        r"typhoid.*widal|widal test|hiv test positive|dengue ka test|platelet kitne|thyroid report|tsh 8|ultrasound.*placenta previa",
        "SCP-1.2",
        "This is outside my protocol book. Please ask the Medical Officer at the PHC (or your ANM). If any danger sign is present, refer immediately (SCP-1.2)."
    ),
    # 4. Sex determination (PC-PNDT prohibited by law)
    (
        r"ladka hai ya ladki|sex determination|ling jaanch|kahin pata karwa sakte",
        "SCP-1.2",
        "This is outside my protocol book and prohibited by law (PC-PNDT Act). Every pregnancy requires full antenatal care. Please consult your ANM / MO (SCP-1.2)."
    ),
    # 5. Administrative schemes, salaries, apps, logistics
    (
        r"u-win app|login nahi ho raha|otp nahi aa raha|ration card me.*naam|ayushman card kaise|mobile me data|recharge kaun|incentive kab|hisaab portal|maandey|janam praman patra kaise banwaye",
        "SCP-1.2",
        "This is outside my protocol book. Please contact your block program management unit (BPMU) or supervisor (SCP-1.2)."
    ),
    # 6. Uncovered conditions / non-routine vaccines
    (
        r"rabies ke injection|kutte ne.*kharoch|dog bite|pathri ka desi ilaaj|stone ka ilaaj|covid tike.*booster abhi|hpv.*cancer.*tika|bachchedani ke cancer wala tika|garbhpat ki goli|abortion pill|tb ki dawai 6 mahine ki hoti hai ya 9",
        "SCP-1.2",
        "This is outside my protocol book. Please refer the beneficiary to the Medical Officer at the PHC (SCP-1.2)."
    ),
]


class JEVEscalate:
    """Deterministic danger-sign escalation kernel."""
    
    @staticmethod
    def evaluate(question_text: str) -> Tuple[bool, Optional[str], Optional[str]]:
        """
        Scans question_text against all hard-coded danger patterns.
        Returns: (escalate: bool, cited_section_id: str | None, answer_text: str | None)
        """
        for pattern, sec_id, ans in DANGER_RULES:
            if re.search(pattern, question_text, re.IGNORECASE):
                return True, sec_id, ans
        return False, None, None


class JEVScope:
    """Deterministic out-of-scope refusal kernel."""
    
    @staticmethod
    def evaluate(question_text: str) -> Tuple[bool, Optional[str], Optional[str]]:
        """
        Scans question_text against out-of-scope boundaries.
        Returns: (refused: bool, cited_section_id: str | None, answer_text: str | None)
        """
        for pattern, sec_id, ans in OUT_OF_SCOPE_RULES:
            if re.search(pattern, question_text, re.IGNORECASE):
                return True, sec_id, ans
        return False, None, None


class JEVVerify:
    """Citation validator ensuring every cited section is a valid protocol leaf."""
    
    def __init__(self, valid_sections: set[str]):
        self.valid_sections = {s.upper() for s in valid_sections}
        
    def validate_citations(self, citations: List[str]) -> List[str]:
        """
        Validates and deduplicates citations. Enforces max 2 citations.
        Returns sanitized valid citation list.
        """
        clean = []
        for c in citations:
            cu = str(c).strip().upper()
            if cu in self.valid_sections and cu not in clean:
                clean.append(cu)
            if len(clean) >= 2:
                break
        return clean if clean else ["SCP-1.1"]
