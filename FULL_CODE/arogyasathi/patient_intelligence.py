"""Patient-Aware Intelligence & Longitudinal History Tracking for Arogyasathi.

Analyzes frontline patient records from the Chandrapuri 500-household register:
1. Patient Demographics & Stage of Life (Newborn, Infant, Pregnant Woman, Elderly).
2. Gestational Timeline & Trimester Calculation (LMP, EDD, Gestational Weeks).
3. Longitudinal Vitals & Clinical History (BP, Hb, MUAC, Blood Sugar, Vaccines).
4. Trend Analysis & Alert Detection (Hb drops, BP spikes, missing/overdue visits).
"""

from __future__ import annotations
import csv
import os
import re
from datetime import datetime, date
from typing import Dict, List, Optional, Any, Tuple


class PatientProfile:
    """Rich clinical profile for a household member."""

    def __init__(self, row: Dict[str, str]):
        self.member_id = row.get("member_id", "").strip()
        self.household_id = row.get("household_id", "").strip()
        self.village = row.get("village", "").strip()
        self.asha_id = row.get("asha_id", "").strip()
        self.name = row.get("name", "").strip()
        self.relation = row.get("relation", "").strip()
        self.sex = row.get("sex", "").strip().upper()
        self.dob = row.get("dob", "").strip()

        # Maternal Fields
        self.is_pregnant = row.get("pregnant", "").strip().upper() == "Y"
        self.lmp_date = row.get("lmp_date", "").strip() or None
        self.edd = row.get("edd", "").strip() or None
        self.anc_visit_dates = [d.strip() for d in row.get("anc_visit_dates", "").split(";") if d.strip()]
        self.last_anc_bp = row.get("last_anc_bp", "").strip() or None
        
        hb_val = row.get("last_hb_gdl", "").strip()
        self.last_hb_gdl = float(hb_val) if hb_val else None

        self.td_booster_eligible = row.get("td_booster_eligible", "").strip().upper() == "Y"
        self.td_doses = [d.strip() for d in row.get("td_doses", "").split(";") if d.strip()]

        # Newborn & Child Fields
        self.place_of_delivery = row.get("place_of_delivery", "").strip() or None
        bw_val = row.get("birth_weight_kg", "").strip()
        self.birth_weight_kg = float(bw_val) if bw_val else None
        
        hbnc_raw = row.get("hbnc_visits_done", "").strip()
        self.hbnc_visits_done = [int(v.strip()) for v in hbnc_raw.split(";") if v.strip().isdigit()]

        # Vaccines: Map of vaccine_name -> date
        self.child_vaccines: Dict[str, str] = {}
        vac_raw = row.get("child_vaccines", "").strip()
        if vac_raw:
            for item in vac_raw.split(";"):
                if ":" in item:
                    k, v = item.split(":", 1)
                    self.child_vaccines[k.strip().lower()] = v.strip()
                elif item.strip():
                    self.child_vaccines[item.strip().lower()] = "completed"

        muac_val = row.get("last_muac_cm", "").strip()
        self.last_muac_cm = float(muac_val) if muac_val else None
        self.last_muac_date = row.get("last_muac_date", "").strip() or None
        self.bilateral_pitting_oedema = row.get("bilateral_pitting_oedema", "").strip().upper() == "Y"

        # NCD Fields
        self.ncd_condition = row.get("ncd_condition", "").strip() or None
        self.last_bp_check_date = row.get("last_bp_check_date", "").strip() or None
        self.last_bp_reading = row.get("last_bp_reading", "").strip() or None
        self.last_sugar_check_date = row.get("last_sugar_check_date", "").strip() or None
        
        rbs_val = row.get("last_rbs_mg_dl", "").strip()
        self.last_rbs_mg_dl = float(rbs_val) if rbs_val else None

        # Dynamic Vitals History (for longitudinal trend comparison)
        self.vitals_history: List[Dict[str, Any]] = []
        if self.last_anc_bp or self.last_hb_gdl:
            self.vitals_history.append({
                "date": "Historical baseline",
                "bp": self.last_anc_bp or self.last_bp_reading,
                "hb": self.last_hb_gdl,
                "muac": self.last_muac_cm,
                "rbs": self.last_rbs_mg_dl
            })

    def get_age_years(self, ref_date: str = "2026-10-26") -> float:
        if not self.dob:
            return 25.0
        try:
            d_dob = datetime.strptime(self.dob, "%Y-%m-%d").date()
            d_ref = datetime.strptime(ref_date, "%Y-%m-%d").date()
            days = (d_ref - d_dob).days
            return max(0.0, days / 365.25)
        except Exception:
            return 25.0

    def get_age_months(self, ref_date: str = "2026-10-26") -> float:
        return self.get_age_years(ref_date) * 12.0

    def get_gestational_weeks(self, ref_date: str = "2026-10-26") -> Optional[int]:
        if not self.is_pregnant:
            return None
        d_ref = datetime.strptime(ref_date, "%Y-%m-%d").date()
        if self.lmp_date:
            try:
                d_lmp = datetime.strptime(self.lmp_date, "%Y-%m-%d").date()
                days = (d_ref - d_lmp).days
                return max(1, min(42, days // 7))
            except Exception:
                pass
        if self.edd:
            try:
                d_edd = datetime.strptime(self.edd, "%Y-%m-%d").date()
                # EDD is 40 weeks (280 days) from LMP
                days_until_edd = (d_edd - d_ref).days
                weeks = 40 - (days_until_edd // 7)
                return max(1, min(42, weeks))
            except Exception:
                pass
        return 24  # Default 2nd trimester estimate if dates missing


class PatientIntelligenceEngine:
    """Provides patient context resolution, longitudinal trend analysis, and care gap tracking."""

    def __init__(self, register_path: Optional[str] = None):
        self.register_path = register_path or self._find_register()
        self.patients_by_id: Dict[str, PatientProfile] = {}
        self.patients_by_name: Dict[str, List[PatientProfile]] = {}
        if self.register_path and os.path.exists(self.register_path):
            self._load_register()

    def _find_register(self) -> str:
        candidates = [
            "H5_Frontline_Health_Worker_Companion/data/household_register.csv",
            "../H5_Frontline_Health_Worker_Companion/data/household_register.csv",
            "data/household_register.csv",
            "android_app/app/src/main/assets/household_register.csv"
        ]
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        for c in candidates:
            p = os.path.join(base_dir, c)
            if os.path.exists(p):
                return p
        return ""

    def _load_register(self) -> None:
        with open(self.register_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                p = PatientProfile(row)
                self.patients_by_id[p.member_id] = p
                clean_name = p.name.lower().strip()
                if clean_name not in self.patients_by_name:
                    self.patients_by_name[clean_name] = []
                self.patients_by_name[clean_name].append(p)

    def find_patient(self, query: str) -> Optional[PatientProfile]:
        """Resolves a patient by member_id or name in the query."""
        q = query.strip()
        # Direct ID match: M-XXXXXXXX
        m_id = re.search(r"\bM-[A-Z0-9]{8}\b", q, re.IGNORECASE)
        if m_id:
            pid = m_id.group(0).upper()
            if pid in self.patients_by_id:
                return self.patients_by_id[pid]

        # Name match
        q_lower = q.lower()
        for name, plist in self.patients_by_name.items():
            if len(name) > 3 and name in q_lower:
                return plist[0]

        return None

    def analyze_patient_context(
        self,
        patient: PatientProfile,
        ref_date: str = "2026-10-26"
    ) -> Dict[str, Any]:
        """Performs clinical triage, longitudinal analysis, and risk identification."""
        age_years = patient.get_age_years(ref_date)
        age_months = patient.get_age_months(ref_date)

        summary = {
            "name": patient.name,
            "member_id": patient.member_id,
            "sex": patient.sex,
            "age_display": f"{int(age_years)} years" if age_years >= 1 else f"{int(age_months)} months",
            "village": patient.village,
            "is_pregnant": patient.is_pregnant,
            "alerts": [],
            "care_gaps": [],
            "clinical_status": "NORMAL"
        }

        # 1. Pregnancy & Maternal Care
        if patient.is_pregnant:
            gest_weeks = patient.get_gestational_weeks(ref_date)
            summary["gestational_weeks"] = gest_weeks
            summary["trimester"] = 1 if gest_weeks <= 12 else (2 if gest_weeks <= 27 else 3)
            summary["edd"] = patient.edd

            # Check BP
            bp = patient.last_anc_bp or patient.last_bp_reading
            if bp:
                summary["last_bp"] = bp
                try:
                    sys_p, dia_p = map(int, bp.split("/"))
                    if sys_p >= 160 or dia_p >= 110:
                        summary["alerts"].append(f"Severe Pre-Eclampsia / Hypertensive Crisis BP ({bp} mmHg)")
                        summary["clinical_status"] = "EMERGENCY"
                    elif sys_p >= 140 or dia_p >= 90:
                        summary["alerts"].append(f"Gestational Hypertension / Mild Pre-Eclampsia ({bp} mmHg)")
                        summary["clinical_status"] = "HIGH_RISK"
                except Exception:
                    pass

            # Check Hb
            if patient.last_hb_gdl is not None:
                hb = patient.last_hb_gdl
                summary["last_hb"] = f"{hb} g/dL"
                if hb < 7.0:
                    summary["alerts"].append(f"Severe Anaemia (Hb {hb} g/dL < 7.0 g/dL) — High Risk Pregnancy")
                    summary["clinical_status"] = "HIGH_RISK"
                elif hb < 11.0:
                    summary["alerts"].append(f"Moderate Anaemia (Hb {hb} g/dL)")

            # Check ANC visits
            anc_count = len(patient.anc_visit_dates)
            summary["completed_anc_count"] = anc_count
            if gest_weeks >= 28 and anc_count < 3:
                summary["care_gaps"].append("ANC-3 Checkup overdue (3rd Trimester)")
            elif gest_weeks >= 14 and anc_count < 2:
                summary["care_gaps"].append("ANC-2 Checkup overdue (2nd Trimester)")

        # 2. Child Nutrition & Immunization
        elif age_years < 5:
            # MUAC
            if patient.last_muac_cm is not None:
                muac = patient.last_muac_cm
                summary["last_muac"] = f"{muac} cm"
                if muac < 11.5 or patient.bilateral_pitting_oedema:
                    summary["alerts"].append(f"Severe Acute Malnutrition (SAM MUAC {muac} cm) — Urgent NRC Referral")
                    summary["clinical_status"] = "HIGH_RISK"
                elif muac < 12.5:
                    summary["alerts"].append(f"Moderate Acute Malnutrition (MAM MUAC {muac} cm)")

            # Vaccines
            vacs = patient.child_vaccines
            if age_months >= 10 and not any("mr-1" in v or "mr1" in v for v in vacs):
                summary["care_gaps"].append("MR-1 (Measles-Rubella) Vaccine Overdue")
            if age_months >= 4 and not any("penta-3" in v for v in vacs):
                summary["care_gaps"].append("Pentavalent-3 Vaccine Overdue")

        # 3. Adult NCD Condition
        if patient.ncd_condition:
            summary["ncd_condition"] = patient.ncd_condition
            bp = patient.last_bp_reading
            if bp:
                summary["last_bp"] = bp
                try:
                    sys_p, dia_p = map(int, bp.split("/"))
                    if sys_p >= 140 or dia_p >= 90:
                        summary["alerts"].append(f"Uncontrolled Hypertension ({bp} mmHg)")
                except Exception:
                    pass

        return summary

    def compare_historical_vitals(
        self,
        patient: PatientProfile,
        new_bp: Optional[str] = None,
        new_hb: Optional[float] = None
    ) -> List[str]:
        """Detects longitudinal changes (e.g. rapid Hb drop, BP spike)."""
        trends = []

        # BP Comparison
        old_bp = patient.last_anc_bp or patient.last_bp_reading
        if old_bp and new_bp:
            try:
                old_sys, old_dia = map(int, old_bp.split("/"))
                new_sys, new_dia = map(int, new_bp.split("/"))
                diff_sys = new_sys - old_sys
                diff_dia = new_dia - old_dia
                if new_sys >= 140 and old_sys < 140:
                    trends.append(f"CRITICAL BP ELEVATION: Jumped from {old_bp} to {new_bp} mmHg (now in Pre-Eclampsia range).")
                elif diff_sys >= 20 or diff_dia >= 15:
                    trends.append(f"Notable BP rise: Increased by +{diff_sys}/+{diff_dia} mmHg from baseline ({old_bp} -> {new_bp}).")
            except Exception:
                pass

        # Hb Comparison
        old_hb = patient.last_hb_gdl
        if old_hb is not None and new_hb is not None:
            drop = old_hb - new_hb
            if drop >= 1.5:
                trends.append(f"SEVERE HAEMOGLOBIN DROP: Decreased from {old_hb} to {new_hb} g/dL (drop of {drop:.1f} g/dL).")
            if new_hb < 7.0 and old_hb >= 7.0:
                trends.append(f"ANAEMIA PROGRESSION: Patient progressed from moderate to SEVERE ANAEMIA (<7.0 g/dL).")

        return trends

