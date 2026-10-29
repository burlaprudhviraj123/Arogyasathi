import dotenv from "dotenv";
import { resolve } from "path";

// Load .env.local first, fallback to .env
dotenv.config({ path: resolve(process.cwd(), ".env.local") });
dotenv.config({ path: resolve(process.cwd(), ".env") });

export const config = {
  port: parseInt(process.env.PORT || "3000", 10),
  postgres: {
    url: process.env.DATABASE_URL || "",
    unpooledUrl: process.env.DATABASE_URL_UNPOOLED || "",
    branch: process.env.NEON_BRANCH || "production",
  },
  auth: {
    baseUrl: process.env.NEON_AUTH_BASE_URL || "",
    jwksUrl: process.env.NEON_AUTH_JWKS_URL || "",
  },
  storage: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID || "",
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || "",
    endpoint: process.env.AWS_ENDPOINT_URL_S3 || "",
    region: process.env.AWS_REGION || "us-east-2",
    bucket: "assets",
  },
  realtime: {
    url: process.env.NEON_REALTIME_URL || "",
    secret: process.env.NEON_REALTIME_SECRET || "",
  },
};

