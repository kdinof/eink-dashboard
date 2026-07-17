# ADR 0002 — Todoist authentication and API integration

- Status: Accepted
- Date: 2026-07-17
- Task: T04 (Todoist module)
- Depends on: T01 (`docs/adr/0001-architecture.md`), T02 (module SPI), T03 (settings slot)

## Context

The Todoist block must fetch a personal user's tasks and complete them, offline-
tolerantly, on an always-on e-ink wall dashboard. This is a personal, single-user
app with **no backend** (ADR-0001), so the only credential is a **Todoist personal
API token** the user pastes once. The token is a long-lived bearer secret that
grants full read/write access to the account — mishandling it (a log line, a plain
file, a crash report, a Room row) is the primary security risk of this task.

We also had to pick the correct, current Todoist API. As of 2026 Todoist has
**unified its former REST v2 and Sync v9 APIs into a single v1 API** at
`https://api.todoist.com/api/v1`. This was verified against the official docs
(<https://developer.todoist.com/api/v1/>) before implementation, per the T04 card.

## Decision 1 — Token storage: Android Keystore, implemented directly

The token is encrypted at rest with an **AES-256-GCM key generated inside the
`AndroidKeyStore`**. The key is non-exportable (its raw bytes never enter app memory
or disk); only the ciphertext (`IV ‖ ciphertext`, Base64) is persisted in a private
`SharedPreferences` file (`todoist_secure`). Plaintext exists only transiently while
building the `Authorization` header.

**Why implement the wrapper directly instead of `androidx.security:security-crypto`:**
- Jetpack Security `EncryptedSharedPreferences` is effectively deprecated/stalled and
  would add a dependency to the pinned, reproducible set (ADR-0001) for ~30 lines of
  standard Keystore code.
- Keeping the crypto in one auditable file (`security/KeystoreTokenStore.kt`) makes
  the security surface reviewable at a glance for T08.

Trade-off: the key is **not** gated on user authentication (`setUserAuthenticationRequired`
is off) because the device is an unattended kiosk with no unlock step. The key is
still hardware-backed and app-scoped. A lock-screen/credential change can invalidate
the key; `load()` handles that by dropping the blob and forcing clean re-entry rather
than crashing.

## Decision 2 — Keep the token out of logs, Room, and errors

- **No `HttpLoggingInterceptor`** is attached to the Todoist OkHttp client. Body/header
  logging is the usual way a bearer token leaks into logcat; we simply never enable it.
- The `Authorization: Bearer …` header is added **per request** by an interceptor that
  reads the token from the `TokenStore` each call — the token is never baked into the
  Retrofit instance and is never held longer than a call.
- Room stores **task data only**; there is no token column anywhere (enforced by the
  entity design and asserted by review). Settings (`DataStore`) hold only the chosen
  view — never the token.
- Transport failures are mapped to a small `TodoistError` type with **fixed, generic
  messages** (`"Todoist rejected the token"`, `"Rate limited"`, …) — a response body or
  token is never embedded. A `TokenRedaction` helper masks a secret in any string as a
  defensive last line, unit-tested in `TokenRedactionTest`.

## Decision 3 — Personal token now, OAuth later

Per the plan (§8.2, §17), the personal token is correct for the private first
version; if the app ever becomes public/multi-user, the token is replaced by OAuth
with server-side secret storage (a future Cloudflare Worker). The `TokenStore` seam
and per-request interceptor make that swap local to the `security`/`data` packages.

## Decision 4 — API surface and resilience

- Endpoints used (verified against v1 docs):
  - `GET /api/v1/tasks/filter?query=…&cursor=…` — cursor-paginated active tasks by
    filter query. Envelope: `{ "results": [...], "next_cursor": "…" }`. Views map to
    filter strings in `TodoistFilters`: Today → `overdue | today`, Upcoming → `7 days`.
  - `GET /api/v1/projects?cursor=…` — for project id → name resolution.
  - `POST /api/v1/tasks/{id}/close` — completes a task; **for a recurring task the
    server advances it to the next occurrence** instead of finishing it (§8.2). The
    client needs no special recurrence logic beyond re-reading on the next refresh.
- **Error handling** (§ risk "Todoist API"): `401/403 → Unauthorized`, `429 →
  RateLimited(Retry-After)`, `5xx → Server`, offline/timeout → `Network`. These are
  isolated in the API client and never surface raw transport details.
- **Isolated API client** behind a `TodoistApi` interface so the rest of the module
  never depends on Retrofit/JSON; the real client is tested with MockWebServer and
  the repository with an in-memory fake.

## Decision 5 — Optimistic completion with a durable retry queue

Completion follows the plan's 8-step flow (§8.2):

1. The user taps the box → the task is hidden immediately (`locallyCompleted` in the
   Room cache).
2. A **durable `PendingOp`** is enqueued in Room (survives process death).
3. The API `close` is attempted.
   - Success → the op is removed.
   - Retryable failure (offline / 429 / 5xx) → the op stays queued; the next refresh
     drains it. Draining stops at the first retryable error (no hammering) and gives
     up after `MAX_ATTEMPTS`, restoring the task.
   - Permanent rejection (401 / unexpected 4xx) → the optimistic hide is **rolled
     back** and a clear message is shown.

`RefreshPolicy.Periodic(5.minutes)` (plan §: "Todoist every 2–5 min + manual") drives
cadence via the shell coordinator; the module starts no loop of its own, preserving
the foreground-only guarantee (ADR-0001 / T02).

## Consequences

- The security-sensitive code is confined to `modules/todoist/security/**` and the
  one request interceptor — a small review surface for T08.
- All logic (filters, hierarchy, optimistic completion, retry, error mapping) is
  covered by JVM unit tests without a device or Robolectric; the Keystore path and
  the physical token smoke test remain device-only manual steps (see the T04 report).
- Room ships schema **v1** (`app/schemas/…/1.json`); any later change must add an
  explicit `Migration`. R8 keep rules for the DTOs/entities/service are in
  `proguard-rules.pro` for when release minification is enabled (T09).
