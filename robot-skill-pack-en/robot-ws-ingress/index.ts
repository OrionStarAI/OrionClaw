import http from "node:http";
import { WebSocketServer, WebSocket } from "ws";
import { randomUUID } from "node:crypto";

type PluginCfg = {
  enabled?: boolean;
  bind?: string;
  port: number;
  path: string;
  token: string;
  allowDeviceIds?: string[];
  gatewayPort?: number;
  gatewayToken?: string;
};

type RobotHello = {
  type: "hello";
  deviceId: string;
  app?: string;
  ver?: string;
  capabilities?: string[];
};

type CommandMsg = {
  type: "cmd";
  id: string;
  cmd: string;
  args?: any;
};

type ResultMsg = {
  type: "result";
  id: string;
  ok: boolean;
  data?: any;
  error?: string;
};

type EventMsg = {
  type: "event";
  event: string;
  data?: any;
};

function jsonParseSafe(s: string): any {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

function asStringArray(v: any): string[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x) => typeof x === "string").map((x) => x.trim()).filter(Boolean);
}

/**
 * Inject a cron.wake event into a specific session via Gateway WS.
 * Protocol: connect → wait for connect.challenge event → send connect req → wait res → send cron.wake req → close.
 * @param sessionKey Optional session key to target a specific session (e.g. agent:robot:subagent:xxx)
 */
async function injectSessionEvent(text: string, gwPort: number, gwToken: string, sessionKey?: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const url = `ws://127.0.0.1:${gwPort}`;
    const ws = new WebSocket(url);
    let settled = false;
    let challengeReceived = false;
    let connectedOk = false;

    const done = (err?: Error) => {
      if (settled) return;
      settled = true;
      try { ws.close(); } catch {}
      if (err) reject(err);
      else resolve();
    };

    const timer = setTimeout(() => done(new Error("injectSessionEvent timeout")), 10000);

    ws.on("open", () => {
      // Wait for connect.challenge event from server before sending connect
    });

    ws.on("message", (raw: any) => {
      let msg: any;
      try { msg = JSON.parse(raw.toString()); } catch { return; }

      // Step 1: server sends connect.challenge
      if (!challengeReceived && msg.type === "event" && msg.event === "connect.challenge") {
        challengeReceived = true;
        const connectReq = {
          type: "req",
          id: randomUUID(),
          method: "connect",
          params: {
            minProtocol: 3,
            maxProtocol: 3,
            client: { id: "robot-ws-ingress-inject", version: "1.0.0", platform: "linux", mode: "operator" },
            role: "operator",
            scopes: ["operator.read", "operator.write"],
            caps: [],
            commands: [],
            permissions: {},
            auth: { token: gwToken },
            locale: "en-US",
            userAgent: "robot-ws-ingress/1.0.0",
            device: { id: "robot-ws-ingress-inject", publicKey: "", signature: "", signedAt: Date.now(), nonce: msg.payload?.nonce ?? "" }
          }
        };
        ws.send(JSON.stringify(connectReq));
        return;
      }

      // Step 2: connect response
      if (challengeReceived && !connectedOk && msg.type === "res" && msg.ok) {
        connectedOk = true;
        const wakeParams: any = { mode: "now", text };
        if (sessionKey) wakeParams.sessionKey = sessionKey;
        const wakeReq = {
          type: "req",
          id: randomUUID(),
          method: "cron.wake",
          params: wakeParams
        };
        ws.send(JSON.stringify(wakeReq));
        return;
      }

      // Step 3: cron.wake response
      if (connectedOk && msg.type === "res") {
        clearTimeout(timer);
        if (msg.ok) {
          done();
        } else {
          done(new Error(`cron.wake failed: ${JSON.stringify(msg.error)}`));
        }
        return;
      }
    });

    ws.on("error", (err: Error) => {
      clearTimeout(timer);
      done(err);
    });

    ws.on("close", () => {
      clearTimeout(timer);
      if (!settled) done(new Error("injectSessionEvent: connection closed unexpectedly"));
    });
  });
}

export default {
  id: "robot-ws-ingress",
  name: "Robot WS Ingress",
  version: "0.0.1",

  /**
   * OpenClaw plugin entrypoint.
   * NOTE: We intentionally avoid touching OpenClaw core. This plugin starts its own WS server
   * inside the Gateway process.
   */
  register(ctx: any) {
    // OpenClaw passes validated plugin config as `pluginConfig`.
    const cfg: PluginCfg = ctx?.pluginConfig;
    const log = ctx?.log ?? console;

    if (!cfg || cfg.enabled === false) {
      log.info?.("[robot-ws-ingress] disabled");
      return;
    }

    const bind = (cfg.bind || "0.0.0.0").trim();
    const port = cfg.port;
    const path = (cfg.path || "/robot/ws").trim() || "/robot/ws";
    const token = (cfg.token || "").trim();
    const allowDeviceIds = asStringArray(cfg.allowDeviceIds);
    const gwPort = cfg.gatewayPort ?? 18789;
    const gwToken = ((cfg.gatewayToken || "").trim()) || ((ctx?.config as any)?.gateway?.auth?.token || "");

    // Hardcoded Robot Loop session key for default callback injection
    const ROBOT_LOOP_SESSION_KEY = process.env.ROBOT_LOOP_SESSION_KEY || "";

    if (!token) {
      throw new Error("[robot-ws-ingress] config.token is required");
    }

    // Prevent double-listen if plugin register is invoked more than once in-process.
    const g: any = globalThis as any;
    if (g.__robotWsIngress?.server) {
      log.warn?.("[robot-ws-ingress] already started; skipping duplicate register");
      return;
    }

    const conns = new Map<string, import("ws").WebSocket>();
    const pending = new Map<
      string,
      {
        resolve: (v: ResultMsg) => void;
        timer: any;
      }
    >();

    // ASR final text queue per device (in-memory, best-effort)
    const asrQueues = new Map<string, Array<{ text: string; ts: number }>>();

    // Long-poll waiters per device for next ASR final
    const asrWaiters = new Map<
      string,
      Array<{
        resolve: (item: { text: string; ts: number } | null) => void;
        timer: any;
      }>
    >();

    // Cached place list per device (from robot.getPlaceList)
    const placeCache = new Map<string, { ts: number; places: string[]; raw?: any }>();
    const PLACE_CACHE_TTL_MS = 5 * 60 * 1000;

    function pushAsr(deviceId: string, text: string, ts: number) {
      const t = (text || "").toString().trim();
      if (!t) return;
      const item = { text: t, ts: Number(ts) || Date.now() };

      // First: wake one waiter if any
      const waitQ = asrWaiters.get(deviceId) ?? [];
      const w = waitQ.shift();
      if (w) {
        try {
          clearTimeout(w.timer);
        } catch {}
        try {
          w.resolve(item);
        } catch {}
        asrWaiters.set(deviceId, waitQ);
        return;
      }

      // Otherwise: enqueue
      const q = asrQueues.get(deviceId) ?? [];
      q.push(item);
      // cap memory
      while (q.length > 50) q.shift();
      asrQueues.set(deviceId, q);
    }

    function sendJson(res: any, code: number, obj: any) {
      res.writeHead(code, { "content-type": "application/json" });
      res.end(JSON.stringify(obj));
    }

    async function readBodyJson(req: any): Promise<any> {
      const chunks: Buffer[] = [];
      for await (const c of req) chunks.push(Buffer.isBuffer(c) ? c : Buffer.from(c));
      const raw = Buffer.concat(chunks).toString("utf8");
      return jsonParseSafe(raw);
    }

    function checkToken(reqUrl: URL, req: any): boolean {
      const q = (reqUrl.searchParams.get("token") || "").trim();
      const h = (req.headers?.["x-robot-token"] || "").toString().trim();
      const t = q || h;
      return !!t && t === token;
    }

    function sendCmdAndWait(deviceId: string, cmd: CommandMsg, timeoutMs: number): Promise<ResultMsg> {
      return new Promise((resolve, reject) => {
        const ws = conns.get(deviceId);
        if (!ws || ws.readyState !== ws.OPEN) {
          reject(new Error("robot not online"));
          return;
        }
        const timer = setTimeout(() => {
          pending.delete(cmd.id);
          reject(new Error("timeout"));
        }, Math.max(1000, timeoutMs | 0));
        pending.set(cmd.id, { resolve, timer });
        ws.send(JSON.stringify(cmd));
      });
    }

    function extractPlacesFromGetPlaceListResult(r: ResultMsg): { places: string[]; raw?: any } {
      // Our Android side returns: data: { result, message, extra }
      // where message is typically a JSON string like: [{..., name:"<PLACE_NAME>", ...}, ...]
      const msg = (r as any)?.data?.message;
      const extra = (r as any)?.data?.extra;
      const out: { places: string[]; raw?: any } = { places: [] };

      const candidates: any[] = [msg, extra];
      for (const c of candidates) {
        if (!c) continue;
        // Try JSON parse if it's a string
        let v: any = c;
        if (typeof v === "string") {
          const parsed = jsonParseSafe(v);
          if (parsed != null) v = parsed;
        }

        if (Array.isArray(v)) {
          // Case 1: array of strings
          const strs = v
            .filter((x) => typeof x === "string")
            .map((x) => x.trim())
            .filter(Boolean);
          if (strs.length) {
            out.places = strs;
            out.raw = v;
            return out;
          }

          // Case 2: array of objects with name field
          const names = v
            .map((x) => (x && typeof x === "object" ? (x as any).name : null))
            .filter((x) => typeof x === "string")
            .map((x: string) => x.trim())
            .filter(Boolean);
          if (names.length) {
            out.places = names;
            out.raw = v;
            return out;
          }

          out.raw = v;
          continue;
        }

        if (typeof v === "object") {
          // Sometimes the list is in { data: [...] } or { places: [...] }
          const arr = (v as any)?.places ?? (v as any)?.data ?? (v as any)?.list;
          if (Array.isArray(arr)) {
            const names = arr
              .map((x) => {
                if (typeof x === "string") return x;
                if (x && typeof x === "object") return (x as any).name;
                return null;
              })
              .filter((x) => typeof x === "string")
              .map((x: string) => x.trim())
              .filter(Boolean);
            out.places = names;
            out.raw = v;
            if (out.places.length) return out;
          }
        }
      }

      out.raw = { msg, extra };
      return out;
    }

    async function getPlaceListCached(deviceId: string, force: boolean): Promise<{ places: string[]; ts: number; raw?: any }> {
      const now = Date.now();
      const cur = placeCache.get(deviceId);
      if (!force && cur && now - cur.ts < PLACE_CACHE_TTL_MS) {
        return { places: cur.places, ts: cur.ts, raw: cur.raw };
      }

      const cmd: CommandMsg = { type: "cmd", id: cryptoRandomId(), cmd: "robot.getPlaceList", args: {} };
      const r = await sendCmdAndWait(deviceId, cmd, 15_000);
      if (!r.ok) throw new Error(r.error || "getPlaceList failed");
      const parsed = extractPlacesFromGetPlaceListResult(r);
      placeCache.set(deviceId, { ts: now, places: parsed.places, raw: parsed.raw });
      return { places: parsed.places, ts: now, raw: parsed.raw };
    }

    const server = http.createServer(async (req, res) => {
      try {
        const url = new URL(req.url || "", `http://${req.headers.host || "localhost"}`);

        // Health endpoint
        if (url.pathname === "/robot/health") {
          sendJson(res, 200, { ok: true, online: conns.size });
          return;
        }

        // Debug endpoint: list online devices (token required)
        if (url.pathname === "/robot/online") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          sendJson(res, 200, { ok: true, devices: Array.from(conns.keys()) });
          return;
        }

        // Command endpoint: POST /robot/cmd (token required)
        // body: { deviceId, id?, cmd, args?, timeoutMs? }
        if (url.pathname === "/robot/cmd" && (req.method || "GET").toUpperCase() === "POST") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          const body = await readBodyJson(req);
          const deviceId = (body?.deviceId || "").toString().trim();
          const cmdStr = (body?.cmd || "").toString().trim();
          const id = (body?.id || cryptoRandomId()).toString().trim();
          const timeoutMs = Number(body?.timeoutMs ?? 10_000);
          const args = body?.args;

          if (!deviceId || !cmdStr) {
            sendJson(res, 400, { ok: false, error: "deviceId and cmd are required" });
            return;
          }

          const cmd: CommandMsg = { type: "cmd", id, cmd: cmdStr, args };

          try {
            const result = await sendCmdAndWait(deviceId, cmd, timeoutMs);
            sendJson(res, 200, { ok: true, result });
          } catch (e: any) {
            sendJson(res, 500, { ok: false, error: e?.message || String(e) });
          }
          return;
        }

        // Place list endpoint (token required)
        // GET /robot/places?deviceId=...&force=0|1
        if (url.pathname === "/robot/places") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          const deviceId = (url.searchParams.get("deviceId") || "").trim();
          const force = (url.searchParams.get("force") || "0").trim() === "1";
          if (!deviceId) {
            sendJson(res, 400, { ok: false, error: "deviceId required" });
            return;
          }
          try {
            const debug = (url.searchParams.get("debug") || "0").trim() === "1";
            const r = await getPlaceListCached(deviceId, force);
            sendJson(res, 200, { ok: true, deviceId, ts: r.ts, places: r.places, ...(debug ? { raw: r.raw } : {}) });
          } catch (e: any) {
            sendJson(res, 500, { ok: false, error: e?.message || String(e) });
          }
          return;
        }

        // ASR endpoints (token required)
        // GET /robot/asr/latest?deviceId=... -> latest event without consuming
        if (url.pathname === "/robot/asr/latest") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          const deviceId = (url.searchParams.get("deviceId") || "").trim();
          if (!deviceId) {
            sendJson(res, 400, { ok: false, error: "deviceId required" });
            return;
          }
          const q = asrQueues.get(deviceId) ?? [];
          const last = q.length ? q[q.length - 1] : null;
          sendJson(res, 200, { ok: true, deviceId, latest: last, queueSize: q.length });
          return;
        }

        // GET /robot/asr/consume?deviceId=...&max=10 -> pop up to max items
        if (url.pathname === "/robot/asr/consume") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          const deviceId = (url.searchParams.get("deviceId") || "").trim();
          const max = Math.min(50, Math.max(1, Number(url.searchParams.get("max") ?? 10)));
          if (!deviceId) {
            sendJson(res, 400, { ok: false, error: "deviceId required" });
            return;
          }
          const q = asrQueues.get(deviceId) ?? [];
          const out = q.splice(0, Math.min(max, q.length));
          asrQueues.set(deviceId, q);
          sendJson(res, 200, { ok: true, deviceId, items: out, remaining: q.length });
          return;
        }

        // GET /robot/asr/wait?deviceId=...&timeoutMs=25000 -> wait for 1 item (consumes one)
        if (url.pathname === "/robot/asr/wait") {
          if (!checkToken(url, req)) {
            sendJson(res, 401, { ok: false, error: "unauthorized" });
            return;
          }
          const deviceId = (url.searchParams.get("deviceId") || "").trim();
          const timeoutMs = Math.min(30_000, Math.max(1000, Number(url.searchParams.get("timeoutMs") ?? 25_000)));
          if (!deviceId) {
            sendJson(res, 400, { ok: false, error: "deviceId required" });
            return;
          }

          // If queue already has items, consume immediately
          const q = asrQueues.get(deviceId) ?? [];
          if (q.length > 0) {
            const item = q.shift()!;
            asrQueues.set(deviceId, q);
            sendJson(res, 200, { ok: true, deviceId, item });
            return;
          }

          // Otherwise, wait
          const waitQ = asrWaiters.get(deviceId) ?? [];
          const timer = setTimeout(() => {
            // remove this waiter (best-effort)
            const arr = asrWaiters.get(deviceId) ?? [];
            const idx = arr.findIndex((x) => x.timer === timer);
            if (idx >= 0) arr.splice(idx, 1);
            asrWaiters.set(deviceId, arr);
            sendJson(res, 200, { ok: true, deviceId, item: null, timeout: true });
          }, timeoutMs);

          waitQ.push({
            timer,
            resolve: (item) => {
              sendJson(res, 200, { ok: true, deviceId, item });
            }
          });
          asrWaiters.set(deviceId, waitQ);
          return;
        }

        res.writeHead(404);
        res.end();
      } catch (e: any) {
        sendJson(res, 500, { ok: false, error: e?.message || String(e) });
      }
    });

    function cryptoRandomId(): string {
      try {
        // Node 18+: crypto.randomUUID
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const c = require("node:crypto");
        return typeof c.randomUUID === "function" ? c.randomUUID() : `${Date.now()}-${Math.random()}`;
      } catch {
        return `${Date.now()}-${Math.random()}`;
      }
    }

    const wss = new WebSocketServer({ noServer: true });

    server.on("upgrade", (req, socket, head) => {
      try {
        const url = new URL(req.url || "", `http://${req.headers.host || "localhost"}`);
        if (url.pathname !== path) {
          socket.destroy();
          return;
        }
        const qToken = (url.searchParams.get("token") || "").trim();
        if (!qToken || qToken !== token) {
          socket.destroy();
          return;
        }
        wss.handleUpgrade(req, socket, head, (ws) => {
          wss.emit("connection", ws, req);
        });
      } catch {
        socket.destroy();
      }
    });

    wss.on("connection", (ws) => {
      let deviceId: string | null = null;

      ws.on("message", (data) => {
        const text = typeof data === "string" ? data : data.toString("utf8");
        const msg = jsonParseSafe(text);
        if (!msg || typeof msg !== "object") return;

        if (msg.type === "hello") {
          const hello = msg as RobotHello;
          const id = (hello.deviceId || "").trim();
          if (!id) {
            ws.close();
            return;
          }
          if (allowDeviceIds.length > 0 && !allowDeviceIds.includes(id)) {
            ws.close();
            return;
          }
          deviceId = id;
          conns.set(id, ws);
          log.info?.(`[robot-ws-ingress] robot online: ${id} (online=${conns.size})`);
          ws.send(JSON.stringify({ type: "hello_ack", deviceId: id }));
          return;
        }

        if (msg.type === "result") {
          const r = msg as ResultMsg;
          // Resolve pending request if any
          const p = pending.get(r.id);
          if (p) {
            pending.delete(r.id);
            try {
              clearTimeout(p.timer);
            } catch {}
            try {
              p.resolve(r);
            } catch {}
          }
          log.info?.(`[robot-ws-ingress] result id=${r.id} ok=${r.ok} error=${r.error || ""}`);
          return;
        }

        if (msg.type === "event") {
          const ev = msg as EventMsg;
          if (!deviceId) return;
          if (ev.event === "asr.final") {
            const text = (ev?.data?.text || "").toString();
            const ts = Number(ev?.data?.ts ?? Date.now());
            pushAsr(deviceId, text, ts);
            log.info?.(`[robot-ws-ingress] asr.final device=${deviceId} text=${JSON.stringify(text)}`);
            if (gwToken) {
              const asrText = `[asr.final] deviceId=${deviceId} text=${JSON.stringify(text)}`;
              injectSessionEvent(asrText, gwPort, gwToken, ROBOT_LOOP_SESSION_KEY).catch((e: Error) => log.warn?.(`[robot-ws-ingress] asr inject failed: ${e?.message}`));
            }
          } else if (ev.event === "nav.done" || ev.event === "nav.error" || ev.event === "nav.status") {
            const data = ev.data ?? {};
            const callbackSessionKey = (data.callbackSessionKey || "").toString().trim() || ROBOT_LOOP_SESSION_KEY;
            log.info?.(`[robot-ws-ingress] ${ev.event} device=${deviceId} callbackSessionKey=${callbackSessionKey || "(none)"} data=${JSON.stringify(data)}`);
            if (gwToken) {
              // Build a human-readable text summary for cron.wake
              const parts: string[] = [`event=${ev.event}`];
              if (data.destName) parts.push(`destName=${data.destName}`);
              if (data.floorIndex !== undefined) parts.push(`floorIndex=${data.floorIndex}`);
              if (data.status !== undefined) parts.push(`status=${data.status}`);
              if (data.response !== undefined) parts.push(`response=${data.response}`);
              if (data.errorCode !== undefined) parts.push(`errorCode=${data.errorCode}`);
              if (data.errorString) parts.push(`errorString=${data.errorString}`);
              if (data.error) parts.push(`error=${data.error}`);
              const wakeText = `[nav.event] ${parts.join(" ")} sessionKey=${callbackSessionKey}`;
              injectSessionEvent(wakeText, gwPort, gwToken, callbackSessionKey).then(() => {
                log.info?.(`[robot-ws-ingress] cron.wake injected for session ${callbackSessionKey}`);
              }).catch((err: Error) => {
                log.error?.(`[robot-ws-ingress] cron.wake failed: ${err?.message || err}`);
              });
            }
          } else {
            log.info?.(`[robot-ws-ingress] event device=${deviceId} event=${ev.event}`);
          }
          return;
        }
      });

      ws.on("close", () => {
        if (deviceId) {
          conns.delete(deviceId);
          log.info?.(`[robot-ws-ingress] robot offline: ${deviceId} (online=${conns.size})`);
        }
      });
    });

    // IMPORTANT: never crash the whole Gateway if we can't bind (EADDRINUSE, etc.).
    server.on("error", (err: any) => {
      const code = err?.code;
      log.error?.(`[robot-ws-ingress] server error code=${code || "?"} msg=${err?.message || err}`);

      // Most common: leftover process already listening.
      if (code === "EADDRINUSE") {
        log.warn?.(`[robot-ws-ingress] port already in use: ${bind}:${port} (plugin will stay disabled in this process)`);
      }

      try {
        wss.close();
      } catch {}
      try {
        server.close();
      } catch {}
      conns.clear();

      // Do NOT throw; keep Gateway alive.
    });

    // Disable built-in Node.js HTTP timeouts so that long-running commands
    // (e.g. nav.startElevator with timeoutMs=300000) are not forcibly cut off
    // by the server before the robot has a chance to respond.
    server.headersTimeout = 0;
    server.requestTimeout = 0;
    server.setTimeout(0);

    server.listen(port, bind, () => {
      log.info?.(`[robot-ws-ingress] listening ws://${bind}:${port}${path} (health: /robot/health)`);
      log.info?.(`[robot-ws-ingress] http endpoints: /robot/health /robot/online /robot/cmd /robot/asr/latest /robot/asr/consume /robot/asr/wait /robot/places`);
      g.__robotWsIngress = { server, wss, conns, bind, port, path };
    });

    // Expose a minimal service object for other plugins/tools if needed
    ctx?.services?.push?.({
      id: "robot-ws-ingress",
      conns,
      send(deviceId: string, cmd: CommandMsg) {
        const ws = conns.get(deviceId);
        if (!ws || ws.readyState !== ws.OPEN) return false;
        ws.send(JSON.stringify(cmd));
        return true;
      }
    });

    // Best-effort cleanup hook if OpenClaw supports it.
    return () => {
      try {
        wss.close();
      } catch {}
      try {
        server.close();
      } catch {}
      conns.clear();
    };
  }
};
