# Phase 4 — complete

Date: 2026-11-02. Required gate: **YES**. Phase 5 has not started.

## Implemented scope

- Home now shows recorded cash, current-month income/consumption/contributions,
  income minus consumption and its saving rate, current budget use/remaining,
  a cash-and-budget daily limit, top three spending categories and five recent
  transactions. Labels disclose reporting date, coverage and source limitations.
- Cash includes signed opening cash and all covered records through the reporting
  date; prior-month activity affects cash, not current-month flows. Contributions
  reduce cash separately from consumption. Future-dated entries cannot fund
  today's cash or spending limit.
- The daily limit is floor(min(max(cash,0), max(remaining budget,0)) / days left
  including today). Missing overall budget, incomplete current-month tracking
  or a reporting date before tracking yields unavailable, not a fabricated zero.
  No goal/bill reservations exist yet; the UI explicitly excludes them.
- HomeDashboardCalculator owns these calculations and delegates budget metrics
  to Phase 3's BudgetCalculator. Exact BigInteger sums convert to signed Long
  only after range checks. Overflow/storage failures clear old totals.
- HomeViewModel observes the existing transactional financial snapshot plus
  category labels; category rename/archive does not change historical amounts.
  Resume/Refresh uses the injected clock in the ledger's reporting zone.
- Quick add/history/category actions use existing workflows. A Home budget link
  opens the reporting month even after another month was selected in Budget.
  Restored month-input drafts are synchronized with that requested month.
- Later reports/charts, forecasts, goals progress, net worth and market values
  remain explicitly unavailable. No placeholders present invented financial values.
- Version 0.4.0/code 4. Schema v3, source tables, migration chain, dependencies
  and permissions are unchanged. No persisted aggregate counters were introduced.

## Verification

Commands run in the actual Codex cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
# Fresh Phase 3 baseline before edits:
./gradlew --no-daemon --console=plain :app:testDebugUnitTest --rerun
# Reviewed Phase 4 build/tests:
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Baseline JVM suite | 75 tests passed before Phase 4 edits |
| Debug application and Android test APK | PASS |
| Current JVM suite | 104 tests; 0 failures, errors or skips |
| Targeted UI regressions | 3 tests; 0 failures or skips |
| Final complete Android suite | 19 tests; 0 failures, errors or skips |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories; none suppressed |
| Schema review | v1/v2/v3 exports and database implementation unchanged |
| APK signature | Verified; same public debug certificate as delivered Phase 3 APK |
| Actual APK update/restart | Phase 3→4 install succeeded; all five database tables preserved; Home ready after restart |
| Manifest | Version 0.4.0/code 4; min SDK 26, target SDK 36; no INTERNET permission |

The 29 new JVM tests cover zero/negative/overflow cash, historical carry-in,
investment-versus-consumption accounting, zero-income and negative saving rates,
cash/budget caps, missing/category-only/zero budgets, partial or absent coverage,
future transactions, leap-day boundaries, deterministic bounded rankings,
invalid source data, source updates, category labels, retry, missing ledger,
and reporting-zone midnight/month rollover without database writes.

Two new Android tests cover the persisted-source dashboard across transaction
and budget edits/deletion, category archive/rename and database reopen, and the
full Home UI flow. The UI creates income/expense/contribution records, checks
derived amounts, sets an overall budget, changes Budget's selected month,
reopens the correct month from Home, recreates the Activity, edits/deletes
records and checks unavailable future metrics.

Testing exposed a restored Budget month field retaining an old input despite
Home requesting the current month; the field now synchronizes with applied
state. UI tests scroll to actionable buttons and await database-backed
archive/restore state and enabled controls. Assertions remain intact; interrupted
or earlier failing attempts are not counted as successful final runs.

Device coverage: Android 8.0/API 26 x86_64 software emulator, 720×1280 at
density 320 (360dp width).

## APK

File: `/workspace/artifacts/finance-planner-phase4-debug.apk`
(12,539,672 bytes).

SHA-256:
`b086db479cf004597e821793825522cfaf0961ee1fcccda45d388d40acc83894`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

Its checksum file is `finance-planner-phase4-debug.apk.sha256`.
Machine-readable final counts are in `/workspace/artifacts/phase4-test-summary.json`.
The delivered bytes match the APK used by the final full Android suite.
An actual update smoke test installed Phase 3, initialized its ledger/categories,
then installed Phase 4 with `adb install -r`. Before/after database snapshots
retained schema v3 and identical rows in ledger, category, finance_transaction,
monthly_budget and category_budget. This initialized-baseline smoke had no
transaction/budget rows; nonempty persisted records are covered by HomeStorageTest
and the full UI test. Both installs and launcher requests succeeded, and restart
reached ready Home.

Screenshots: `finance-planner-phase4-home.png` and
`finance-planner-phase4-daily.png` in `/workspace/artifacts`.

## Limits and phase boundary

This is a debug development APK. Physical ARM devices, API 35/36 runtime behavior,
release signing/minification and Android Studio IDE sync remain unverified.
The recorded daily limit is not a bank balance or a guarantee of affordability.
Goals, bill reservations, redemptions/holding links, full currency settings,
reporting/charts, forecasts, net-worth valuations and market feeds retain their
own roadmap phases.

Existing Phase 3 work was preserved, with a local source baseline archive before
edits. No environment configuration, publication or repository push was performed;
new-task restoration of these Phase 4 edits is not claimed.

**Phase 4 is ready. Stop here; Phase 5 requires a new instruction.**
