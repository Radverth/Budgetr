# Offline Saving Completion Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Finish the inherited offline transaction queue and publish a verified PR.

**Architecture:** Room holds pending transactions, overlaid on cached Sheet rows. WorkManager sends them after connectivity returns. Each queue entry belongs to its original spreadsheet; acknowledgement follows a successful cache refresh.

**Tech Stack:** Kotlin, Room, WorkManager, Compose, Robolectric, GitHub Actions.

**Spec:** User request: merge #40 and start offline saving; inherited branch implements transaction adds, edits and deletes, pending indicators, retry and discard.

## Global Constraints
- Keep agent/offline-saving; commit small steps and open a PR.
- No local Gradle or Android builds; run checks through GitHub Actions.
- Save phone screenshots in .bridge-shots/.
- Category-only bulk changes and other budget settings remain online operations.

## Review Focus
- Switching spreadsheets must never send an old queued transaction to a new sheet.
- Missing row IDs must not permit delayed edits to an unrelated row.
- Closing the app must leave durable background work scheduled.
- A failed refresh must not make a saved transaction disappear from the local overlay.
- A refused change must block subsequent changes to that transaction from overtaking it.

### Task 1: Protect queued transactions
**Files:** PendingChangeEntity.kt, BudgetrDatabase.kt, SheetsRepositoryImpl.kt, OfflineSavingTest.kt (under app/src/main or test/java/com/budgetr/app).
**Interfaces:** Existing repository add/update/delete and pending Flow APIs stay unchanged; queue entries gain spreadsheetId.
- [x] Add real Room/repository regression tests for sheet isolation and missing IDs.
- [ ] Observe expected failures in CI.
- [ ] Bind queue entries to a spreadsheet and reject unsafe edits before queueing.
- [ ] Retain pending entries until refresh succeeds; block dependent changes after failure.
- [ ] Run full CI and commit fixes.

### Task 2: Durable sending and visual verification
**Files:** PendingSyncWorker.kt, SheetsRepositoryImpl.kt, .bridge-notes.md.
**Interfaces:** schedule(context) persists follow-up work before enqueue returns; worker retries transient errors.
- [ ] Schedule work on save/retry with APPEND_OR_REPLACE so active workers cannot swallow new requests.
- [ ] Verify offline save and queue behaviour in repository tests.
- [x] Download and inspect 360×780, 412×915 and dark-mode pending indicators.
- [ ] Run full CI, inspect remaining screenshots, document limitations, push and open PR.
