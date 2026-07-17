# ADR 0003 — Foreground-only local web settings

- Status: Accepted
- Date: 2026-07-17
- Supersedes: the "no server component" wording in ADR-0001, but not its no-backend decision

## Context

Entering tokens and editing module settings on an e-ink reader is cumbersome. The
product remains personal and single-device, so a cloud account and hosted backend
would add more privacy and operations cost than value.

## Decision

The Android process hosts a Ktor/Netty HTTP server bound only to its active Wi-Fi
IPv4 address while `MainActivity` is started. It serves a static same-origin web UI
and a versioned JSON API over local HTTP. There is no cloud service, remote access,
background service, mDNS, analytics, or external web asset.

Access requires a single-use six-digit PIN shown on the reader. Successful pairing
issues a random 256-bit bearer credential; only its SHA-256 hash is persisted on the
reader. Pairing attempts are rate-limited and trusted sessions remain valid until
revoked. The Todoist credential is write-only through the API and remains encrypted
by Android Keystore at rest.

Android runtime permissions cannot be granted by a browser. The API queues a request
and the Activity launches the system dialog for physical confirmation on the reader.

## Consequences

- Compose and HTTP share one application-scoped graph and settings facade.
- The panel is deliberately unencrypted in transit and is safe only on a trusted,
  encrypted home Wi-Fi network.
- Closing or backgrounding the Activity stops the listener; changing Wi-Fi changes
  the URL and therefore the QR code.
- Adding a Google account remains an Android system operation; the panel can only
  select calendars already present in `CalendarContract`.

This final consequence is superseded by ADR-0004 for optional Google Calendar API OAuth.
