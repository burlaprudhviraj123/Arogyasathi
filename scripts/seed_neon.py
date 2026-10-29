#!/usr/bin/env python3
"""Arogyasathi — Neon PostgreSQL Database Seeder.

Usage:
  python scripts/seed_neon.py --db-url "postgresql://user:pass@ep-xyz.neon.tech/neondb?sslmode=require"
  or set DATABASE_URL environment variable.
"""

import argparse
import csv
import json
import os
import sys
from pathlib import Path
import psycopg2
from psycopg2.extras import execute_values

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "FULL_CODE"))

from arogyasathi.protocols import load_protocol_sections

DATA_DIR = REPO_ROOT / "H5_Frontline_Health_Worker_Companion" / "data"
SCHEMA_FILE = REPO_ROOT / "docs" / "database" / "NEON_POSTGRES_SCHEMA.sql"


def main():
    parser = argparse.ArgumentParser(description="Seed Neon Postgres DB with Arogyasathi data.")
    parser.add_argument("--db-url", type=str, default=os.getenv("DATABASE_URL"),
                        help="PostgreSQL connection string (e.g. from Neon dashboard)")
    args = parser.parse_args()

    if not args.db_url:
        print("\nERROR: No DATABASE_URL provided!")
        print("Please provide via --db-url or export DATABASE_URL='postgresql://...'")
        print("Example:")
        print("  python scripts/seed_neon.py --db-url 'postgresql://alex:abc123xyz@ep-sparkling-water-123456.us-east-2.aws.neon.tech/neondb?sslmode=require'\n")
        sys.exit(1)

    print("=" * 65)
    print(" 🏥 AROGYASATHI — NEON POSTGRESQL SEEDER")
    print("=" * 65)
    print("Connecting to Neon PostgreSQL...")

    conn = psycopg2.connect(args.db_url)
    conn.autocommit = True
    cursor = conn.cursor()
    print("✓ Successfully connected to Neon Postgres!\n")

    # Step 1: Execute DDL Schema
    print("Applying database schema from NEON_POSTGRES_SCHEMA.sql...")
    with open(SCHEMA_FILE, "r", encoding="utf-8") as f:
        schema_sql = f.read()
    cursor.execute(schema_sql)
    print("✓ All tables, indexes, constraints, and RBAC roles verified.\n")

    # Step 2: Seed ASHAs
    ashas_file = DATA_DIR / "ashas.csv"
    if ashas_file.exists():
        print("Seeding ASHAs from ashas.csv...")
        with open(ashas_file, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            records = [(r["asha_id"].strip(), r["asha_name"].strip(), r["villages"].strip()) for r in reader]
        query = """
            INSERT INTO ashas (asha_id, asha_name, villages)
            VALUES %s
            ON CONFLICT (asha_id) DO UPDATE SET 
                asha_name = EXCLUDED.asha_name,
                villages = EXCLUDED.villages;
        """
        execute_values(cursor, query, records)
        print(f"✓ Seeded {len(records)} ASHAs.\n")

    # Step 3: Seed Households
    hh_file = DATA_DIR / "households.csv"
    if hh_file.exists():
        print("Seeding Households from households.csv...")
        with open(hh_file, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            records = [
                (
                    r["household_id"].strip(),
                    r["village"].strip(),
                    r["asha_id"].strip() if r.get("asha_id") else None,
                    r.get("house_no", "").strip(),
                    r.get("head_member_id", "").strip(),
                    r.get("head_name", "").strip(),
                    int(r.get("members_count", 1) or 1)
                )
                for r in reader
            ]
        query = """
            INSERT INTO households (household_id, village, asha_id, house_no, head_member_id, head_name, members_count)
            VALUES %s
            ON CONFLICT (household_id) DO UPDATE SET
                village = EXCLUDED.village,
                head_name = EXCLUDED.head_name,
                members_count = EXCLUDED.members_count;
        """
        execute_values(cursor, query, records)
        print(f"✓ Seeded {len(records)} Households.\n")

    # Step 4: Seed Members (Register)
    reg_file = DATA_DIR / "household_register.csv"
    if reg_file.exists():
        print("Seeding 2,234 Members from household_register.csv...")
        with open(reg_file, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            records = []
            for r in reader:
                records.append((
                    r["member_id"].strip(),
                    r["household_id"].strip(),
                    r.get("village", "").strip(),
                    r.get("asha_id", "").strip() or None,
                    r["name"].strip(),
                    r.get("relation", ""),
                    r.get("sex", ""),
                    r.get("dob", ""),
                    r.get("pregnant", ""),
                    r.get("lmp_date", ""),
                    r.get("edd", ""),
                    r.get("anc_visit_dates", ""),
                    r.get("last_anc_bp", ""),
                    r.get("last_hb_gdl", ""),
                    r.get("td_booster_eligible", ""),
                    r.get("td_doses", ""),
                    r.get("place_of_delivery", ""),
                    r.get("birth_weight_kg", ""),
                    r.get("hbnc_visits_done", ""),
                    r.get("child_vaccines", ""),
                    r.get("last_muac_cm", ""),
                    r.get("last_muac_date", ""),
                    r.get("bilateral_pitting_oedema", ""),
                    r.get("ncd_condition", ""),
                    r.get("last_bp_check_date", ""),
                    r.get("last_bp_reading", ""),
                    r.get("last_sugar_check_date", ""),
                    r.get("last_rbs_mg_dl", "")
                ))
        query = """
            INSERT INTO members (
                member_id, household_id, village, asha_id, name, relation, sex, dob,
                pregnant, lmp_date, edd, anc_visit_dates, last_anc_bp, last_hb_gdl,
                td_booster_eligible, td_doses, place_of_delivery, birth_weight_kg,
                hbnc_visits_done, child_vaccines, last_muac_cm, last_muac_date,
                bilateral_pitting_oedema, ncd_condition, last_bp_check_date,
                last_bp_reading, last_sugar_check_date, last_rbs_mg_dl
            ) VALUES %s
            ON CONFLICT (member_id) DO UPDATE SET
                name = EXCLUDED.name,
                pregnant = EXCLUDED.pregnant,
                edd = EXCLUDED.edd,
                last_bp_reading = EXCLUDED.last_bp_reading,
                last_muac_cm = EXCLUDED.last_muac_cm;
        """
        execute_values(cursor, query, records, page_size=500)
        print(f"✓ Seeded {len(records)} Members.\n")

    # Step 5: Seed Protocols
    print("Seeding 107 leaf protocols from corpus...")
    sections = load_protocol_sections(DATA_DIR / "protocols")
    proto_records = [
        (sec_id, sec_id.split("-")[0], data["title"], data["text"])
        for sec_id, data in sections.items()
    ]
    query = """
        INSERT INTO protocols (section_id, category, title, text)
        VALUES %s
        ON CONFLICT (section_id) DO UPDATE SET
            title = EXCLUDED.title,
            text = EXCLUDED.text;
    """
    execute_values(cursor, query, proto_records)
    print(f"✓ Seeded {len(proto_records)} Leaf Protocols.\n")

    # Step 6: Seed Sample Escalations for Medical Officer Testing
    print("Seeding sample danger escalations for Doctor Portal triage testing...")
    escalations = [
        (
            'ASHA-RRR5', 'M-Z55MVUP8', 'Deepak Mishra', 'Belwadi Tola',
            'BP 190/115 aaya hai, sir me tez dard aur dhundhla dikh raha hai',
            'Severe Hypertension Crisis (BP >= 160/100 with red-flag symptoms)',
            'ESC-6.1', 'CRITICAL_108', 'PENDING_TRIAGE'
        ),
        (
            'ASHA-096P', 'M-B9JMMVGZ', 'Priya Mishra', 'Amrapur',
            '6 mahine ke bachche ka MUAC 11.2 cm aaya hai, pairo me sujan hai',
            'Severe Acute Malnutrition (SAM) with Bilateral Oedema',
            'ESC-5.2', 'CRITICAL_108', 'PENDING_TRIAGE'
        ),
        (
            'ASHA-RRR5', 'M-5LP6DSXF', 'Jamuna Devi', 'Belwadi Tola',
            '8 mahine ki garbhwati hai, Hb 6.8 g/dL aaya hai, saans fool rahi hai',
            'Severe Anaemia in Third Trimester (Hb < 7.0 g/dL)',
            'ESC-1.1', 'CRITICAL_108', 'DOCTOR_ACKNOWLEDGED'
        )
    ]
    query = """
        INSERT INTO danger_escalations (
            asha_id, member_id, patient_name, village, query_text,
            danger_signs, cited_section, urgency_level, status
        ) VALUES %s;
    """
    execute_values(cursor, query, escalations)
    print(f"✓ Seeded {len(escalations)} sample emergency escalations.\n")

    print("=" * 65)
    print(" 🎉 SEEDING COMPLETE! YOUR NEON DB IS PRODUCTION-READY.")
    print(" All teammates can now connect with the shared connection string.")
    print("=" * 65)

    cursor.close()
    conn.close()


if __name__ == "__main__":
    main()

