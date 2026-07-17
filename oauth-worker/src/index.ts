interface Env {
  OAUTH_HANDOFFS: KVNamespace;
  GOOGLE_CLIENT_ID: string;
  GOOGLE_CLIENT_SECRET: string;
  HANDOFF_ENCRYPTION_KEY: string;
  BROKER_SIGNING_KEY: string;
}

type Handoff = {
  id: string;
  secretHash: string;
  returnUrl: string;
  verifier: string;
};

type TokenSet = {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
};

const GOOGLE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
const GOOGLE_REVOKE = "https://oauth2.googleapis.com/revoke";
const SCOPE = "https://www.googleapis.com/auth/calendar.readonly";
const TTL_SECONDS = 600;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (request.method === "POST" && url.pathname === "/handoffs") return createHandoff(request, env, url);
      if (request.method === "GET" && url.pathname === "/oauth/callback") return oauthCallback(url, env);
      const redeem = url.pathname.match(/^\/handoffs\/([^/]+)\/redeem$/);
      if (request.method === "POST" && redeem) return redeemHandoff(request, env, redeem[1]);
      if (request.method === "POST" && url.pathname === "/oauth/refresh") return refreshToken(request, env);
      if (request.method === "POST" && url.pathname === "/oauth/revoke") return revokeToken(request, env);
      if (request.method === "GET" && url.pathname === "/health") return json({ ok: true });
      return json({ error: "not_found" }, 404);
    } catch {
      return json({ error: "invalid_request" }, 400);
    }
  },
};

async function createHandoff(request: Request, env: Env, requestUrl: URL): Promise<Response> {
  const body = await readJson<{ id: string; secretHash: string; returnUrl: string }>(request);
  if (!/^[A-Za-z0-9_-]{32,128}$/.test(body.id) || !/^[A-Za-z0-9_-]{32,128}$/.test(body.secretHash)) {
    return json({ error: "invalid_handoff" }, 400);
  }
  validateLocalReturnUrl(body.returnUrl);
  const state = randomUrlSafe(32);
  const verifier = randomUrlSafe(64);
  const challenge = base64Url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const handoff: Handoff = { id: body.id, secretHash: body.secretHash, returnUrl: body.returnUrl, verifier };
  await env.OAUTH_HANDOFFS.put(`state:${state}`, JSON.stringify(handoff), { expirationTtl: TTL_SECONDS });

  const callback = `${requestUrl.origin}/oauth/callback`;
  const auth = new URL(GOOGLE_AUTH);
  auth.searchParams.set("client_id", env.GOOGLE_CLIENT_ID);
  auth.searchParams.set("redirect_uri", callback);
  auth.searchParams.set("response_type", "code");
  auth.searchParams.set("scope", SCOPE);
  auth.searchParams.set("access_type", "offline");
  auth.searchParams.set("prompt", "consent");
  auth.searchParams.set("state", state);
  auth.searchParams.set("code_challenge", challenge);
  auth.searchParams.set("code_challenge_method", "S256");
  return json({ authorizationUrl: auth.toString(), expiresIn: TTL_SECONDS });
}

async function oauthCallback(url: URL, env: Env): Promise<Response> {
  const state = url.searchParams.get("state") ?? "";
  const handoffRaw = await env.OAUTH_HANDOFFS.get(`state:${state}`);
  if (!handoffRaw) return html("This authorization request expired. Return to the dashboard and try again.", 410);
  const handoff = JSON.parse(handoffRaw) as Handoff;
  await env.OAUTH_HANDOFFS.delete(`state:${state}`);
  const error = url.searchParams.get("error");
  if (error) return Response.redirect(`${handoff.returnUrl}#google_error=${encodeURIComponent(error)}`, 302);
  const code = url.searchParams.get("code");
  if (!code) return html("Google did not return an authorization code.", 400);

  const response = await fetch(GOOGLE_TOKEN, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      code,
      code_verifier: handoff.verifier,
      grant_type: "authorization_code",
      redirect_uri: `${url.origin}/oauth/callback`,
    }),
  });
  if (!response.ok) return html("Google token exchange failed. Return to the dashboard and try again.", 502);
  const tokens = await response.json<TokenSet>();
  if (!tokens.refresh_token) return html("Google did not issue offline access. Revoke the app and try again.", 502);
  const encrypted = await encryptJson(tokens, env.HANDOFF_ENCRYPTION_KEY);
  await env.OAUTH_HANDOFFS.put(
    `handoff:${handoff.id}`,
    JSON.stringify({ secretHash: handoff.secretHash, encrypted }),
    { expirationTtl: TTL_SECONDS },
  );
  return Response.redirect(`${handoff.returnUrl}#google=${encodeURIComponent(handoff.id)}`, 302);
}

async function redeemHandoff(request: Request, env: Env, id: string): Promise<Response> {
  const body = await readJson<{ secret: string }>(request);
  const raw = await env.OAUTH_HANDOFFS.get(`handoff:${id}`);
  if (!raw) return json({ error: "handoff_expired" }, 410);
  const stored = JSON.parse(raw) as { secretHash: string; encrypted: string };
  const actual = base64Url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(body.secret)));
  if (!timingSafeEqual(actual, stored.secretHash)) return json({ error: "handoff_rejected" }, 401);
  const tokens = await decryptJson<TokenSet>(stored.encrypted, env.HANDOFF_ENCRYPTION_KEY);
  await env.OAUTH_HANDOFFS.delete(`handoff:${id}`);
  return json({
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    expiresIn: tokens.expires_in,
    brokerToken: await signBrokerToken(id, env.BROKER_SIGNING_KEY),
  });
}

async function refreshToken(request: Request, env: Env): Promise<Response> {
  const body = await readJson<{ refreshToken: string; brokerToken: string }>(request);
  if (!(await verifyBrokerToken(body.brokerToken, env.BROKER_SIGNING_KEY))) return json({ error: "unauthorized" }, 401);
  const response = await fetch(GOOGLE_TOKEN, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      refresh_token: body.refreshToken,
      grant_type: "refresh_token",
    }),
  });
  if (!response.ok) return json({ error: response.status === 400 ? "invalid_grant" : "refresh_failed" }, 401);
  const token = await response.json<{ access_token: string; expires_in: number }>();
  return json({ accessToken: token.access_token, expiresIn: token.expires_in });
}

async function revokeToken(request: Request, env: Env): Promise<Response> {
  const body = await readJson<{ refreshToken: string; brokerToken: string }>(request);
  if (!(await verifyBrokerToken(body.brokerToken, env.BROKER_SIGNING_KEY))) return json({ error: "unauthorized" }, 401);
  await fetch(GOOGLE_REVOKE, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ token: body.refreshToken }),
  });
  return new Response(null, { status: 204, headers: secureHeaders() });
}

function validateLocalReturnUrl(raw: string): void {
  const url = new URL(raw);
  const parts = url.hostname.split(".").map(Number);
  const privateIp = parts.length === 4 && parts.every(n => Number.isInteger(n) && n >= 0 && n <= 255) &&
    (parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168));
  if (url.protocol !== "http:" || url.port !== "8787" || !privateIp || url.pathname !== "/") throw new Error("invalid return URL");
}

async function encryptJson(value: unknown, encodedKey: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", decodeUrlSafe(encodedKey), "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(value)));
  const packed = new Uint8Array(iv.length + ciphertext.byteLength);
  packed.set(iv); packed.set(new Uint8Array(ciphertext), iv.length);
  return base64Url(packed);
}

async function decryptJson<T>(value: string, encodedKey: string): Promise<T> {
  const packed = decodeUrlSafe(value);
  const key = await crypto.subtle.importKey("raw", decodeUrlSafe(encodedKey), "AES-GCM", false, ["decrypt"]);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: packed.slice(0, 12) }, key, packed.slice(12));
  return JSON.parse(new TextDecoder().decode(plain)) as T;
}

async function signBrokerToken(subject: string, encodedKey: string): Promise<string> {
  const expires = Math.floor(Date.now() / 1000) + 365 * 24 * 60 * 60;
  const payload = base64Url(new TextEncoder().encode(JSON.stringify({ sub: subject, exp: expires })));
  const signature = await hmac(payload, encodedKey);
  return `${payload}.${signature}`;
}

async function verifyBrokerToken(token: string, key: string): Promise<boolean> {
  const [payload, signature] = token.split(".");
  if (!payload || !signature || !timingSafeEqual(signature, await hmac(payload, key))) return false;
  const decoded = JSON.parse(new TextDecoder().decode(decodeUrlSafe(payload))) as { exp: number };
  return decoded.exp > Math.floor(Date.now() / 1000);
}

async function hmac(value: string, encodedKey: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", decodeUrlSafe(encodedKey), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return base64Url(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value)));
}

async function readJson<T>(request: Request): Promise<T> {
  if (!request.headers.get("content-type")?.includes("application/json")) throw new Error("json required");
  return request.json<T>();
}

function randomUrlSafe(bytes: number): string { return base64Url(crypto.getRandomValues(new Uint8Array(bytes))); }
function base64Url(value: ArrayBuffer | Uint8Array): string {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value);
  let raw = ""; for (const byte of bytes) raw += String.fromCharCode(byte);
  return btoa(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function decodeUrlSafe(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
}
function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let result = 0; for (let i = 0; i < a.length; i++) result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return result === 0;
}
function secureHeaders(): HeadersInit {
  return { "cache-control": "no-store", "content-security-policy": "default-src 'none'", "x-content-type-options": "nosniff" };
}
function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { ...secureHeaders(), "content-type": "application/json" } });
}
function html(message: string, status: number): Response {
  return new Response(`<!doctype html><meta name="viewport" content="width=device-width"><title>E-Ink OAuth</title><p>${message}</p>`, {
    status, headers: { ...secureHeaders(), "content-type": "text/html; charset=utf-8" },
  });
}
