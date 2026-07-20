# ADR 0005 — TaskForge as a local Markdown dashboard module

- Status: Accepted
- Date: 2026-07-18

## Context

TaskForge has no task cloud or public task API. It reads and writes Obsidian Tasks
syntax in a local vault, while the user's chosen vault service moves those files
between devices. The dashboard must therefore share the Android-local
`tasks/TaskForge.md`, not proxy TaskForge or copy tasks into a second source of truth.

Todoist remains an independent optional module. TaskForge is additive.

## Decision

- Obsidian Sync is the single cross-device transport. Every device owns a local
  vault; iCloud and Obsidian Sync must not target the same working folder.
- The reader grants this app durable access to one `.md` document through Storage
  Access Framework. No broad storage permission is requested.
- Markdown is authoritative. A private last-good byte snapshot is retained only
  for stale display when the document provider is temporarily unavailable.
- The module parses inline Obsidian Tasks and refreshes once per foreground minute.
  Its default view is strictly tasks due today; alternate date presets, OR tags,
  and a display limit are persisted in the module's DataStore.
- Completing a normal task is a compare-before-write, one-byte `[ ]` → `[x]`
  patch against a seekable document descriptor followed by `fsync` and verification.
  A moved line remains identifiable by content hash and occurrence; changed or
  ambiguous content is rejected and reloaded.
- Recurring tasks are read-only in the dashboard. TaskForge must complete them so
  its recurrence engine can produce the next occurrence.

## Operational setup

1. Back up the current iCloud vault.
2. Create/connect an Obsidian Sync remote vault and let each device finish its
   initial download before editing.
3. On Meebook, point Obsidian and TaskForge at the same local vault, exempt Obsidian
   from battery optimisation, and choose `tasks/TaskForge.md` in Dashboard Settings.
4. Verify edits in both directions on the physical device. If Android still stops
   Obsidian, evaluate one replacement sync agent; never run two engines over the
   same working directory.

## Consequences

The dashboard can neither report Obsidian Sync progress nor guarantee its latency;
it reports only the last successful local read. Providers without seekable
read/write descriptors remain usable for display but not completion.
