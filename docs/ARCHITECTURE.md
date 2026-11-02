# Architecture

## Phase 8 tracked assets, liabilities and dated history

WealthMigrations.FROM_4_TO_5 adds four tables and guards, preserving all six
original financial tables and schema exports 1–4. No dependency, permission or
network client is added. WealthSourceEntity uses nested transactional Room
relations for ledger/transactions, items/values and captures/copied lines.
It is separate from BudgetSnapshot so non-cash values never affect Home cash,
budget limits, goal earmarks or shared forecast capacity.

LocalWealthRepository serializes validation and CRUD in Room transactions.
A new item and initial observation commit together. Date collisions are explicit;
kind and value ownership stay fixed. No wealth mutation creates transactions,
repayments, investments, sales or goal changes. Current items can be excluded
or deleted, preserving saved historical components. Capture creation reads its
entire source in the same transaction that freezes cash and all component names,
kinds, categories, values and original valuation dates. Captures cannot be updated;
explicit delete is the only replacement route. Source IDs deliberately have no
foreign key to current items; capture deletion cascades copied lines.

WealthRules owns validation; WealthCalculator owns latest-on/before-date selection,
tracked gross/net totals and stored-history deltas. It reuses RecordedCashCalculator
and checked BigInteger accounting. Unknown active values never become zero or
acquisition cost. Future observations are excluded on clock rollback. Current
cash may be negative; assets/debts may be zero. Gross/net/delta overflows remain
explicit and cannot enter a snapshot. Frozen history is validated independently
of current items and never reconstructed from current values.

WealthViewModel observes source/refresh using the injected ledger-zone clock,
retains failed drafts, guards concurrent writes and clears stale totals after
read errors. WealthScreen opens from Home as a detail route with normal Back/
Settings behavior, native lazy cards and scrollable bilingual dialogs. It shows
value dates/ages, missing values, current scope, dated value editing, exclusion,
confirmed deletion, confirmed today-only capture and frozen component disclosure.
Refresh/resume recomputes today without pretending to refresh a market quote.

The domain, ViewModel, storage, migration, reactive UI and main-application tests
exercise no double counting, immutable history, CRUD/guards, language switching
and restart. See PHASE_8_STATUS.md for actual counts, APK evidence and limits.
Manual investment holdings remain Phase 9; no investment-inclusive valuation,
redemption or provider work is started.

## Phase 7 goals, earmarks and purchase scenarios

Schema v4 adds only finance_goal through GoalMigrations.FROM_3_TO_4. The explicit
1→2→3→4 chain preserves all prior financial rows; the old v1/v2/v3 exports remain
immutable. Fresh-create and migrated databases install matching goal guards.
LocalGoalRepository serializes validation/CRUD in a Room transaction, preserves
identity/creation time on edits, and never creates a transaction or changes cash.
It delegates observation to the existing financial source rather than combining
independent goal/cash streams. BudgetSnapshotEntity includes goals as a relation
under the same transaction as ledger, transactions and budgets.

GoalRules owns input validation, normalization and exact aggregate reserves.
RecordedCashCalculator now owns the all-history cash fold shared by Home and
GoalPlanner. Home preserves its recorded cash/saving/expense semantics and
applies goal reserves only to unreserved cash and the daily limit. Paused and
overfunded earmarks remain reserved. A cash shortfall after a legitimate source
edit is shown explicitly; transaction writes are never rejected or fabricated
to force earmarks to fit. Large reserve sums use BigInteger and cannot wrap.

GoalPlanner derives progress, individual month-end requirements and one shared
priority scenario from that source. It reuses ForecastCalculator with the
method/window selected in Forecasts, requiring two complete months for future
dates. It assigns available cash once, then shares monthly net capacity across
active goals; paused goals receive no new allocation. Exact ceiling arithmetic
and supported-date checks precede calendar projection. Unbacked or unavailable
cash, missing history, zero flow, overflow and unsupported dates have distinct
states. Data model formulas and schedule assumptions are in DATA_MODEL.md.

GoalsViewModel observes source/options/refresh, uses the injected ledger-zone
clock, retains drafts after write errors and guards concurrent writes. Read
errors clear old totals/dates while preserving user input. FinanceApp passes
Forecasts options to it through a value-based effect, keeping the ViewModels
independent. GoalsScreen is a detail route opened from Plan or Home, with normal
Settings/back navigation and Activity state restoration. Native lazy cards,
scrollable dialogs and exact labels are bilingual; progress bars are decorative
and never replace readable values. Goal names are user data and are not translated.

Migration/device tests cover all five nonempty old financial tables, source
edits, reopen, invariants, planning dates, shared capacity, CRUD, pause/overfund,
configuration recreation and language switching. No assets, purchase execution,
interest/return model, goal-generated cash transfer or health score is introduced.
Phase 8 is described above.

## Phase 6 forecasts and explainable rules

ForecastCalculator derives complete monthly samples from the existing
transactional BudgetSnapshot. ForecastOptions chooses rolling/weighted mean and
a 3/6/12-month calendar window. Samples retain their month, RecordedFlows and
actual weight; FinancialForecast retains exclusions, evidence, both 1/3-month
horizons, observed ranges and typed rule inputs. The formulas and exact trigger
boundaries are specified in DATA_MODEL.md. No aggregate or forecast is persisted.

RecordedFlowCalculator now accepts checked nonnegative gross totals as well as
source records, keeping income/consumption/contribution/saving/net accounting
under one domain owner. Forecasts use BigInteger ratios and one half-even rounding
per horizon. Gross, net and historical-range overflow have explicitly different
failure scopes; no saturation or approximate floating-point accounting is used.

ForecastViewModel observes the existing repository plus selected options and a
refresh signal. The injected clock uses the ledger zone. Resume/refresh advances
the as-of date and newly completed month without a write. Errors clear estimates
and rules; retry preserves options. SavedStateHandle retains only method/window
through saved Activity state; it does not persist money or duplicate ledger data.

Plan now hosts ForecastScreen. Its native LazyColumn reads method/window →
coverage/evidence → 1/3-month totals/ranges → rules → optional source months.
Source disclosure uses rememberSaveable. Text exposes exact values, formulas,
rule inputs, sample limitations and unknown-data states without chart dependencies,
color-only meaning or hover. Home and Reports direct users to Plan. Goals and
purchase workflows are now implemented by Phase 7 above; an optional health
indicator is deferred.

Unit tests cover both methods/horizons, evidence thresholds, coverage, clock,
rounding, date bounds, rule boundaries, local overflow and restored options.
Device tests exercise persisted data/edit/delete/reopen, reactive native UI,
method/window selection and Activity recreation. No schema, dependency,
permission or environment configuration change is required.

The user additionally requested a bilingual interface. AppLocalization supplies
English/Indonesian Android resources through Compose LocalResources and
LocalConfiguration, keeping the Activity context and navigation state intact.
All current screens, dynamic explanations/errors, type labels and accessibility
labels use resource strings; values and user-authored category names/notes are
arguments, never translation keys. Both resource sets have matching positional
placeholders. App Bundle language splitting is disabled so the selected language
remains available offline without Play Core or another dependency.

The existing DataStore settings repository stores language independently of
theme; FoundationViewModel exposes successful observed choices and write errors.
The missing-language default is English for older installs. Settings offers the
endonyms English and Bahasa Indonesia. Room financial storage and migrations
are unchanged. Full settings beyond this requested addition remain Phase 15.

## Phase 5 reports and native visualizations

RecordedFlowCalculator owns shared period accounting for Home and reports;
Home preserves its all-history cash fold and delegates monthly flow accounting
to it. ReportCalculator derives weekly/monthly/yearly totals, explicit coverage,
eligible previous-period comparisons, reconciled time buckets and category shares
from the existing transactional financial snapshot. No Room schema, migration,
counter, service or production dependency changed.

ReportsViewModel owns applied kind/date, current ledger-zone as-of date, input/
observation errors and category labels. Invalid input retains the last explicitly
labeled applied period; read/calculation errors clear totals/charts. Today reads
the clock afresh, including across midnight without a preceding database write.
The selection survives Activity recreation; chart/date disclosures use
rememberSaveable. This is native app navigation, not URL-backed web state.

ReportsScreen owns rendering only. Native Material 3 determinate bars encode
positive amounts from a shared zero baseline: at most 7×3 weekly, 31×3 monthly,
or 12×3 yearly temporal marks plus lazy category rows. The renderer converts only
bar fractions to float; exact labels remain Money/BigDecimal. No Canvas/WebView,
chart library, animation loop, tooltip-only interaction or export path is added.
Direct labels are the nonvisual/grayscale fallback; decorative bars suppress
duplicate semantics. Unknown buckets have no zero bar, and zero/no-covered scales
are explicitly distinct.

Mobile reading order is period controls → applied range/coverage → totals →
comparison → chart disclosure → categories. Date input is disclosed on demand.
Primary/secondary/tertiary colors identify income/consumption/contributions with
redundant text. Source/as-of notes, exact values and caveats stay visible without
hover. Category bars use total consumption as their scale; temporal bars use the
maximum covered series value across the displayed period. QA includes bucket/
total reconciliation, signed deltas, missingness, state restoration, native labels
and the full offline record-edit flow. Phase 6 forecasts are described above.

## Phase 4 Home dashboard

HomeDashboardCalculator derives current cash, recorded monthly flows/saving/rate,
top categories, recent activity and a cash-and-budget daily limit from the same
transactional BudgetSnapshot used by Phase 3. It delegates consumption/budget
formulas to BudgetCalculator. There are no new tables, counters, migrations,
dependencies, services or permissions.

HomeViewModel combines the financial snapshot and category labels. It applies
the injected clock in the ledger zone, refreshes on Home resume/manual Refresh,
and clears old totals on observation/calculation errors or missing ledger.
HomeScreen renders immutable state and navigates to existing transaction/category/
budget workflows. Its budget actions explicitly select the dashboard month;
bottom navigation continues to preserve the user's other Budget selection.

All derived amounts use exact checked minor units. Investment contributions
reduce cash but are not consumption, and future-dated entries cannot fund the
current daily limit. Coverage, cash/budget limits, zero-income rate, overflow
and unavailable later-phase metrics remain visible. Schema v3 and all earlier
data/migrations are unchanged. Phase 5 reports/charting are described above.

## Phase 3 budgets

BudgetCalculator is the single domain owner of consumption actuals, remaining,
utilization, status, daily average, projection and daily budget allowance. It
uses exact minor units/BigInteger and explicit rounding. Coverage and reporting
date are inputs; no clock calls or database access are hidden in formulas.
Unknown/partial history and projection overflow remain visible.

LocalBudgetRepository observes a Room @Transaction relation snapshot of ledger,
budgets and source transactions, so combined amount flows cannot briefly mix old
and new financial records. Category names/activity are observed separately for
display. Writes validate currency, expense identity/activity, nonnegative amounts
and combined allocation limits inside one database transaction. Schema v3 adds
only budget tables, indexes and integrity triggers; explicit 2→3 migration chains
from v1 and retains existing transaction guards.

BudgetViewModel owns month/editor/loading/error state and calls the calculator.
BudgetScreen renders state and emits actions; resume/Refresh updates the ledger
reporting date. No production dependencies were added. Home now consumes the
same source snapshot through the Phase 4 dashboard described above.

## Phase 2 transaction bookkeeping

Home links to transaction history and category management. Budget/Plan/Market
remain future-feature empty states. BookkeepingViewModel owns loading, filter,
editor, mutation and error state; Composables emit actions and render state.
Edits retain IDs and creation timestamps, close only on successful persistence,
and block duplicate submissions while writing. Saved records survive database
reopen; editor/filter state survives Activity recreation through the ViewModel.

TransactionRules and CategoryNames own domain validation. LocalBookkeepingRepository
uses Room transactions to serialize validation and writes, including duplicate-name
checks and referenced-category deletion. Category type is immutable. Archival
prevents new uses while preserving historical display/editing; restoring is
explicit. A renamed category updates history through its stable ID.

Room schema v2 adds finance_transaction with a ledger FK, a composite
(categoryId, transactionType) FK and indexed history queries. Explicit migration
1→2 preserves all v1 records. Fresh-create and migration paths install identical
database guards for amounts, coverage dates and category archival. Production
database construction registers both the migration and callback. There is no
destructive fallback. The migration test reconstructs the actual exported v1
schema and Room identity, then validates it through Room v2 and reopens it.

No new production dependencies, network permissions or services were added.
Phase 3 budget formulas and Phase 4 derived cash/Home totals are described above.

## Repository and restoration

The initial repository was empty: no commits, source, Gradle, Compose, tests,
dependencies, or conventions. Phase 0 established the design and an Android
build scaffold. A later cloud reconnection restored an empty checkout; this task
reconstructed the foundation and implemented Phase 1. Do not claim
that unpublished filesystem changes automatically survive a new environment.

## Layers

Source dependencies are Presentation → Domain ← Data. Runtime calls go from
ViewModel to domain repository interface to local implementation. Domain contains
no Android/Room/Compose types. Compose renders immutable lifecycle-collected
StateFlow and emits events; it cannot call DAOs, maintain duplicated totals,
or call external APIs. Hilt is the composition root.

Start with one `:app` module and package boundaries: `core` (currency, datetime,
UI, database composition, DI), `domain` (models, repository contracts, future
use cases), `data.local` (entities/DAO/seeds), `data.repository` (implementations),
and `presentation` (shell, settings, state). Introduce feature packages with their
owning phases rather than speculative empty services/modules.

The unchanged v1 tables store the singleton ledger and categories, with exported schema
and uniqueness indexes. Initialization and one-time category seeding are one
transaction. Reinitialization does not reset records. Future schema changes need
non-destructive migrations and migration tests. DataStore owns only appearance
preferences; ledger currency/zone/opening cash are authoritative Room fields.

Repository boundaries expose typed failures for expected storage/validation
errors. Cancellation propagates and unexpected programmer errors are not swallowed.
No raw exception payloads, amounts, notes, or credentials are logged. Failed
initialization/preferences show a recoverable UI error instead of invented data.

The UTC clock is injected; reporting defaults to Asia/Jakarta. Date ranges are
half-open and weeks start Monday. Money uses integer minor units and exact
decimal parsing/display; overflow and currency mismatches are explicit failures.
The theme follows system/light/dark and its preference persists locally.

## Stable compatible pins

| Dependency | Version | Justification |
| --- | --- | --- |
| AGP / Gradle | 8.13.2 / 8.13 | Previously tested stable API 36 build baseline |
| Kotlin / Compose compiler | 2.2.21 | Matching compiler plugins |
| Compose BOM | 2025.10.00 | Consistent stable UI, Material 3, and UI tests |
| Activity | 1.11.0 | Launcher, ViewModel factory, Compose host, edge-to-edge |
| Lifecycle | 2.9.4 | StateFlow collection and ViewModel scope |
| Navigation Compose | 2.9.5 | Saved-state four-tab navigation and settings/back |
| Room | 2.8.3 | Offline relational persistence and schema export |
| DataStore | 1.1.7 | Atomic durable nonfinancial preferences |
| Hilt | 2.57.2 | Application, repositories, database, clock, and ViewModel DI |
| KSP | 2.2.21-2.0.4 | Compatible Kotlin processor for Room and Hilt |
| Coroutines | 1.10.2 | Flow, structured concurrency, tests |
| JUnit / Android JUnit / runner | 4.13.2 / 1.3.0 / 1.7.0 | Unit and actual device instrumentation |

Published stable versions were verified against Google Maven/Maven Central
metadata before implementation. These preserve the tested Kotlin/AGP baseline;
newer available versions are upgrade advisories, not automatically compatible
upgrades. Record actual lint warnings without suppressing them. Compose core
icons provide small standard navigation icons; no extended icon/chart/ML library.

Retrofit/OkHttp/Serialization are deferred to market transport (Phase 10),
WorkManager to bounded refresh (14), and Keystore/optional biometric protection
to justified security/backup features (16/17). No internet permission or remote
provider is present. Cached quotes must retain source/retrieval timestamps and
last successful data on failure. Licensing and credentials require a documented
provider choice, never hardcoded UI or fragile scraping.

Security baseline: private storage, backup/transfer exclusions for every storage
domain, only intentional launcher Activity exported, no logs/telemetry/ads/API
keys. Room is not encrypted by default. Dedicated security/device/OEM backup
behavior is assessed in later gates; no invented cryptography.

## Historical Phase 1 validation

The current debug APK and Android test APK build successfully. All 34 JVM tests
pass (18 money, 8 date boundaries, 4 ViewModel/error/retry, 4 storage-error and
cancellation cases). `lintDebug` has 0 errors and 7 `NewerVersionAvailable`
advisories for the intentional Kotlin/Compose-compiler, Hilt, and coroutines pins.
No lint check was disabled. Prebuilt dependency native libraries are packaged
without additional stripping because this project does not compile native code.

The debug signature verifies and its manifest declares min SDK 26, target SDK
36, a launcher, and no internet permission. The exported Room v1 schema, XML,
local documentation links, and Git whitespace checks are valid.

**PASS: device checks executed on Android 8.0 / API 26.** All four instrumentation
tests passed with zero failures, errors, or skips: atomic/idempotent Room
initialization and reopening, DataStore recreation, four-tab/settings/back
navigation, and Activity recreation. `am start -W` returned `Status: ok`; the
running Home screen was captured and inspected. The device tests used the same
APK bytes as the published download (SHA-256 recorded in PHASE_1_STATUS.md).

The API 35 software emulator did not finish starting in the available setup;
API 26 succeeded using a foreground managed emulator session after stale locks
from an exited task emulator were removed. Only task-owned locks were cleared;
user data was not wiped. Hardware virtualization is unavailable in this cloud
machine. API 35/36 device behavior, physical ARM devices, and IDE sync remain
NOT VERIFIED; broader device coverage belongs to subsequent hardening gates.

The reusable setup was rerun successfully using retained verified SDK packages,
followed by the build/unit/lint/test-APK checks. Official archive fallback keeps
TLS and published checksums enabled if SDK Manager extraction fails. Cloud
scripts set the full JDK, SDK, writable preferences, and platform-CA truststore.
Publication/restoration in a new cloud task remains separate and NOT VERIFIED.

**Phase 1 gate: met.** Historical Phase 2 verification is recorded in
[PHASE_2_STATUS.md](PHASE_2_STATUS.md); current Phase 3 verification is in
[PHASE_3_STATUS.md](PHASE_3_STATUS.md); current Home verification is in
[PHASE_4_STATUS.md](PHASE_4_STATUS.md); current reports verification is in
[PHASE_5_STATUS.md](PHASE_5_STATUS.md); current forecast verification is in
[PHASE_6_STATUS.md](PHASE_6_STATUS.md); current goals verification is in
[PHASE_7_STATUS.md](PHASE_7_STATUS.md); current wealth verification is in
[PHASE_8_STATUS.md](PHASE_8_STATUS.md). Stop before Phase 9.
