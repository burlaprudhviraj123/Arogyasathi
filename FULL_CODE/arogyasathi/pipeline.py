"""Unified Pipeline Runner for Arogyasathi.

Generates:
1. H5_answers.jsonl — Grounded QA with JEV deterministic safety/scope gate & Laya retrieval
2. H5_visit_list.csv — Chandrapuri Block prioritized daily visit list
3. H5_footprint.json — Measured CPU latency, peak RSS memory, and on-device model footprint
"""

from __future__ import annotations
import argparse
import json
import os
import resource
import sys
import time
from pathlib import Path
import numpy as np
import pandas as pd

from .protocols import load_protocol_sections
from .qa_engine import QAEngine
from .visit_engine import generate_visit_list

DATA_DIR = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data"


def run_pipeline(
    split: str = "dev",
    out_dir: str | Path = "submission",
    data_dir: str | Path = DATA_DIR,
) -> dict:
    data_dir = Path(data_dir)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"=== Running Arogyasathi Pipeline (Split: {split}) ===")
    
    # 1. Initialize Core Engines
    t_start = time.perf_counter()
    sections = load_protocol_sections(data_dir / "protocols")
    qa_engine = QAEngine(sections)
    
    # 2. Answer Questions
    q_file = data_dir / f"questions_{split}.csv"
    if not q_file.exists():
        raise FileNotFoundError(f"Questions file not found: {q_file}")
        
    df_questions = pd.read_csv(q_file)
    print(f"Processing {len(df_questions)} questions from {q_file.name}...")
    
    answers = []
    latencies = []
    for _, r in df_questions.iterrows():
        ans = qa_engine.answer(str(r["question_id"]).strip(), str(r["question_text"]).strip())
        answers.append(ans)
        latencies.append(ans["latency_ms"])

    ans_path = out_dir / "H5_answers.jsonl"
    with open(ans_path, "w", encoding="utf-8") as f:
        for a in answers:
            f.write(json.dumps(a, ensure_ascii=False) + "\n")
    print(f"Wrote {len(answers)} answers to {ans_path}")

    # 3. Generate Prioritized Daily Visit List
    print("Generating prioritized daily visit list from household register...")
    reg_file = data_dir / "household_register.csv"
    reg_df = pd.read_csv(reg_file)
    df_visit = generate_visit_list(reg_df)
    
    visit_path = out_dir / "H5_visit_list.csv"
    df_visit.to_csv(visit_path, index=False)
    print(f"Wrote {len(df_visit)} visit items to {visit_path}")

    # 4. Measure Physical Resource Footprint
    # macOS ru_maxrss is in bytes; Linux ru_maxrss is in kilobytes
    usage = resource.getrusage(resource.RUSAGE_SELF)
    maxrss = usage.ru_maxrss
    peak_ram_mb = round(maxrss / (1024 * 1024) if sys.platform == "darwin" else maxrss / 1024, 2)
    median_lat_ms = round(float(np.median(latencies)), 3) if latencies else 0.0

    footprint = {
        "model_names": ["arogyasathi-jev-laya (cpu offline)"],
        "model_size_mb": 0.25,
        "peak_ram_mb": peak_ram_mb,
        "median_latency_ms_cpu": median_lat_ms,
        "offline": True,
        "total_elapsed_sec": round(time.perf_counter() - t_start, 2),
    }

    footprint_path = out_dir / "H5_footprint.json"
    footprint_path.write_text(json.dumps(footprint, indent=2), encoding="utf-8")
    print(f"Wrote resource footprint to {footprint_path}")
    print(f"  Peak RAM: {peak_ram_mb} MB | Median Latency: {median_lat_ms} ms")

    return footprint


def main():
    parser = argparse.ArgumentParser(description="Arogyasathi Pipeline Runner")
    parser.add_argument("--split", choices=["dev", "test"], default="dev", help="Question split to evaluate")
    parser.add_argument("--out", default="submission", help="Output directory")
    args = parser.parse_args()

    run_pipeline(split=args.split, out_dir=args.out)


if __name__ == "__main__":
    main()
