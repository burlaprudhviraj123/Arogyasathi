"""Tests for FastAPI Edge Sync Gateway."""

import pytest
from fastapi.testclient import TestClient
from arogyasathi.server import app

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["protocols_loaded"] == 107


def test_query_danger_sign_escalation():
    payload = {
        "query": "bachche ko bukhar hai aur jhatke aa rahe hai",
        "worker_id": "ASHA-RRR5"
    }
    response = client.post("/api/v1/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["escalate"] is True
    assert "ESC-5.1" in data["cited_section_ids"]


def test_query_out_of_scope_refusal():
    payload = {
        "query": "2 saal ke bachche ko kaun sa cough syrup du",
        "worker_id": "ASHA-RRR5"
    }
    response = client.post("/api/v1/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["refused"] is True
    assert "SCP-1.2" in data["cited_section_ids"]


def test_query_routine_protocol():
    payload = {
        "query": "dast me kitna ors aur zinc dena chahiye",
        "worker_id": "ASHA-RRR5"
    }
    response = client.post("/api/v1/query", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["escalate"] is False
    assert data["refused"] is False
    assert len(data["cited_section_ids"]) > 0


def test_sync_visit_outcomes():
    payload = {
        "worker_id": "ASHA-RRR5",
        "outcomes": [
            {
                "outcome_id": "OUT_TEST_001",
                "household_id": "HH-014Q8O",
                "member_id": "M-5LP6DSXF",
                "member_name": "Rajesh Manjhi",
                "due_item": "BP-followup",
                "status": "COMPLETED",
                "notes": "BP checked 120/80 normal",
                "timestamp": 1730000000000
            }
        ]
    }
    response = client.post("/api/v1/sync/visit_outcomes", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "SUCCESS"
    assert data["synced_count"] == 1

    # Verify listing
    res_list = client.get("/api/v1/sync/outcomes")
    assert res_list.status_code == 200
    assert res_list.json()["total_synced"] >= 1


def test_daily_visits_endpoint():
    response = client.get("/api/v1/visits/today?village=Belwadi%20Tola")
    assert response.status_code == 200
    data = response.json()
    assert "total_visits" in data
    assert data["total_visits"] > 0


def test_protocols_endpoint():
    response = client.get("/api/v1/protocols?category=ANC")
    assert response.status_code == 200
    data = response.json()
    assert data["count"] > 0
    for p in data["protocols"]:
        assert p["section_id"].startswith("ANC")


def test_root_and_slides_endpoints():
    res_root = client.get("/")
    assert res_root.status_code == 200
    assert "text/html" in res_root.headers.get("content-type", "")
    assert "आरोग्यसाथी" in res_root.text

    res_slides = client.get("/slides")
    assert res_slides.status_code == 200
    assert "text/html" in res_slides.headers.get("content-type", "")
    assert "Arogyasathi" in res_slides.text
