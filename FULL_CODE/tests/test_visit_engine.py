"""Unit tests for the Chandrapuri Block Daily Visit Engine."""

import pandas as pd
from pathlib import Path
from arogyasathi.visit_engine import generate_visit_list

DATA_DIR = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data"


def test_visit_engine_dev_truth():
    """Verify exact 1.0 F1 and 1.0 priority accuracy against dev truth."""
    reg_df = pd.read_csv(DATA_DIR / "household_register.csv")
    truth_df = pd.read_csv(DATA_DIR / "dev_truth_visit_list.csv")
    scope_df = pd.read_csv(DATA_DIR / "dev_truth_visit_scope.csv")

    scope_members = set(scope_df["member_id"].astype(str).str.strip())
    truth_pairs = {
        (str(r["member_id"]).strip(), str(r["due_item"]).strip()): int(r["priority"])
        for _, r in truth_df.iterrows()
    }

    pred_df = generate_visit_list(reg_df)
    pred_in_scope = pred_df[pred_df["member_id"].isin(scope_members)]
    pred_pairs = {
        (str(r["member_id"]).strip(), str(r["due_item"]).strip()): int(r["priority"])
        for _, r in pred_in_scope.iterrows()
    }

    # Evaluate F1
    hits = set(pred_pairs.keys()) & set(truth_pairs.keys())
    assert len(hits) == len(truth_pairs), f"Missed {len(truth_pairs) - len(hits)} visits"
    assert len(pred_pairs) == len(truth_pairs), f"Surplus {len(pred_pairs) - len(truth_pairs)} visits"

    # Evaluate Priority Accuracy
    for k, prio in truth_pairs.items():
        assert pred_pairs.get(k) == prio, f"Priority mismatch on {k}: expected {prio}, got {pred_pairs.get(k)}"
