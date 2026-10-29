#!/usr/bin/env bash
# Packager script for TalentAP Mega Hackathon 2026 - Track H5

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================================"
echo " Packaging Arogyasathi Submission Bundle (Track H5)"
echo "================================================================"

BUNDLE_DIR="release_bundle"
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/submission"

# 1. Copy competition submission files
cp submission/H5_answers.jsonl "$BUNDLE_DIR/submission/"
cp submission/H5_visit_list.csv "$BUNDLE_DIR/submission/"
cp submission/H5_footprint.json "$BUNDLE_DIR/submission/"

# 2. Copy Android APK
APK_SRC="android_app/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SRC" ]; then
    APK_SRC="android_app/app/build/outputs/apk/debug/app-debug.apk"
fi
if [ ! -f "$APK_SRC" ]; then
    echo "Building Android APK..."
    (cd android_app && ./gradlew assembleRelease -q)
    APK_SRC="android_app/app/build/outputs/apk/release/app-release.apk"
fi
cp "$APK_SRC" "$BUNDLE_DIR/Arogyasathi-v1.0-offline.apk"

# 3. Copy FULL_CODE (Python engine, CLI, tests and clean Android source)
mkdir -p "$BUNDLE_DIR/FULL_CODE"
rsync -av --exclude="__pycache__" --exclude="*.pyc" --exclude=".pytest_cache" FULL_CODE/ "$BUNDLE_DIR/FULL_CODE/"
rsync -av --exclude="build" --exclude=".gradle" --exclude=".idea" --exclude="*.apk" --exclude="local.properties" --exclude="*.hprof" android_app/ "$BUNDLE_DIR/FULL_CODE/android_app/"

# 4. Copy key documents
cp README.md "$BUNDLE_DIR/"
cp docs/PROJECT_MANIFEST.md "$BUNDLE_DIR/"
cp docs/presentation/01_HACKATHON_PITCH_AND_ARCHITECTURE_WALKTHROUGH.md "$BUNDLE_DIR/PITCH_AND_ARCHITECTURE.md"
cp docs/presentation/02_JUDGES_SLIDE_DECK.md "$BUNDLE_DIR/JUDGES_SLIDE_DECK.md"
cp docs/presentation/03_DEMO_VIDEO_SCRIPT_AND_RECORDING_GUIDE.md "$BUNDLE_DIR/DEMO_VIDEO_SCRIPT.md"
cp web_demo.html "$BUNDLE_DIR/"
cp slides.html "$BUNDLE_DIR/"
cp benchmark_comparison.py "$BUNDLE_DIR/"

# 5. Create ZIP archive
ZIP_NAME="Arogyasathi_Track_H5_Submission.zip"
rm -f "$ZIP_NAME"
(cd "$BUNDLE_DIR" && zip -r "../$ZIP_NAME" .)

ZIP_SIZE=$(ls -lh "$ZIP_NAME" | awk '{print $5}')

echo ""
echo "================================================================"
echo " PACKAGE CREATED: $ZIP_NAME ($ZIP_SIZE)"
echo "================================================================"
unzip -l "$ZIP_NAME"
echo "================================================================"
