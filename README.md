# Finance Planner

Private, local-first Kotlin Android app. Phase 8 adds non-cash assets, manual
liabilities, dated valuations and immutable tracked-net-worth snapshots to goals,
forecasts, reports, Home, budgets and bookkeeping. Manual investment holdings
and market APIs remain later phases. Phase 9 has not started.

Numbers and actions appear first. Tap the information icon for calculation details.
Angka dan aksi utama ditampilkan lebih dulu; ketuk ikon info untuk penjelasan.

## Assets and net worth / Aset dan kekayaan bersih

Open **Home → Assets and net worth**. Add a non-cash, non-investment asset or a
manual liability, its category, acquisition/opening date and initial dated value.
Amounts use the ledger currency and Indonesian notation (`25.000,50`); zero is
valid, negative values and future dates are rejected. Acquisition cost is optional
metadata, never an extra asset value or a fallback when no valuation exists.

Buka **Beranda → Aset dan kekayaan bersih**. Tambahkan aset nonkas selain
kepemilikan investasi, atau kewajiban/utang manual, lalu catat nilainya dan
tanggal penilaian. **Catat nilai bertanggal** menambah pengamatan; **Tampilkan
nilai bertanggal** membuka riwayat untuk diedit/dihapus. Jenis aset/kewajiban
tetap setelah dibuat. Mengeluarkan item dari total saat ini mempertahankan
riwayatnya; tidak mencatat penjualan, pelunasan, atau perpindahan uang.

Tracked net worth counts recorded ledger cash once, plus the latest dated active
asset values, minus active liabilities. Goal earmarks remain inside that cash.
Non-cash assets never increase Home spending cash. Missing active values make
the total unavailable; cost or zero is never silently substituted. Original value
dates and ages stay visible. Holdings and unrecorded wealth/debts are excluded;
contributions alone cannot establish an investment holding's value.

**Save today’s snapshot** asks for confirmation and freezes cash, totals and each
component's name, kind, value and original observation date. Later source edits,
exclusions and deletions do not rewrite it. Only today's ledger date can be
captured, once per date; replacement requires explicitly deleting that capture.
Saved history never backfills current values into earlier dates. The difference
between two stored dates is an absolute tracked change, not an investment return.

**Simpan snapshot hari ini** menyimpan salinan komponen dan tanggal nilainya.
Perubahan data sekarang tidak mengubah snapshot lama. Hari yang belum pernah
disimpan tetap tidak tersedia; snapshot lama tidak direkayasa dari nilai saat ini.

## Goals and purchase plans / Tujuan dan rencana pembelian

Open **Plan → Goals and purchases**, or **Home → Manage goals**. Add a goal name,
kind (saving/purchase), positive target, nonnegative current earmark, its as-of
date, target date, priority and active/paused state. Target dates may be overdue;
earmark dates must fall within ledger coverage and cannot be in the future.
Amounts keep Indonesian notation. Edit/delete requires the existing UI actions;
deleting confirms that the goal's reserve is released without moving cash.

Buka **Rencana → Tujuan dan pembelian**, atau **Beranda → Kelola tujuan**.
Masukkan nama, jenis, target positif, dana yang sudah disisihkan, tanggal
pencatatan dana, tanggal target, prioritas, serta status aktif/dijeda. Dana
tujuan hanya mencadangkan kas yang sudah ada; tidak membuat pengeluaran atau
memindahkan uang. Tujuan yang dijeda tetap mencadangkan dana yang sudah
disisihkan hingga jumlahnya diubah atau tujuannya dihapus.

Progress derives from the declared earmark, including amounts above target.
Home shows total reserves and cash after reserves; its daily spending limit now
uses that unreserved cash together with the remaining overall budget. Earmarks
are neither extra wealth nor additional spending. Recording an actual purchase
still uses Transactions; review/release its goal earmark separately afterward.

One scenario allocates free cash once, then shares monthly capacity across
active goals in priority, target-date and creation order. Paused goals receive
no new allocation. Capacity uses the selected Forecasts method/window and its
rounded 1-month income minus consumption minus contributions, clamped at zero;
at least two complete months are required for future dates. Future contributions
are assumed at each next full month's end. The displayed amount needed per
month is for that goal alone and does not reserve shared capacity.

No history differs from zero capacity. Already available recorded cash can cover
a scenario immediately; cash/earmark shortfalls and arithmetic/date limits
withhold unsupported dates. Cash assignments and dates do not update earmarks
automatically, do not create transactions/assets and do not guarantee affordability.

## Language / Bahasa

Open **Settings → Language**, then choose **English** or **Bahasa Indonesia**.
The interface changes immediately and the choice survives reopening the app.
Both languages are included for offline use. Existing installs default to English
until a choice is saved. Changing language preserves stored amounts, categories,
notes, ISO dates, the ledger currency and Indonesian amount-entry notation.

Buka **Pengaturan → Bahasa**, lalu pilih **English** atau **Bahasa Indonesia**.
Pilihan langsung diterapkan dan tersimpan saat aplikasi dibuka kembali. Kedua
bahasa tersedia secara luring. Bahasa tidak mengubah nilai uang, nama kategori,
catatan, tanggal ISO, mata uang buku, atau format input jumlah Indonesia.

## Read forecasts and rules

Open **Plan**. Choose **Rolling mean** (equal monthly weights) or **Weighted
mean** (weights 1…N from oldest to newest), and a **3/6/12-month** history window.
The page estimates totals for the next **1 and 3 calendar months**, excluding
the remainder of this month. **Refresh forecasts** updates the reporting date.

Only fully tracked, completed calendar months qualify. Covered empty months
count as zero; unknown/partial/current months do not count. A new ledger has no
complete past months, so estimates initially remain unavailable. Evidence is
LOW with one sample, MEDIUM with 2–5 and HIGHER with 6+; these labels are not
probabilities. No samples means no forecast. **Show source months** exposes
the actual monthly values and weights.

Estimates separate consumption and contributions. Saving excludes contributions;
net cash flow includes them and excludes opening cash. The displayed historical
min/max ranges are not prediction intervals or bounds on future outcomes.
One observed month cannot establish variability. Each horizon rounds the original
weighted ratio once to minor units, then derives saving/net flow.

Rules show their version, inputs and thresholds: limited history, an estimated
income–consumption gap, contribution pressure on cash, a missing next-month
overall budget, estimated consumption above that budget, or a 20%+ rise between
the last two complete months with a positive earlier amount. They update with
the selected method/window and saved edits. No rule guarantees affordability;
no rule firing does not establish financial health. Goals and shared purchase
capacity use the separate scenario described above; a health indicator is deferred.

## Read reports

Open **Home → Reports**, then choose **Week**, **Month** or **Year**. Weeks run
Monday–Sunday. Use **Previous**, **Next**, **Today**, or **Choose date** to select
a calendar period; **Refresh report** updates its as-of date and source records.

Reports separate income, consumption and investment contributions, show income
minus consumption, saving rate, and net cash flow. Opening cash is excluded;
net cash flow is not a cash balance. Zero income has no saving rate.

Coverage and the actual recorded range remain visible. Unknown/future history
is unavailable rather than zero. Complete periods compare complete calendar
periods; an in-progress period compares the same number of elapsed calendar
days in the previous period. Missing/partial history, a too-short previous
period, or arithmetic limits make comparison unavailable. Percentage change is
unavailable when its previous amount is zero.

**Show chart** reveals directly labeled income/consumption/contribution bars,
using days for a week/month or months for a year. All three series share a
zero-based amount scale. Unknown buckets have no zero bar. Exact text labels
and category amounts/shares remain usable without color or hover.

## Use the Home dashboard

Home shows recorded cash through the ledger reporting date, current-month income,
consumption, investment contributions, saving/rate, budget use, top spending
categories and recent transactions. Changes to saved records update the overview.
Transactions dated in the future are excluded until their date.

The **Daily spending limit** is the lower of cash after goal reserves and remaining overall
budget, clamped at zero and divided by remaining days including today, rounded
down. An overall budget and complete current-month tracking are required. Goal
earmarks are reserved; unrecorded bills and obligations are not assumed. This recorded-data limit is
not a bank balance or a guarantee of affordability.

**Add transaction**, **Transactions**, **Categories** and budget links open the
existing workflows. A Home budget link opens the dashboard's reporting month,
even if another month was previously selected in Budget. **Refresh dashboard**
and returning to Home update its reporting date. Missing/future metrics stay
explicitly unavailable; no unsupported values or forecasts are invented.

## Use monthly budgets

Open **Budget**, choose a `YYYY-MM` month, then **Set overall budget** or
**Add category budget**. Allocations may be zero but not negative. Category
allocations cannot exceed the overall cap when one exists; reduce them before
lowering the cap. Removing an overall cap preserves category allocations, and
removing any allocation preserves transactions.

Recorded consumption includes unallocated/archived categories. Income and
investment contributions are excluded. Actual/remaining/utilization update from
saved transactions. Future-dated entries start counting on their date.
**Budget per day** is a budget allowance, not spendable cash. Projections and
daily allowances are unavailable for months with incomplete tracking coverage.
Use **Refresh** to update the displayed reporting date; returning to Budget
also refreshes it. Amounts and dates follow the ledger's IDR/Asia-Jakarta defaults.

## Use the transaction book

- Open **Home → Transactions → Add transaction**. Choose income, expense or
  investment contribution, an active category, a positive IDR amount, and a date.
  Numeric input uses Indonesian notation: `25.000,50` means Rp25,000.50.
- Dates use `YYYY-MM-DD` and must be on or after the ledger's tracked-from date,
  shown in the form. Earlier history/import and ledger rebasing are future work.
- History is newest first. **Show filters** combines inclusive from/through
  dates, type and category. **Clear filters** returns to all records.
- **Edit** preserves the record ID. **Delete** requires confirmation.
- Open **Home → Categories** to add, rename, archive, restore or delete an unused
  category. Names are unique per type, including archived names. Type is fixed
  after creation. Used categories must be archived rather than deleted; historical
  transactions retain the category and can still be edited.

## Build and test

Open this directory in Android Studio with AGP 8.13 support. Install a full
JDK 21 (minimum 17), SDK Platform 36, and Build-Tools 35.0.0. Configure the SDK
through Android Studio's ignored `local.properties` or `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# With an Android 8+ device/emulator connected:
./gradlew :app:connectedDebugAndroidTest
```

Cloud shells first run `source /workspace/.finance-tooling/activate.sh`.
The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
It is a development build, not the final personal-finance product.

The current development APK is version 0.8.0 (code 8). Schema v5 adds four wealth tables
through the explicit non-destructive 1→2→3→4→5 migration chain. See
[Phase 8 status](docs/PHASE_8_STATUS.md) for current checks and limitations.
[Phase 7 status](docs/PHASE_7_STATUS.md),
[Phase 6 status](docs/PHASE_6_STATUS.md),
[Phase 5 status](docs/PHASE_5_STATUS.md),
[Phase 4 status](docs/PHASE_4_STATUS.md), [Phase 3 status](docs/PHASE_3_STATUS.md),
[Phase 2 status](docs/PHASE_2_STATUS.md) and [Phase 1 status](docs/PHASE_1_STATUS.md)
retain the historical results.

## Design

- [Product](docs/PRODUCT.md)
- [Architecture and dependencies](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Roadmap and quality gates](docs/ROADMAP.md)

Room stores the ledger and categories in private internal storage; DataStore
stores nonfinancial preferences. No internet permission, network client,
telemetry, or ads are included. Android cloud backup and device transfer are
explicitly excluded. Private storage does not imply database encryption.
Schemas are exported under `app/schemas`. Never use destructive migrations.
