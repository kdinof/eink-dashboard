# T06 — TaskForge Markdown module

## Status

`completed_with_physical_followup`

## Result

TaskForge is registered as a separate one-minute dashboard module alongside
Todoist. It reads one SAF-selected Markdown document, parses Obsidian Tasks
metadata, supports Today / Today+overdue / Next 7 days / All open views, OR tag
filters and 5/10/20 task limits. The local reader settings and paired web panel
both expose configuration and local-file freshness.

Normal tasks use a verified one-byte completion patch. Recurring, changed,
ambiguous and read-only tasks are not modified. A private last-good snapshot
provides stale display without becoming a second source of truth.

Unit tests cover current-vault syntax, Cyrillic titles, nested tasks, filters,
date boundaries, duplicate locators, stale fallback, conflict mapping and safe
settings decoding. `./scripts/check.sh` passes and assembles the debug APK.

## Physical follow-up

On Meebook: connect the Obsidian Sync local vault, disable battery optimisation
for Obsidian, choose `tasks/TaskForge.md`, then verify an external edit and a
dashboard completion both reach the other devices. This cannot be certified
without the physical reader and the user's private Sync account.
