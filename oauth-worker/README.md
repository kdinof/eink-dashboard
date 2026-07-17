# E-Ink Google Calendar OAuth broker

The broker handles Google OAuth code exchange and access-token refresh. Calendar
lists and events never pass through it; the reader calls Google Calendar directly.

## Provision

1. Create a Cloudflare KV namespace and copy `wrangler.example.toml` to
   `wrangler.toml`, filling the namespace id and custom domain route.
2. In Google Cloud, enable Calendar API and create a Web OAuth client. Register
   `https://<worker-domain>/oauth/callback` as the redirect URI and request only
   `https://www.googleapis.com/auth/calendar.readonly`.
3. Set all four secrets listed in `wrangler.example.toml` with `wrangler secret put`.
4. Run `npm install`, `npm run check`, then `npm run deploy`.

OAuth handoffs expire after ten minutes. Refresh tokens are encrypted while waiting
for one-time redemption and deleted immediately afterwards.
