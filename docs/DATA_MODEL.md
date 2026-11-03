# Data model

Phase 9 extends schema v5 with manual holdings, dated holding values, allocation
targets, contribution plans and frozen investment components. Schema v6 preserves
all old fields and adds nullable action/link transaction metadata plus a zero-default
snapshot investment subtotal. Historical schema exports 1–5 remain immutable.
IDs are UUIDs except singleton ledger 1 and allocation bucket keys. Enums use
stable names; UTC timestamps, epoch-day dates and strict YYYY-MM months remain.
Money/currency rules are unchanged; missing values use null rather than fake zero.

Money is `Long` minor units plus an ISO currency code, never floating point.
IDR uses ISO scale 2: Rp500,000 = 50,000,000 minor units. Fractional digits are
preserved. Parsing accepts strict locale grouping and optional minus (for signed
cash), rejects symbols/exponents/excess decimals/malformed grouping and overflow.
Transactions require strictly positive amounts. Arithmetic is checked;
do not use unbounded SQLite integer SUM without an overflow-safe strategy.
The ledger is initially single currency; preferences cannot relabel stored money.

## Implemented schema version 6

Version 1 ledger/category columns and identities are unchanged. The explicit
Room 1→2 migration adds transactions and integrity triggers without reseeding,
resetting, or rewriting existing ledger/category data. Migration 2→3 adds the two
budget tables and guards; ledger/category/transaction definitions remain unchanged.
Migration 3→4 adds finance_goal and its guards without rewriting any prior row.
Migration 4→5 adds wealth_item/value/capture/capture_line with matching fresh-create
guards, without reseeding or modifying any of the six prior financial tables.

- **Ledger:** singleton ID, currency code, reporting zone, tracked-from date,
  signed opening cash, creation and optional update time. Opening cash precedes
  the coverage start; later transactions must be within coverage. Rebasing earlier
  history needs explicit import/opening-balance rules. Defaults: IDR, Asia/Jakarta.
- **Category:** UUID, name, normalized name, transaction type INCOME/EXPENSE/
  INVESTMENT, active flag, display order, optional unique seed key, timestamps.
  Unique (type, normalized name), seed key, and (ID, type) indexes. Seeds are
  inserted once in the same transaction as ledger creation. Renaming/archival
  are supported. Type is immutable after creation. Referenced categories cannot
  be deleted; archive/restore preserves their identity and historical references.
  New names are trimmed, whitespace-normalized, and compared with Locale.ROOT.
- **Transaction:** UUID, singleton ledger FK, stable type name, positive Long
  minor units, composite (category ID, type) FK, calendar date as epoch day,
  optional note represented by an empty string (maximum 500 characters), and
  creation/optional update timestamps. Currency is inherited from the ledger.
  Repository writes are atomic and validate currency, coverage date, category
  identity/type/activity and note length. Database triggers reject nonpositive
  amounts, dates before coverage and new/retargeted use of archived categories.
  An existing transaction may retain its archived category when edited.
  History sorts by date, creation time and ID descending. Inclusive date bounds,
  type and category filters combine with AND; unbounded filters are explicit.
  Legacy investment records are contributions; Phase 9 action/holding fields
  are described below. Derived usage and cash totals are never stored.
- **Monthly budget:** UUID, ledger FK, strict YYYY-MM key, nonnegative Long minor
  allocation, creation/update timestamps, unique ledger/month.
- **Category budget:** UUID, ledger FK, month, composite category/type FK constrained
  to EXPENSE, nonnegative allocation, timestamps, unique ledger/category/month.
  Referenced categories cannot be deleted. Existing archived allocations remain
  editable; new allocations require an active expense category.
- **DataStore:** SYSTEM/LIGHT/DARK appearance and ENGLISH/INDONESIAN interface
  language, stored as independent enum-name keys. Missing language defaults to
  ENGLISH for existing installs. No money, credentials or duplicated ledger
  settings. Invalid/unreadable preferences expose an error; a failed write does
  not pretend to have saved a choice. Changing language never rewrites categories,
  notes, currency, dates, amounts or any Room row.
- **Financial goal:** UUID, singleton ledger FK, normalized display name (1–80
  characters), SAVING/PURCHASE kind, positive Long target, nonnegative Long
  current earmark, earmark as-of epoch day, target epoch day, HIGH/NORMAL/LOW
  priority, ACTIVE/PAUSED state, UTC creation/update times. An earmark may exceed
  target. Repository validation requires its as-of date within ledger coverage
  and no later than the ledger's current reporting date. Target dates may be in
  the past. SQLite guards validate amounts/types/enums/date bounds/coverage and
  a restricted ledger FK; dynamic current-date validation belongs to the injected
  clock/repository/calculator, not SQLite's system clock. No cached progress,
  completion date, contribution counter or asset/transaction link is stored.

## Phase 8 assets, liabilities and frozen dated history

- **wealth_item:** UUID, singleton ledger FK, cleaned name (1–80 characters),
  immutable ASSET/LIABILITY kind, compatible PROPERTY/VEHICLE/PERSONAL or LOAN/CARD
  category (OTHER for either), acquisition/opening epoch day, optional asset-only
  nonnegative acquisition-cost minor units, notes (≤500), active flag, timestamps.
  Acquisition may predate ledger coverage but not today or any existing value.
- **wealth_value:** UUID, item FK with delete cascade, unique item/date, nonnegative
  manual value/amount owed, notes and timestamps. Dates must be on/after the
  acquisition and no later than the injected ledger-zone today. Same-date entry
  must edit the existing value; it cannot silently replace it. Cost never supplies
  an unknown observation. Currency is inherited from the ledger.
- **wealth_capture:** UUID, ledger FK, unique ledger/date, currency, signed cash,
  nonnegative gross assets/liabilities, signed total, TRACKED_V1 method and UTC
  capture timestamp. Only today's date is capturable; all components must be
  known and representable as Long. Duplicate dates require explicit delete first.
- **wealth_capture_line:** UUID, capture FK with delete cascade, unique capture/
  source item ID, copied name/kind/category/nonnegative value and original value
  date. The source ID intentionally has no FK to current items. Editing,
  archiving or deleting those items/values cannot rewrite or remove copied lines.
  SQL rejects UPDATE to captures/lines. Only explicit capture deletion removes
  both parent and components. Repository writes parent and lines atomically.

WealthSource reads ledger, all transactions, items with values and captures with
copied lines in one Room transaction. Cash reuses RecordedCashCalculator across
all covered history. Latest value on/before today wins for each active acquired
item; future values/items are excluded and counted if the clock moves backward.
An item's last observation date and age remain visible, including stale values.
Missing active assets/debts make the relevant subtotal and total unavailable.
Exclusion affects only today's total and does not record a sale or repayment.

Current tracked total = recorded cash + active asset observations − active debt
observations. Cash includes earmarked funds exactly once, with no extra goal
term. Holdings, unrecorded wealth/debt and market values are excluded. A cash
purchase can reduce cash and add a separate manual asset value once; its cost is
not added again. Contributions reduce cash and never manufacture holdings.
Gross sums, final total and changes use BigInteger before checked Long conversion;
an unsupported subtotal/total cannot be captured or wrap. Cash, assets and debt
can remain independently visible when another total overflows.

Historical totals are exclusively stored captures, sorted by date descending;
no chart/reconstruction/backfill applies current values to old dates. Delta is
the exact signed difference between the latest two captures, unavailable with
fewer than two or overflow. It is not a return percentage. Original component
names/dates/values survive current source changes and deletion; current source
editing cannot recreate a historical observation. Currency/method/line sums and
the cash+assets−liabilities equation are validated when reading captures.
Phase 9 adds the explicit v2 method below without reinterpreting v1.

## Phase 9 investment records and accounting

- **investment_holding:** UUID, ledger FK, cleaned 1–80-character name, optional
  symbol (≤30), STOCK/BOND/FUND/GOLD/OTHER bucket, acquisition date, optional
  nonnegative recorded cost, notes (≤500), active flag and timestamps. CASH is
  prohibited here; ledger cash has one authority. Acquisition may predate ledger
  tracking but cannot exceed today or an existing value/linked entry date.
- **investment_value:** UUID, holding FK with delete cascade, unique holding/date,
  nonnegative manual value, notes and timestamps. Dates cannot precede acquisition
  or be future. Normal same-date entry must edit its observation. The cash-entry
  form explicitly upserts the supplied post-entry balance for that date atomically.
- **allocation_target:** bucket PK (CASH plus all five investment classes), ledger
  FK, integer 0–10000 basis points. A complete six-row plan sums exactly 10000;
  empty means no targets. Validation/replacement is atomic; SQL bounds partial
  sums and invalid partial plans fail domain reads instead of masquerading as valid.
- **contribution_plan:** UUID, ledger FK, unique ledger/month, nonnegative target.
  Zero is an explicit target without a percentage. Removing a target keeps all
  financial transactions and valuations.
- **finance_transaction additions:** investmentAction nullable CONTRIBUTION/
  REDEMPTION, holdingId nullable FK with restrict delete/update. Legacy null action
  on INVESTMENT means contribution; both fields must be null on other types.
  Linked dates respect acquisition; inactive holdings cannot receive new/retargeted
  entries but existing links remain editable. All old categories/types/amounts/
  notes/IDs/dates/timestamps stay unchanged after migration.
- **wealth_capture additions:** investmentsMinor nonnegative default 0. TRACKED_V1
  must have zero and no investment lines. TRACKED_V2 captures cash + assets +
  investments − debt with frozen component validation. New captures use v2.
- **wealth_capture_investment:** UUID, capture FK/cascade, unique capture/source
  holding, copied name/bucket/value/original valuation date. No FK to current
  holdings; deletion/exclusion/current reclassification cannot rewrite it. Updates
  are forbidden. Snapshot deletion removes copied components explicitly.

Recorded cash = opening + income − consumption − contributions + redemptions.
Redemptions do not count as income, consumption, saving or recurring forecast
capacity. Monthly contribution target progress uses gross contributions; redeemed
and net amounts are separate. Correcting a cash record does not synthesize an
undo of an independently recorded holding observation. A linked entry requires
an explicit nonnegative post-entry value and commits cash/value together; no
amount is added to that value a second time. Unlinked entries affect cash only.

Current portfolio = cash + active manual holding values; property and other
non-investment assets enter net worth separately. Cost remains metadata, never
an extra asset or inferred profit/return. Latest on/before-date observations are
used, with original dates visible. Missing active values block unsupported totals;
future observations are excluded after clock rollback. Snapshot components never
backfill unobserved history. v1/v2 historical deltas are unavailable across scope
versions; equal-scope stored observations can compare exactly.

Allocation denominator is cash + holding values. Actual shares are unavailable
for missing values, nonpositive totals or negative cash. Exact largest-remainder
rounding distributes 10000 basis points (stable bucket-order ties); targets must
sum to 10000. Target currency amounts use the same exact distribution across the
portfolio's minor units, so monetary gaps sum to zero. No floating-point money,
trading action, automatic purchase, return model or provider quote is involved.

## Phase 10 market cache (separate database)

MarketDatabase v1 owns `quote_cache` in `market-cache.db`; FinanceDatabase stays
v6, with all 15 tables and old schema exports unchanged. There is no financial
foreign key, quote-to-holding link or implicit currency conversion.

The composite primary key is `(provider, market, symbol)`: symbols from different
venues/providers cannot collide. A row has nullable quote fields (exact plain
decimal price text, ISO currency, source observation UTC millis, successful fetch
UTC millis, attributed source name), required last-attempt UTC millis, nullable
typed failure and nullable rate-limit retry time. All five quote fields must be
present together or all absent; a quote with price zero is known, not missing.
Price has up to 20 integer/18 fractional digits (38 significant digits maximum),
is nonnegative, and never passes through Double or fixed ledger minor units.
Currency remains the provider quote currency, not the ledger currency.

Success atomically replaces quote and clears failure/cooldown. Failure/empty
updates attempt/error metadata while retaining the old quote and successful fetch
time. Cancellation leaves last-attempt evidence and old data without a permanent
busy flag. Duplicate identities update one row; other markets/providers stay
independent. Unexpected older source times, future/negative times, mismatched
instrument identity, malformed decimal/currency and currency changes are rejected.
Initial empty/no-provider states do not invent a price or production instrument.

Freshness is derived, never persisted: missing if no quote; recent only when both
`0 <= now − observedAt < 15 minutes` and `0 <= now − fetchedAt < 15 minutes`;
otherwise outdated. A successful request for an old observation is still outdated.
Rate-limit Retry-After is bounded to one day; no refresh occurs before the saved
deadline. No request is automatically scheduled when the deadline passes.

## Proposed later tables

| Concept | Fields / integrity |
| --- | --- |
| Watchlist (11) | Provider/instrument identity, market/symbol, configurable name |
| IPO company/evidence (13) | Attributable sources, publication/retrieval times, active/superseded and deduplicated evidence |

Separate overall/category budget tables avoid nullable unique-key ambiguity.
When an overall budget exists, category allocations cannot exceed it; expense
usage includes unbudgeted categories. Investments are not consumption expenses.
Derived usage/remaining/progress/rates never become editable duplicate counters.

Room reads ledger, allocations and transactions as one transactional snapshot.
Writes serialize checks and updates. Repository allocation sums use BigInteger.
SQLite cap guards sum existing nonnegative allocations: these are bounded by a
present Long cap, and an overflow while adding a cap aborts the statement because
such a sum exceeds every representable cap. No SQLite aggregate feeds displayed
financial totals. Deleting an overall cap retains category allocations.

## Accounting and calculations

Cash = opening cash + income − expenses − investment contributions + redemptions.
Monthly saving = income − consumption expenses; rate is unavailable at zero
income. Returned investment principal is not salary/income. Manual investment
cost/value must not also auto-increment on transaction linking without an explicit
change of authority. Asset creation does not create a second purchase expense.
Net worth = exclusive cash + investments + assets − tracked debt; goals are
earmarks of already counted wealth, never extra assets. Untracked debt/holdings
are explicitly disclosed. Quotes in USD do not silently value an IDR ledger.

Remaining budget = allocation − expenses (negative is valid). Utilization is
undefined when allocation is zero. Safe daily budget = floor(max(remaining,0) /
remaining days including today), with zero for nonpositive days. Home safe-to-spend
must also respect liquid cash and explicitly implemented reservations. Burn rate
= spending / elapsed days; projection = burn rate × month days, with one explicit
half-even output rounding. First/final day, leap months, zero/negative/no data,
and exact overflow boundaries require tests in Phase 3. Status: OVER_BUDGET when
spending exceeds allocation; otherwise HIGH ≥95%, WATCH ≥80%, SAFE below 80%.
Zero allocation/zero usage is neutral, not an invented ratio.

Phase 3 calculates recorded actuals through min(reporting date, month end), and
excludes income, investments and future-dated entries. No allocation differs
from an explicit zero allocation. Remaining can be negative. Percentage display
truncates to two decimals so it cannot round up across a status threshold;
threshold comparisons use exact integers. Average/projection round half-even to
minor units, with projection calculated once from the unrounded original ratio.
Daily allowance floors to minor units and includes today; closed months use zero.

Months before coverage and future months have unknown actuals, not zero.
Partial-coverage months show recorded expenses/remaining and explicitly withhold
full-month projection/daily allowance. A valid zero-spending covered period is
zero. Aggregate actuals beyond Long produce a visible OUT_OF_RANGE error;
projection-only overflow preserves actuals while marking projection unavailable.
The Budget screen's allowance does not include liquid cash or reservations.

Phase 4 Home derives recorded cash from signed opening cash plus all covered
income minus consumption and investment contributions, through the reporting
date. Prior-month records affect cash, not current-month totals. Current-month
saving remains income minus consumption; contributions affect cash separately.
Saving rate is undefined at zero income. Sums use BigInteger with checked signed
Long conversion; overflow hides totals rather than wrapping or retaining old
values. Future records are counted separately and never fund today's spending.

Home reuses BudgetCalculator for consumption, status and projection. Its daily
spending limit is floor(min(max(recorded cash,0), max(remaining budget,0)) /
remaining days including today). It is unavailable without an overall cap,
before tracking or with partial current-month coverage. No reservations exist
in Phase 4's historical implementation. Phase 7 now applies goal reserves as
specified below. Names/archival follow stable category IDs.
Recent activity sorts by date, creation time and ID descending and is limited
to five; the top three consumption categories use stable ID tie-breaking.
Home links to assets and tracked net worth without mixing non-cash values into
spending cash. Market metrics remain unavailable. Forecasts/goals/wealth use
their separate contracts below.

Phase 5 RecordedFlowCalculator is shared by Home and reports: income, consumption,
contributions, saving and saving rate have one accounting owner. Period net cash
flow additionally subtracts contributions and excludes opening cash. Its overflow
is local/unavailable while valid gross totals remain visible. Cash balance still
folds the full ledger with BigInteger so large historical inflows/outflows can
cancel before checked Long conversion.

Reports use half-open calendar weeks (Monday), months and years, capped at the
ledger reporting date. A ReportSlice carries its full period, actual covered
range, coverage status, nullable totals and source-record count. Zero covered
activity is zero; untracked/future slices have null totals. Daily buckets for
weeks/months and monthly buckets for years reconcile with the selected totals.
Category shares include archived/unallocated categories and truncate to two
decimals. Only rendering fractions use floating point; labels/accounting are exact.

Complete periods compare complete preceding calendar periods, with differing
lengths disclosed. In-progress periods compare equal elapsed calendar days from
the start of the previous period. Selected partial history, incomplete prior
coverage, too few prior days, unsupported dates or arithmetic limits make
comparison unavailable. A zero previous amount has an absolute delta but no
percentage change; a zero current income has no saving rate. Previous-total or
comparison-delta overflow never hides valid current totals.

## Phase 7 earmarks, capacity and dates

BudgetSnapshotEntity reads goals in the same Room transaction as ledger, budgets
and transactions. BudgetSnapshot carries a default-empty goals list, and the
existing source repository is the common owner of this mapping. Goal writes
serialize in withTransaction; they do not create/edit/delete transactions,
budgets or ledger cash. Deletion explicitly releases only the planning reserve.

RecordedCashCalculator is the single owner of all-history recorded cash used by
Home and GoalPlanner. GoalRules owns validation, name cleanup and exact total
earmarks. ACTIVE and PAUSED earmarks both stay reserved, including overfunding.
They represent existing cash and are never added to wealth or expenses. Cash can
later fall below declared earmarks after legitimate transaction edits; such
writes are allowed, while Home floors spendable cash at zero and goal scenario
dates are withheld until records/earmarks are reconciled.

`unreserved cash = max(recorded cash − sum(all current earmarks), 0)`.
Home's daily limit is floor(min(unreserved cash, max(remaining overall budget,0))
/ remaining days including today), under the same overall-cap/current-month
coverage requirements as before. An overflowing aggregate reserve is reported
without hiding valid cash/flow totals; unreserved cash is exactly zero in that case.
Budget's allocation-only daily allowance still has no liquid-cash/reserve cap.

Goal progress = earmark / target × 100, truncated to two decimals, with EMPTY,
PARTIAL, FUNDED and OVERFUNDED states. Remaining = max(target − earmark,0).
An incomplete target is overdue when targetDate < today, and due today when
equal. A sufficient earmark is not evidence of a historical achievement date.
The as-of date remains visible and no growth or purchase happens automatically.

Planning uses the same method/window selected in Forecasts. Monthly capacity
= max(the rounded one-month forecast income − consumption − contributions,0),
under the Phase 6 accounting contract. At least two complete samples are
required for future dates. Missing history, a baseline/range error and a known
zero capacity remain distinct. A negative net-flow overflow is still known to
have zero positive capacity. Current/future transaction exclusions are inherited
from ForecastCalculator; actual current records still affect recorded cash.

Rows sort by priority rank (HIGH, NORMAL, LOW), target date, creation time and ID.
One shared scenario assigns each active goal min(remaining, unassigned free cash)
before allocating the same monthly capacity to cumulative remaining needs in
that order. Paused goals get no new free cash/capacity, but keep reserves. Cash
assignments are not saved into earmarks. Fully earmarked goals consume no future
capacity; excess earmarks are not silently released.

When all earmarks are cash-backed, a fully earmarked goal or one covered by its
scenario cash assignment is ready today, even without history. Otherwise:
`months = ceil(cumulative remaining after scenario cash / shared capacity)`;
ready date is the end of current month + months. The first future contribution
is the next full calendar month's end, even on today's month-end. Month-length,
leap-year, year rollover and supported years 0001–9999 are respected. Large
cumulative sums use BigInteger; date range checks precede calendar arithmetic.

The separate per-goal monthly amount = ceil(remaining earmark / eligible future
month-end dates before/on the target). It is for that goal alone, not a reserve
of capacity; when no such dates remain and money is needed it is unavailable.
The priority scenario date, not the individual quotient, determines whether the
shared scenario is ready by the deadline. No statistical certainty, return,
inflation, remainder-of-month income or unrecorded obligation is assumed.

## Phase 6 forecast and rule contract

ForecastCalculator uses the 3, 6 or 12 calendar months preceding the current
ledger month. A month qualifies only if its first day is on/after trackedFrom;
all candidate months have already ended. Covered months without records are
zero samples. Unknown/partial months are omitted, with counts shown; older
months outside the chosen window never backfill omitted slots. Current-month
and future records are counted as excluded. Opening cash never enters a sample.

For N eligible samples, oldest first, rolling weights are all 1 and weighted
weights are 1…N. For each gross series (income, consumption, contributions):

`horizon estimate = halfEven(sum(month amount × weight) × horizon months / sum(weights))`.

BigInteger numerators avoid intermediate overflow. Each horizon rounds the
original ratio once to minor units; the 3-month total does not multiply a rounded
1-month amount. RecordedFlowCalculator owns saving/rate/net accounting for both
recorded and estimated gross totals. Saving and net flow derive from rounded
gross amounts; zero income has no saving rate. Predictions are explicitly
separate from recorded samples and never become transactions.

Horizons cover the next 1 and 3 whole calendar months, starting on the next
month's first day. This month's remainder is excluded. Estimates repeat a
monthly baseline; there is no seasonality, trend extrapolation, expected return,
pay-date model, bill reservation or purchase-capacity calculation. The month
length is not normalized; the method uses complete monthly totals.

Zero samples means unavailable; one gives LOW evidence, 2–5 MEDIUM and 6+ HIGHER.
Evidence is qualitative data quantity, never a success probability. For each
gross series, the observed monthly minimum/maximum multiplied by the horizon
is shown as a historical range, not a statistical confidence/prediction interval
or a bound on future behavior. One sample cannot establish variability.

Historical monthly gross overflow clears estimates/rules. An overflowing horizon
is unavailable independently of a valid shorter horizon. A range endpoint can
overflow without hiding a valid point estimate. Net-flow overflow is local and
does not hide valid gross totals. Unsupported future calendar years are unavailable.

Rules version 1 evaluates the rounded 1-month baseline for forecast rules;
integer comparisons are exact and no percentage rounding decides a threshold:

| Rule | Trigger / inputs |
| --- | --- |
| More history | fewer than 2 eligible samples |
| Income–consumption gap | forecast consumption > forecast income |
| Contribution pressure | contributions > 0 and income − consumption − contributions < 0; BigInteger sign check |
| Missing next budget | valid 1-month forecast and no next-month overall allocation; category-only budgets do not substitute |
| Next budget gap | forecast consumption > next-month overall allocation, including a zero allocation |
| Consumption rise | latest two eligible complete months; previous > 0 and latest × 100 ≥ previous × 120; displayed percentage truncates to 2 decimals |

Rules expose selected method/window, inputs, relevant dates and version. The
historical-rise rule uses actual months rather than either forecast method.
No trigger does not establish healthy finances or complete records. There is
no optional health indicator until component/missing-data rules are defined.
IPO rumor weights never establish official filing or IPO certainty.

Every post-v1 schema change requires migration and tests. Backups have a separate
schemaVersion. Import validates amounts/currency/dates/enums/references/duplicates
before an atomic commit; invalid input leaves existing data intact. Credentials
are never exported. No destructive migration or custom cryptography.
