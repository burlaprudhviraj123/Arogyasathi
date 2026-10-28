"""Visit list generation engine implementing 09_VIS Chandrapuri Block rules."""

from __future__ import annotations
from datetime import date, timedelta
import re
import pandas as pd

TODAY = date(2026, 10, 26)

def parse_date(s: any) -> date | None:
    if not isinstance(s, str) or not s.strip():
        return None
    try:
        return date.fromisoformat(s.strip())
    except Exception:
        return None

def parse_bp(s: any) -> tuple[int | None, int | None]:
    if not isinstance(s, str) or not s.strip():
        return None, None
    m = re.search(r"(\d+)\s*/\s*(\d+)", s)
    if m:
        sys, dia = int(m.group(1)), int(m.group(2))
        if sys == 0 and dia == 0:
            return None, None
        return sys, dia
    return None, None

def parse_float(s: any) -> float | None:
    try:
        val = float(s)
        return val if pd.notna(val) else None
    except Exception:
        return None

def normalize_vaccine_code(c: str) -> str:
    c = str(c).strip()
    c_lower = re.sub(r"[^a-z0-9]", "", c.lower())
    mapping = {
        "bcg": "BCG",
        "hepb0": "HepB-0", "hepb": "HepB-0",
        "opv0": "OPV-0", "opv1": "OPV-1", "opv2": "OPV-2", "opv3": "OPV-3", "opvb": "OPV-B",
        "penta1": "Penta-1", "penta2": "Penta-2", "penta3": "Penta-3",
        "rvv1": "RVV-1", "rvv2": "RVV-2", "rvv3": "RVV-3",
        "fipv1": "fIPV-1", "fipv2": "fIPV-2", "fipv3": "fIPV-3",
        "pcv1": "PCV-1", "pcv2": "PCV-2", "pcvb": "PCV-B",
        "mr1": "MR-1", "mr2": "MR-2",
        "dptb1": "DPT-B1", "dptb2": "DPT-B2",
    }
    return mapping.get(c_lower, c)

def get_vaccine_history(s: any) -> dict[str, date | None]:
    if not isinstance(s, str) or not s.strip():
        return {}
    history = {}
    for entry in s.split(";"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split(":")
        code = normalize_vaccine_code(parts[0])
        dt = parse_date(parts[1]) if len(parts) > 1 else None
        if code not in history or (dt and (not history[code] or dt < history[code])):
            history[code] = dt
    return history

def evaluate_status(due_date: date, overdue_after: date) -> str | None:
    if due_date > TODAY + timedelta(days=6):
        return None
    if due_date > TODAY:
        return "due_this_week"
    if TODAY > overdue_after:
        return "overdue"
    return "due_today"

VACCINE_RULES = [
    ("BCG", 0, 365, None, None),
    ("HepB-0", 0, 0, None, None),
    ("OPV-0", 0, 15, None, None),
    ("OPV-1", 42, 1825, None, None),
    ("OPV-2", 70, 1825, "OPV-1", 28),
    ("OPV-3", 98, 1825, "OPV-2", 28),
    ("Penta-1", 42, 365, None, None),
    ("Penta-2", 70, 365, "Penta-1", 28),
    ("Penta-3", 98, 365, "Penta-2", 28),
    ("RVV-1", 42, 365, None, None),
    ("RVV-2", 70, 365, "RVV-1", 28),
    ("RVV-3", 98, 365, "RVV-2", 28),
    ("fIPV-1", 42, 365, None, None),
    ("fIPV-2", 98, 365, "fIPV-1", 56),
    ("fIPV-3", 276, 729, "fIPV-2", 56),
    ("PCV-1", 42, 365, None, None),
    ("PCV-2", 98, 729, "PCV-1", 60),
    ("PCV-B", 276, 729, "PCV-2", 60),
    ("MR-1", 276, 1825, None, None),
    ("MR-2", 490, 1825, "MR-1", 28),
    ("OPV-B", 490, 1825, "OPV-3", 180),
    ("DPT-B1", 490, 2554, "Penta-3", 180),
    ("DPT-B2", 1827, 2554, "DPT-B1", None),
]

ANC_WINDOWS = {
    1: (0, 84),
    2: (98, 182),
    3: (196, 238),
    4: (252, 280),
}

HBNC_SCHEDULE = {
    "institution": [3, 7, 14, 21, 28, 42],
    "home": [1, 3, 7, 14, 21, 28, 42],
}

def generate_visit_list(reg_df: pd.DataFrame | None = None) -> pd.DataFrame:
    """Generates the prioritized daily visit list for 2026-10-26."""
    if reg_df is None:
        from pathlib import Path
        data_path = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data" / "household_register.csv"
        reg_df = pd.read_csv(data_path)
    items = []
    
    for _, r in reg_df.iterrows():
        mid = str(r["member_id"]).strip()
        dob = parse_date(r["dob"])
        if not dob:
            continue
        age_days = (TODAY - dob).days
        
        # Determine high-risk (VIS-5.1)
        is_high_risk = False
        
        is_pregnant = (str(r.get("pregnant", "")).strip().upper() == "Y")
        last_hb = parse_float(r.get("last_hb_gdl"))
        anc_sys, anc_dia = parse_bp(r.get("last_anc_bp"))
        
        if is_pregnant:
            if (last_hb is not None and last_hb < 7.0) or (anc_sys is not None and (anc_sys >= 140 or anc_dia >= 90)):
                is_high_risk = True
                
        if age_days <= 42:
            is_high_risk = True
            
        last_muac = parse_float(r.get("last_muac_cm"))
        oedema = (str(r.get("bilateral_pitting_oedema", "")).strip().upper() == "Y")
        if 184 <= age_days <= 1824:
            if (last_muac is not None and last_muac < 11.5) or oedema:
                is_high_risk = True
                
        ncd_cond = str(r.get("ncd_condition", "")).strip()
        ncd_sys, ncd_dia = parse_bp(r.get("last_bp_reading"))
        if ncd_cond in ["HTN", "DM", "HTN+DM"] or ("HTN" in ncd_cond) or ("DM" in ncd_cond):
            if ncd_sys is not None and (ncd_sys >= 140 or ncd_dia >= 90):
                is_high_risk = True
                
        # 1. Pregnant Women Items (VIS-2)
        if is_pregnant:
            lmp = parse_date(r.get("lmp_date"))
            edd = parse_date(r.get("edd"))
            if lmp:
                # ANC Checkups
                anc_dates_str = str(r.get("anc_visit_dates", "")).strip()
                anc_dates = [d for d in [parse_date(x) for x in anc_dates_str.split(";")] if d]
                k = len(anc_dates) + 1
                if k <= 4:
                    open_days, close_days = ANC_WINDOWS[k]
                    open_date = lmp + timedelta(days=open_days)
                    close_date = lmp + timedelta(days=close_days)
                    
                    st = None
                    if TODAY > close_date:
                        st = "overdue"
                    elif open_date <= TODAY <= close_date:
                        st = "due_today"
                    elif open_date <= TODAY + timedelta(days=6):
                        st = "due_this_week"
                        
                    if st:
                        prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                        items.append((mid, f"ANC-{k}", prio, st, open_date))
                elif edd and TODAY > edd:
                    # Past EDD with missing ANC
                    pass
                    
                # Td in Pregnancy (VIS-2.2)
                anc_done_count = len(anc_dates)
                if anc_done_count > 0:
                    first_anc_date = anc_dates[0]
                    td_boost = (str(r.get("td_booster_eligible", "")).strip().upper() == "Y")
                    td_str = str(r.get("td_doses", "")).strip()
                    td_history = {}
                    for x in td_str.split(";"):
                        if ":" in x:
                            parts = x.split(":")
                            td_history[parts[0].strip()] = parse_date(parts[1])
                            
                    if td_boost:
                        if "Td-BST" not in td_history:
                            due_date = first_anc_date
                            overdue_date = due_date + timedelta(days=28)
                            st = evaluate_status(due_date, overdue_date)
                            if st:
                                prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                                items.append((mid, "Td-BST", prio, st, due_date))
                    else:
                        if "Td-1" not in td_history:
                            due_date = first_anc_date
                            overdue_date = due_date + timedelta(days=28)
                            st = evaluate_status(due_date, overdue_date)
                            if st:
                                prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                                items.append((mid, "Td-1", prio, st, due_date))
                        else:
                            if "Td-2" not in td_history:
                                td1_date = td_history["Td-1"]
                                if td1_date:
                                    due_date = td1_date + timedelta(days=28)
                                    overdue_date = due_date + timedelta(days=28)
                                    st = evaluate_status(due_date, overdue_date)
                                    if st:
                                        prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                                        items.append((mid, "Td-2", prio, st, due_date))
                                        
            # High-Risk Pregnancy Follow-up (VIS-2.3)
            if is_high_risk and ((last_hb is not None and last_hb < 7.0) or (anc_sys is not None and (anc_sys >= 140 or anc_dia >= 90))):
                items.append((mid, "HRP-followup", 1, "due_today", TODAY))
                
        # 2. Newborn HBNC Visits (age <= 42 days, VIS-4)
        if age_days <= 42:
            deliv = str(r.get("place_of_delivery", "")).strip().lower()
            sched = HBNC_SCHEDULE["home"] if deliv == "home" else HBNC_SCHEDULE["institution"]
            done_str = str(r.get("hbnc_visits_done", "")).strip()
            done_days = set()
            for x in done_str.split(";"):
                try:
                    done_days.add(int(x.strip()))
                except Exception:
                    pass
                
            past_visits = [d for d in sched if (dob + timedelta(days=d-1)) <= TODAY]
            future_visits = [d for d in sched if (dob + timedelta(days=d-1)) > TODAY]
            
            hbnc_item = None
            if past_visits:
                latest_past = past_visits[-1]
                if latest_past not in done_days:
                    v_date = dob + timedelta(days=latest_past - 1)
                    st = "due_today" if v_date == TODAY else "overdue"
                    hbnc_item = (mid, f"HBNC-D{latest_past}", 1 if is_high_risk else 2, st, v_date)
            if not hbnc_item and future_visits:
                next_visit = future_visits[0]
                if next_visit not in done_days:
                    v_date = dob + timedelta(days=next_visit - 1)
                    if v_date <= TODAY + timedelta(days=6):
                        hbnc_item = (mid, f"HBNC-D{next_visit}", 3, "due_this_week", v_date)
            if hbnc_item:
                items.append(hbnc_item)
                
        # 3. Children Vaccines (age <= 2554 days, VIS-3)
        if age_days <= 2554:
            vac_hist = get_vaccine_history(r.get("child_vaccines"))
            for code, due_age, not_after, prev_needed, min_gap in VACCINE_RULES:
                if code in vac_hist:
                    continue
                if prev_needed and (prev_needed not in vac_hist or not vac_hist[prev_needed]):
                    continue
                if age_days > not_after:
                    continue
                    
                earliest_date = dob + timedelta(days=due_age)
                if prev_needed and min_gap:
                    prev_date = vac_hist[prev_needed]
                    earliest_date = max(earliest_date, prev_date + timedelta(days=min_gap))
                    
                if (earliest_date - dob).days > not_after:
                    continue
                    
                not_after_date = dob + timedelta(days=not_after)
                overdue_date = min(earliest_date + timedelta(days=28), not_after_date)
                
                st = evaluate_status(earliest_date, overdue_date)
                if st:
                    prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                    items.append((mid, code, prio, st, earliest_date))
                    
        # 4. MUAC Check (178 to 1824 days, VIS-5.2)
        if 178 <= age_days <= 1824:
            last_muac_d = parse_date(r.get("last_muac_date"))
            if not last_muac_d:
                due_date = dob + timedelta(days=184)
            else:
                due_date = last_muac_d + timedelta(days=30)
            overdue_date = due_date + timedelta(days=28)
            st = evaluate_status(due_date, overdue_date)
            if st:
                prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                items.append((mid, "MUAC-check", prio, st, due_date))
                
        # 5. NCD Follow-up (VIS-5.3)
        if ncd_cond in ["HTN", "DM", "HTN+DM"] or ("HTN" in ncd_cond) or ("DM" in ncd_cond):
            if "HTN" in ncd_cond:
                last_bp_d = parse_date(r.get("last_bp_check_date"))
                if not last_bp_d:
                    st = "overdue"
                    due_date = TODAY - timedelta(days=30)
                else:
                    due_date = last_bp_d + timedelta(days=30)
                    overdue_date = due_date + timedelta(days=28)
                    st = evaluate_status(due_date, overdue_date)
                if st:
                    prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                    items.append((mid, "BP-followup", prio, st, due_date))
            if "DM" in ncd_cond:
                last_sug_d = parse_date(r.get("last_sugar_check_date"))
                if not last_sug_d:
                    st = "overdue"
                    due_date = TODAY - timedelta(days=30)
                else:
                    due_date = last_sug_d + timedelta(days=30)
                    overdue_date = due_date + timedelta(days=28)
                    st = evaluate_status(due_date, overdue_date)
                if st:
                    prio = 1 if (is_high_risk and st in ["due_today", "overdue"]) else (2 if st in ["due_today", "overdue"] else 3)
                    items.append((mid, "Sugar-followup", prio, st, due_date))

    df_out = pd.DataFrame(items, columns=["member_id", "due_item", "priority", "status", "due_date"])
    return df_out.drop_duplicates(["member_id", "due_item"])[["member_id", "due_item", "priority"]]
