import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  ListObjectsV2Command,
} from "@aws-sdk/client-s3";
import { config } from "../config.js";

export const s3 = new S3Client({
  endpoint: config.storage.endpoint,
  region: config.storage.region,
  credentials: {
    accessKeyId: config.storage.accessKeyId,
    secretAccessKey: config.storage.secretAccessKey,
  },
  forcePathStyle: true,
});

export async function testObjectStorage() {
  if (!config.storage.endpoint || !config.storage.accessKeyId) {
    throw new Error("Neon Object Storage credentials missing in environment.");
  }

  const testKey = `test-health-check-${Date.now()}.json`;
  const testPayload = JSON.stringify({
    service: "Neon Object Storage",
    bucket: config.storage.bucket,
    timestamp: new Date().toISOString(),
    status: "HEALTHY",
  });

  // 1. Put Object
  await s3.send(
    new PutObjectCommand({
      Bucket: config.storage.bucket,
      Key: testKey,
      Body: testPayload,
      ContentType: "application/json",
    })
  );

  // 2. Get Object back
  const getRes = await s3.send(
    new GetObjectCommand({
      Bucket: config.storage.bucket,
      Key: testKey,
    })
  );

  const bodyString = await getRes.Body?.transformToString();

  // 3. List objects
  const listRes = await s3.send(
    new ListObjectsV2Command({
      Bucket: config.storage.bucket,
      MaxKeys: 10,
    })
  );

  return {
    success: true,
    service: "Neon Object Storage",
    bucket: config.storage.bucket,
    testKey,
    retrievedContent: bodyString,
    totalObjectsInBucket: listRes.KeyCount || 0,
  };
}
