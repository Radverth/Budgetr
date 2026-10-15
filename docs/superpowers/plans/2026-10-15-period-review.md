# End-of-period Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At payday, show the completed period's spending against its saved plan, category results and days under target, then let Tom assign available leftover money once.

**Architecture:** Capture an immutable review before payday changes or removes transactions. Keep review snapshots and allocation receipts in Room and the existing phone backup; reuse the existing daily-target and envelope calculations. Present a screen reachable from Home's attention list and the Budget hub. An allocation is a budget bookkeeping operation, not a bank transfer.

**Tech Stack:** Existing Kotlin, Compose Material 3, Room, Hilt, coroutines, Moshi, JUnit and Roborazzi. GitHub Actions performs all Gradle work.

**Spec:** `.bridge-plan.md`, Phase 3 item 19. This document defines the implementation details below; the roadmap remains unchecked until the feature is delivered.

## Global Constraints

- Stay on `agent/period-review`; never commit to main, force-push or change repository settings.
- Never run Android or Gradle builds locally. Run lint, tests and debug APK builds in GitHub Actions.
- Keep the existing workflow triggers for pushes to `agent/**`, main and pull requests.
- Preserve transactions dated in the new period; never allocate the same remainder twice after retry, restart or restore.
- Save and inspect phone screenshots in `.bridge-shots/` at 360 and 412 dp in light and dark.
- No new dependencies or unrelated payday refactoring.

## Confirmed starting point

- PR #47 is merged (`a594ac8`); its PR checks passed. The branch initially has no partial feature edits.
- `SheetsRepositoryImpl.checkAndProcessNewPayPeriod()` snapshots History, rolls account balances forward, re-dates recurring rows, rolls envelopes forward, then removes one-off costs.
- History currently stores totals and category spend, but no plan, daily targets or allocation receipts. Historical reviews cannot honestly reconstruct those missing figures.
- `DailyTargetRepository` stores only one period's pot in preferences. Its observer can replace that value at payday before the rollover snapshot reads it. Retain period-keyed plan inputs before enabling the review.
- Account rollover already includes leftover cash. A review must not add the same cash to account balances again.
- Goal updates are online absolute writes and currently address rows by cached row number. A review contribution must resolve the goal by name immediately before writing and keep a durable receipt for ambiguous network outcomes.

## Product behavior

- First install: explain that the first full review appears after the next completed pay period. Existing History stays available; do not invent historical targets.
- Review: inclusive date range; one-off spending versus the saved daily pot; fixed costs separately; planned categories ranked by monetary variance, with unbudgeted spending listed separately. Label the strongest result “Most under budget” and the weakest “Most over budget”; omit either label when no category qualifies.
- Days under target use the existing carried daily targets, borrowing/pay-forward moves and budgeted-category inclusion setting. Include zero-spend days. Explain that these are the targets calculated from the saved closing plan, not a log of every target displayed during the month.
- If closing plan data is unavailable, show actual totals and “Plan wasn't saved for this period”; omit comparisons and daily success counts.
- Leftover choices: keep available for next period, contribute to an existing goal, or add to an existing category's carry-over. Support one chosen destination and amount, with the remainder kept for next period. Show a confirmation with amount and destination before applying.
- Leftover eligibility is bounded by positive saved end balance less automatically reserved category carry-over, and by currently available unreserved carried cash. Exclude income belonging to the new period and queued/unreconciled transactions. Block allocation until payday processing and pending sync complete. A deficit offers no allocation.
- Old reviews remain readable; only the most recently completed period can allocate into the current period. Completed allocation receipts are read-only. Closing a review without choosing changes no money.
- A goal contribution updates its saved total and reserves that amount out of spendable carried cash. Category allocation adds to carry-over and relies on the existing envelope reserve. Keeping cash for next period must not credit account balances again.

## Review Focus

1. Payday observer races: the old pot and budget inputs survive new-period observers and process restart.
2. Partial rollover/network failure: the snapshot is immutable, the new period's transactions survive, and retry cannot duplicate a contribution.
3. Already reserved or spent money: automatic envelope carry and current spending reduce what can be allocated; no invented money or negative eligible amount.
4. Missing historical plan, skipped months and first install: honest unavailable states instead of fabricated comparisons.
5. Backup restore, renamed/deleted goals and concurrent edits: no replay of completed allocations and no write to a stale row.

### Task 1: Review calculations and preserved closing inputs

**Files:**
- Create `app/src/main/java/com/budgetr/app/data/model/PeriodReview.kt`.
- Create `app/src/main/java/com/budgetr/app/util/PeriodReviewCalculator.kt`.
- Create `app/src/test/java/com/budgetr/app/util/PeriodReviewCalculatorTest.kt`.

**Interfaces:**
- `ReviewPlan(periodStart: String, pot: Double?, envelopes: List<Envelope>, countBudgeted: Boolean)`.
- `PeriodReviewCalculator.calculate(period: PayPeriod, transactions: List<Transaction>, plan: ReviewPlan?, transfers: List<DailyTransfer>, endBalance: Double): PeriodReview`.
- `PeriodReview` contains period bounds, actual spending, nullable plan/daily metrics, category variances, end balance and reserved carry. Persist money decisions in pennies to avoid fractional-penny allocations.
- `eligibleLeftover(savedBalance: Long, reservedCarry: Long, availableNow: Long): Long` returns the nonnegative minimum of unreserved saved balance and availableNow.

- [ ] Write failing tests: £100 planned / £80 spent = £20 under; £80 planned / £100 spent = £20 over; category limits include previous carry; unbudgeted tags are not ranked as budget failures.
- [ ] Add tests for half-open period dates, zero-spend days, borrowing/pay-forward, excluded budgeted categories, clock-change dates, absent plan and negative balance. For eligibility, 10000 saved minus 3000 reserved with 5000 available now yields 5000 pennies; repeat with negative inputs yields zero.
- [ ] Push tests and minimal compiling interfaces to the branch; watch CI fail on behavioral assertions before implementing calculations.
- [ ] Implement by composing `HistoryCalculator`, `DailyTargetCalculator` and `EnvelopeCalculator`; avoid duplicating their date/spend semantics.
- [ ] Push and watch CI pass the full suite, lint and debug build. Commit the calculation task separately.

### Task 2: Durable review snapshot and payday integration

**Files:**
- Create `data/local/entity/PeriodReviewEntity.kt`, `data/local/dao/PeriodReviewDao.kt`, and `data/repository/PeriodReviewRepository.kt` under the existing app package.
- Modify `data/local/BudgetrDatabase.kt` and `di/DatabaseModule.kt` for migration 14→15.
- Modify `data/repository/DailyTargetRepository.kt`, `ui/screens/plan/PaydayPlanViewModel.kt`, and `data/repository/SheetsRepositoryImpl.kt`.
- Modify `data/backup/PhoneBackup.kt` and `data/backup/BackupRepository.kt`.
- Create `app/src/test/java/com/budgetr/app/data/repository/PeriodReviewRepositoryTest.kt`; extend `data/backup/BackupCodecTest.kt`.

**Interfaces:**
- Store a period-keyed plan and an optional immutable closing snapshot plus allocation state. Include spreadsheet identity in local keys so switching Sheets cannot expose another budget's review.
- `observeReviews(): Flow<List<PeriodReview>>`; `savePlan(plan: ReviewPlan)`; `captureBeforeRollover(...)` uses insert-if-absent for the closing snapshot.
- Repository depends on DAOs/preferences, not `SheetsRepository`, to avoid a dependency cycle when rollover invokes it.

- [ ] Write failing repository tests for preserving the previous plan while the next period starts, capturing once across retry, first install, multiple missed periods, and pre-existing new-period transactions. Skipped periods must show unavailable comparisons rather than applying one month's plan to several months.
- [ ] Write migration tests preserving existing envelopes and transactions, and backup round-trip/backward-compatibility tests including allocation receipts.
- [ ] Save period-keyed plan inputs when the daily pot is established/changed and a payday plan is saved. Capture current envelope limits/settings at close before rollover modifies them; do not overwrite a closed snapshot.
- [ ] Capture only reconciled closing data before re-dating or deleting rows. If pending writes or a failed refresh make data incomplete, postpone payday processing and show its existing error path.
- [ ] Complete the snapshot only after successful rollover; interrupted snapshots cannot offer allocation. Restrict deletion to ending-period one-offs if the current helper does not already do so.
- [ ] Back up snapshots and allocation receipts together with existing rollover/envelope state. Restore must not rerun an allocation or restore a stale processed-payday marker.
- [ ] Push, watch the full CI result, and commit persistence separately.

### Task 3: Apply one leftover decision safely

**Files:**
- Extend `PeriodReviewRepository.kt` and `PeriodReviewDao.kt`.
- Create `data/repository/ReviewAllocationRepository.kt` to coordinate the review with Sheets goal writes.
- Extend `data/repository/SheetsRepository.kt` and `SheetsRepositoryImpl.kt` for a contribution operation that resolves the goal by name and accepts a durable expected/desired saved balance.
- Extend `PeriodReviewRepositoryTest.kt`; create `ReviewAllocationRepositoryTest.kt`.

**Interfaces:**
- `allocate(periodStart: String, destination: ReviewDestination, amountPence: Long)`.
- Destination is `NextPeriod`, `Goal(name)` or `Category(tag)`; no arbitrary destination strings.
- Persist allocation intent before remote work: stable operation ID, destination, amount, expected/desired goal totals and completion state. Retrying uses that intent, never adds the amount to the latest goal total again.

- [ ] Write failing tests for repeat taps, restart after a lost goal-write response, deleted/renamed goal, changed goal balance, stale review, unavailable funds, and existing envelope carry.
- [ ] Revalidate eligibility and destination at commit time. Serialize decisions; record local balance/category changes and completion in one Room transaction.
- [ ] For goals, read current Sheet rows by name: expected total permits the absolute write; desired total permits finishing the interrupted operation; a different total requires reconciliation and must not be overwritten silently.
- [ ] Keep uncertain goal writes locked to their original intent until reconciled. Never permit switching destination and allocating the same money while remote outcome is unknown.
- [ ] Keep cash already in account rollover for next period without adding it again. Reserve goal money once; add category carry once. Verify Home, daily target and cash-flow consumers consistently reflect the resulting available money.
- [ ] Push and watch CI pass; commit the allocation task separately.

### Task 4: Review screen, entry points and phone checks

**Files:**
- Create `ui/screens/review/PeriodReviewViewModel.kt` and `PeriodReviewScreen.kt`.
- Modify `ui/navigation/NavRoutes.kt` and `MainScreen.kt`.
- Modify `ui/screens/home/HomeViewModel.kt`, `HomeScreen.kt`, `HomeSections.kt`.
- Modify `ui/screens/budgethub/BudgetHubScreen.kt` and `BudgetHubViewModel.kt`.
- Extend `app/src/test/java/com/budgetr/app/screenshots/ScreenshotTests.kt` and add allocation interaction tests.

**Interfaces:**
- `PeriodReviewUiState` exposes loading, missing-plan, ready, saving, unresolved-write, completed and error states.
- `PeriodReviewContent(state, onChooseDestination, onConfirm, onDismiss)` is independent of Hilt for screenshots.
- Home shows “Your period review is ready” only after successful rollover; dismissal hides the prompt, not the saved review. Budget always offers “Period review”.

- [ ] Write UI tests proving confirmation is required, the confirm button disables while saving, unavailable funds cannot submit, and errors retain the original intent for retry.
- [ ] Implement totals, category comparisons, daily count, destination/amount selection and completed receipt. Show progress and plain-English errors. Use existing typography and cards.
- [ ] Add screenshots for ready review, missing-plan/empty state, overspent period, destination selection and completed receipt at both existing phone sizes/themes. Include long goal/category names.
- [ ] Push; run `gh run watch <id> --exit-status`. For failures read `gh run view <id> --log-failed`, fix, and push again.
- [ ] Download the `screenshots` artifact to `.bridge-shots/`, inspect all new screenshots, fix clipping/overlap and repeat CI if needed. Report any dialog rendering limitations honestly.
- [ ] Complete a fresh whole-branch review, update `.bridge-plan.md` and `.bridge-notes.md`, and update the PR from planning to the delivered feature. Report CI run link and remaining phone-install checks.

## Execution status

Planning only. No app code, schema or on-screen behavior changed yet. Implementation awaits the planning skill's required plan review and execution-method choice.
