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
