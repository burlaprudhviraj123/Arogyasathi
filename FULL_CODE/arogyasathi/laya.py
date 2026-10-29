"""Laya Engine for Arogyasathi.

Implements edge language adaptation, cross-dialect retrieval, and protocol grounding:
1. Laya-Lexicon: Cross-dialect normalizer for Hindi, Hinglish, Marathi, Telugu, Bengali
2. Laya-Retriever: BM25 indexer & ranker over the 107 protocol sections
3. Laya-Synthesizer: Fact-grounded answer synthesizer strictly constrained to <= 400 chars
"""

from __future__ import annotations
import math
import re
from collections import Counter
from typing import List, Tuple, Dict, Any
from .protocols import load_protocol_sections

# Cross-dialect clinical mapping for frontline health workers in rural blocks
LAYA_LEXICON: Dict[str, str] = {
    # Diarrhea & ORS / Zinc
    "dast": "diarrhoea loose motions chd-1.1 chd-1.2",
    "motion": "diarrhoea loose motions chd-1.1 chd-1.2",
    "motions": "diarrhoea loose motions chd-1.1 chd-1.2",
    "loose": "diarrhoea loose motions chd-1.1 chd-1.2",
    "ors": "ors packet 100 200 ml 50 100 ml 10 minutes wait chd-1.2",
    "zinc": "zinc 14 days 20 mg 10 mg 6 months chd-1.3",
    "ghol": "ors packet 1 litre drinking water 24 hours chd-1.1",
    
    # Fever & Respiratory
    "bukhar": "fever 38 100.4 chd-2.1 chd-2.2",
    "taap": "fever chd-2.1 chd-2.2",
    "tap": "fever chd-2.1 chd-2.2",
    "saans": "breaths per minute 50 breaths 40 breaths 60 breaths fast breathing chd-3.1",
    "swasa": "breaths per minute 50 breaths 40 breaths fast breathing chd-3.1",
    "daggu": "cough fast breathing chd-3.1",
    "khansi": "cough fast breathing chd-3.1",
    "pasli": "chest indrawing esc-5.2",
    
    # Immunization & Vaccines
    "tika": "vaccine immunisation session imm-1.1 imm-2",
    "teeka": "vaccine immunisation imm-1.1 imm-2",
    "tike": "vaccine immunisation imm-1.1 imm-2",
    "tikakaran": "immunisation sessions due list imm-1.1 imm-1.2",
    "injection": "vaccine injection imm-1.1",
    "penta": "penta pentavalent till one year of age 6 10 14 weeks imm-2.2",
    "bcg": "bcg till one year birth 0.1 ml imm-2.1",
    "hepb": "hepatitis b birth 24 hours imm-2.1",
    "opv": "opv birth 15 days 2 drops 5 years imm-2.1 imm-2.2",
    "rvv": "rotavirus drops 5 drops imm-2.2",
    "fipv": "fipv intradermal 0.1 ml 6 14 weeks 9 months imm-2.2 imm-2.3",
    "pcv": "pcv pneumococcal 6 14 weeks 9 months booster imm-2.2 imm-2.3",
    "mr": "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
    "khasra": "measles rubella 9 12 months mr-1 mr-2 imm-2.3 imm-2.4",
    "dpt": "dpt booster 16 24 months 5 6 years 7 years imm-2.4 imm-2.5",
    "vitamin a": "vitamin a 9 doses every 6 months 1 lakh 2 lakh iu imm-2.4",
    "je": "chandrapuri not a je-endemic district no je imm-2.4",
    "td": "td tetanus diphtheria pregnancy anc-2.5 imm-4.1",
    "booster": "booster dose dpt opv pcv imm-2.4",
    "gap": "minimum gap 4 weeks 2 months 8 weeks imm-2.6",
    "overdue": "overdue 28 days grace period vis-1.3 counting conventions",
    "kab overdue": "overdue 28 days grace period vis-1.3 counting conventions",
    
    # Maternal & ANC
    "pregnant": "pregnancy antenatal anc-1.1 anc-1.2",
    "garbh": "pregnancy antenatal anc-1.1 anc-1.2",
    "garbhvati": "pregnancy antenatal anc-1.1 anc-1.2",
    "anc": "antenatal checkup minimum four anc anc-1.2",
    "edd": "expected date of delivery lmp 280 days 9 months 7 days anc-1.1",
    "lmp": "last menstrual period edd 280 days anc-1.1",
    "pmsma": "pmsma 9th of every month doctor checkup anc-1.3",
    "ifa": "red ifa tablet 180 days 100 mg iron anc-2.2 hbnc-3.1",
    "lal goli": "red ifa tablet 180 days iron anc-2.2 hbnc-3.1",
    "calcium": "calcium two tablets a day 500 mg 6 months anc-2.3 hbnc-3.1",
    "albendazole": "albendazole 400 mg deworming 2nd trimester anc-2.4",
    "hb": "haemoglobin 7.0 9.9 11.0 moderate mild severe anc-3.1",
    "khoon ki kami": "anaemia haemoglobin cutoffs anc-3.1 anc-3.2",
    "high risk": "high risk pregnancy severe anaemia hypertension anc-5.1 vis-2.3",
    "jssk": "jssk free delivery transport 102 48 hours food anc-5.3",
    
    # Newborn & HBNC
    "hbnc": "hbnc visit schedule institutional 6 home 7 days 1 3 7 14 21 28 42 hbnc-1.1",
    "prasav": "delivery institutional home hbnc-1.1",
    "janam": "birth newborn hbnc-1.1",
    "delivery": "delivery hospital home hbnc-1.1",
    "nehlana": "bathe bathing first 48 hours 2000 g hbnc-2.2",
    "nehlayen": "bathe bathing first 48 hours 2000 g hbnc-2.2",
    "nehla": "bathe bathing first 48 hours 2000 g hbnc-2.2",
    "nahana": "bathe bathing first 48 hours 2000 g hbnc-2.2",
    "doodh": "breastfeeding within 1 hour exclusive 6 months colostrum hbnc-2.1",
    "sthanpaan": "breastfeeding within 1 hour exclusive 6 months hbnc-2.1",
    "naal": "cord care dry clean nothing apply hbnc-2.3",
    "jachcha": "mother check bleeding pads fever foul-smelling discharge hbnc-3.1",
    "kmc": "kangaroo mother care skin-to-skin low birth weight hbnc-1.2",
    "hbyc": "hbyc visits 3 6 9 12 15 months hbnc-4.1",
    
    # Child Nutrition (MUAC / SAM / MAM)
    "muac": "muac 6 59 months 11.5 12.5 cm yellow red green nut-1.1 nut-1.2",
    "oedema": "bilateral pitting oedema both feet dent pit nut-1.1",
    "sujan": "bilateral pitting oedema both feet dent pit nut-1.1",
    "sam": "sam severe acute malnutrition nrc 11.5 cm nut-2.1",
    "mam": "mam moderate acute malnutrition 11.5 12.4 cm nut-2.3",
    "nrc": "nutrition rehabilitation centre sam complications nut-2.1",
    
    # NCD (Hypertension & Diabetes)
    "ncd": "hypertension diabetes screening 30 years cbac ncd-1.1",
    "cbac": "cbac score 4 or more priority ncd-1.1",
    "bp": "blood pressure 140 90 hypertension monthly ncd-1.2 ncd-2.1",
    "sugar": "diabetes random blood sugar 200 mg dl ncd-1.3",
    "salt": "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
    "namak": "maximum 1 teaspoon 5 g salt day lifestyle ncd-3.1",
    "exercise": "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1 lifestyle counselling",
    "kasrat": "exercise physical activity 30 minutes 5 days a week 150 minutes ncd-3.1",
    "baithe": "physical activity 30 minutes 5 days a week 150 minutes ncd-3.1 sedentary",
    
    # Visit Engine Rules
    "chautha anc": "fourth 4th anc 36 weeks anc-1.2",
    "chautha": "fourth 4th anc-1.2",
}


class LayaLexicon:
    """Normalizes and enriches queries with domain-specific clinical equivalents."""
    
    @staticmethod
    def expand_tokens(text: str) -> List[str]:
        raw_toks = re.findall(r"[a-z0-9]+(?:\.[0-9]+)?", text.lower())
        expanded = list(raw_toks)
        
        t_joined = " " + " ".join(raw_toks) + " "
        for phrase, expansion in LAYA_LEXICON.items():
            if " " in phrase and f" {phrase} " in t_joined:
                expanded.extend(expansion.split())
            elif phrase in raw_toks:
                expanded.extend(expansion.split())
                
        return expanded


class LayaRetriever:
    """BM25 indexer and ranker over protocol leaf sections."""
    
    def __init__(self, sections: Dict[str, Dict[str, Any]], k1: float = 1.5, b: float = 0.75):
        self.sections = sections
        self.doc_ids = list(sections.keys())
        
        # Build document corpus with heavy title weighting
        self.docs = []
        for sid in self.doc_ids:
            s = sections[sid]
            # Triple weight the section title and ID
            doc_text = f"{sid} {s['title']} {s['title']} {s['title']} {s['text']}"
            self.docs.append(LayaLexicon.expand_tokens(doc_text))
            
        self.n = len(self.docs)
        self.avg_len = sum(map(len, self.docs)) / self.n if self.n else 1.0
        self.df = Counter(t for d in self.docs for t in set(d))
        self.tf = [Counter(d) for d in self.docs]
        self.k1 = k1
        self.b = b

    def retrieve(self, query: str, top_k: int = 2) -> List[Tuple[float, str]]:
        q_toks = LayaLexicon.expand_tokens(query)
        scores = []
        
        for i, tf in enumerate(self.tf):
            s = 0.0
            dlen = len(self.docs[i])
            for t in q_toks:
                if t in tf:
                    idf = math.log(1 + (self.n - self.df[t] + 0.5) / (self.df[t] + 0.5))
                    denom = tf[t] + self.k1 * (1 - self.b + self.b * dlen / self.avg_len)
                    s += idf * (tf[t] * (self.k1 + 1)) / denom
            scores.append((s, self.doc_ids[i]))
            
        scores.sort(reverse=True)
        return scores[:top_k]


class LayaSynthesizer:
    """Synthesizes fact-grounded answer text within the 400-char evaluator constraint."""
    
    @staticmethod
    def synthesize(sections: Dict[str, Dict[str, Any]], cited_ids: List[str], query: str) -> str:
        if not cited_ids:
            return "Refer to protocol guidelines (SCP-1.1)."
            
        top_sec = sections[cited_ids[0]]
        clean = re.sub(r"[#*|>]", " ", top_sec["text"])
        clean = re.sub(r"\s+", " ", clean).strip()
        
        # Split text into clinical statements
        chunks = [c.strip() for c in re.split(r"(?: - |\n|\. )", clean) if c.strip() and not c.startswith("---")]
        
        # Query tokens with lexicon expansion
        q_toks = set(LayaLexicon.expand_tokens(query))
        
        scored = []
        for c in chunks:
            c_words = set(re.findall(r"[a-z0-9]+", c.lower()))
            common = len(q_toks.intersection(c_words))
            scored.append((common, c))
            
        # Sort chunks by query relevance
        scored.sort(key=lambda x: x[0], reverse=True)
        
        selected = []
        total_len = 0
        for score, chunk in scored:
            if total_len + len(chunk) + 2 <= 380:
                selected.append(chunk)
                total_len += len(chunk) + 2
            if total_len >= 260:
                break
                
        if not selected:
            selected = [chunks[0] if chunks else clean[:380]]
            
        ans_text = ". ".join(selected)
        
        # Append primary section citation
        suffix = f" ({cited_ids[0]})."
        if len(ans_text) + len(suffix) <= 400:
            ans_text = ans_text + suffix
        else:
            ans_text = ans_text[: 400 - len(suffix)] + suffix
            
        return ans_text[:400]

