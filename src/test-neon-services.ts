import { testPostgresConnection, initPostgresSchema } from "./services/postgres.js";
import { testNeonAuth } from "./services/auth.js";
import { testObjectStorage } from "./services/storage.js";
import { testRealtimeConnection } from "./services/realtime.js";

async function runEndToEndTests() {
  console.log("=================================================================");
  console.log(" 🧪 TESTING ALL WIRED NEON SERVICES (END-TO-END)");
  console.log("=================================================================\n");

  let allPassed = true;

  // 1. Lakebase Postgres
  try {
    console.log("1. Testing Lakebase Postgres connection & schema...");
    const dbInfo = await testPostgresConnection();
    console.log(`   ✓ Connected to Postgres DB: ${(dbInfo as { db_name: string }).db_name}`);
    console.log(`   ✓ Server Timestamp: ${(dbInfo as { current_time: string }).current_time}`);
    const schema = await initPostgresSchema();
    console.log(`   ✓ Schema: ${schema.message}`);
  } catch (err) {
    console.error("   ✕ Postgres Failed:", err);
    allPassed = false;
  }

  console.log("");

  // 2. Neon Auth
  try {
    console.log("2. Testing Neon Auth (Managed Better Auth)...");
    const auth = await testNeonAuth();
    console.log(`   ✓ Base URL Reachable: ${auth.baseUrl} (HTTP ${auth.baseStatus})`);
    console.log(`   ✓ JWKS Keys Retrieved: ${auth.jwksKeyCount} public keys from ${auth.jwksUrl}`);
  } catch (err) {
    console.error("   ✕ Neon Auth Failed:", err);
    allPassed = false;
  }

  console.log("");

  // 3. Neon Object Storage
  try {
    console.log("3. Testing Neon Object Storage (S3 / assets bucket)...");
    const storage = await testObjectStorage();
    console.log(`   ✓ PutObject & GetObject verified on bucket: ${storage.bucket}`);
    console.log(`   ✓ Uploaded Key: ${storage.testKey}`);
    console.log(`   ✓ Retrieved Payload: ${storage.retrievedContent}`);
  } catch (err) {
    console.error("   ✕ Object Storage Failed:", err);
    allPassed = false;
  }

  console.log("");

  // 4. Neon Realtime
  try {
    console.log("4. Testing Neon Realtime (WebSocket endpoint)...");
    const rt = await testRealtimeConnection();
    console.log(`   ✓ Realtime WebSocket Endpoint Reachable: ${rt.url}`);
    console.log(`   ✓ Connected: ${rt.connected}`);
  } catch (err) {
    console.error("   ✕ Realtime Failed:", err);
    allPassed = false;
  }

  console.log("\n=================================================================");
  if (allPassed) {
    console.log(" 🎉 ALL 4 NEON SERVICES (POSTGRES, AUTH, STORAGE, REALTIME) PASSED!");
  } else {
    console.error(" ⚠️ SOME SERVICES FAILED.");
    process.exit(1);
  }
  console.log("=================================================================");
}

runEndToEndTests().catch((err) => {
  console.error("Fatal test runner error:", err);
  process.exit(1);
});

