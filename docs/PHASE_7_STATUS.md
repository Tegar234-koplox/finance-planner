# Phase 7 — complete

Date: 2026-11-02. Required gate: **YES**.
Phase 8 has not started.

## Implemented scope

- Plan → Goals and purchases and Home → Manage goals open saving/purchase plans.
  Goals have editable name, kind, target, current earmark/as-of date, target date,
  priority and active/paused state. Earmarks are nonnegative and may exceed target;
  target must be positive. Strict ISO dates support overdue targets, but as-of
  dates must be within ledger coverage and cannot be in the future.
- Schema v4 adds finance_goal only. The explicit 1→2→3→4 chain retains prior
  financial rows; v1/v2/v3 schema exports are unchanged. The goal table has a
  restricted ledger FK and fresh/migration guards for types, enums, name/amount/
  date bounds and coverage. Clock/zone-sensitive validation stays in repository
  and domain code, using the injected clock rather than SQLite wall time.
- Goal CRUD is transactional and preserves identity/creation time on edits. It
  never transfers money, generates transactions or modifies ledger/budget rows.
  Deletion confirms that it releases only the planning reserve. Buying a planned
  purchase remains a separate actual transaction; assets remain Phase 8.
- Goals join the existing Room financial snapshot, keeping cash and reservations
  atomic. RecordedCashCalculator shares the exact all-history cash owner between
  Home and goals. GoalRules owns exact aggregate reserves and validation.
- All current earmarks remain reserved, including paused and overfunded goals.
  Home displays recorded cash, reserves and unreserved cash. Its daily limit is
  now bounded by that unreserved cash and remaining overall budget under the
  existing complete-current-month/overall-cap requirements. Actual cash,
  consumption, saving and budget actuals remain unchanged by earmarking.
- A shared priority scenario assigns unreserved cash once, then shares monthly
  capacity across active goals. Ordering is priority, target date, creation and
  ID. Paused goals receive no new allocation. Fully earmarked goals consume no
  future capacity; excess reserves are never silently released.
- Capacity reuses the selected Forecasts method/window: max(rounded one-month
  income − consumption − contributions,0), requiring at least two complete
  samples for future dates. Dates assume contributions at the end of each next
  full calendar month. BigInteger cumulative needs and exact ceiling division
  prevent shared money and overflow from creating an early date.
- Individual monthly-needed amounts use remaining earmark divided by eligible
  future month-end dates, rounded up; these are explicitly for that goal alone,
  not reservations of shared capacity. The shared scenario date determines
  whether that scenario is ready by the target.
- Progress distinguishes empty/partial/funded/overfunded and truncates percentage
  to two decimals; remaining floors at zero. Overdue/due-today are separate
  incomplete-goal facts. Unknown history, zero capacity, unsupported ranges,
  unavailable cash and unbacked reserves have explicit unavailable states.
  A legitimate transaction edit may make earmarks unbacked; such edits remain
  allowed, Home floors free cash at zero and unsupported goal dates disappear.
- Native cards/dialogs, exact values, errors, type/status labels and hints are
  bilingual. Current resources have 391 matched English/Indonesian keys. User
  names are not translated. The editor retains failed drafts, guards concurrent
  writes, survives Activity recreation and uses the current ledger-zone clock.
- Version 0.7.0/code 7. No dependencies or permissions were added. No asset values,
  returns, purchase execution, health score or automatic goal growth is invented.

## Verification

Commands in the actual cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
# Fresh Phase 6 baseline before edits:
./gradlew --offline --no-daemon --console=plain :app:testDebugUnitTest --rerun
# Phase 7 build/checks:
./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --offline --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Baseline JVM suite | 176 tests passed before edits |
| Current JVM suite | 216 tests; 0 failures, errors or skips |
| Debug application/test APK | PASS |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories; none suppressed |
| Goal migration/storage | 3 tests passed on API 26 |
| Goal main-application UI | PASS on API 26, including pause/overfund/recreation/language/data cleanup |
| Goal reactive scenario UI | PASS after awaiting the updated Room order before scrolling; all date assertions retained |
| Final complete Android suite | 32 tests; 0 failures, errors or skips |
| Old schema/history integrity | v1/v2/v3 exports and Phase 1–6 status reports preserved |
| Native localization | 391 English/Indonesian keys with matching positional placeholders |
| Actual APK upgrade/restart | PASS; v0.6.0→v0.7.0, schema 3→4, all old rows preserved, goals initially empty, bilingual restart/fixture cleanup verified |
| APK signature/checksum | Verified; same public debug certificate as delivered v0.6.0; delivered bytes match final build |

The 40 new JVM tests cover funded/overfunded/overdue/due-today, exact percentages,
priority/ties/shared free cash/shared capacity, within-month capacity carry,
pause/reserve behavior, missing/partial/one-month history versus known zero flow,
method selection, contributions, current versus future records, first/final-day
schedules, leap February/year rollover, ceiling requirements, deadline equality,
very large cumulative sums, local overflows, malformed/currency/date inputs and
Home reserve caps. ViewModel tests exercise CRUD/identity, failed drafts,
parse errors without writes, busy guards, read errors/retry, missing ledger,
options and ledger-zone midnight/month rollover.

GoalMigrationTest opens a genuine exported v3 fixture with all five original
financial tables nonempty, migrates to v4 and compares every row before/after,
then writes a goal, reopens and deletes it without changing those old tables.
GoalStorageTest checks exact cash/reserves/capacity across CRUD, pause/overfund,
source edits and reopen, stale IDs, invalid future/currency/coverage writes,
fresh SQL guards, ledger FK protection and no generated transaction rows.

GoalsUiTest creates recorded income, adds/edits/pauses/overfunds a purchase,
recreates the Activity, switches languages, checks Home cash/reserves, deletes
the goal and its synthetic income, and verifies cash returns to zero.
GoalsContentUiTest uses real Room history and an injected clock to check shared
scenario dates, priority changes, current cash updates and Indonesian rendering.
Existing migration tests now register the full chain and assert current schema
v4 while retaining their historical fixtures and integrity assertions.

The cloud resume left only a terminated emulator process with stale AVD locks.
Only those stale locks were backed up/removed; userdata was preserved and the
emulator restarted. Device coverage: Android 8/API 26 x86_64 software emulator,
720×1280 with density override 320 (360dp width). An initial no-device attempt
and earlier unsuccessful automation are not counted as passing device tests.

## APK and upgrade

File: `/workspace/artifacts/finance-planner-phase7-debug.apk`
(13,060,091 bytes), version 0.7.0/code 7, min SDK 26, target SDK 36.
The manifest still has no INTERNET permission.

SHA-256:
`c1e8b772d41b09dce79cb55e0dd6e5e68608e2ce1cdd79b9811a15f4072d3422`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

The checksum file is `/workspace/artifacts/finance-planner-phase7-debug.apk.sha256`.
Final machine-readable counts/upgrade evidence is saved to
`/workspace/artifacts/phase7-test-summary.json`.
The delivered bytes match the application APK used in the complete Android suite.

The actual-APK smoke installed the delivered v0.6.0 APK on the dedicated emulator
and created one synthetic Rp100,000 income through its UI. Installing the exact
delivered v0.7.0 APK with `adb install -r` migrated schema 3→4. Before/after
snapshots retained identical rows in ledger (1), category (11), transaction (1),
monthly budget (0) and category budget (0); finance_goal was initially empty.
GoalMigrationTest separately covers a v3 fixture with every old table nonempty.

A temporary completed-history/goal fixture then checked the real APK's Home and
goal scenarios. It had two full months of income/consumption/contributions, one
future income, and high/normal/paused goals. Recorded cash was Rp110,000, reserves
Rp60,000, unreserved cash Rp50,000 and monthly capacity Rp5,000. The high-priority
goal's Rp40,000 remainder received Rp40,000 of scenario cash exactly once.
Its per-goal monthly quotient was Rp13,333.34 for three future month-end dates;
the scenario could cover it today without auto-saving its earmark.

Light/dark and Indonesian/English screenshots were inspected. Indonesian stayed
active after a real process restart. Afterward all original snapshot rows were
restored exactly, every visual goal/source fixture was removed, the original
synthetic income was deleted through the UI, and the database was checked empty
of transactions/goals. Original ledger/category/budget rows remained unchanged.
Appearance returned to Follow system and language to English; Home and Goals
reopened successfully. Screenshots show synthetic emulator data only; no sample
records are bundled in the APK.

Screenshots in `/workspace/artifacts`: `finance-planner-phase7-upgrade-home.png`,
`finance-planner-phase7-reserves-light.png`, `finance-planner-phase7-goals-light.png`,
`finance-planner-phase7-scenario-light.png`, `finance-planner-phase7-scenario-dark.png`,
`finance-planner-phase7-scenario-id.png`, `finance-planner-phase7-reserves-id.png`
and `finance-planner-phase7-goals-id.png`.

The fresh software emulator briefly displayed a System UI ANR dialog during
manual smoke. Only that platform dialog was handled with Wait, then all required
application checks were completed. Application failures were not dismissed or
counted as passes.

## Try the feature

Open Plan → Goals and purchases, add a target and earmark, then return to Home
to see reserved/unreserved cash. Pause the goal and confirm its existing earmark
stays reserved. Change the earmark or remove the goal to release its reserve;
recorded cash itself does not change. Actual spending still belongs in Transactions.

A new ledger may have no complete historical months. Goals/progress/current cash
still work; future-capacity dates remain unavailable until two full covered
months have ended. Inspect the Forecasts selection and the explicitly shown
queue/schedule before comparing scenario dates with target dates.

The same flow is available as Rencana → Tujuan dan pembelian or Beranda → Kelola
tujuan; Pengaturan → Bahasa switches languages without changing user data.

## Limits and phase boundary

This is a debug development build. Physical ARM devices, newer Android runtime
versions, release signing/minification, full TalkBack/large-font coverage and
forecast accuracy are not claimed. Earmarks are user declarations of existing
cash; they are not verified bank balances, transfers or extra wealth. Past as-of
dates can become stale. Scenario dates do not guarantee affordability or reserve
unrecorded bills/obligations. The per-goal quotient is not a shared funding plan.
No historical achievement date, purchase, asset, interest, return or inflation
is manufactured. Optional health and future full settings remain deferred.
API 26's legacy system navigation-bar scrim/contrast remains an existing Phase 18
polish concern; no physical-device/system-bar accessibility claim is made.

Existing Phase 2–6 work is preserved with a source baseline before Phase 7.
Environment publication/settings were not changed. The Git workflow/identity
preferences are recorded in ROADMAP.md: account-owned branch → commit → push →
PR → merge; verified user author/committer, no bot or co-author attribution.
No commit, push or merge was performed for this local completion.

**Phase 7 is ready. Stop here; Phase 8 requires a new instruction.**
