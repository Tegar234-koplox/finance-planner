# Phase 5 — complete

Date: 2026-11-02. Required gate: **YES**. Phase 6 has not started.

## Implemented scope

- Home → Reports opens weekly, monthly and yearly reports. Weeks run Monday to
  Sunday; Previous/Next/Today and strict YYYY-MM-DD date input select the period.
  Refresh/resume uses the current clock in the ledger's reporting zone.
- Reports derive income, consumption, investment contributions, income minus
  consumption, saving rate and net cash flow from saved transactions. Opening
  cash is excluded; net cash flow is not a balance. Contributions reduce net
  cash flow separately from consumption and saving. Zero income has no rate.
- RecordedFlowCalculator shares period accounting with Home. BigInteger sums
  and checked signed Long conversions preserve exact money. A net-flow overflow
  leaves valid gross totals visible; a selected gross-total/storage failure
  clears the report. An overflowing prior period disables only comparison.
- Complete periods compare complete prior calendar periods, with differing
  lengths disclosed. In-progress periods compare equal elapsed calendar days.
  Incomplete current/prior history, a shorter prior period or an arithmetic limit
  makes comparison unavailable. Prior zero income/consumption has no percentage
  change. Dates and comparison rules are shown alongside the results.
- Coverage distinguishes complete, in-progress, partial, untracked and future
  periods. Unknown ranges have no totals or zero bars; covered empty ranges have
  explicit zero values. Future records remain excluded until their date.
- The optional time chart groups by day for week/month and month for year.
  Income, consumption and contributions share a zero-based amount scale, exact
  text labels and native Material bars. No value depends on color, hover or a
  tooltip. Category bars show every recorded consumption category, including
  archived/unallocated categories, with exact amounts and rounded-down shares.
- Lazy native Compose rows bound temporal chart marks to 21, 93 or 36.
  Reading order is period/coverage → totals → comparison → chart → categories.
  Decorative bars do not duplicate TalkBack announcements. Choose date and Show
  chart disclose additional content; Activity recreation preserves the selected
  period and disclosure state. Input errors retain the labeled applied period;
  load errors clear stale totals/charts and provide retry.
- Version 0.5.0/code 5. Schema v3, all storage implementations, migrations,
  dependencies and permissions are unchanged. Reports do not write financial
  data or persist aggregate counters. Forecasts remain Phase 6.

## Verification

Commands run in the actual Codex cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
# Fresh Phase 4 baseline before edits:
./gradlew --no-daemon --console=plain :app:testDebugUnitTest --rerun
# Phase 5 build and checks:
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Baseline JVM suite | 104 tests passed before Phase 5 edits |
| Debug application and Android test APK | PASS |
| Final JVM suite | 137 tests; 0 failures, errors or skips |
| Targeted Reports storage/UI suite | 2 tests; 0 failures, errors or skips |
| Final complete Android suite | 21 tests; 0 failures, errors or skips |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories; none suppressed |
| Schema/storage review | v1/v2/v3 exports, source tables, repositories and migration chain unchanged |
| APK signature | Verified; same public debug certificate as delivered Phase 4 APK |
| Actual APK update/restart | Phase 4→5 preserved identical rows in all five tables, including a nonempty transaction; Home/Reports ready after restart |
| Visual smoke | Reports and positive-value chart inspected at 360dp width in light/dark themes |
| Manifest | Version 0.5.0/code 5; min SDK 26, target SDK 36; no INTERNET permission |
| Patch/artifact integrity | git diff --check passed; delivered APK matches the final build and checksum |

The 33 additional JVM tests cover Monday weeks, leap months, year/month rollover,
complete versus elapsed-period comparisons, prior periods that are too short,
partial/missing/future history, zero income/zero denominators, signed changes,
opening-cash exclusion, contributions, bucket reconciliation, deterministic
category ranking/shares, overflow isolation, currency/date validation, selection,
source/category updates, retry and ledger-zone midnight. Home's shared accounting
has regression coverage, including opening cash offsetting a net-period overflow.
The Reports ViewModel fixtures fail on any attempted financial write.

ReportsStorageTest exercises real Room records across months, comparison,
transaction edits/deletion, category rename/archive and database reopen.
ReportsUiTest creates income/expense/contribution records through the UI, checks
totals/rates and chart labels, switches Week/Month/Year, recreates the Activity,
selects unknown future history, rejects an invalid date, returns to Today and
checks zero consumption after deleting the records. Existing navigation,
transaction, budget, Home, migration and persistence tests also pass. Earlier
automation attempts are not counted as successful final runs.

Device coverage: Android 8.0/API 26 x86_64 software emulator, 720×1280 at
density 320 (360dp width). Exact labels, source values and unknown-history text
were inspected in both themes. Full TalkBack operation, large font and physical
device testing remain outside this verification.

## APK and upgrade evidence

File: `/workspace/artifacts/finance-planner-phase5-debug.apk`
(12,627,668 bytes).

SHA-256:
`5960a3127f2994b39ef38c7a7a33188bba503a643ea2f2b50afd2064122ccd93`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

Checksum: `/workspace/artifacts/finance-planner-phase5-debug.apk.sha256`.
Machine-readable counts and upgrade results:
`/workspace/artifacts/phase5-test-summary.json`.
The delivered bytes match the application APK used by the final full Android
suite and the subsequent actual-APK upgrade smoke.

The upgrade smoke installed the delivered Phase 4 APK and entered one synthetic
Rp10,000 expense through its UI, then installed Phase 5 with `adb install -r`.
Before/after snapshots retained schema v3 and identical rows in ledger (1),
category (11), finance_transaction (1), monthly_budget (0) and category_budget (0).
The expense appeared in Reports and its chart. Restart succeeded; light/dark
theme navigation retained the report. The synthetic transaction was deleted
through the UI afterward, the database was checked empty of transactions, and
the appearance preference was restored to Follow system.

Screenshots under `/workspace/artifacts`:

- `finance-planner-phase5-report-light.png`
- `finance-planner-phase5-chart-light.png`
- `finance-planner-phase5-chart-dark.png`

These screenshots show synthetic emulator test data, never user financial data.
No example transactions are bundled in the APK.

## Try the delivered build

Install the APK as an update to the previous debug build. Open Home → Reports,
switch Week/Month/Year, and use Choose date to inspect an earlier or future
period. Show chart reveals labeled bars. Add or edit a transaction through Home
and return to Reports to verify its amounts update. A newly created ledger has
no earlier tracking coverage, so earlier comparisons correctly remain unavailable.

## Limits and phase boundary

This is a debug development APK. Physical ARM devices, API 35/36 runtime behavior,
release signing/minification and Android Studio IDE sync remain unverified.
Reports describe recorded data, not a bank balance, a forecast or advice.
Complete months/years can differ in length; this is disclosed, not normalized
away. Charts do not invent history before the ledger's tracked-from date.
Goals, asset valuations, investment holdings, market feeds, exports and forecasts
retain their own roadmap phases.

Existing Phase 2–4 work was preserved, with a local Phase 4 source baseline before
edits. No environment configuration, publication or repository push was performed;
new-task restoration of these Phase 5 edits is not claimed.

**Phase 5 is ready. Stop here; Phase 6 requires a new instruction.**
