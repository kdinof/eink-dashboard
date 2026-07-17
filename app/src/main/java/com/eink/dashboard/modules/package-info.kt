/**
 * `modules` — pluggable dashboard data sources, one sub-package per feature:
 * `modules/calendar` (T03), `modules/todoist` (T04), `modules/weather` and the
 * clock/battery system modules (T05).
 *
 * Owner: **T03–T05**. Each module registers itself with the registry defined by
 * T02 and must not modify the Dashboard shell layout. T01 leaves this empty.
 */
package com.eink.dashboard.modules
