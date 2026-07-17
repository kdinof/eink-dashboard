# ADR 0004 — Phone OAuth and direct Google Calendar API source

- Status: Accepted (deployment pending external credentials)
- Date: 2026-07-17
- Supersedes: ADR-0003's CalendarContract-only consequence

## Decision

Calendar supports `DEVICE` (`CalendarContract`) and `GOOGLE` sources. Google OAuth
starts in the local web panel, opens Google's page on the phone, returns through a
public HTTPS Cloudflare Worker callback, and completes through a one-time handoff
to the reader. The reader stores the refresh token with Android Keystore and calls
Google Calendar API directly with the read-only Calendar scope.

The Worker performs confidential-client code exchange, refresh and revocation. It
never receives calendar lists or events. Handoff tokens live in KV for at most ten
minutes, are encrypted, require a separately generated redemption secret, and are
deleted after one successful redemption.

## Consequences

- A Google Cloud OAuth Web client, Production consent screen and HTTPS callback
  domain are required. The Android build receives the broker URL through the
  `EINK_GOOGLE_BROKER_URL` Gradle property.
- Google credentials transit the broker because a web client secret cannot be
  embedded in the APK. Calendar content remains reader-to-Google only.
- Existing device calendars remain available and are the default until Google OAuth
  completes successfully.
