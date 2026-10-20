# Sequential roadmap

Phase 0 design/build baseline was completed. Phase 1 is authorized and underway.
Stop after Phase 1's report; later phases require another instruction.

For each phase: inspect code/Git state, state goal/files/approach/risks/tests,
implement the smallest coherent change, validate meaningful checks, review
diff/status, document outcomes and report readiness YES/NO. Never count skipped,
zero-test, or unrun targets as passed. Preserve user changes; use the existing
isolated cloud checkout, no worktrees unless requested.

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
is the small settings foundation; full settings remain Phase 15. Market APIs,
transaction entry, budgets, analytics, forecasts, and goals have not started.
