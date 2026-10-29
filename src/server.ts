import http from "http";
import { config } from "./config.js";
import { testPostgresConnection, initPostgresSchema } from "./services/postgres.js";
import { testNeonAuth } from "./services/auth.js";
import { testObjectStorage } from "./services/storage.js";
import { testRealtimeConnection } from "./services/realtime.js";

const server = http.createServer(async (req, res) => {
  const url = req.url || "/";

  // Enable CORS
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  res.setHeader("Content-Type", "application/json");

  try {
    if (url === "/" || url === "/api/health") {
      res.writeHead(200);
      res.end(
        JSON.stringify({
          status: "UP",
          service: "Arogyasathi Neon Cloud Gateway",
          branch: config.postgres.branch,
          services: {
            postgres: "Lakebase Postgres (Neon)",
            auth: "Neon Auth (Managed Better Auth)",
            storage: `Neon Object Storage (S3 / ${config.storage.bucket})`,
            realtime: "Neon Realtime",
          },
          endpoints: [
            "/api/health",
            "/api/postgres",
            "/api/auth",
            "/api/storage",
            "/api/realtime",
          ],
        })
      );
      return;
    }

    if (url === "/api/postgres") {
      const dbInfo = await testPostgresConnection();
      const schemaInit = await initPostgresSchema();
      res.writeHead(200);
      res.end(JSON.stringify({ success: true, dbInfo, schemaInit }));
      return;
    }

    if (url === "/api/auth") {
      const authInfo = await testNeonAuth();
      res.writeHead(200);
      res.end(JSON.stringify(authInfo));
      return;
    }

    if (url === "/api/storage") {
      const storageInfo = await testObjectStorage();
      res.writeHead(200);
      res.end(JSON.stringify(storageInfo));
      return;
    }

    if (url === "/api/realtime") {
      const rtInfo = await testRealtimeConnection();
      res.writeHead(200);
      res.end(JSON.stringify(rtInfo));
      return;
    }

    res.writeHead(404);
    res.end(JSON.stringify({ error: "Endpoint not found" }));
  } catch (err: unknown) {
    const error = err as Error;
    res.writeHead(500);
    res.end(JSON.stringify({ success: false, error: error.message }));
  }
});

const PORT = config.port;
server.listen(PORT, () => {
  console.log(`=================================================================`);
  console.log(` 🏥 AROGYASATHI CLOUD GATEWAY — NEON BACKEND SERVICES`);
  console.log(` Listening on http://localhost:${PORT}`);
  console.log(` Services: Lakebase Postgres | Neon Auth | Object Storage | Realtime`);
  console.log(`=================================================================`);
});

export default server;
