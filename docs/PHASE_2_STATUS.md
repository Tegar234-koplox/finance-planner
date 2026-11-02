# Phase 2 — complete

Date: 2026-11-02. Required gate: **YES**. Phase 3 has not started.

## Implemented

- Transaction creation, editing and confirmed deletion; stable IDs/creation
  timestamps, exact positive IDR minor units, notes and calendar dates.
- Newest-first history with combined inclusive date, type and category filters,
  explicit applied-filter labels, clear/reset, empty/loading/error states.
- Category creation, rename, archive, restore and unused-category deletion.
  Names are normalized and unique per type, including archived names. Type is
  immutable. Referenced categories cannot be deleted. Archived categories remain
  visible in history and may be retained when editing existing transactions.
- Home entry points to Transactions/Categories; lifecycle-aware ViewModel state,
  visible write errors, duplicate-submit protection and cancellation controls.
- Room v2 transaction table, ledger FK, composite category/type FK and indexed
  history. Explicit 1→2 migration preserves every v1 ledger/category field.
  Identical fresh-create/migration triggers protect positive amounts, coverage
  dates and archived-category use. No destructive migration.
- App version 0.2.0, version code 2. No new production dependencies or network
  permissions. Budget calculations and dashboard totals remain later phases.

## Validation

All commands ran in the actual Codex cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Current result |
| --- | --- |
| Debug APK and Android test APK | PASS |
| JVM suite | 49 tests; 0 failures, errors or skips |
| Android suite, API 26/x86_64 software emulator | 11 tests; 0 failures, errors or skips |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories; none suppressed |
| Debug APK signature | Verified, APK Signature Scheme v2 |
| Delivered APK install/process restart | Install Success; both launcher requests Status: ok; ready Home UI confirmed |
| APK manifest | Version 0.2.0/code 2, min SDK 26, target SDK 36; no INTERNET permission |
| Exported schema review | v1 ledger/category definitions unchanged in v2 |
| Git whitespace review | PASS |

The 15 added JVM tests cover amount/currency/date/category validation, archival,
note limits, leap-date ranges, normalized category names, write-error recovery,
invalid forms/filters, duplicate submission and observation retry.

The seven added Android tests cover transaction CRUD/filter/reopen, category
rename/archive/restore/reference safety and concurrent duplicates, direct database
constraint enforcement, actual v1-schema migration/reopen, Android IDR decimal
parsing/formatting, and both transaction and category UI lifecycles. Transaction
UI coverage includes income/category selection, decimal amounts, editing,
Activity recreation, date filtering/reset, delete cancellation and deletion.
The four original foundation/navigation/DataStore tests also pass.

Device testing found an Android 8 currency-metadata discrepancy: native
`Currency.getInstance("IDR").defaultFractionDigits` returns 0. Relying on it
rejected fractional input. IDR now explicitly uses the data model's ISO scale 2;
the Android regression test verifies `25.000,50` stores 2,500,050 minor units.
The migration fixture was also corrected to handle v1 tables without indices.
The final complete suites above pass after both corrections.

## APK

Deliverable: `/workspace/artifacts/finance-planner-phase2-debug.apk`
(12,332,141 bytes), matching the APK used by the final device suite.

SHA-256:
`0dca1ee51d79ce61f00a401de772526485a15fb28e06ca97b1b1ec3198f11841`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

Machine-readable test counts are saved in
`/workspace/artifacts/phase2-test-summary.json`.
The APK's checksum file and a screenshot of the restarted, ready Home screen are
saved alongside it in `/workspace/artifacts`.

## Environment and limitations

The full JDK 21, SDK Platform 36, Build-Tools 35.0.0, platform tools, emulator
and API 26 image are retained under `/workspace/.finance-tooling`. The reusable
setup was run twice before application edits; the current build/test workflow
was then verified on Phase 2. The environment draft contains tested
`install_script` and `start_skill`, including activation, current proxy-CA trust,
existing-checkout guidance, startup and readiness checks. No app secrets or
backend are required. Review/save in environment settings and publish to activate
the saved configuration/snapshot. Publication and new-task restoration were
not performed or claimed.

This is a debug development APK, not a production release. Runtime verification
covers API 26 software emulation; API 35/36 runtime behavior, physical ARM devices,
release signing/minification and Android Studio IDE sync remain unverified.
Migration was tested against the exported v1 schema; updating over an APK from
an earlier task also requires its original signing key, which was not available
in this fresh checkout.

The ledger retains its existing coverage start; earlier-history import/rebasing
is not introduced. Investment transactions are contributions; redemptions and
holding links remain Phase 9. Budgets, reports, forecasts, goals, asset/investment
planning, market feeds and export/import remain their roadmap phases.

**Phase 2 is ready. Stop here; do not begin Phase 3 without a new instruction.**
