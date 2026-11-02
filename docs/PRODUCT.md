# Product

Finance Planner is a private personal financial intelligence Android application.
It helps answer: what cash is available, whether spending is within budget,
what is safe to spend today, how behavior is changing, and when a purchase or
savings goal is affordable, and how recorded assets and liabilities affect tracked
net worth. Investment allocation follows;
external market information and evidence-based IPO monitoring are secondary.

The primary destinations are Home, Budget, Plan, and Market. Settings is reached
separately from the top bar. Use restrained, accessible Material 3 screens and
honest loading, empty, and error states. Charts must explain a useful comparison.

The offline finance MVP consists of transactions/categories, budgeting,
dashboard, reports, deterministic forecasts/recommendations, and goals/purchases
(Phases 1–7). Daily-use approval also requires the applicable security, backup,
test, UI, performance, and release gates. Current Phase 8 includes transaction
CRUD/history/filter, category management, monthly overall/category budgets and
a Home dashboard derived from those records. Home distinguishes recorded cash,
consumption and contributions, and bounds its daily limit by both cash and budget.
Missing coverage/future metrics are explicit. Weekly/monthly/yearly reports
and labeled charts distinguish complete, partial, unknown and future periods.
Plan provides rolling/weighted historical baselines for the next 1 and 3 calendar
months, with 3/6/12-month windows, explicit sample coverage/evidence and source
months. Deterministic rules expose inputs, thresholds and a version. Historical
ranges are not prediction intervals, and evidence strength is not a probability.
Goals and purchase plans support exact earmark progress, deadlines, priority,
pausing and deletion. Earmarks reserve existing recorded cash and lower Home's
daily limit; they never create extra wealth or a duplicate expense. A shared
priority scenario allocates unreserved cash once and shares the selected
forecast's monthly net capacity, assuming contributions at future month ends.
Insufficient history, nonpositive capacity, unbacked earmarks and range limits
remain explicit. No purchase or transfer is executed. Assets and manual liabilities have dated
observations, explicit missing values and saved frozen net-worth snapshots.
Acquisition cost is metadata only; non-cash wealth never increases spendable
cash. Historical totals exist only when saved, and changing/deleting current
items never rewrites them. Cash and goal earmarks are counted once; investment
holdings and unrecorded wealth/debts are explicitly outside this tracked total.
Investment holdings/market valuations remain future phases. The optional internal health indicator is deferred; no
health or credit score is calculated.

At the user's request, the full current interface supports English and Bahasa
Indonesia. Settings stores an explicit language choice independently of theme.
Both languages ship offline; stored category names/notes and accounting/date/
currency contracts never change when the UI language changes. This is a small
settings addition; other Phase 15 settings remain deferred.

Core records, reports, calculations, forecasts, goals, assets, investments, and
net worth must work offline. External outages must never break these features.
Personal records remain local; external requests contain provider inputs such
as symbols, not transaction history. No analytics SDKs, tracking, or ads.

Initial currency is IDR with exact values and Indonesian-friendly formatting.
Categories are editable data; category management starts in Phase 2. Investment
contributions reduce cash but are distinct from consumption expenses. Financial
formulas have a single domain owner. Confidence is qualitative evidence strength,
not certainty. Recommendations expose their rule and calculation.

Future scope: manual investment planning, locally cached
US and Indonesian watchlists/IHSG, evidence-based AI IPO radar, bounded background
refresh, settings, validated versioned export/import, and optional internal
financial health indicator. Never label that indicator a credit score.

Non-goals: banking connections, payments, trading/order execution, public fintech
accounts, mandatory servers, ML on small datasets, promised returns, unlicensed
scraping, telemetry, or shared private API keys inside an APK.

Final acceptance requires working offline bookkeeping, editing/deletion,
budgets/safe spending, weekly/monthly/yearly summaries, transparent forecasts,
explainable recommendations, goals/purchases, asset/net-worth/investment planning,
safe migrations and portability, restart persistence, release build and tests.
Market data must fail gracefully; provider availability is independently checked.
IPO radar distinguishes rumors from attributable authoritative evidence.
