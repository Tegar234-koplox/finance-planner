# Phase 8 — assets, manual liabilities and dated net worth

Status: complete and ready (YES). Phase 9 has not started.

## Delivered behavior

- Home → Assets and net worth opens a fully offline, bilingual detail screen.
  Add/edit non-cash, non-investment assets or manual liabilities, compatible
  categories, acquisition/opening dates, optional asset cost and notes.
- Record/edit/delete dated nonnegative values, including known zero. Dates cannot
  be future or precede acquisition. Each item has one observation per date;
  conflicting entry requires editing that observation. Item kind stays fixed.
- Current tracked net worth counts recorded ledger cash once, plus active asset
  observations, minus active debt observations. Cost is metadata only. Goals
  remain earmarks inside already counted cash. Holdings and unrecorded assets/
  debt are explicitly excluded; contributions never invent a holding value.
- The latest observation on/before today is used; original date and age remain
  visible. Future observations are excluded after clock rollback. A missing
  active value makes its subtotal/total unavailable. Exact BigInteger sums precede
  checked Long conversion, with separate cash/asset/debt/net/delta range states.
- Exclude/restore affects only the current total. Deletion confirms its effects.
  No wealth write creates transactions, a purchase, repayment, sale or transfer;
  non-cash assets never increase Home spending cash or goal funding capacity.
- Save today's snapshot confirms and atomically freezes cash, totals, method and
  each component's name, kind, category, value and original valuation date. Later
  edits/exclusions/deletion of current items cannot rewrite old captures. There
  is one capture per ledger date; replacement requires explicit delete first.
- Saved history is ordered by date. Earlier dates without captures stay unknown;
  current valuations never backfill old dates. Change between the latest two
  saved dates is an absolute tracked change, not an investment return.
- Room schema v5 adds wealth_item, wealth_value, wealth_capture and
  wealth_capture_line through migration 4→5. All six prior financial table
  definitions/rows are preserved; no destructive fallback or new dependency,
  network client or permission was added. Application version: 0.8.0/code 8.

## Checks

Actual cloud commands:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
./gradlew --offline --no-daemon --console=plain testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew --offline --no-daemon --console=plain connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Fresh pre-edit baseline | 216 JVM tests passed |
| Current JVM suite | 251 tests; 0 failures, errors or skips |
| Debug app/test APK builds | PASS |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories and 2 UnusedResources labels (wealth_kind/category); none suppressed |
| New targeted Android suite | 6 tests passed; 0 failures, errors or skips |
| Complete Android suite | 38 tests passed; 0 failures, errors or skips |
| Bilingual resources | 470 matched English/Indonesian string keys, including 79 wealth keys; positional placeholders match |
| Historical schemas/reports | Schemas 1–4 and Phase 1–7 status reports unchanged |
| Old entity definitions | All 6 schema-v4 entity definitions identical in exported schema v5 |
| Actual delivered-APK update | PASS; 0.7.0→0.8.0, schema 4→5, all six nonempty old tables preserved |

The 35 new JVM tests cover cash counted once, cost metadata versus valuation,
expense/asset accounting, contributions without synthetic holdings, goal reserves,
negative cash/net worth, known zero versus missing, date/age/latest observation,
future exclusion, archived data, acquisition before coverage, missing cash,
immutable names/amounts/dates, ordered snapshots/delta, duplicate dates, local
and net overflow, exact final sums, invalid/currency/category/note/date input,
corrupted captures and ambiguous identities. ViewModel tests cover exact parsing,
atomic initial drafts, edit/archive identity, failed drafts/retry, busy guards,
read-error clearing, missing ledger, snapshot errors and ledger-zone midnight.

WealthMigrationTest creates a genuine exported schema-v4 fixture with all six
old financial tables nonempty, migrates through Room, compares every old row,
then captures an asset, deletes the item and reopens its unchanged frozen history.
WealthStorageTest uses actual Room CRUD to check cash/goal/budget preservation,
snapshot survival after source edits/deletion, duplicate dates, atomic validation,
zero/unknown values, range/currency/date guards, immutable SQL captures and FK
cascade boundaries. Existing migration tests register the full 1→2→3→4→5 chain
without changing their old fixture schema files.

WealthContentUiTest uses real Room and a fixed clock to verify reactive current
values with frozen historical names/dates/totals and Indonesian rendering.
WealthUiTest exercises the real application: create asset and debt, capture,
edit a dated value, exclude an asset, recreate the Activity, switch languages,
delete current items while history remains, explicitly delete the snapshot,
and verify recorded Home cash stays unchanged. Its synthetic data is removed.

## APK and device smoke

APK: `/workspace/artifacts/finance-planner-phase8-debug.apk`, version 0.8.0/code 8,
12,917,555 bytes, min SDK 26, target SDK 36. The manifest has no INTERNET
permission. Its debug certificate matches the earlier delivered APKs.

SHA-256: `3b1af9a6fdefced406762c6eaf34caa849d7e056730a2cd79558e3bbf397bdae`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

Checksum sidecar: `/workspace/artifacts/finance-planner-phase8-debug.apk.sha256`.
Final counts/upgrade evidence is saved to
`/workspace/artifacts/phase8-test-summary.json`.

The actual smoke installed the earlier delivered v0.7.0 APK, opened it with all
six original financial tables nonempty (ledger 1, categories 11, income 1,
overall budget 1, category budget 1, goal 1), then installed these exact v0.8.0
bytes with `adb install -r`. Room migrated 4→5, every old row compared equal,
and all four new wealth tables were initially empty. The APK used in the full
38-test Android suite is byte-identical to the delivered file.

A temporary synthetic vehicle/debt fixture then verified recorded cash Rp100,000,
asset value Rp15,000,000 and debt Rp4,000,000: tracked net worth Rp11,100,000.
The asset's Rp20,000,000 acquisition cost was not added; the existing goal earmark
was not added/subtracted again. Values remained dated seven/two days earlier.
The actual UI saved today's capture and exposed the copied component names,
values and dates. A deliberately seeded older observation made the stored-date
change −Rp450,000; that fixture is synthetic evidence, not a backdated app action.
Light/dark and English/Indonesian screenshots were inspected, and Indonesian
persisted across a real process restart. No synthetic rows ship in the APK.

An initial visual-automation attempt did not pass the duplicate-label snapshot
confirmation; it is not counted as a pass. The helper was corrected to choose
the centered dialog action, and the complete visual/capture/restart flow then
passed without changing application source or weakening any assertion.

After smoke, the exact original post-upgrade database was restored, then only
known upgrade fixture IDs were removed. Every remaining original financial row
matched the clean v0.7.0 baseline; transactions, goals, budgets and all wealth
tables were empty. Theme returned to Follow system, language to English, and
Home/Wealth reopened successfully. Screenshots are in `/workspace/artifacts`:
`finance-planner-phase8-old-home.png`, `finance-planner-phase8-upgrade-home.png`,
`finance-planner-phase8-wealth-light.png`, `finance-planner-phase8-total-light.png`,
`finance-planner-phase8-history-light.png`, `finance-planner-phase8-components-light.png`,
`finance-planner-phase8-wealth-dark.png`, `finance-planner-phase8-total-dark.png`,
`finance-planner-phase8-wealth-id.png` and `finance-planner-phase8-total-id.png`.

## Try it / Cara menguji

Install the APK over the earlier debug APK using the same package and signature;
keep the existing app data. Open Home → Assets and net worth. Add a named asset
with an acquisition date no later than today and a manual value, then add a
liability. Check that net worth adds the asset and subtracts the debt, while Home
cash stays unchanged. Save today's snapshot, edit the current value, and show
saved components: old value/name/date must remain intact. Exclude/delete the
item and check that saved history still survives. Deleting the saved snapshot
requires its separate confirmation. Use Settings → Language to switch languages.

Pasang APK di atas versi debug sebelumnya tanpa menghapus data. Buka Beranda →
Aset dan kekayaan bersih, catat aset serta utang, lalu simpan snapshot hari ini.
Ubah nilai saat ini dan periksa komponen snapshot: nilai, nama dan tanggal lama
harus tetap sama. Keluarkan/hapus aset dan pastikan riwayat tersimpan tidak ikut
hilang serta kas Beranda tidak bertambah. Pengaturan → Bahasa mengganti bahasa.

## Limits and phase boundary

This is a debug development APK. Device checks cover Android 8/API 26 x86_64
software emulator at 720×1280, density 320 (360dp). Physical ARM/newer Android,
release signing/minification, full TalkBack/large-font and broad performance
coverage are not claimed. System navigation-bar contrast remains a Phase 18
polish concern. No investment holdings, redemptions, allocations, quote/provider
work or Phase 9 implementation is included. Manual data and value ages are
visible; no current market value or complete bank-verified statement is claimed.
Export/import/security/release gates remain their later roadmap phases.

Prior Phase 2–7 work is preserved with a Phase 8 source baseline outside the repo.
Environment settings/publication were not changed. No commit/push/merge was
performed. Git preferences remain branch → commit → push → PR → merge, verified
user author/committer and no bot/co-author/contributor attribution.

**Readiness: YES. Stop after Phase 8; Phase 9 requires a new instruction.**
