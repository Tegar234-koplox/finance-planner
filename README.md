# Finance Planner

Private, local-first Kotlin Android app. Phase 9 added manual investment holdings,
exact allocation targets, monthly contribution plans and cash redemptions to
bookkeeping, goals and dated net-worth history. The interface supports English
and Bahasa Indonesia. Phase 10 adds provider contracts, bounded HTTPS transport
and an isolated offline quote cache. Market-specific providers/watchlists remain
Phase 11 onward; no provider is connected or price invented in this build.

## Market prices / Harga pasar

Open **Market** to view saved quotes when an authorized source is available.
The current build has no connected price source, so it shows an honest empty
state and **Open portfolio** for manual investment records. Detailed explanations
remain behind the information icon.

Buka **Pasar**. Jika belum ada sumber harga yang terhubung, layar menampilkan
**Belum ada harga pasar** dan tombol **Buka portofolio**. Catatan investasi manual
tetap tersedia secara luring. Penyedia harga dan watchlist masuk fase berikutnya.

Cached prices retain their own currency, exact decimal precision, source and
observation/retrieval timestamps. “Recent” requires both timestamps to be less
than 15 minutes old; it is not a live-price guarantee. Offline, empty, timeout,
authorization, rate-limit and provider-error results preserve the last successful
price and its timestamps. Prices never automatically change manual valuations,
cash, goals or net worth, and USD quotes never become IDR values implicitly.

Numbers and actions appear first. Tap the information icon for calculation details.
Angka dan aksi utama ditampilkan lebih dulu; ketuk ikon info untuk penjelasan.

## Investments / Investasi

Open **Home → Investments**. The three tabs separate **Holdings**, **Allocation**
and **Cash activity**. Add a named holding, asset class, acquisition date and
initial dated value. Optional symbol, recorded cost and notes are under Edit
details. Cash belongs to the ledger, never a duplicate holding. Values are manual
observations; recording a cost does not add it to wealth or prove a return.

Buka **Beranda → Investasi**. **Kepemilikan** menyimpan nilai investasi manual
bertanggal. **Alokasi** membandingkan persentase saat ini dengan target yang
jumlahnya harus tepat 100%. **Arus kas** mencatat setoran dan pencairan, serta
target setoran bulanan. Angka memakai format Indonesia, misalnya `25.000,50`.
Penjelasan perhitungan tersedia melalui ikon info; nilai dan aksi tetap terlihat.

Portfolio allocation includes recorded cash once and active holding values,
excluding non-investment assets. Actual percentages use exact largest-remainder
rounding and sum to 100%. Missing values, a zero total or negative cash withhold
unsupported percentages. Targets never execute trades. Current holdings can be
excluded or deleted; linked cash entries must be unlinked/deleted before removing
their holding, or the holding can simply be excluded.

Contributions reduce cash. Redemptions increase cash and are not income,
consumption or saving. When a cash entry links a holding, explicitly enter its
value after that entry; both writes commit together, without adding the cash
amount to its value again. Unlinked entries change cash only. Editing/deleting
a cash entry does not reverse other manual observations or saved snapshots;
review those values separately when correcting a record. Bookkeeping preserves
the action and optional holding link on edits.

Monthly targets measure gross recorded contributions. Redemptions and net
contributions are shown separately. A zero target has no percentage; incomplete
tracking and untracked/future months remain explicit. Forecasts do not extrapolate
redemptions into future income or capacity.

New wealth snapshots include copied investment components (method TRACKED_V2).
Old TRACKED_V1 snapshots remain unchanged and exclude holdings. Changes between
different scopes are unavailable instead of implying a comparable historical
return. No current values are backfilled into older snapshots.

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
asset and investment values, minus active liabilities. Goal earmarks remain inside that cash.
Non-cash assets never increase Home spending cash. Missing active values make
the total unavailable; cost or zero is never silently substituted. Original value
dates and ages stay visible. Unrecorded wealth/debts are excluded; contributions alone cannot establish an
investment holding's value.

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

The current development APK is version 0.10.0 (code 10). Financial schema v6 and
its explicit 1→2→3→4→5→6 migration chain remain unchanged. Rebuildable market
information uses a separate schema-v1 database. See
[Phase 10 status](docs/PHASE_10_STATUS.md) for current checks and limitations.
[Phase 9 status](docs/PHASE_9_STATUS.md),
[Phase 8 status](docs/PHASE_8_STATUS.md),
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

Room stores financial records and the separate market cache in private internal
storage; DataStore stores nonfinancial preferences. Phase 10 adds the Internet
permission and HTTPS-only transport for subsequent authorized market adapters.
No provider endpoint or API key is shipped, and no network refresh runs on launch,
resume, a timer or in the background. No telemetry or ads are included.
Personal financial records are never inputs to the market transport.
Android cloud backup and device transfer are
explicitly excluded. Private storage does not imply database encryption.
Schemas are exported under `app/schemas`. Never use destructive migrations.
