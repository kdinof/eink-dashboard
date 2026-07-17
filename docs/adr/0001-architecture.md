# ADR 0001 — Foundation architecture: single-activity Compose, module registry, no backend

- Status: Accepted
- Date: 2026-07-17
- Task: T01 (project foundation)
- Depends on: T00 device audit (`DEVICE_AUDIT.md`)

## Context

The E-Ink Dashboard runs on a confirmed target device (T00): **Meebook M103**,
Rockchip RK3566, **Android 11 / API 30**, ABI **arm64-v8a** (64-bit userspace),
grayscale e-ink panel **1404×1872 px @ 240 dpi** (~936×1248 dp). It is a
personal, always-on wall/desk dashboard that reads Google Calendar, Todoist and
weather. There is no multi-user requirement, no server component, and the panel
refreshes slowly and in grayscale.

Three foundational decisions must be fixed before any product module is built,
so that T02–T09 share one stable structure.

## Decision 1 — Single-Activity + Jetpack Compose

One `ComponentActivity` (`MainActivity`) hosts the whole app; screens are Compose
composables, navigation is in-process state, not multiple Activities.

**Why:**
- The dashboard is effectively one long-lived screen with a few sub-screens
  (Dashboard / Settings / Diagnostics). Multiple Activities add lifecycle and
  window-transition complexity that fights e-ink (each transition is a costly
  full refresh).
- A single Activity gives one place to own immersive fullscreen, the
  foreground-only refresh lifecycle, and keep-screen-on (T02).
- Compose lets us centrally disable animations, ripple and transitions — which
  are actively harmful on e-ink (ghosting, partial-refresh smearing) — instead
  of fighting View-system defaults.

**Consequences:** the app is Compose-first; the XML layer is limited to the
launch theme and the manifest. Animation is disabled by policy in T02.

## Decision 2 — Module registry for dashboard blocks

Dashboard data sources (Calendar, Todoist, Weather, Clock, Battery) are
**pluggable modules** registered in a central registry, not hard-wired into the
Dashboard screen. The concrete contract (`DashboardModule`, `ModuleState`,
`RefreshPolicy`, `RefreshReason`) and the `DashboardModuleRegistry` are defined
by **T02**; T01 only fixes the package layout and the ownership boundary.

**Why:**
- T03–T05 must be buildable in parallel worktrees without editing each other's
  files or the shell. A registry is the seam that makes that possible.
- Each block has different refresh cadence and failure modes (calendar is local
  and cheap; Todoist/weather are network and rate-limited). A uniform module
  contract lets the shell schedule and render them uniformly while each owns its
  own data and error states.
- New blocks are added by registering a module, not by modifying the Dashboard
  layout — this is the extension point the whole plan depends on.

**Package layout fixed by T01:**

| Package | Responsibility | Owner task |
|---|---|---|
| `core` | Shared foundation (`DeviceProfile`, later e-ink flag, tickers) | project / T02 / T07 |
| `dashboard` | Shell, layout, refresh coordinator | T02 |
| `modules` | One sub-package per data source | T03–T05 |
| `settings` | DataStore settings + Settings screen | T02 |
| `diagnostics` | Diagnostics screen | T02 |

**Consequences:** T01 ships these packages documented but empty (except
`core.DeviceProfile`). No module contract is implemented yet — that is
deliberately T02's file ownership.

## Decision 3 — No backend / no third-party cloud

The app talks **only** to first-party device APIs (CalendarProvider) and the
public APIs the user already uses (Todoist, Open-Meteo), directly from the
device. No custom server, no Cloudflare, no analytics, no crash-reporting SDK.

**Why:**
- Single personal device: a backend adds hosting, auth, privacy surface and
  operational cost for zero functional benefit.
- Privacy posture (T08): calendar contents and the Todoist token must never
  leave the device to an intermediary. Direct-to-source keeps the data on the
  device and the token in Android Keystore (T04).
- The device security patch level is old (2021-06); minimizing network surface
  and never proxying user data lowers risk.

**Consequences:** all secrets live on-device only; there is no server to rotate
tokens or cache data. Offline resilience is handled locally (Room cache in
T04/T05). This ADR must be revisited (a new ADR) if a backend is ever proposed.

## Toolchain (pinned for reproducibility)

Chosen to be compatible with API 30 and the arm64-v8a / pure-Kotlin decision
(no native SDK — T00 recommendation):

| Component | Version |
|---|---|
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.6.1 |
| Kotlin | 1.9.24 |
| Compose Compiler | 1.5.14 |
| Compose BOM | 2024.06.00 |
| JDK (build) | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 30 (confirmed by T00) |
| build-tools | 34.0.0 |

Wired dependencies (implementations deferred to owning tasks): Coroutines, Room,
DataStore, Retrofit + OkHttp + kotlinx.serialization, and the JUnit / Truth /
Robolectric / coroutines-test / MockWebServer test stack.

## Alternatives considered

- **Multiple Activities / Fragments+Views** — rejected: more lifecycle surface,
  worse e-ink transition control, no upside for a single-screen dashboard.
- **Hard-wired blocks (no registry)** — rejected: blocks parallel T03–T05 work
  and every new block would edit the shell.
- **Thin backend for token storage / caching** — rejected: unnecessary for one
  device and worsens the privacy/security posture.
