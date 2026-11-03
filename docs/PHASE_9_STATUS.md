# Phase 9 — manual investments and compact financial screens

Application verification is in progress. GitHub PR/squash merge is blocked by API
access (`Forbidden`); branch pushes work. Phase 10 has not started.

## Behavior

- Home → Investments opens Holdings, Allocation and Cash activity. Manual
  holdings have an asset class, acquisition date, optional symbol/cost/notes and
  dated observations. Zero is known; missing values are unavailable. Cash cannot
  be duplicated as a holding, and cost is never added to wealth or inferred profit.
- Latest active observations on/before today supply holding totals. Future data
  is excluded after clock rollback. Original dates remain visible. Exclusion
  changes today's total without recording a sale or cash transfer. Linked cash
  entries protect holding deletion; frozen historical components do not depend
  on current holdings remaining present.
- Portfolio allocation counts recorded cash once plus active holding values.
  Complete targets total exactly 10000 basis points. Exact largest remainders
  keep actual weights at 100% and target monetary amounts at the exact portfolio
  total. Missing values, zero/nonpositive totals and negative cash withhold shares.
  Targets never place trades.
- Contributions reduce cash; redemptions increase cash without becoming income,
  consumption or saving. Linked entries require an explicit post-entry holding
  value and commit both records atomically. The cash amount is never added to that
  value again. Unlinked entries change cash only. Editing/deleting a cash record
  does not invent a reversal of another independently recorded valuation.
- Monthly targets use gross recorded contributions, showing redeemed/net amounts
  separately. Zero targets have no percentage. Coverage and future/untracked
  months remain explicit. Forecasts do not extrapolate redemptions into income
  or future recurring capacity. Goals continue to reserve existing cash only.
- TRACKED_V2 wealth captures freeze investment name/class/value/original date
  together with cash, assets and debt. Old TRACKED_V1 observations and components
  stay unchanged. Cross-scope historical changes remain unavailable rather than
  implying comparable returns or inventing old investment values.
- All primary financial screens prioritize metrics/actions. The information
  icon opens detailed explanations; optional metadata and histories disclose on
  request. Holding Update value edits today's existing observation; an explicit
  dated-history action can add another date. Forms scroll within a bounded height,
  and Android 8 navigation-bar contrast follows the app theme.
- Schema v6 adds five investment tables, nullable transaction action/holding link,
  and a zero-default snapshot investment subtotal through migration 5→6. The
  full 1→2→3→4→5→6 chain is explicit. No dependency, network client, permission,
  quote/provider, trade execution or Phase 10 implementation was added.

## Verification

Actual cloud commands:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
./gradlew --offline --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew --offline --no-daemon --console=plain connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Phase 8 baseline | 251 JVM / 38 Android tests passed historically |
| Compact UI feature | 251 JVM tests and five targeted Android flows passed |
| Current JVM suite | 285 tests, zero failures/errors/skips |
| Investment migration/storage | Four tests passed on API 26 |
| Investment reactive content UI | PASS: zero/missing values, targets, source updates, Indonesian rendering |
| Investment main-application UI | PASS: targets, contribution, redemption, cash/wealth, recreation, bilingual UI, frozen capture and cleanup |
| Debug app/test APK | PASS |
| Android lint | 0 errors; 7 existing NewerVersionAvailable advisories |
| Complete Android suite | In progress: 45 tests |
| Actual delivered APK update | To be verified |

The 34 new JVM tests exercise allocation rounding/ties/exact totals, invalid target
plans and percent input, zero denominator, negative cash, known zero versus missing,
cost/contribution nonduplication, active/future/dated values, signed cash, gross/net
monthly targets, coverage, overflow, invalid currencies/identities and legacy/v2
snapshots. ViewModel tests cover parsing, failed drafts, atomic initial/post-entry
values, busy/read-error guards, ledger-zone midnight/month selection and editing
today's existing observation without blocking explicit dated-history entry.

InvestmentMigrationTest opens a genuine exported schema-v5 fixture with all ten
old tables nonempty. It compares every old row/column after migration, preserves
legacy null-action contributions and a frozen v1 snapshot, writes a v2 investment
capture, removes its current holding and reopens the unchanged copied components.
InvestmentStorageTest checks linked contribution/redemption values once, unchanged
goal earmarks, gross targets, reopening, invalid-write atomicity, deletion/link
boundaries, immutable SQL captures, FK guards and cascade scope.

PortfolioContentUiTest uses actual Room and a fixed clock for zero/unknown shares,
reactive values and locale changes. PortfolioUiTest uses the real MainActivity to
create income, a holding, 60/40 targets and a monthly plan; records contribution
and redemption with explicit balances; verifies income/cash/net worth/weights;
recreates the Activity, changes languages, captures wealth and deletes all fixtures.
Initial UI automation failed on duplicate dropdown text and a merged semantic
node; stable option tags and the correct text assertion fixed the test without
removing financial assertions. These failed attempts are not counted as passes.

Lint exposed API-31-only BigInteger conversion calls. Exact bounded conversions
replaced them, preserving API 26 support. No Android lint finding was suppressed.
The old cloud emulator process terminated after a passing targeted run; userdata
was retained, stale locks were backed up and the software emulator restarted.
No absent-device attempt is counted as a passing test. The first complete run
passed 44/45 tests; ReportsStorageTest still expected schema 5 instead of 6.
Only that version assertion changed; the financial assertions were retained and
the full suite is rerun. A separate fresh emulator isolates real APK-upgrade smoke
from instrumentation so neither task mutates the other device.

## APK and upgrade

Final APK, checksum and upgrade evidence will be recorded after the full suite.

## Manual testing / Uji manual

Install over the previous debug APK without clearing data. Open Home → Investments.
With recorded cash Rp2,000, add a manual fund worth Rp1,000: portfolio+cash Rp3,000.
Set cash/fund targets to 60/40 and a contribution target Rp500. Record a linked
Rp200 contribution with holding value after entry Rp1,200: cash Rp1,800. Record a
Rp300 redemption with value after entry Rp900: cash Rp2,100, holdings Rp900 and
portfolio+cash still Rp3,000. Income remains Rp2,000; gross target progress is 40%.
Cash/fund actual shares are 70/30. Save a wealth snapshot and verify its copied
investment components survive later edits/exclusions/deletion.

Buka Beranda → Investasi. Catat nilai investasi secara manual, atur target alokasi
tepat 100%, lalu uji setoran dan pencairan dengan nilai investasi setelah arus kas.
Kas dan nilai investasi harus dihitung satu kali. Pencairan tidak menjadi pendapatan.
Simpan snapshot, ubah data sekarang, dan periksa bahwa komponen lama tetap sama.
Ganti bahasa/tema, tutup-buka aplikasi, serta coba mode pesawat. Nilai tetap tersedia.
Gunakan nama UJI dan hapus hanya data percobaan sendiri; jangan hapus data aplikasi.
Persentase target menerima dua desimal dengan koma, misalnya `12,50`.

## Git delivery and limits

Configured author/committer: Tegar Putra <tegarsangputra2@gmail.com>, matching the
existing user-owned history. No bot, co-author or contributor trailers were added.
The previously uncommitted Phase 2–8 work was preserved in baseline commit a8881be,
pushed as feat/offline-finance-through-phase8. Compact UI commit b7de9ae was pushed
as feat/compact-finance-ui. Phase 9 uses feat/phase9-manual-investments, with coherent
implementation/test/documentation commits. Main is never pushed directly.

Actual GitHub API calls to user/GraphQL return Forbidden, and PR creation fails.
There are no GitHub connector/browser tools in this session. No PR or squash merge
is claimed. Git read/push access alone does not establish PR/merge authorization.
The concrete branch and PR description will be ready for the requested workflow
when authenticated GitHub API access is available. Environment settings/publication
were not changed to bypass that restriction.

Device evidence covers an Android 8/API 26 x86_64 software emulator at 720×1280,
density 320 (360dp). Physical ARM/newer Android, release signing/minification,
full TalkBack/large-font and performance coverage are not claimed. This remains a
debug development build. Export/import/security/release gates remain later phases.
All historical schema exports 1–5 and Phase 1–8 status reports are preserved.

Readiness: pending the full Android suite and actual APK upgrade; GitHub PR/merge
remains blocked by API access. Stop after Phase 9.
