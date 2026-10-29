#!/usr/bin/env python3
"""Pre-flight certification audit for Arogyasathi Hackathon Submission."""

import json
import subprocess
import tempfile
import zipfile
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
ZIP_PATH = BASE_DIR / "Arogyasathi_Track_H5_Submission.zip"
TRUTH_PATH = BASE_DIR / "H5_Frontline_Health_Worker_Companion" / "data" / "dev_truth.jsonl"
EVALUATOR_PATH = BASE_DIR / "H5_Frontline_Health_Worker_Companion" / "evaluate.py"

REQUIRED_ANSWER_KEYS = {
    "question_id": str,
    "answer_text": str,
    "cited_section_ids": list,
    "escalate": bool,
    "refused": bool,
    "latency_ms": (int, float),
}

def audit():
    print("=" * 64)
    print(" AROGYASATHI — OFFICIAL PRE-FLIGHT AUDIT & CERTIFICATION")
    print("=" * 64)

    assert ZIP_PATH.exists(), f"Missing archive: {ZIP_PATH}"
    size_mb = ZIP_PATH.stat().st_size / (1024 * 1024)
    print(f"Archive file: {ZIP_PATH.name} ({size_mb:.2f} MB)")

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        with zipfile.ZipFile(ZIP_PATH, "r") as z:
            z.extractall(tmp)

        extracted_files = list(tmp.rglob("*"))
        print(f"[✓] Extracted {len(extracted_files)} files cleanly to sandbox.")

        # 1. Verify Root Documents
        for doc in ["README.md", "PROJECT_MANIFEST.md", "PITCH_AND_ARCHITECTURE.md", "JUDGES_SLIDE_DECK.md", "DEMO_VIDEO_SCRIPT.md", "web_demo.html", "slides.html"]:
            assert (tmp / doc).exists(), f"Missing required document in zip root: {doc}"
        print("[✓] All 7 key documents verified in archive root.")

        # 2. Verify APK Package
        apk_path = tmp / "Arogyasathi-v1.0-offline.apk"
        assert apk_path.exists(), "Missing Arogyasathi-v1.0-offline.apk in root"
        with zipfile.ZipFile(apk_path, "r") as az:
            apk_files = set(az.namelist())
            for req in ["AndroidManifest.xml", "classes.dex", "assets/protocols.json", "assets/households.csv", "assets/household_register.csv", "assets/ashas.csv"]:
                assert req in apk_files, f"APK missing entry: {req}"
        apk_mb = apk_path.stat().st_size / (1024 * 1024)
        print(f"[✓] Standalone APK verified: {apk_mb:.2f} MB with all assets & manifest present.")

        # 3. Verify Scored Test Submission (200 test questions)
        sub_dir = tmp / "submission"
        assert (sub_dir / "H5_answers.jsonl").exists()
        assert (sub_dir / "H5_visit_list.csv").exists()
        assert (sub_dir / "H5_footprint.json").exists()

        with open(tmp / "web_demo.html") as f:
            assert f.read(100).startswith("<!DOCTYPE html>")

        # Protocol leaf IDs
        with open(BASE_DIR / "android_app" / "app" / "src" / "main" / "assets" / "protocols.json") as f:
            protocols = json.load(f)
        valid_protocol_ids = {p["section_id"] for p in protocols}

        # Check answers
        answers_count = 0
        with open(sub_dir / "H5_answers.jsonl", "r", encoding="utf-8") as f:
            for ln, line in enumerate(f, 1):
                rec = json.loads(line)
                answers_count += 1
                for k, expected_type in REQUIRED_ANSWER_KEYS.items():
                    assert k in rec, f"Line {ln}: missing {k}"
                    assert isinstance(rec[k], expected_type), f"Line {ln}: {k} wrong type"
                assert len(rec["answer_text"]) <= 400, f"Line {ln}: answer_text > 400 chars"
                assert len(rec["cited_section_ids"]) <= 2, f"Line {ln}: citations > 2"
                for cid in rec["cited_section_ids"]:
                    assert cid in valid_protocol_ids, f"Line {ln}: unknown citation {cid}"
        assert answers_count == 200, f"Expected 200 test answers, got {answers_count}"
        print(f"[✓] Scored Submission verified: Exactly 200 answers, 0 format violations.")

        # 4. Verify Official Evaluator on Dev Split (40 questions)
        cmd = [
            "python3", str(EVALUATOR_PATH),
            "--submission", str(BASE_DIR / "submission_dev"),
            "--truth", str(TRUTH_PATH),
            "--out", str(tmp / "score_audit.json")
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        assert res.returncode == 0, f"Evaluator failed: {res.stderr}"

        with open(tmp / "score_audit.json") as f:
            score = json.load(f)

        assert score["escalation_gate"] == "PASS", "Escalation gate failed"
        assert score["escalation_recall"] == 1.0, "Escalation recall < 1.0"
        assert score["false_escalation_rate"] == 0.0, "False escalation rate > 0.0"
        assert score["primary_score"] > 0.95, f"Score too low: {score['primary_score']}"
        print(f"[✓] Official Evaluator verified on dev split:")
        print(f"    - Primary Score          : {score['primary_score']:.4f}")
        print(f"    - Escalation Gate        : {score['escalation_gate']}")
        print(f"    - Escalation Recall      : {score['escalation_recall']:.4f}")
        print(f"    - False Escalation Rate  : {score['false_escalation_rate']:.4f}")
        print(f"    - Visit List Score       : {score['visit_list_score']:.4f}")

    print("=" * 64)
    print(" PRE-FLIGHT CERTIFICATION: ALL AUDITS PASSED (100% READY)")
    print("=" * 64)

if __name__ == "__main__":
    audit()
