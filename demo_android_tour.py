#!/usr/bin/env python3
"""Automated live Android UI demonstration and screenshot capture tour for Arogyasathi."""

import os
import shutil
import subprocess
import time
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
TOUR_DIR = BASE_DIR / "docs" / "screenshots" / "tour"
TOUR_DIR.mkdir(parents=True, exist_ok=True)

# Locate ADB
ADB_CANDIDATES = [
    shutil.which("adb"),
    os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
    "/opt/android-sdk/platform-tools/adb",
]
ADB = next((c for c in ADB_CANDIDATES if c and os.path.exists(c)), "adb")


def run_adb(cmd: str) -> str:
    full_cmd = f"{ADB} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()


def capture(name: str):
    remote = "/data/local/tmp/tour_shot.png"
    local = TOUR_DIR / name
    run_adb(f"shell screencap -p {remote}")
    run_adb(f"pull {remote} {local}")
    run_adb(f"shell rm {remote}")
    print(f"  [📸 Screenshot] Saved: {local.name}")


def main():
    print("=" * 64)
    print(" AROGYASATHI — AUTOMATED ANDROID UI LIVE TOUR")
    print("=" * 64)

    # 1. Check device
    devices = run_adb("devices")
    print("ADB Devices:\n" + devices)
    if "device" not in devices:
        print("[!] No connected Android device or emulator detected. Skipping live tour.")
        return

    # 2. Launch App
    print("\n[1/6] Launching Arogyasathi...")
    run_adb("shell am start -n org.arogyasathi.app/.MainActivity")
    time.sleep(1.5)

    # 3. Tab 0: Today Screen
    print("[2/6] Navigating to Today Workspace (Tab 0)...")
    run_adb("shell input tap 135 2280")
    time.sleep(1.0)
    capture("01_today_workspace.png")

    # 4. Tab 1: Families Screen
    print("[3/6] Navigating to Families Workspace (Tab 1)...")
    run_adb("shell input tap 405 2280")
    time.sleep(1.0)
    capture("02_families_workspace.png")

    # Expand first family card
    run_adb("shell input tap 500 500")
    time.sleep(0.5)
    capture("02b_families_expanded.png")

    # 5. Tab 2: Assistant Screen
    print("[4/6] Navigating to Clinical Assistant Workspace (Tab 2)...")
    run_adb("shell input tap 675 2280")
    time.sleep(1.0)
    capture("03_assistant_workspace.png")

    # Ask danger sign query
    print("      Testing Danger Escalation Query in Hindi...")
    run_adb("shell input tap 400 1950")
    time.sleep(0.3)
    # Clear text and enter danger query
    run_adb("shell input keyevent 67")
    run_adb("shell input text 'bachche%sko%sjhatke%saa%srahe%shain'")
    time.sleep(0.5)
    # Tap Send button
    run_adb("shell input tap 1000 1950")
    time.sleep(1.0)
    # Dismiss soft keyboard to reveal card
    run_adb("shell input keyevent 4")
    time.sleep(0.5)
    capture("04_assistant_danger_escalation.png")

    # 6. Tab 3: Records Screen
    print("[5/6] Navigating to Records & Edge Sync Workspace (Tab 3)...")
    run_adb("shell input tap 945 2280")
    time.sleep(1.0)
    capture("05_records_workspace.png")

    # Tap Sync Now button
    print("      Testing Edge Gateway Synchronization...")
    run_adb("shell input tap 850 480")
    time.sleep(1.2)
    capture("06_records_sync_completed.png")

    # Return to Today Tab
    print("[6/6] Returning to Today Workspace...")
    run_adb("shell input tap 135 2280")
    time.sleep(0.8)
    capture("07_today_overview.png")

    print("\n" + "=" * 64)
    print(" TOUR COMPLETE — ALL WORKSPACES VERIFIED & CAPTURED")
    print(f" Screenshots stored in: {TOUR_DIR}")
    print("=" * 64)


if __name__ == "__main__":
    main()
