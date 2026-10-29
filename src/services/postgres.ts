import { neon } from "@neondatabase/serverless";
import { config } from "../config.js";

if (!config.postgres.url) {
  console.warn("WARNING: DATABASE_URL is not set. Postgres queries will fail.");
}

export const sql = neon(config.postgres.url);

export async function testPostgresConnection() {
  const result = await sql`SELECT NOW() as current_time, current_database() as db_name, version() as pg_version;`;
  return result[0];
}

export async function initPostgresSchema() {
  // Create core tables if they do not exist
  await sql`
    CREATE TABLE IF NOT EXISTS ashas (
      asha_id VARCHAR(50) PRIMARY KEY,
      asha_name VARCHAR(255) NOT NULL,
      villages TEXT NOT NULL,
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS protocols (
      section_id VARCHAR(50) PRIMARY KEY,
      category VARCHAR(50) NOT NULL,
      title TEXT NOT NULL,
      text TEXT NOT NULL,
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS visit_outcomes (
      outcome_id VARCHAR(100) PRIMARY KEY,
      household_id VARCHAR(50),
      member_id VARCHAR(50),
      member_name VARCHAR(255),
      asha_id VARCHAR(50),
      due_item VARCHAR(100) NOT NULL,
      status VARCHAR(50) NOT NULL,
      notes TEXT,
      recorded_at_epoch BIGINT NOT NULL,
      synced_at TIMESTAMPTZ DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS danger_escalations (
      escalation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      asha_id VARCHAR(50),
      member_id VARCHAR(50),
      patient_name VARCHAR(255),
      village VARCHAR(100),
      query_text TEXT NOT NULL,
      danger_signs TEXT NOT NULL,
      cited_section VARCHAR(50),
      urgency_level VARCHAR(50) DEFAULT 'CRITICAL_108',
      status VARCHAR(50) DEFAULT 'PENDING_TRIAGE',
      created_at TIMESTAMPTZ DEFAULT NOW()
    );
  `;

  return { success: true, message: "Arogyasathi core schema verified on Neon Lakebase Postgres" };
}

export async function getEmergencyEscalations() {
  return await sql`
    SELECT escalation_id, asha_id, patient_name, village, query_text, danger_signs, cited_section, urgency_level, status, created_at
    FROM danger_escalations
    ORDER BY created_at DESC
    LIMIT 20;
  `;
}
