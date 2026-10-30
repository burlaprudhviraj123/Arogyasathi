-- =============================================================================
-- AROGYASATHI (आरोग्यसाथी) — NEON POSTGRESQL PRODUCTION SCHEMA
-- Chandrapuri Block Frontline Healthcare Ecosystem (Track H5 / Problem 10)
-- Compatible with Neon Serverless Postgres, SQLAlchemy, and Prisma
-- =============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Clean previous instances if needed (optional)
-- DROP TABLE IF EXISTS feedback_queue CASCADE;
-- DROP TABLE IF EXISTS danger_escalations CASCADE;
-- DROP TABLE IF EXISTS visit_outcomes CASCADE;
-- DROP TABLE IF EXISTS protocols CASCADE;
-- DROP TABLE IF EXISTS members CASCADE;
-- DROP TABLE IF EXISTS households CASCADE;
-- DROP TABLE IF EXISTS ashas CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;

-- -----------------------------------------------------------------------------
-- 1. USERS & RBAC ROLES (Collaboration Accounts for Teammates)
-- Roles: 'ASHA', 'SUPERVISOR', 'MEDICAL_OFFICER', 'ADMIN'
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ASHA', 'SUPERVISOR', 'MEDICAL_OFFICER', 'ADMIN')),
    assigned_asha_id VARCHAR(50),
    assigned_facility VARCHAR(255) DEFAULT 'Chandrapuri CHC',
    sub_centre_id VARCHAR(50),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- -----------------------------------------------------------------------------
-- 2. ASHAS (Frontline Workers in Chandrapuri Block)
-- Primary Auth: Registered Phone Number + OTP (App) / Caller ID + DTMF (Call IVR)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ashas (
    asha_id VARCHAR(50) PRIMARY KEY,
    asha_name VARCHAR(255) NOT NULL,
    villages TEXT NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    sub_centre VARCHAR(255),
    sub_centre_id VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ashas_phone ON ashas(phone);

-- -----------------------------------------------------------------------------
-- 3. HOUSEHOLDS (500 Households across Chandrapuri Villages)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS households (
    household_id VARCHAR(50) PRIMARY KEY,
    village VARCHAR(100) NOT NULL,
    asha_id VARCHAR(50) REFERENCES ashas(asha_id) ON DELETE SET NULL,
    house_no VARCHAR(50),
    head_member_id VARCHAR(50),
    head_name VARCHAR(255),
    members_count INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_households_village ON households(village);
CREATE INDEX IF NOT EXISTS idx_households_asha ON households(asha_id);

-- -----------------------------------------------------------------------------
-- 4. MEMBERS (2,234 Household Members — Register Source of Truth)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS members (
    member_id VARCHAR(50) PRIMARY KEY,
    household_id VARCHAR(50) REFERENCES households(household_id) ON DELETE CASCADE,
    village VARCHAR(100),
    asha_id VARCHAR(50) REFERENCES ashas(asha_id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    relation VARCHAR(50),
    sex VARCHAR(10),
    dob VARCHAR(20),
    pregnant VARCHAR(10),
    lmp_date VARCHAR(20),
    edd VARCHAR(20),
    anc_visit_dates TEXT,
    last_anc_bp VARCHAR(50),
    last_hb_gdl VARCHAR(50),
    td_booster_eligible VARCHAR(10),
    td_doses VARCHAR(50),
    place_of_delivery VARCHAR(100),
    birth_weight_kg VARCHAR(20),
    hbnc_visits_done VARCHAR(50),
    child_vaccines TEXT,
    last_muac_cm VARCHAR(20),
    last_muac_date VARCHAR(20),
    bilateral_pitting_oedema VARCHAR(10),
    ncd_condition VARCHAR(100),
    last_bp_check_date VARCHAR(20),
    last_bp_reading VARCHAR(50),
    last_sugar_check_date VARCHAR(20),
    last_rbs_mg_dl VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_members_household ON members(household_id);
CREATE INDEX IF NOT EXISTS idx_members_asha ON members(asha_id);
CREATE INDEX IF NOT EXISTS idx_members_village ON members(village);
CREATE INDEX IF NOT EXISTS idx_members_pregnant ON members(pregnant) WHERE pregnant = 'Y';
CREATE INDEX IF NOT EXISTS idx_members_ncd ON members(ncd_condition) WHERE ncd_condition IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 5. PROTOCOLS (107 Official Leaf Protocol Sections)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS protocols (
    section_id VARCHAR(50) PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_protocols_category ON protocols(category);

-- -----------------------------------------------------------------------------
-- 6. VISIT OUTCOMES (Synced from Offline Android App to Central DB)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS visit_outcomes (
    outcome_id VARCHAR(100) PRIMARY KEY,
    household_id VARCHAR(50) REFERENCES households(household_id) ON DELETE CASCADE,
    member_id VARCHAR(50) REFERENCES members(member_id) ON DELETE CASCADE,
    member_name VARCHAR(255),
    asha_id VARCHAR(50) REFERENCES ashas(asha_id) ON DELETE SET NULL,
    due_item VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('COMPLETED', 'REFUSED', 'RESCHEDULED', 'PATIENT_ABSENT')),
    notes TEXT,
    recorded_at_epoch BIGINT NOT NULL,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outcomes_asha ON visit_outcomes(asha_id);
CREATE INDEX IF NOT EXISTS idx_outcomes_member ON visit_outcomes(member_id);
CREATE INDEX IF NOT EXISTS idx_outcomes_status ON visit_outcomes(status);
CREATE INDEX IF NOT EXISTS idx_outcomes_synced ON visit_outcomes(synced_at DESC);

-- -----------------------------------------------------------------------------
-- 7. DANGER ESCALATIONS (Real-Time Referral Alerts for Medical Officers / Doctors)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS danger_escalations (
    escalation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asha_id VARCHAR(50) REFERENCES ashas(asha_id) ON DELETE SET NULL,
    member_id VARCHAR(50) REFERENCES members(member_id) ON DELETE SET NULL,
    patient_name VARCHAR(255),
    village VARCHAR(100),
    query_text TEXT NOT NULL,
    danger_signs TEXT NOT NULL,
    cited_section VARCHAR(50) REFERENCES protocols(section_id) ON DELETE SET NULL,
    urgency_level VARCHAR(50) DEFAULT 'CRITICAL_108',
    status VARCHAR(50) DEFAULT 'PENDING_TRIAGE' CHECK (status IN ('PENDING_TRIAGE', 'DOCTOR_ACKNOWLEDGED', 'AMBULANCE_DISPATCHED', 'ADMITTED_CHC', 'RESOLVED')),
    doctor_notes TEXT,
    acknowledged_by VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_escalations_status ON danger_escalations(status);
CREATE INDEX IF NOT EXISTS idx_escalations_asha ON danger_escalations(asha_id);
CREATE INDEX IF NOT EXISTS idx_escalations_created ON danger_escalations(created_at DESC);

-- -----------------------------------------------------------------------------
-- 8. FEEDBACK QUEUE (Supervisor & Doctor Feedback for Laya Fine-Tuning)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedback_queue (
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

CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback_queue(status);

-- -----------------------------------------------------------------------------
-- 9. PRE-SEEDED TEAM COLLABORATION ACCOUNTS
-- Pre-configured accounts for teammates working on Doctor, Supervisor, and Admin
-- -----------------------------------------------------------------------------
INSERT INTO users (email, full_name, role, assigned_facility, phone)
VALUES 
    ('doctor.chc@arogyasathi.org', 'Dr. Anand Sharma (Medical Officer)', 'MEDICAL_OFFICER', 'Chandrapuri CHC', '+91-9876543210'),
    ('supervisor.phc@arogyasathi.org', 'Sunita Rao (PHC Health Supervisor)', 'SUPERVISOR', 'Belwadi PHC', '+91-9876543211'),
    ('admin@arogyasathi.org', 'Central Health Tech Administrator', 'ADMIN', 'District Health Office', '+91-9876543212')
ON CONFLICT (email) DO NOTHING;

-- Pre-seed ASHA worker accounts matching ashas.csv
INSERT INTO users (email, full_name, role, assigned_asha_id, assigned_facility, sub_centre_id, phone)
VALUES 
    ('asha.kalavati@arogyasathi.org', 'Kalavati Kumari', 'ASHA', 'ASHA-RRR5', 'Belwadi Tola Ayushman Arogya Mandir (HWC)', 'SC-BWT-101', '+91-9876543220'),
    ('asha.sunaina@arogyasathi.org', 'Sunaina Devi', 'ASHA', 'ASHA-096P', 'Amrapur Health & Wellness Centre', 'SC-AMP-102', '+91-9876543221'),
    ('asha.rinku@arogyasathi.org', 'Rinku Devi', 'ASHA', 'ASHA-YFK5', 'Kesarganj Health & Wellness Centre', 'SC-KSG-103', '+91-9876543222'),
    ('asha.shabnam@arogyasathi.org', 'Shabnam Khatoon', 'ASHA', 'ASHA-L9R8', 'Dhanpura Health & Wellness Centre', 'SC-DHP-104', '+91-9876543223'),
    ('asha.malti@arogyasathi.org', 'Malti Devi', 'ASHA', 'ASHA-KQXR', 'Navagaon Khurd Health & Wellness Centre', 'SC-NGK-105', '+91-9876543224')
ON CONFLICT (email) DO UPDATE SET
    assigned_facility = EXCLUDED.assigned_facility,
    sub_centre_id = EXCLUDED.sub_centre_id;

