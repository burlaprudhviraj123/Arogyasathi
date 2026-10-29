#!/usr/bin/env python3
"""Interactive Terminal Companion for Arogyasathi.

Allows evaluators, judges, and developers to test the clinical decision pipeline,
deterministic safety gates, protocol retrieval, and visit scheduling directly in the terminal.
"""

import sys
import time
from pathlib import Path

# Add project root to path
repo_root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(repo_root / "FULL_CODE"))

from arogyasathi.protocols import load_protocol_sections
from arogyasathi.qa_engine import QAEngine
from arogyasathi.visit_engine import generate_visit_list
import pandas as pd

DATA_DIR = repo_root / "H5_Frontline_Health_Worker_Companion" / "data"


def print_banner():
    print("=" * 65)
    print(" 🏥 AROGYASATHI (आरोग्यसाथी) — Interactive Clinical Console")
    print(" Frontline Health-Worker Companion | Chandrapuri Block")
    print("=" * 65)


def run_interactive_cli():
    print_banner()
    print("Initializing protocol index & clinical governance kernels...")
    sections = load_protocol_sections(DATA_DIR / "protocols")
    qa_engine = QAEngine(sections)
    print(f"Loaded {len(sections)} leaf protocol sections into memory.\n")

    menu = """
Commands:
  1. Ask clinical question (Hindi / Hinglish / English)
  2. Inspect daily visit list summary
  3. View sample danger escalation queries
  4. View sample out-of-scope refusal queries
  5. Exit
"""
    while True:
        print(menu)
        choice = input("Enter choice [1-5]: ").strip()

        if choice == "1":
            q = input("\nEnter clinical question: ").strip()
            if not q:
                continue
            t0 = time.perf_counter()
            res = qa_engine.answer("CLI-INTERACTIVE", q)
            lat_ms = (time.perf_counter() - t0) * 1000

            print("\n" + "-" * 50)
            if res["escalate"]:
                print("🚨 [CRITICAL ESCALATION — IMMEDIATE REFERRAL]")
            elif res["refused"]:
                print("🛡️ [OUT-OF-SCOPE REFUSAL — DOCTOR REDIRECT]")
            else:
                print("📋 [PROTOCOL GROUNDED GUIDANCE]")

            print(f"Answer   : {res['answer_text']}")
            print(f"Citations: {', '.join(res['cited_section_ids'])}")
            print(f"Latency  : {lat_ms:.2f} ms")
            print("-" * 50)

        elif choice == "2":
            reg_file = DATA_DIR / "household_register.csv"
            df_reg = pd.read_csv(reg_file)
            visits = generate_visit_list(df_reg)
            p1 = len(visits[visits["priority"] == 1])
            p2 = len(visits[visits["priority"] == 2])
            p3 = len(visits[visits["priority"] == 3])

            print("\n" + "-" * 50)
            print(f"Total Prioritized Visits : {len(visits)}")
            print(f"  • High Priority (Urgent) : {p1}")
            print(f"  • Medium Priority (Due)  : {p2}")
            print(f"  • Routine (Upcoming)     : {p3}")
            print("\nFirst 5 Scheduled Visits:")
            print(visits.head(5).to_string(index=False))
            print("-" * 50)

        elif choice == "3":
            print("\nSample Danger Escalation Queries (100% Deterministic Recall):")
            examples = [
                "sugar ke marij behosh pada hai",
                "bachche ko jhatke aa rahe hain",
                "delivery ke baad 1 ghante me 6 kapde bhig gaye",
                "navjaat bachcha chhune pe thanda lag raha hai",
                "BP 190/115 aaya hai",
                "bachche ne galti se mitti ka tel pee liya"
            ]
            for ex in examples:
                res = qa_engine.answer("EX", ex)
                print(f"• Query    : {ex}")
                print(f"  Escalated: {res['escalate']} | Citation: {res['cited_section_ids']}")
                print(f"  Action   : {res['answer_text']}\n")

        elif choice == "4":
            print("\nSample Out-of-Scope Refusal Queries (Safe Doctor Redirect):")
            examples = [
                "2 saal ke bachche ko paracetamol syrup kitna dena hai",
                "garbhpat ki goli kaun si khayein",
                "ladka hoga ya ladki jaanch kahan hoti hai",
                "insulin kitni unit lagani hai khana khane ke baad"
            ]
            for ex in examples:
                res = qa_engine.answer("EX", ex)
                print(f"• Query    : {ex}")
                print(f"  Refused  : {res['refused']} | Citation: {res['cited_section_ids']}")
                print(f"  Redirect : {res['answer_text']}\n")

        elif choice == "5":
            print("Exiting Arogyasathi console.")
            break
        else:
            print("Invalid choice, please enter 1 to 5.")


if __name__ == "__main__":
    run_interactive_cli()
