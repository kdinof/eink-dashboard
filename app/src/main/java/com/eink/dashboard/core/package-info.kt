/**
 * `core` — foundation-level building blocks shared by every layer and owned at
 * the project level (not by a single feature):
 *
 *  - [DeviceProfile] — confirmed T00 target-device constraints and px↔dp math.
 *  - `core/eink` (T07) — vendor e-ink control behind a feature flag with a
 *    mandatory no-op fallback; the app must run on stock Android rendering.
 *  - `core/lifecycle`, `core/time` (T02) — refresh coordinator and minute ticker.
 *
 * Sub-packages are created by their owning task; T01 provides only DeviceProfile.
 */
package com.eink.dashboard.core
