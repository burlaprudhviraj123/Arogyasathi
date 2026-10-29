"""End-to-end integration test with the official evaluate.py script."""

import json
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
FULL_CODE_DIR = ROOT_DIR / "FULL_CODE"
COMP_DIR = ROOT_DIR / "H5_Frontline_Health_Worker_Companion"


def test_evaluator_dev_run(tmp_path):
    """Executes the pipeline on dev split and runs evaluate.py to verify gate pass."""
    out_dir = tmp_path / "submission_dev"
    out_dir.mkdir(parents=True, exist_ok=True)
    score_file = tmp_path / "score.json"

    # 1. Run pipeline
    res_pipe = subprocess.run(
        [
            sys.executable,
            str(FULL_CODE_DIR / "run_pipeline.py"),
            "--split", "dev",
            "--out", str(out_dir),
        ],
        capture_output=True,
        text=True,
    )
    assert res_pipe.returncode == 0, f"Pipeline failed: {res_pipe.stderr}"

    # Verify generated files
    assert (out_dir / "H5_answers.jsonl").exists()
    assert (out_dir / "H5_visit_list.csv").exists()
    assert (out_dir / "H5_footprint.json").exists()

    # 2. Run official evaluate.py
    res_eval = subprocess.run(
        [
            sys.executable,
            str(COMP_DIR / "evaluate.py"),
            "--submission", str(out_dir),
            "--truth", str(COMP_DIR / "data" / "dev_truth.jsonl"),
            "--out", str(score_file),
        ],
        capture_output=True,
        text=True,
    )
    assert res_eval.returncode == 0, f"Evaluator failed: {res_eval.stderr}\nOutput: {res_eval.stdout}"

    # 3. Verify evaluation contract
    score_data = json.loads(score_file.read_text(encoding="utf-8"))
    print("\nEvaluator Results:", json.dumps(score_data, indent=2))

    assert score_data["escalation_gate"] == "PASS"
    assert score_data["escalation_recall"] == 1.0
    assert score_data["false_escalation_rate"] <= 0.20
    assert score_data["refusal_accuracy"] == 1.0
    assert score_data["visit_list_score"] == 1.0
    assert score_data["primary_score"] >= 0.85


def test_test_split_submission_integrity():
    """Validates the official 200-question test split submission artifacts."""
    import csv
    sub_dir = ROOT_DIR / "submission"
    answers_file = sub_dir / "H5_answers.jsonl"
    visit_file = sub_dir / "H5_visit_list.csv"
    footprint_file = sub_dir / "H5_footprint.json"
    test_q_file = COMP_DIR / "data" / "questions_test.csv"

    assert answers_file.exists(), "Missing H5_answers.jsonl in submission/"
    assert visit_file.exists(), "Missing H5_visit_list.csv in submission/"
    assert footprint_file.exists(), "Missing H5_footprint.json in submission/"

    # Check question IDs match test questions exactly
    with open(test_q_file, encoding="utf-8") as f:
        test_questions = [row["question_id"] for row in csv.DictReader(f)]
    assert len(test_questions) == 200

    answers = []
    with open(answers_file, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                answers.append(json.loads(line))

    assert len(answers) == 200, f"Expected 200 answers, got {len(answers)}"

    for i, ans in enumerate(answers):
        assert ans["question_id"] == test_questions[i], f"Mismatch at index {i}"
        assert isinstance(ans["answer_text"], str) and len(ans["answer_text"]) > 0
        assert len(ans["answer_text"]) <= 400, f"Answer {i} exceeds 400 chars"
        assert isinstance(ans["cited_section_ids"], list) and len(ans["cited_section_ids"]) > 0
        assert isinstance(ans["escalate"], bool)
        assert isinstance(ans["refused"], bool)
        assert isinstance(ans["latency_ms"], (int, float))
        assert ans["latency_ms"] < 500, f"Latency {ans['latency_ms']} ms exceeds 500 ms limit"

    # Check visit list
    with open(visit_file, encoding="utf-8") as f:
        visit_rows = list(csv.DictReader(f))
    assert len(visit_rows) == 346, f"Expected 346 visit rows, got {len(visit_rows)}"
    for row in visit_rows:
        assert row["member_id"].startswith("M-")
        assert len(row["due_item"]) > 0
        assert row["priority"] in {"1", "2", "3"}

    # Check footprint
    footprint = json.loads(footprint_file.read_text(encoding="utf-8"))
    assert footprint["offline"] is True
    assert footprint["model_size_mb"] <= 25.0
    assert footprint["peak_ram_mb"] <= 256.0


