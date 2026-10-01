# Main feature audit — 1 October 2026

Main at `61791731ae7d1732b4634deb2412d438ca60a214` and release `v1.0.15` have identical app trees. Both lack the seven additions recorded in the handoff. This is a merge-target problem, not evidence that the phone failed to update.

| PR | Feature missing from main | Status / target |
| --- | --- | --- |
| #23 | Safe-to-spend Home card and widget | Open → main |
| #24 | Pay-period budget caps and reset dates | Open → agent/safe-to-spend |
| #25 | Spending categories | Open → agent/payday-budget-caps |
| #26 | Category budgets and rollover | Open → agent/spending-categories |
| #27 | Spending history and suggestions | Open → agent/envelopes |
| #28 | Payday allocation plan | Open → agent/spending-history |
| #29 | Reserve unspent category budgets from safe-to-spend | Merged → agent/payday-plan |

## Evidence

- GitHub reports #29 merged at 15:29 UTC into `agent/payday-plan`, not main. #23–#28 remain open.
- Main's navigation has no history or payday-plan routes. Its source tree has no Envelope, History, PayPeriod, PaydayPlan or SpendTags calculator files. Its budget calculator still lacks the pay-period filter.
- The cumulative feature branch includes all seven features and the review-fix commits `e10a8ba`, `42f5984`, `b4b0f7a`, and `a15fd3d`; none of those feature commits is an ancestor of main.
- Before this audit, `agent/safe-to-spend-envelopes` and `origin/agent/payday-plan` have identical trees, despite different merge histories.
- Main's successful release run [36886115834](https://github.com/Radverth/Budgetr/actions/runs/36886115834) started from the old code (`21ea4f9`) and published v1.0.15. A successful build does not imply the features were merged.
- The cumulative feature code passed [PR Check 36884686792](https://github.com/Radverth/Budgetr/actions/runs/36884686792). That workflow runs lint, unit tests and a debug build on agent pushes and PRs. Main uses the separate release workflow, which currently builds only a release APK.

## Route to main

With the current PR targets, propagate the cumulative changes from the top of the stack down: **#28 → #27 → #26 → #25 → #24 → #23**, checking conflicts and CI at each step. #29 is already merged. Merging in ascending order does not bring later additions back into main automatically.

Alternatively, prepare one consolidated integration PR targeting main and close the superseded stack after it merges. This audit does not merge or retarget any feature PR.

Keep main's current version metadata when resolving integration: the feature branch still has versionCode 15 / versionName 1.0.14, while main has 16 / 1.0.15. The next release must advance beyond the installed version. Before using categories, check the handoff's requirement that column G of each account sheet is available. History begins at the next processed payday; it is not retroactive.

This is a source/history audit, not a new runtime or phone-screen validation. No application code was changed. The audit PR targets `agent/payday-plan` so its diff contains documentation only; it does not itself deliver the missing features to main.
