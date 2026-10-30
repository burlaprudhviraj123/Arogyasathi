import http from "http";
import { URL } from "url";
import { config } from "./config.js";
import {
  testPostgresConnection,
  initPostgresSchema,
  getBootstrapDataset,
  syncBatchToNeon,
  VisitOutcomePayload,
  DangerEscalationPayload,
} from "./services/postgres.js";
import { testNeonAuth } from "./services/auth.js";
import { testObjectStorage } from "./services/storage.js";
import { testRealtimeConnection } from "./services/realtime.js";

function readRequestBody(req: http.IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf-8")));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const pathname = parsedUrl.pathname;
  const ashaId = parsedUrl.searchParams.get("asha_id") || undefined;
  const category = parsedUrl.searchParams.get("category") || undefined;

  try {
    if (pathname === "/health" || pathname === "/api/health") {
      const dbInfo = await testPostgresConnection().catch((e) => ({ error: String(e) }));
      const authInfo = await testNeonAuth().catch((e) => ({ error: String(e) }));
      const storageInfo = await testObjectStorage().catch((e) => ({ error: String(e) }));
      const realtimeInfo = await testRealtimeConnection().catch((e) => ({ error: String(e) }));

      res.writeHead(200);
      res.end(
        JSON.stringify(
          {
            status: "ok",
            service: "Arogyasathi Cloud Gateway (Neon Integrated)",
            timestamp: new Date().toISOString(),
            services: {
              postgres: dbInfo,
              auth: authInfo,
              storage: storageInfo,
              realtime: realtimeInfo,
            },
          },
          null,
          2
        )
      );
      return;
    }

    if (pathname === "/api/init-schema" && req.method === "POST") {
      const result = await initPostgresSchema();
      res.writeHead(200);
      res.end(JSON.stringify(result, null, 2));
      return;
    }

    // Central Neon Bootstrap Endpoint for Android Room Offline Cache
    if (
      (pathname === "/api/v1/bootstrap" || pathname === "/api/bootstrap") &&
      req.method === "GET"
    ) {
      const data = await getBootstrapDataset(ashaId);
      res.writeHead(200);
      res.end(
        JSON.stringify({
          status: "ok",
          source: data.source,
          counts: {
            ashas: data.ashas.length,
            households: data.households.length,
            members: data.members.length,
            protocols: data.protocols.length,
          },
          ashas: data.ashas,
          households: data.households,
          members: data.members,
          protocols: data.protocols,
        })
      );
      return;
    }

    if (
      (pathname === "/api/v1/protocols" || pathname === "/api/protocols") &&
      req.method === "GET"
    ) {
      const data = await getBootstrapDataset();
      const filtered = category
        ? data.protocols.filter((p) => p.category.toUpperCase() === category.toUpperCase())
        : data.protocols;
      res.writeHead(200);
      res.end(
        JSON.stringify({
          status: "ok",
          source: data.source,
          count: filtered.length,
          protocols: filtered,
        })
      );
      return;
    }

    if (
      (pathname === "/api/v1/households" || pathname === "/api/households") &&
      req.method === "GET"
    ) {
      const data = await getBootstrapDataset(ashaId);
      res.writeHead(200);
      res.end(
        JSON.stringify({
          status: "ok",
          source: data.source,
          count: data.households.length,
          households: data.households,
        })
      );
      return;
    }

    if (
      (pathname === "/api/v1/members" || pathname === "/api/members") &&
      req.method === "GET"
    ) {
      const data = await getBootstrapDataset(ashaId);
      res.writeHead(200);
      res.end(
        JSON.stringify({
          status: "ok",
          source: data.source,
          count: data.members.length,
          members: data.members,
        })
      );
      return;
    }

    // Bidirectional Sync Endpoint for Android Outbox Queue -> Neon Postgres
    if (
      (pathname === "/api/v1/sync" ||
        pathname === "/api/sync" ||
        pathname === "/api/v1/sync/visit_outcomes") &&
      req.method === "POST"
    ) {
      const rawBody = await readRequestBody(req);
      const parsed = rawBody ? (JSON.parse(rawBody) as {
        worker_id?: string;
        outcomes?: VisitOutcomePayload[];
        escalations?: DangerEscalationPayload[];
      }) : {};
      const result = await syncBatchToNeon(parsed);
      res.writeHead(200);
      res.end(JSON.stringify(result));
      return;
    }

    res.writeHead(200);
    res.end(
      JSON.stringify(
        {
          name: "Arogyasathi Cloud Gateway",
          endpoints: [
            "GET  /health",
            "POST /api/init-schema",
            "GET  /api/v1/bootstrap",
            "GET  /api/v1/protocols",
            "GET  /api/v1/households",
            "GET  /api/v1/members",
            "POST /api/v1/sync",
          ],
        },
        null,
        2
      )
    );
  } catch (err) {
    res.writeHead(500);
    res.end(JSON.stringify({ error: String(err) }, null, 2));
  }
});

server.listen(config.port, () => {
  console.log(`Arogyasathi Cloud Gateway running on http://localhost:${config.port}`);
});
