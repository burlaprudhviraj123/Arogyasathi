import { config } from "../config.js";

export async function testNeonAuth() {
  if (!config.auth.jwksUrl || !config.auth.baseUrl) {
    throw new Error("Neon Auth environment variables (NEON_AUTH_BASE_URL, NEON_AUTH_JWKS_URL) are missing.");
  }

  // 1. Fetch JWKS public keys
  const jwksRes = await fetch(config.auth.jwksUrl);
  if (!jwksRes.ok) {
    throw new Error(`Failed to fetch JWKS from ${config.auth.jwksUrl}: ${jwksRes.status} ${jwksRes.statusText}`);
  }
  const jwksData = (await jwksRes.json()) as { keys?: unknown[] };

  // 2. Fetch Auth base endpoint health / metadata
  const baseRes = await fetch(config.auth.baseUrl);
  const baseStatus = baseRes.status;

  return {
    success: true,
    service: "Neon Auth (Managed Better Auth)",
    baseUrl: config.auth.baseUrl,
    jwksUrl: config.auth.jwksUrl,
    jwksKeyCount: jwksData.keys ? jwksData.keys.length : 0,
    baseStatus,
  };
}

