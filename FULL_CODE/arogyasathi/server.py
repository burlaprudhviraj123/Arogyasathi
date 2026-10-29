"""FastAPI Server Gateway for Arogyasathi.

Provides:
1. Online / Sub-Centre Sync Gateway for deferred synchronization of visit outcomes.
2. Clinical Protocol Q&A Endpoint powered by JEV safety gate and Laya retrieval.
3. Daily Prioritized Visit List generation for Chandrapuri Block.
4. Leaf Protocol Corpus query endpoints.
"""

from __future__ import annotations
import time
from typing import List, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .protocols import load_protocol_sections
from .qa_engine import QAEngine
from .visit_engine import generate_visit_list
import pandas as pd
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parents[2] / "H5_Frontline_Health_Worker_Companion" / "data"

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


@app.post("/api/v1/sync/visit_outcomes", response_model=SyncResponse)
def sync_visit_outcomes(req: SyncRequest):
    global SYNCED_OUTCOMES
    count = 0
    now_ts = int(time.time() * 1000)

    for item in req.outcomes:
        record = item.model_dump()
        record["synced_at"] = now_ts
        record["worker_id"] = req.worker_id
        SYNCED_OUTCOMES.append(record)
        count += 1

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
            "body": data["text"]
        })
    return {
        "count": len(out),
        "protocols": out
    }
