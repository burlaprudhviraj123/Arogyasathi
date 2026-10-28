"""Protocol parsing and indexing for Arogyasathi."""

from __future__ import annotations
import re
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data"

SEC_RE = re.compile(r"^###\s+([A-Z]{2,4}-\d+\.\d+)\s*(.*)")
GROUP_RE = re.compile(r"^##\s+([A-Z]{2,4}-\d+)\s*(.*)")

def load_protocol_sections(protocol_dir: Path | None = None) -> dict[str, dict]:
    """Loads all 107 leaf sections from protocol documents."""
    if protocol_dir is None:
        protocol_dir = DATA_DIR / "protocols"
        
    sections = {}
    for p in sorted(protocol_dir.glob("*.md")):
        cur_sec = None
        cur_title = ""
        cur_text = []
        for line in p.read_text(encoding="utf-8").splitlines():
            m = SEC_RE.match(line)
            if m:
                if cur_sec:
                    sections[cur_sec] = {
                        "section_id": cur_sec,
                        "title": cur_title.strip(),
                        "text": "\n".join(cur_text).strip(),
                        "filename": p.name
                    }
                cur_sec = m.group(1)
                cur_title = m.group(2)
                cur_text = [line]
            elif line.startswith("#"):
                if cur_sec:
                    sections[cur_sec] = {
                        "section_id": cur_sec,
                        "title": cur_title.strip(),
                        "text": "\n".join(cur_text).strip(),
                        "filename": p.name
                    }
                    cur_sec = None
            elif cur_sec:
                cur_text.append(line)
        if cur_sec:
            sections[cur_sec] = {
                "section_id": cur_sec,
                "title": cur_title.strip(),
                "text": "\n".join(cur_text).strip(),
                "filename": p.name
            }
            
    return sections
