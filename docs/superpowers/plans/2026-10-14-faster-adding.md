# Faster Adding Implementation Plan

> Execute inline using superpowers:executing-plans; the unattended bridge request authorizes implementation and delivery.

**Goal:** Complete roadmap item 4 from `.bridge-plan.md` after merging #37.
**Architecture:** Keep the shared add/edit form and QuickAddActivity. Derive suggestions from cached transactions; persist the last successful source account. Use launcher shortcut and Quick Settings tile to open the same expense flow.
**Tech Stack:** Kotlin, Compose, SharedPreferences, Android shortcuts/TileService, JUnit/Robolectric/Roborazzi.
**Spec:** `.bridge-plan.md`, item 4.

## Constraints
Keep existing agent branch; small commits per change. No local Gradle/Android builds. Run tests, lint, APK build and screenshots in Actions. Save reviewed images in .bridge-shots/.

## Review focus
Deleted accounts fall back safely; empty descriptions and transfers do not become unsafe templates; edits retain their original fields; suggestion selection keeps the entered amount; external launch without setup opens setup, and save errors remain visible.

## Tasks
- [x] Add failing account fallback tests in TransactionsSelectionTest, push and observe CI failure.
- [x] Add remembered account in PreferencesManager and TransactionsUiState, update it only after successful additions/transfers. Commit.
- [x] Add recent/frequent suggestion derivation and tests (distinct descriptions, latest category/account, invalid accounts and blank descriptions excluded). Put amount first with decimal keyboard/focus; chips fill description/category/account/tag but never amount. Share the primary fields with screenshot coverage. Commit.
- [x] Add static launcher shortcut and TileService using QuickAddActivity; guard missing setup and show loading/errors. Add resource/intent tests. Commit.
- [ ] Run full CI, download and inspect 360/412dp light and dark screenshots; fix issues, review diff and open PR. Update handoff notes.

## Verification record
CI is the sole Gradle runner per bridge rules.
- RED: run 38642194139 ran 142 tests: 4 expected failures (account fallback and missing suggestions), 3 existing ignored dialog tests.
- Final review found and fixed filtering after limiting, warm-launch handling and premature missing-account feedback. Follow-up review found no significant issues.
- Full final CI and phone screenshot review pending; final outcome is recorded in the PR and .bridge-notes.md.
- Scope decisions: retain the existing agent branch; remember accounts per spreadsheet; show at most six description chips; existing dialog screenshot limitation means entry controls are captured separately.
