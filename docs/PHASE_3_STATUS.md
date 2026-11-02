# Phase 3 — complete

Date: 2026-11-02. Required gate: **YES**. Phase 4 has not started.

## Scope and implementation

- Overall and expense-category allocations by strict YYYY-MM month, including
  zero, editing, confirmed removal and persistent IDs/timestamps. Removing an
  overall cap keeps category allocations; all removals preserve transactions.
- Category allocations share an optional overall cap. Repository checks and
  writes are atomic, and database guards enforce the same cap. Lowering a cap
  below existing allocations is rejected without changing stored values.
- Budget actuals derive from consumption transactions through the ledger's
  reporting date. Unallocated/archived categories count; income, investment
  contributions and future-dated transactions do not inflate actual spending.
- Remaining amounts may be negative. Utilization/status, recorded daily average,
  month-end projection and daily budget allowance have one pure domain owner,
  BudgetCalculator. No formula or database access lives in a Composable.
- Budget screen supports month selection, previous/next, applied reporting date,
  Refresh/resume, actuals, error recovery, editor retention on failure and
  duplicate-submit protection. Room invalidation updates transaction-derived
  values. Home retains Phase 2 functionality; no Phase 4 dashboard was added.
- Schema v3 adds monthly_budget and category_budget, unique keys/FKs/indexes and
  fresh-create/migration guards. Migration 2→3 chains from 1→2 and leaves all
  ledger/category/transaction schema definitions unchanged. Category deletion
  now also protects budget references.
- No production dependencies, network permissions, backend or credentials added.
  App version 0.3.0, version code 3.

## Calculation contracts

Consumption and allocation sums use exact minor units and BigInteger. Aggregate
actuals beyond Long are a visible OUT_OF_RANGE error. Projection-only overflow
keeps actuals available and labels the projection unavailable. The Long bound is
checked explicitly before BigInteger conversion to support Android 8/API 26.

Status uses exact integer comparisons: spending above allocation is OVER_BUDGET;
otherwise HIGH at 95%, WATCH at 80%, SAFE below 80%. Explicit zero allocation
with zero spending is NEUTRAL, with undefined utilization. Missing allocation is
not interpreted as zero. Displayed utilization truncates to two decimals to avoid
rounding into a different threshold.

Average and projection round half-even to minor units. Projection computes
expenses × month days ÷ elapsed days once from the original ratio, never from a
rounded daily average. Daily allowance floors max(remaining, 0) ÷ days left,
including today; closed months have zero days left and zero allowance.

No-record covered periods legitimately have zero spending. Future/untracked
months have unknown actuals. Months whose tracking begins after the first day
show recorded amounts with a partial-coverage message, and withhold full-month
projection/daily allowance. The Budget allowance is not spendable cash; cash
limits and reservations belong to the later Home decision workflow.

## Verification

Commands ran in the actual restored Codex cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
# Executed the restored Phase 2 JVM suite afresh before edits:
./gradlew --no-daemon --console=plain :app:testDebugUnitTest --rerun
# Final Phase 3 checks:
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Restored source/JDK/SDK/dependency cache | Retained; initial build and 49 restored JVM tests passed |
| Debug application and Android test APK | PASS |
| JVM tests | 75 tests; 0 failures, errors or skips |
| Android tests | 17 tests; 0 failures, errors or skips |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories, not suppressed |
| Schema review | All v2 ledger/category/transaction definitions unchanged in v3 |
| APK signature | Verified; same public signing certificate as delivered Phase 2 APK |
| Actual APK update/restart | Phase 2→3 install succeeded; schema 2→3 and original ledger/categories preserved; ready Home/Budget verified |
| Manifest | Version 0.3.0/code 3; min SDK 26, target SDK 36; no INTERNET permission |

The 26 new JVM tests cover month validation, no budget versus zero budget,
positive/negative remaining, exact 80%/95% thresholds, first/final/closed months,
leap/non-leap February, 31-day months, single half-even rounding, category/type/
date filtering, unallocated spending, partial/future/untracked coverage, overflow,
source updates, failed-save recovery, invalid input, duplicate submits and retry.

Six new Android tests cover durable allocation CRUD, update/deletion of source
transactions, category archival/reference safety, concurrent cap enforcement,
large sums, raw database constraints, actual v2-schema migration/reopen, and the
full UI flow. The UI test creates a real expense through the transaction form,
sets overall/category budgets, checks actual/remaining values and overspending,
recreates the Activity, rejects a too-small cap, switches months and removes
allocations. All 11 previous Android tests still pass, including chained
v1→v3 migration.

Device: fresh task-owned Android 8.0/API 26 x86_64 software emulator, 720×1280
at density 320 (360dp width). The original restored AVD and its data were retained.

## APK

File: `/workspace/artifacts/finance-planner-phase3-debug.apk`
(12,413,573 bytes).

SHA-256:
`be7b2a1a8f42d00c47b16be2ddbf612a6549a126f9b2ff320b9145c71bd24412`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

The checksum file and machine-readable test summary are alongside the APK:
`finance-planner-phase3-debug.apk.sha256` and `phase3-test-summary.json`.
The delivered APK's bytes match the APK used by the complete Android suite.
An additional actual update installed the delivered Phase 2 APK, initialized its
database, then installed Phase 3 with `adb install -r`. Both launcher requests
returned Status: ok. Database snapshots before/after confirmed version 2→3 and
identical original ledger/category/transaction rows; restart reached ready Home.
The Budget screenshot is `/workspace/artifacts/finance-planner-phase3-budget.png`.

## Limits and next phase

This is a debug development APK. Physical ARM devices, API 35/36 runtime behavior,
release signing/minification and Android Studio IDE sync remain unverified.
Amounts are single-ledger IDR; previous-history import/rebasing and full currency
settings retain their roadmap phases. Forecasts, reservations, cash-aware Home,
goals, market feeds and export/import were not added.

The published environment's files and toolchain were verified after restoration.
Existing Phase 2 changes were preserved, with a local baseline archive before
editing. Environment configuration and publication were not changed in this task.
Publishing a new snapshot is separate from this code/test result.

**Phase 3 is ready. Stop here; Phase 4 requires a new instruction.**
