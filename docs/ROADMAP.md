# Sequential roadmap

Phases 0–9 implementation and application gates are complete. Phase 9 passed
285 JVM tests, all 45 Android tests on API 26, debug/test-APK builds, and lint
(0 errors, 7 existing upgrade advisories). Actual APK 0.8→0.9 migration 5→6
preserved all original columns/rows in ten nonempty tables and legacy v1 captures.
Compact bilingual UI and manual investment flows are verified. GitHub branch
pushes work; PR creation/squash merge remain blocked by API access (Forbidden).
See PHASE_9_STATUS.md for evidence/limits and the outstanding delivery step.
Phase 1–8 reports remain historical. Stop after Phase 9; Phase 10 has not started.

For each phase: inspect code/Git state, state goal/files/approach/risks/tests,
implement the smallest coherent change, validate meaningful checks, review
diff/status, document outcomes and report readiness YES/NO. Never count skipped,
zero-test, or unrun targets as passed. Preserve user changes; use the existing
isolated cloud checkout, no worktrees unless requested.
For every subsequent coherent change, create a commit. When a feature is complete,
push its branch, create a pull request and squash merge after checks pass.
Never push changes directly to main. Keep the user’s identity for commits and
GitHub publication; report any missing account/API access explicitly.
Keep primary screens concise: metrics/actions first, short status captions,
and detailed calculation explanations behind an information control.
Use the user's verified Git account identity for author and committer. Do not
attribute changes to bots, GPT or similar identities, and do not add co-author,
co-contributor or generated-contributor trailers. Confirm the account identity
from configured/connected account evidence before committing; never invent it.

| Phase | Scope | Required gate |
| --- | --- | --- |
| 0 | Audit, PRODUCT/ARCHITECTURE/DATA_MODEL/ROADMAP, minimal build | Build, internally consistent model, justified dependencies |
| 1 | Theme/shell/navigation, Hilt, Room, DataStore, errors/repositories, dates/money/components | Build, meaningful unit tests, lint, device launch; Room/DataStore recreation and Compose navigation |
| 2 | Transaction CRUD/history/filter, editable categories | Persistence/edit/delete/date/type/category integrity and migration tests |
| 3 | Category/overall budgets, actual/remaining/utilization/projection/safe spending | Zero/no data/negative/first-final-day/leap-month calculation boundaries |
| 4 | Decision-useful Home dashboard | Values follow source records; missing future metrics remain unavailable |
| 5 | Weekly/monthly/yearly reports and useful charts | Complete/partial period, zero income, comparisons, missing history |
| 6 | Rolling/weighted forecasts, explainable rules, optional health | Both horizons, sample thresholds, partial data, exact rules/uncertainty |
| 7 | Goals and purchase planning/capacity/dates | Funded/overfunded/overdue/no-capacity/month-boundary calculations |
| 8 | Assets/net worth and dated history | No double counting or invented historical valuations |
| 9 | Manual investments/allocations/contributions | Exact targets, zero denominator, redemption/cash/wealth consistency |
| 10 | Provider abstractions, transport, quote cache | Offline/stale/empty/provider-error cache behavior |
| 11 | Configurable US watchlists/indices | Actual accessible coverage, licensing/rate limits/key handling |
| 12 | IHSG through authorized provider | Actual provider availability; cached fallback; no fragile scraping |
| 13 | Evidence-based AI IPO radar | All weights/dedup/supersession/age/official-source rules tested |
| 14 | Bounded WorkManager refresh/reminders | Constraints/backoff/permanent failure/unique work/cancellation |
| 15 | Full theme/currency/period/refresh/notification/privacy settings | Durable preferences, safe ledger settings, denied permissions |
| 16 | Dedicated security hardening | Storage/backup/intents/components/logs/keys/debug-release review |
| 17 | Versioned export/validated atomic import/optional encryption | Round trip, invalid/duplicate/migration/interrupted restore preserves data |
| 18 | Responsive/accessibility/UI polish | Light/dark, screen sizes, text, keyboard/back, touch targets |
| 19 | Harden existing unit/DAO/repository/UI suites | Nonzero suites; core offline/restart flows and boundaries |
| 20 | Evidence-based performance review | Profiling of queries/recomposition/lists/Flows/refresh; no main-thread IO |
| 21 | Release/signing/shrinker/docs/user guide | Actual release build, migrations/backup/security/edge-case/device tests |

Tests are added throughout, not postponed to 19. Phase 1 appearance preference
is the small settings foundation; Phase 6 adds the user-requested English/
Indonesian language preference. Other full settings remain Phase 15. Transaction
entry/history/filter, category management, monthly budgets and the source-backed
Home decision dashboard, weekly/monthly/yearly reports and labeled charts are
implemented, together with rolling/weighted 1/3-month forecasts and explainable
rules using complete historical months. The interface is bilingual; a health
indicator is deferred. Goals/purchases, earmark progress, cash reservations and
shared capacity/date scenarios are implemented. Non-cash assets, manual liabilities,
dated valuations and frozen tracked-net-worth snapshots are implemented. Manual investment holdings, exact allocation targets, contribution plans,
redemptions and investment-inclusive frozen snapshots are implemented. Market
provider APIs have not started.
