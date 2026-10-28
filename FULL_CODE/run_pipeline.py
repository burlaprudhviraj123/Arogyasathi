#!/usr/bin/env python3
"""Arogyasathi — Production CLI Entrypoint.

Usage:
  python FULL_CODE/run_pipeline.py --split dev --out submission_dev
  python FULL_CODE/run_pipeline.py --split test --out submission
"""

import sys
from pathlib import Path

# Add FULL_CODE to Python path
sys.path.insert(0, str(Path(__file__).resolve().parent))

from arogyasathi.pipeline import main

if __name__ == "__main__":
    main()
