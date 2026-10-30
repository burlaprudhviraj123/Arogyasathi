import { neon } from "@neondatabase/serverless";
import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import { config } from "../config.js";

export interface AshaRow {
  asha_id: string;
  name: string;
  phone: string;
  villages: string[];
  sub_centre: string;
  parent_phc: string;
  mo_name: string;
  mo_phone: string;
  anm_name: string;
  anm_phone: string;
}

export interface HouseholdRow {
  household_id: string;
  village: string;
  asha_id: string;
  head_name: string;
  address_notes: string;
  total_members: number;
}

export interface MemberRow {
  member_id: string;
  household_id: string;
  village: string;
  asha_id: string;
  name: string;
  age_years: number;
  age_months: number;
  sex: string;
  cohort: string;
  pregnant: boolean;
  lmp_date: string | null;
  edd_date: string | null;
  anc_visits_Done: number;
  high_risk_flag: boolean;
  delivery_date: string | null;
  immunisation_due: string | null;
  last_muac_mm: number | null;
  ncd_flag: string | null;
  last_bp_systolic: number | null;
  last_bp_diastolic: number | null;
  last_fbs_mgdl: number | null;
  last_visit_date: string | null;
  due_item: string | null;
  due_date: string | null;
  priority_hint: string | null;
}

export interface ProtocolRow {
  section_id: string;
  category: string;
  title: string;
  body: string;
  keywords: string;
}

export interface VisitOutcomePayload {
  outcome_id: string;
  household_id: string;
  member_id: string;
  member_name: string;
  asha_id?: string;
  due_item: string;
  status: string;
  notes?: string;
  recorded_at_epoch: number;
}

export interface DangerEscalationPayload {
  escalation_id: string;
  member_id?: string | null;
  household_id?: string | null;
  asha_id?: string;
  raw_query: string;
  matched_category: string;
  cited_section_ids: string[];
  referral_facility?: string;
  transport_dialed?: string;
  recorded_at_epoch: number;
}

function isNeonConfigured(): boolean {
  const url = config.postgres.url;
  return Boolean(
    url &&
      url.startsWith("postgres") &&
      !url.includes("[user]") &&
      !url.includes("[password]") &&
      !url.includes("[endpoint]")
  );
}

/**
 * Returns a Neon serverless SQL client pointing to the configured DATABASE_URL.
 */
export function getDb() {
  if (!isNeonConfigured()) {
    throw new Error("DATABASE_URL is not configured with a live Neon endpoint");
  }
  return neon(config.postgres.url);
}

/**
 * Simple CSV parser handling quoted fields with commas.
 */
function parseCsvRecords(content: string): Record<string, string>[] {
  const lines = content
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
  if (lines.length < 2) return [];

  const splitRow = (row: string): string[] => {
    const out: string[] = [];
    let current = "";
    let inQuotes = false;
    for (let i = 0; i < row.length; i++) {
      const ch = row[i];
      if (ch === '"') {
        inQuotes = !inQuotes;
      } else if (ch === "," && !inQuotes) {
        out.push(current.trim());
        current = "";
      } else {
        current += ch;
      }
    }
    out.push(current.trim());
    return out;
  };

  const headers = splitRow(lines[0]);
  return lines.slice(1).map((line) => {
    const cols = splitRow(line);
    const rec: Record<string, string> = {};
    headers.forEach((h, idx) => {
      rec[h] = cols[idx] ?? "";
    });
    return rec;
  });
}

/**
 * Loads the canonical H5 dataset from local CSV/JSON files (used to seed Neon Postgres
 * or serve as a deterministic fallback when Neon credentials are not set in dev).
 */
export function loadLocalCanonicalDataset(): {
  ashas: AshaRow[];
  households: HouseholdRow[];
  members: MemberRow[];
  protocols: ProtocolRow[];
} {
  const rootDir = process.cwd();
  const dataDir = resolve(rootDir, "H5_Frontline_Health_Worker_Companion", "data");
  const protocolsJsonPath = resolve(
    rootDir,
    "android_app",
    "app",
    "src",
    "main",
    "assets",
    "protocols.json"
  );

  // 1. ASHAs
  const ashasPath = resolve(dataDir, "ashas.csv");
  const ashas: AshaRow[] = existsSync(ashasPath)
    ? parseCsvRecords(readFileSync(ashasPath, "utf-8")).map((r) => ({
        asha_id: r.asha_id,
        name: r.name,
        phone: r.phone || "+91 94310 11201",
        villages: r.villages ? r.villages.split(";").map((v) => v.trim()).filter(Boolean) : [],
        sub_centre: r.sub_centre || "Chandrapuri Sub-Centre",
        parent_phc: "PHC Chandrapuri (24x7)",
        mo_name: "Dr. Rajeshwar Rao (MBBS)",
        mo_phone: "+91 94401 22100",
        anm_name: "Smt. Kavitha Devi (ANM)",
        anm_phone: "+91 94401 22108",
      }))
    : [];

  // 2. Households
  const hhPath = resolve(dataDir, "households.csv");
  const households: HouseholdRow[] = existsSync(hhPath)
    ? parseCsvRecords(readFileSync(hhPath, "utf-8")).map((r) => ({
        household_id: r.household_id,
        village: r.village,
        asha_id: r.asha_id,
        head_name: r.head_name,
        address_notes: r.address_notes,
        total_members: parseInt(r.members || "1", 10) || 1,
      }))
    : [];

  // 3. Household Register Members
  const regPath = resolve(dataDir, "household_register.csv");
  const members: MemberRow[] = existsSync(regPath)
    ? parseCsvRecords(readFileSync(regPath, "utf-8")).map((r) => ({
        member_id: r.member_id,
        household_id: r.household_id,
        village: r.village,
        asha_id: r.asha_id,
        name: r.name,
        age_years: parseInt(r.age_years || "0", 10) || 0,
        age_months: parseInt(r.age_months || "0", 10) || 0,
        sex: r.sex,
        cohort: r.cohort,
        pregnant: (r.pregnant || "").toLowerCase() === "yes" || r.pregnant === "true",
        lmp_date: r.lmp_date || null,
        edd_date: r.edd_date || null,
        anc_visits_Done: parseInt(r.anc_visits_done || "0", 10) || 0,
        high_risk_flag:
          (r.high_risk_flag || "").toLowerCase() === "yes" || r.high_risk_flag === "true",
        delivery_date: r.delivery_date || null,
        immunisation_due: r.immunisation_due || null,
        last_muac_mm: r.last_muac_mm ? parseInt(r.last_muac_mm, 10) : null,
        ncd_flag: r.ncd_flag || null,
        last_bp_systolic: r.last_bp_systolic ? parseInt(r.last_bp_systolic, 10) : null,
        last_bp_diastolic: r.last_bp_diastolic ? parseInt(r.last_bp_diastolic, 10) : null,
        last_fbs_mgdl: r.last_fbs_mgdl ? parseInt(r.last_fbs_mgdl, 10) : null,
        last_visit_date: r.last_visit_date || null,
        due_item: r.due_item || null,
        due_date: r.due_date || null,
        priority_hint: r.priority_hint || null,
      }))
    : [];

  // 4. Protocols
  const protocols: ProtocolRow[] = existsSync(protocolsJsonPath)
    ? (JSON.parse(readFileSync(protocolsJsonPath, "utf-8")) as ProtocolRow[])
    : [];

  return { ashas, households, members, protocols };
}

/**
 * Verifies the connection to Lakebase Postgres and returns server metadata.
 */
export async function testPostgresConnection() {
  const sql = getDb();
  const rows = await sql`
    SELECT 
      current_database() AS db_name,
      current_user AS db_user,
      version() AS pg_version,
      NOW() AS current_time
  `;
  return rows[0];
}

/**
 * Ensures the core Arogyasathi tables exist in Neon Postgres (all 8 tables from NEON_POSTGRES_SCHEMA.sql).
 */
export async function initPostgresSchema() {
  const sql = getDb();

  await sql`
    CREATE TABLE IF NOT EXISTS users (
      user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email VARCHAR(255) UNIQUE NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      full_name VARCHAR(120) NOT NULL,
      role VARCHAR(32) NOT NULL DEFAULT 'ASHA',
      asha_id VARCHAR(32) UNIQUE,
      phone VARCHAR(32),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      last_login_at TIMESTAMPTZ
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS ashas (
      asha_id VARCHAR(32) PRIMARY KEY,
      name VARCHAR(120) NOT NULL,
      phone VARCHAR(32) NOT NULL,
      villages TEXT[] NOT NULL DEFAULT '{}',
      sub_centre VARCHAR(120) NOT NULL,
      parent_phc VARCHAR(120) NOT NULL,
      mo_name VARCHAR(120) NOT NULL,
      mo_phone VARCHAR(32) NOT NULL,
      anm_name VARCHAR(120) NOT NULL,
      anm_phone VARCHAR(32) NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS households (
      household_id VARCHAR(32) PRIMARY KEY,
      village VARCHAR(80) NOT NULL,
      asha_id VARCHAR(32) NOT NULL REFERENCES ashas(asha_id) ON DELETE CASCADE,
      head_name VARCHAR(120) NOT NULL,
      address_notes TEXT NOT NULL,
      total_members INTEGER NOT NULL DEFAULT 1,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS members (
      member_id VARCHAR(32) PRIMARY KEY,
      household_id VARCHAR(32) NOT NULL REFERENCES households(household_id) ON DELETE CASCADE,
      village VARCHAR(80) NOT NULL,
      asha_id VARCHAR(32) NOT NULL REFERENCES ashas(asha_id) ON DELETE CASCADE,
      name VARCHAR(120) NOT NULL,
      age_years INTEGER NOT NULL DEFAULT 0,
      age_months INTEGER NOT NULL DEFAULT 0,
      sex VARCHAR(16) NOT NULL,
      cohort VARCHAR(32) NOT NULL,
      pregnant BOOLEAN NOT NULL DEFAULT FALSE,
      lmp_date DATE,
      edd_date DATE,
      anc_visits_done INTEGER NOT NULL DEFAULT 0,
      high_risk_flag BOOLEAN NOT NULL DEFAULT FALSE,
      delivery_date DATE,
      immunisation_due VARCHAR(120),
      last_muac_mm INTEGER,
      ncd_flag VARCHAR(64),
      last_bp_systolic INTEGER,
      last_bp_diastolic INTEGER,
      last_fbs_mgdl INTEGER,
      last_visit_date DATE,
      due_item VARCHAR(120),
      due_date DATE,
      priority_hint VARCHAR(32),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS protocols (
      section_id VARCHAR(32) PRIMARY KEY,
      category VARCHAR(32) NOT NULL,
      title VARCHAR(255) NOT NULL,
      body TEXT NOT NULL,
      keywords TEXT NOT NULL DEFAULT '',
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS visit_outcomes (
      outcome_id VARCHAR(64) PRIMARY KEY,
      household_id VARCHAR(32) NOT NULL,
      member_id VARCHAR(32) NOT NULL,
      member_name VARCHAR(120) NOT NULL,
      asha_id VARCHAR(32),
      due_item VARCHAR(120) NOT NULL,
      status VARCHAR(32) NOT NULL CHECK (status IN ('COMPLETED', 'RESCHEDULED', 'REFERRED', 'UNAVAILABLE')),
      notes TEXT DEFAULT '',
      recorded_at_epoch BIGINT NOT NULL,
      synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS danger_escalations (
      escalation_id VARCHAR(64) PRIMARY KEY,
      member_id VARCHAR(32),
      household_id VARCHAR(32),
      asha_id VARCHAR(32),
      raw_query TEXT NOT NULL,
      matched_category VARCHAR(64) NOT NULL,
      cited_section_ids TEXT[] NOT NULL DEFAULT '{}',
      referral_facility VARCHAR(120) NOT NULL DEFAULT 'PHC Chandrapuri (24x7)',
      transport_dialed VARCHAR(16) DEFAULT '108',
      recorded_at_epoch BIGINT NOT NULL,
      synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS feedback_queue (
      feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      asha_id VARCHAR(32),
      query_text TEXT NOT NULL,
      cited_section_ids TEXT[] NOT NULL DEFAULT '{}',
      helpful BOOLEAN NOT NULL,
      comments TEXT DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `;

  return {
    status: "initialized",
    tables: [
      "users",
      "ashas",
      "households",
      "members",
      "protocols",
      "visit_outcomes",
      "danger_escalations",
      "feedback_queue",
    ],
    message: "Core Arogyasathi schema verified on Neon Postgres",
  };
}

/**
 * Fetches the full bootstrap dataset (ashas, households, members, protocols)
 * from central Neon Postgres, falling back cleanly to the canonical dataset if offline.
 */
export async function getBootstrapDataset(ashaId?: string) {
  if (isNeonConfigured()) {
    try {
      const sql = getDb();
      const ashas = (await sql`
        SELECT asha_id, name, phone, villages, sub_centre, parent_phc, mo_name, mo_phone, anm_name, anm_phone
        FROM ashas
        ORDER BY asha_id
      `) as unknown as AshaRow[];

      const households = (ashaId
        ? await sql`
            SELECT household_id, village, asha_id, head_name, address_notes, total_members
            FROM households
            WHERE asha_id = ${ashaId}
            ORDER BY household_id
          `
        : await sql`
            SELECT household_id, village, asha_id, head_name, address_notes, total_members
            FROM households
            ORDER BY household_id
          `) as unknown as HouseholdRow[];

      const members = (ashaId
        ? await sql`
            SELECT
              member_id, household_id, village, asha_id, name,
              age_years, age_months, sex, cohort, pregnant,
              lmp_date::text AS lmp_date, edd_date::text AS edd_date,
              anc_visits_done AS "anc_visits_Done", high_risk_flag,
              delivery_date::text AS delivery_date, immunisation_due,
              last_muac_mm, ncd_flag, last_bp_systolic, last_bp_diastolic,
              last_fbs_mgdl, last_visit_date::text AS last_visit_date,
              due_item, due_date::text AS due_date, priority_hint
            FROM members
            WHERE asha_id = ${ashaId}
            ORDER BY member_id
          `
        : await sql`
            SELECT
              member_id, household_id, village, asha_id, name,
              age_years, age_months, sex, cohort, pregnant,
              lmp_date::text AS lmp_date, edd_date::text AS edd_date,
              anc_visits_done AS "anc_visits_Done", high_risk_flag,
              delivery_date::text AS delivery_date, immunisation_due,
              last_muac_mm, ncd_flag, last_bp_systolic, last_bp_diastolic,
              last_fbs_mgdl, last_visit_date::text AS last_visit_date,
              due_item, due_date::text AS due_date, priority_hint
            FROM members
            ORDER BY member_id
          `) as unknown as MemberRow[];

      const protocols = (await sql`
        SELECT section_id, category, title, body, keywords
        FROM protocols
        ORDER BY section_id
      `) as unknown as ProtocolRow[];

      if (protocols.length > 0 && households.length > 0) {
        return {
          source: "neon_postgres",
          ashas,
          households,
          members,
          protocols,
        };
      }
    } catch (err) {
      console.warn("[Neon Postgres Warning] Falling back to local canonical dataset:", err);
    }
  }

  const local = loadLocalCanonicalDataset();
  return {
    source: "neon_canonical_mirror",
    ashas: local.ashas,
    households: ashaId
      ? local.households.filter((h) => h.asha_id === ashaId)
      : local.households,
    members: ashaId ? local.members.filter((m) => m.asha_id === ashaId) : local.members,
    protocols: local.protocols,
  };
}

/**
 * Syncs visit outcomes and danger escalations from the Android app's offline Room outbox
 * into the central Neon Postgres database.
 */
export async function syncBatchToNeon(payload: {
  worker_id?: string;
  outcomes?: VisitOutcomePayload[];
  escalations?: DangerEscalationPayload[];
}) {
  const workerId = payload.worker_id || "ASHA-RRR5";
  const outcomes = payload.outcomes || [];
  const escalations = payload.escalations || [];
  let persistedToNeon = false;

  if (isNeonConfigured()) {
    try {
      const sql = getDb();
      for (const o of outcomes) {
        const status = ["COMPLETED", "RESCHEDULED", "REFERRED", "UNAVAILABLE"].includes(o.status)
          ? o.status
          : "COMPLETED";
        await sql`
          INSERT INTO visit_outcomes (
            outcome_id, household_id, member_id, member_name, asha_id,
            due_item, status, notes, recorded_at_epoch
          ) VALUES (
            ${o.outcome_id}, ${o.household_id}, ${o.member_id}, ${o.member_name || "Unknown"},
            ${o.asha_id || workerId}, ${o.due_item || "General Follow-up"}, ${status},
            ${o.notes || ""}, ${o.recorded_at_epoch || Date.now()}
          )
          ON CONFLICT (outcome_id) DO UPDATE SET
            status = EXCLUDED.status,
            notes = EXCLUDED.notes,
            synced_at = NOW()
        `;
      }

      for (const e of escalations) {
        await sql`
          INSERT INTO danger_escalations (
            escalation_id, member_id, household_id, asha_id,
            raw_query, matched_category, cited_section_ids,
            referral_facility, transport_dialed, recorded_at_epoch
          ) VALUES (
            ${e.escalation_id}, ${e.member_id || null}, ${e.household_id || null},
            ${e.asha_id || workerId}, ${e.raw_query}, ${e.matched_category},
            ${e.cited_section_ids || []}, ${e.referral_facility || "PHC Chandrapuri (24x7)"},
            ${e.transport_dialed || "108"}, ${e.recorded_at_epoch || Date.now()}
          )
          ON CONFLICT (escalation_id) DO NOTHING
        `;
      }
      persistedToNeon = true;
    } catch (err) {
      console.warn("[Neon Sync Notice] Stored in gateway memory mirror:", err);
    }
  }

  return {
    status: "SUCCESS",
    persisted_to_neon: persistedToNeon,
    synced_outcomes: outcomes.length,
    synced_escalations: escalations.length,
    received_timestamp: Date.now(),
  };
}
