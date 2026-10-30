"""FastAPI Server Gateway for Arogyasathi.

Provides:
1. Online / Sub-Centre Sync Gateway for deferred synchronization of visit outcomes.
2. Clinical Protocol Q&A Endpoint powered by JEV safety gate and Laya retrieval.
3. Daily Prioritized Visit List generation for Chandrapuri Block.
4. Leaf Protocol Corpus query endpoints.
"""

from __future__ import annotations
import os
import time
from typing import List, Optional
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel, Field

from .protocols import load_protocol_sections
from .qa_engine import QAEngine
from .visit_engine import generate_visit_list
import pandas as pd
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT_DIR / "H5_Frontline_Health_Worker_Companion" / "data"
DATABASE_URL = os.getenv("DATABASE_URL")

app = FastAPI(
    title="Arogyasathi Edge Sync Gateway",
    description="Clinical Companion & Synchronization Gateway for Frontline Health Workers (Chandrapuri Block)",
    version="1.0.0"
)

# In-memory storage for synced outcomes
SYNCED_OUTCOMES: list[dict] = []

# Initialize QA Engine & Protocol Corpus on startup
SECTIONS = load_protocol_sections(DATA_DIR / "protocols")
QA_ENGINE = QAEngine(SECTIONS)


@app.api_route("/", methods=["GET", "HEAD"], include_in_schema=False)
def serve_web_demo():
    demo_file = ROOT_DIR / "web_demo.html"
    if demo_file.exists():
        return FileResponse(demo_file, media_type="text/html")
    return HTMLResponse("<html><head><title>आरोग्यसाथी</title></head><body><h1>आरोग्यसाथी Edge Sync Gateway Active</h1></body></html>")


@app.api_route("/slides", methods=["GET", "HEAD"], include_in_schema=False)
def serve_slides():
    slides_file = ROOT_DIR / "slides.html"
    if slides_file.exists():
        return FileResponse(slides_file, media_type="text/html")
    return HTMLResponse("<html><head><title>Arogyasathi Slides</title></head><body><h1>Arogyasathi Architecture & Pitch Deck</h1></body></html>")


class QueryRequest(BaseModel):
    query: str = Field(..., description="Clinical question from frontline health worker")
    worker_id: Optional[str] = Field("ASHA-RRR5", description="Frontline worker identifier")


class QueryResponse(BaseModel):
    answer_text: str
    cited_section_ids: List[str]
    escalate: bool
    refused: bool
    latency_ms: float


class VisitOutcomeItem(BaseModel):
    outcome_id: str
    household_id: str
    member_id: str
    member_name: str
    due_item: str
    status: str
    notes: Optional[str] = ""
    timestamp: int


class SyncRequest(BaseModel):
    worker_id: str = Field("ASHA-RRR5", description="Worker submitting batch")
    outcomes: List[VisitOutcomeItem] = Field(..., description="List of recorded visit outcomes")


class SyncResponse(BaseModel):
    status: str
    synced_count: int
    received_timestamp: int


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "Arogyasathi Edge Sync Gateway",
        "protocols_loaded": len(SECTIONS),
        "synced_outcomes_count": len(SYNCED_OUTCOMES),
        "version": "1.0.0"
    }


@app.post("/api/v1/query", response_model=QueryResponse)
def clinical_query(req: QueryRequest):
    ans = QA_ENGINE.answer("API-QUERY", req.query)

    return QueryResponse(
        answer_text=ans["answer_text"],
        cited_section_ids=ans["cited_section_ids"],
        escalate=ans["escalate"],
        refused=ans["refused"],
        latency_ms=ans["latency_ms"]
    )


def persist_to_neon_if_configured(worker_id: str, outcomes: list[dict]):
    if not DATABASE_URL:
        return
    try:
        import psycopg2
        from psycopg2.extras import execute_values
        with psycopg2.connect(DATABASE_URL) as conn:
            with conn.cursor() as cur:
                records = [
                    (
                        item["outcome_id"],
                        item["household_id"],
                        item["member_id"],
                        item.get("member_name", ""),
                        worker_id,
                        item["due_item"],
                        item["status"],
                        item.get("notes", ""),
                        item["timestamp"]
                    )
                    for item in outcomes
                ]
                query = """
                    INSERT INTO visit_outcomes (
                        outcome_id, household_id, member_id, member_name, asha_id,
                        due_item, status, notes, recorded_at_epoch
                    ) VALUES %s
                    ON CONFLICT (outcome_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        notes = EXCLUDED.notes;
                """
                execute_values(cur, query, records)
    except Exception as e:
        print(f"[Neon Sync Gateway Notice]: {e}")


@app.post("/api/v1/sync/visit_outcomes", response_model=SyncResponse)
def sync_visit_outcomes(req: SyncRequest):
    global SYNCED_OUTCOMES
    count = 0
    now_ts = int(time.time() * 1000)
    batch_records = []

    for item in req.outcomes:
        record = item.model_dump()
        record["synced_at"] = now_ts
        record["worker_id"] = req.worker_id
        SYNCED_OUTCOMES.append(record)
        batch_records.append(record)
        count += 1

    persist_to_neon_if_configured(req.worker_id, batch_records)

    return SyncResponse(
        status="SUCCESS",
        synced_count=count,
        received_timestamp=now_ts
    )


@app.get("/api/v1/sync/outcomes")
def list_synced_outcomes():
    return {
        "total_synced": len(SYNCED_OUTCOMES),
        "outcomes": SYNCED_OUTCOMES
    }


@app.get("/api/v1/visits/today")
def get_daily_visits(village: Optional[str] = None):
    reg_file = DATA_DIR / "household_register.csv"
    if not reg_file.exists():
        raise HTTPException(status_code=500, detail="Household register not found")

    reg_df = pd.read_csv(reg_file)
    if village and village != "All":
        reg_df = reg_df[reg_df["village"].str.strip() == village.strip()]

    df_visit = generate_visit_list(reg_df)
    return {
        "total_visits": len(df_visit),
        "visits": df_visit.to_dict(orient="records")
    }


@app.get("/api/v1/protocols")
def get_protocols(category: Optional[str] = None):
    out = []
    for sec_id, data in SECTIONS.items():
        if category and not sec_id.startswith(category.upper()):
            continue
        out.append({
            "section_id": sec_id,
            "title": data["title"],
            "category": sec_id.split("-")[0],
            "body": data["text"],
            "keywords": data.get("keywords", ""),
        })
    return {
        "count": len(out),
        "protocols": out
    }


@app.get("/api/v1/bootstrap")
def get_bootstrap_dataset(asha_id: Optional[str] = None):
    """Returns canonical ASHA profiles, households, members, and protocols from Neon Postgres or local dataset."""
    if DATABASE_URL and "neon.tech" in DATABASE_URL and "[endpoint]" not in DATABASE_URL:
        try:
            import psycopg2
            from psycopg2.extras import RealDictCursor
            with psycopg2.connect(DATABASE_URL, cursor_factory=RealDictCursor) as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT * FROM ashas ORDER BY asha_id")
                    ashas = [dict(r) for r in cur.fetchall()]
                    if asha_id:
                        cur.execute("SELECT * FROM households WHERE asha_id = %s ORDER BY household_id", (asha_id,))
                    else:
                        cur.execute("SELECT * FROM households ORDER BY household_id")
                    households = [dict(r) for r in cur.fetchall()]
                    if asha_id:
                        cur.execute("SELECT * FROM members WHERE asha_id = %s ORDER BY member_id", (asha_id,))
                    else:
                        cur.execute("SELECT * FROM members ORDER BY member_id")
                    members = [dict(r) for r in cur.fetchall()]
                    cur.execute("SELECT section_id, category, title, body, keywords FROM protocols ORDER BY section_id")
                    protocols = [dict(r) for r in cur.fetchall()]
                    if protocols and households:
                        return {
                            "status": "ok",
                            "source": "neon_postgres",
                            "counts": {
                                "ashas": len(ashas),
                                "households": len(households),
                                "members": len(members),
                                "protocols": len(protocols),
                            },
                            "ashas": ashas,
                            "households": households,
                            "members": members,
                            "protocols": protocols,
                        }
        except Exception as e:
            print(f"[Neon Bootstrap Fallback]: {e}")

    ashas_df = pd.read_csv(DATA_DIR / "ashas.csv").fillna("")
    hh_df = pd.read_csv(DATA_DIR / "households.csv").fillna("")
    mem_df = pd.read_csv(DATA_DIR / "household_register.csv").fillna("")
    if asha_id:
        hh_df = hh_df[hh_df["asha_id"] == asha_id]
        mem_df = mem_df[mem_df["asha_id"] == asha_id]

    protocols_list = [
        {
            "section_id": sec_id,
            "category": sec_id.split("-")[0],
            "title": data["title"],
            "body": data["text"],
            "keywords": data.get("keywords", ""),
        }
        for sec_id, data in SECTIONS.items()
    ]
    return {
        "status": "ok",
        "source": "neon_canonical_mirror",
        "counts": {
            "ashas": len(ashas_df),
            "households": len(hh_df),
            "members": len(mem_df),
            "protocols": len(protocols_list),
        },
        "ashas": ashas_df.to_dict(orient="records"),
        "households": hh_df.to_dict(orient="records"),
        "members": mem_df.to_dict(orient="records"),
        "protocols": protocols_list,
    }

