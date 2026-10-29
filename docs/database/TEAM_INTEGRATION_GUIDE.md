# Arogyasathi — Central Neon PostgreSQL & Team Collaboration Architecture

> **Purpose:** Align the entire team around a single, authoritative cloud PostgreSQL database on **Neon**, ensuring that all teammates build the online portals (Medical Officer, Supervisor, Central Admin, and FastAPI server) against the **exact same table structures, constraints, and attributes** used by the offline Android app.

---

## 1. Team Ownership & Architecture Division

As established in our system architecture, the team divides cleanly between **Offline Edge (Android)** and **Online Cloud (FastAPI + Dashboards)**:

```
                                  NEON POSTGRESQL (Central Cloud DB)
                         postgresql://[user]:[password]@[endpoint].neon.tech/neondb
                                                      │
         ┌───────────────────────────┬────────────────┴───────────────────────────┬───────────────────────────┐
         ▼                           ▼                                            ▼                           ▼
┌──────────────────┐       ┌────────────────────────┐                   ┌────────────────────┐      ┌────────────────────┐
│   ROLE B (YOU)   │       │  ROLE A & C (TEAMMATE) │                   │ TEAMMATE (ONLINE)  │      │ TEAMMATE (ONLINE)  │
│  Offline Android │       │  FastAPI Sync Server   │                   │   Medical Officer  │      │   PHC Supervisor   │
│  (Kotlin/Compose)│       │  & Twilio IVR Call API │                   │  (Doctor Portal)   │      │ (Analytics Portal) │
│                  │       │                        │                   │                    │      │                    │
│ Local SQLite DB  │       │ Receives offline syncs │                   │ Queries live table:│      │ Queries live table:│
│ 100% Offline Ops │──────▶│ POST /api/v1/sync      │──────────────────▶│ danger_escalations │      │   visit_outcomes   │
│ Store & Forward  │ (sync)│ Writes to Neon Postgres│                   │ Acknowledges 108   │      │ Tracks ASHA beat   │
└──────────────────┘       └────────────────────────┘                   └────────────────────┘      └────────────────────┘
```

| Teammate | Assigned Role | Primary Stack | Main Neon Tables Accessed |
| :--- | :--- | :--- | :--- |
| **You (Prudhvi)** | **Android Offline Lead** | Kotlin + Jetpack Compose + Local SQLite | *Owns offline client*; syncs `visit_outcomes` to Neon via FastAPI gateway when online. |
| **Teammate 1** | **Medical Officer (Doctor Portal)** | Next.js / React / Python | Reads `danger_escalations`, `members`, `protocols`; updates triage status (`DOCTOR_ACKNOWLEDGED`, `AMBULANCE_DISPATCHED`). |
| **Teammate 2** | **Supervisor (PHC Portal)** | Next.js / React / Python | Reads `visit_outcomes`, `ashas`, `households`; monitors visit completion, high-risk pregnancies, SAM malnutrition. |
| **Teammate 3** | **Backend & Admin Lead** | FastAPI + Twilio + Postgres | Owns `users` auth, `feedback_queue` for Laya fine-tuning, and Twilio IVR voice calls for keypad phones. |

---

## 2. Neon Database Setup (3 Steps)

### Step 1: Create Free Project on Neon
1. Log in to [neon.tech](https://neon.tech) and create a project named **`arogyasathi-db`**.
2. Go to **Project Settings** $\rightarrow$ **Collaborators / Members** $\rightarrow$ Invite all teammates by email.
   * *Everyone now has access to the database, branching, and query editor!*

### Step 2: Run the Schema & Seed Chandrapuri Datasets
In your terminal, run the automated seed script with your Neon connection string:

```bash
# Install psycopg2 if not already present
pip install psycopg2-binary

# Run the 1-click seeder
python scripts/seed_neon.py --db-url "postgresql://<user>:<password>@<endpoint>.neon.tech/neondb?sslmode=require"
```

This automatically:
1. Creates all 8 tables, indexes, and RBAC constraints from [`docs/database/NEON_POSTGRES_SCHEMA.sql`](file:///Users/burlaprudhviraj/Projects/Arogyasathi/docs/database/NEON_POSTGRES_SCHEMA.sql).
2. Seeds all **5 ASHAs** (`ashas.csv`).
3. Seeds all **500 Households** (`households.csv`).
4. Seeds all **2,234 Household Members** (`household_register.csv`).
5. Seeds all **107 Leaf Protocol Sections** from the medical corpus.
6. Seeds **Pre-configured Team Collaboration Accounts** (Doctor, Supervisor, Admin).
7. Seeds **Sample Emergency Escalations** for the Doctor portal to test immediately.

---

## 3. Authoritative Schema Specification for Teammates

### A. `users` (Team Collaboration & RBAC)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ASHA', 'SUPERVISOR', 'MEDICAL_OFFICER', 'ADMIN')),
    assigned_asha_id VARCHAR(50),
    assigned_facility VARCHAR(255) DEFAULT 'Chandrapuri CHC',
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```
*Pre-seeded logins for teammates:*
* `doctor.chc@arogyasathi.org` (Role: `MEDICAL_OFFICER`)
* `supervisor.phc@arogyasathi.org` (Role: `SUPERVISOR`)
* `admin@arogyasathi.org` (Role: `ADMIN`)
* `asha.kalavati@arogyasathi.org` (Role: `ASHA`, ID: `ASHA-RRR5`)

---

### B. `danger_escalations` (For the Medical Officer / Doctor Teammate)
Whenever an ASHA in the field encounters an immediate danger sign (e.g. postpartum hemorrhage, infant convulsions, severe hypertension crisis), a record is inserted here:

```sql
CREATE TABLE danger_escalations (
    escalation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asha_id VARCHAR(50) REFERENCES ashas(asha_id),
    member_id VARCHAR(50) REFERENCES members(member_id),
    patient_name VARCHAR(255),
    village VARCHAR(100),
    query_text TEXT NOT NULL,
    danger_signs TEXT NOT NULL,
    cited_section VARCHAR(50) REFERENCES protocols(section_id),
    urgency_level VARCHAR(50) DEFAULT 'CRITICAL_108',
    status VARCHAR(50) DEFAULT 'PENDING_TRIAGE' 
        CHECK (status IN ('PENDING_TRIAGE', 'DOCTOR_ACKNOWLEDGED', 'AMBULANCE_DISPATCHED', 'ADMITTED_CHC', 'RESOLVED')),
    doctor_notes TEXT,
    acknowledged_by VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

#### Sample Query for Doctor Portal:
```sql
-- Show all active emergency referrals needing triage
SELECT escalation_id, patient_name, village, danger_signs, cited_section, created_at
FROM danger_escalations
WHERE status = 'PENDING_TRIAGE'
ORDER BY created_at DESC;
```

---

### C. `visit_outcomes` (For the PHC Supervisor Teammate)
Stores all visit logs synced from the offline Android apps:

```sql
CREATE TABLE visit_outcomes (
    outcome_id VARCHAR(100) PRIMARY KEY,
    household_id VARCHAR(50) REFERENCES households(household_id),
    member_id VARCHAR(50) REFERENCES members(member_id),
    member_name VARCHAR(255),
    asha_id VARCHAR(50) REFERENCES ashas(asha_id),
    due_item VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('COMPLETED', 'REFUSED', 'RESCHEDULED', 'PATIENT_ABSENT')),
    notes TEXT,
    recorded_at_epoch BIGINT NOT NULL,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);
```

#### Sample Query for Supervisor Dashboard:
```sql
-- Daily completion stats per ASHA worker
SELECT 
    a.asha_name,
    a.villages,
    COUNT(vo.outcome_id) FILTER (WHERE vo.status = 'COMPLETED') AS completed_today,
    COUNT(vo.outcome_id) FILTER (WHERE vo.status != 'COMPLETED') AS pending_or_missed
FROM ashas a
LEFT JOIN visit_outcomes vo ON a.asha_id = vo.asha_id
GROUP BY a.asha_id, a.asha_name, a.villages;
```

---

### D. `feedback_queue` (For Model Fine-Tuning & Admin)
Implements the continuous improvement loop from our architecture document:

```sql
CREATE TABLE feedback_queue (
    feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_text TEXT NOT NULL,
    model_mode VARCHAR(20) DEFAULT 'OFFLINE' CHECK (model_mode IN ('OFFLINE', 'ONLINE', 'CALL_IVR')),
    predicted_intent VARCHAR(100),
    predicted_citation VARCHAR(50),
    corrected_intent VARCHAR(100),
    corrected_citation VARCHAR(50),
    reviewer_role VARCHAR(50),
    reviewer_email VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING_REVIEW' CHECK (status IN ('PENDING_REVIEW', 'APPROVED_FOR_TRAINING', 'REJECTED')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 4. Teammate Quick-Start Checklist

1. [ ] **Join Neon Project**: Accept the email invitation to the `arogyasathi-db` project on Neon.
2. [ ] **Get Database URL**: Copy the connection string from Neon:
   ```env
   DATABASE_URL=postgresql://user:pass@ep-xyz.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
3. [ ] **Doctor Portal Dev**: Connect your frontend to query `SELECT * FROM danger_escalations WHERE status = 'PENDING_TRIAGE'`.
4. [ ] **Supervisor Portal Dev**: Connect your frontend to query `SELECT * FROM visit_outcomes` and join with `ashas` and `members`.
5. [ ] **FastAPI Backend Dev**: Use [`FULL_CODE/arogyasathi/server.py`](file:///Users/burlaprudhviraj/Projects/Arogyasathi/FULL_CODE/arogyasathi/server.py) to sync incoming Android outbox arrays directly into `visit_outcomes`.

