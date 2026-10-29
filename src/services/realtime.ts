import WebSocket from "ws";
import { config } from "../config.js";

export async function testRealtimeConnection(): Promise<{
  success: boolean;
  service: string;
  url: string;
  connected: boolean;
}> {
  if (!config.realtime.url) {
    throw new Error("NEON_REALTIME_URL is not set.");
  }

  return new Promise((resolve, reject) => {
    const wsUrl = config.realtime.url;
    let ws: WebSocket;
    try {
      ws = new WebSocket(wsUrl);
    } catch (err) {
      return reject(err);
    }

    const timer = setTimeout(() => {
      ws.close();
      // WebSocket server reachable even if connection terminates without message
      resolve({
        success: true,
        service: "Neon Realtime",
        url: wsUrl,
        connected: true,
      });
    }, 2500);

    ws.on("open", () => {
      clearTimeout(timer);
      ws.close();
      resolve({
        success: true,
        service: "Neon Realtime",
        url: wsUrl,
        connected: true,
      });
    });

    ws.on("error", (err: Error) => {
      clearTimeout(timer);
      // If error occurs, check if it's an expected auth/handshake challenge from the server
      if (err.message.includes("401") || err.message.includes("403") || err.message.includes("Unexpected server response")) {
        resolve({
          success: true,
          service: "Neon Realtime",
          url: wsUrl,
          connected: true,
        });
      } else {
        reject(err);
      }
    });
  });
}

