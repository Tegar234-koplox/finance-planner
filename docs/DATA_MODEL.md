# Data model

Phase 1 implements only ledger/category tables and appearance preference. The
remaining tables are a proposal, introduced with their owning phases and proper
Room migrations. IDs are stable UUIDs except singleton ledger ID 1. Enums use
stable names, timestamps UTC epoch milliseconds, dates epoch days, month keys
validated YYYY-MM. Optional values use null, not fake zero/sentinel timestamps.

Money is `Long` minor units plus an ISO currency code, never floating point.
IDR uses ISO scale 2: Rp500,000 = 50,000,000 minor units. Fractional digits are
preserved. Parsing accepts strict locale grouping and optional minus (for signed
cash), rejects symbols/exponents/excess decimals/malformed grouping and overflow.
Actual transactions will require strictly positive amounts. Arithmetic is checked;
do not use unbounded SQLite integer SUM without an overflow-safe strategy.
The ledger is initially single currency; preferences cannot relabel stored money.

## Implemented schema version 1

- **Ledger:** singleton ID, currency code, reporting zone, tracked-from date,
  signed opening cash, creation and optional update time. Opening cash precedes
  the coverage start; later transactions must be within coverage. Rebasing earlier
  history needs explicit import/opening-balance rules. Defaults: IDR, Asia/Jakarta.
- **Category:** UUID, name, normalized name, transaction type INCOME/EXPENSE/
  INVESTMENT, active flag, display order, optional unique seed key, timestamps.
  Unique (type, normalized name), seed key, and (ID, type) indexes. Seeds are
  inserted once in the same transaction as ledger creation. Renaming/archival
  are Phase 2; referenced categories cannot be deleted or retyped unsafely.
- **DataStore:** SYSTEM/LIGHT/DARK appearance preference. No money, credentials,
  or duplicated ledger settings. Invalid/unreadable preferences expose an error.

## Proposed later tables

| Concept | Fields / integrity |
| --- | --- |
| Transaction (2) | UUID, ledger/type, positive amount, category FK/type match, note, calendar date, created/updated time; investment action contribution/redemption with optional holding FK added in 9 |
| Monthly budget (3) | ID, ledger, month, nonnegative allocation; unique ledger/month |
| Category budget (3) | ID, ledger, expense category FK, month, allocation; unique ledger/category/month |
| Financial goal (7) | ID, ledger, name, type, positive target, nonnegative current earmark, as-of date, target date, priority, status, timestamps; progress derived |
| Asset (8) | ID, ledger, name/category, acquisition cost, manual current value, purchase/valuation dates, notes; excludes cash/investments |
| Net-worth snapshot (8/9) | Ledger/date unique, dated cash/asset/investment/debt components, total, method version; historical observations never rebuilt using current valuations |
| Investment (9) | ID, ledger, instrument/symbol/market, class, manual invested cost/current value, valuation date, notes; exclusive ownership with assets |
| Allocation target (9) | Ledger/bucket unique, basis points 0–10000; complete target plans total 10000; cash comes from ledger, not a duplicate holding |
| Contribution plan (9) | Ledger/month unique, target; actual contributions/redemptions derived from transactions |
| Watchlist (10) | ID, symbol, market, display name, configurable provider/instrument identity; identity unique per provider/market |
| Quote/sync state (10) | Watchlist FK, exact canonical decimal price/currency/change, source time, last-success time; separate last-attempt/error, preserve cache on failure |
| IPO company/evidence (13) | Company/name/industry; evidence FK, signal type, source URL/identifier, text, publication/retrieval time, verified/active/supersedes; score/status derived from deduplicated evidence |

Separate overall/category budget tables avoid nullable unique-key ambiguity.
When an overall budget exists, category allocations cannot exceed it; expense
usage includes unbudgeted categories. Investments are not consumption expenses.
Derived usage/remaining/progress/rates never become editable duplicate counters.

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

Forecasts use complete covered months, distinguish zero from unknown/partial
coverage, and expose method/window/sample count and LOW (<2), MEDIUM (2–5),
HIGHER (6+) evidence. Zero samples means unavailable. Recommendations expose
actual inputs and rule version; do not compare partial and complete periods
without normalization. Health is optional and requires defined component/missing-
data rules. IPO rumor weights never establish official filing or IPO certainty.

Every post-v1 schema change requires migration and tests. Backups have a separate
schemaVersion. Import validates amounts/currency/dates/enums/references/duplicates
before an atomic commit; invalid input leaves existing data intact. Credentials
are never exported. No destructive migration or custom cryptography.
