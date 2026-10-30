#!/usr/bin/env python3
"""Unified E2E Test Runner for Arogyasathi Offline Voice Packs.

Discovers and executes tests organized by tier:
  Tier 1: Feature Coverage (positive & specification validation)
  Tier 2: Boundary & Corner Cases (BVA, stress, corrupted input combinations)
  Tier 3: Pairwise Integration Tests
  Tier 4: Frontline Clinical Scenarios
  Tier 5: Adversarial Hardening

Supports filtering by tier, structured JSON report output, and detailed summaries.
Exit code 0 on 100% pass, non-zero on any failure or unhandled exception.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import unittest
from pathlib import Path
from typing import Any, Dict, List, Optional


class E2ETestResult(unittest.TestResult):
    """Custom TestResult that collects structured execution metrics."""

    def __init__(self, tier_name: str) -> None:
        super().__init__()
        self.tier_name = tier_name
        self.records: List[Dict[str, Any]] = []
        self._test_start_time = 0.0

    def startTest(self, test: unittest.TestCase) -> None:
        super().startTest(test)
        self._test_start_time = time.perf_counter()

    def addSuccess(self, test: unittest.TestCase) -> None:
        super().addSuccess(test)
        duration = time.perf_counter() - self._test_start_time
        self.records.append({
            "tier": self.tier_name,
            "test_id": test.id(),
            "name": test._testMethodName,
            "description": test.shortDescription() or test._testMethodName,
            "status": "PASS",
            "duration_sec": round(duration, 4),
            "error": None,
        })

    def addFailure(self, test: unittest.TestCase, err: Any) -> None:
        super().addFailure(test, err)
        duration = time.perf_counter() - self._test_start_time
        self.records.append({
            "tier": self.tier_name,
            "test_id": test.id(),
            "name": test._testMethodName,
            "description": test.shortDescription() or test._testMethodName,
            "status": "FAIL",
            "duration_sec": round(duration, 4),
            "error": self._exc_info_to_string(err, test),
        })

    def addError(self, test: unittest.TestCase, err: Any) -> None:
        super().addError(test, err)
        duration = time.perf_counter() - self._test_start_time
        self.records.append({
            "tier": self.tier_name,
            "test_id": test.id(),
            "name": test._testMethodName,
            "description": test.shortDescription() or test._testMethodName,
            "status": "ERROR",
            "duration_sec": round(duration, 4),
            "error": self._exc_info_to_string(err, test),
        })

    def addSkip(self, test: unittest.TestCase, reason: str) -> None:
        super().addSkip(test, reason)
        duration = time.perf_counter() - self._test_start_time
        self.records.append({
            "tier": self.tier_name,
            "test_id": test.id(),
            "name": test._testMethodName,
            "description": test.shortDescription() or test._testMethodName,
            "status": "SKIP",
            "duration_sec": round(duration, 4),
            "error": reason,
        })


TIER_DIRECTORIES = {
    1: ("Tier 1: Feature Coverage", "tier1_feature"),
    2: ("Tier 2: Boundary & Corner Cases", "tier2_boundary"),
    3: ("Tier 3: Pairwise Integration", "tier3_pairwise"),
    4: ("Tier 4: Frontline Scenarios", "tier4_realworld"),
    5: ("Tier 5: Adversarial Hardening", "tier5_adversarial"),
}


def discover_and_run(
    tiers: List[int],
    pattern: str = "test_*.py",
    verbose: bool = False,
) -> Dict[str, Any]:
    """Discover and execute tests for selected tiers."""
    tests_root = Path(__file__).resolve().parent

    overall_start = time.perf_counter()
    all_records: List[Dict[str, Any]] = []
    tier_summaries: Dict[str, Dict[str, int]] = {}

    total_passed = 0
    total_failed = 0
    total_errors = 0
    total_skipped = 0

    print("=" * 80)
    print("  AROGYASATHI E2E TEST RUNNER — OFFLINE VOICE PACKS PIPELINE")
    print("=" * 80)

    for tier_num in tiers:
        if tier_num not in TIER_DIRECTORIES:
            continue

        label, dir_name = TIER_DIRECTORIES[tier_num]
        tier_path = tests_root / dir_name

        if not tier_path.exists():
            if verbose:
                print(f"[-] {label}: Directory '{dir_name}' not present, skipping.")
            continue

        print(f"\n[+] Executing {label} ({dir_name}/)...")

        loader = unittest.TestLoader()
        suite = loader.discover(start_dir=str(tier_path), pattern=pattern, top_level_dir=str(tests_root.parent.parent))

        count = suite.countTestCases()
        if count == 0:
            print(f"    No test cases matched in {dir_name}/.")
            continue

        result = E2ETestResult(tier_name=label)
        suite.run(result)

        passed = sum(1 for r in result.records if r["status"] == "PASS")
        failed = sum(1 for r in result.records if r["status"] == "FAIL")
        errors = sum(1 for r in result.records if r["status"] == "ERROR")
        skipped = sum(1 for r in result.records if r["status"] == "SKIP")

        total_passed += passed
        total_failed += failed
        total_errors += errors
        total_skipped += skipped
        all_records.extend(result.records)

        tier_summaries[label] = {
            "total": len(result.records),
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "skipped": skipped,
        }

        for r in result.records:
            icon = "✓" if r["status"] == "PASS" else ("✗" if r["status"] in ("FAIL", "ERROR") else "○")
            if verbose or r["status"] != "PASS":
                print(f"    [{icon}] {r['status']:<5} {r['test_id']} ({r['duration_sec']}s)")
                if r["error"]:
                    indented = "\n".join("        " + line for line in r["error"].strip().splitlines())
                    print(f"{indented}")
            else:
                # Concise dot-like progress for clean output
                pass

        status_text = "ALL PASSED" if (failed == 0 and errors == 0) else f"FAILED ({failed} failed, {errors} errors)"
        print(f"    Summary: {passed}/{len(result.records)} passed, {failed} failed, {errors} errors, {skipped} skipped — {status_text}")

    total_duration = time.perf_counter() - overall_start
    total_tests = len(all_records)
    all_success = (total_failed == 0 and total_errors == 0 and total_tests > 0)

    print("\n" + "=" * 80)
    print("  FINAL EXECUTION SUMMARY")
    print("=" * 80)
    for tier_label, counts in tier_summaries.items():
        print(f"  * {tier_label}: {counts['passed']}/{counts['total']} passed ({counts['failed']} fail, {counts['errors']} err)")

    print("-" * 80)
    print(f"  Total Tests:    {total_tests}")
    print(f"  Total Passed:   {total_passed}")
    print(f"  Total Failed:   {total_failed}")
    print(f"  Total Errors:   {total_errors}")
    print(f"  Total Skipped:  {total_skipped}")
    print(f"  Total Duration: {total_duration:.2f} seconds")
    print(f"  Final Verdict:  {'[PASS] 100% SUCCESS' if all_success else '[FAIL] TEST SUITE FAILED'}")
    print("=" * 80)

    report = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_tests": total_tests,
        "passed": total_passed,
        "failed": total_failed,
        "errors": total_errors,
        "skipped": total_skipped,
        "duration_seconds": round(total_duration, 4),
        "success": all_success,
        "tier_summaries": tier_summaries,
        "records": all_records,
    }

    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Arogyasathi Offline Voice Packs E2E Test Runner")
    parser.add_argument(
        "--tier",
        type=str,
        default="all",
        help="Tier to execute: 1, 2, 3, 4, 5, or 'all' (default: all available)",
    )
    parser.add_argument(
        "--pattern",
        type=str,
        default="test_*.py",
        help="Test file discovery glob pattern (default: test_*.py)",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable verbose output showing individual test names and timings",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print JSON report to stdout at conclusion",
    )
    parser.add_argument(
        "--json-output",
        type=str,
        default=None,
        help="File path to save JSON execution report",
    )

    args = parser.parse_args()

    if args.tier.lower() == "all":
        tiers = [1, 2, 3, 4, 5]
    else:
        try:
            tiers = [int(args.tier)]
        except ValueError:
            print(f"Error: Invalid tier '{args.tier}'. Choose 1, 2, 3, 4, 5, or 'all'.", file=sys.stderr)
            sys.exit(2)

    report = discover_and_run(tiers=tiers, pattern=args.pattern, verbose=args.verbose)

    if args.json_output:
        out_path = Path(args.json_output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        with out_path.open("w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"\n[+] Structured JSON report written to: {out_path.resolve()}")

    if args.json:
        print(json.dumps(report, indent=2))

    sys.exit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
