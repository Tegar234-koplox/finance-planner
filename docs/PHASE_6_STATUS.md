# Phase 6 — complete

Date: 2026-11-02. Required gate: **YES**.
Phase 7 has not started.

## Implemented scope

- Plan now provides rolling and weighted historical-baseline forecasts. Windows
  are 3, 6 or 12 preceding calendar months; horizons are the next 1 and 3 complete
  calendar months, excluding the current month's remainder.
- A sample must be a fully tracked completed month. Covered empty months count
  as zero; unknown/partial months do not. Counts, exclusions, source months and
  weights are visible. Current and future records cannot change the baseline.
  Opening cash is excluded from predicted flows.
- Rolling weights are all 1; weighted weights increase 1…N from oldest to newest
  eligible month. BigInteger ratios round once per horizon, half-even to minor
  units. Three-month totals do not multiply the rounded one-month value.
  RecordedFlowCalculator owns saving/rate/net accounting for Home, Reports and
  estimated gross totals; contributions remain distinct from consumption.
- No samples yields unavailable. One sample has LOW evidence, 2–5 MEDIUM, 6+
  HIGHER. These are qualitative sample-quantity labels, not success probabilities.
  Historical min/max monthly ranges scaled by the horizon are explicitly not
  prediction intervals or future bounds. One sample cannot establish variation.
- Rules version 1 exposes selected inputs, dates and thresholds: fewer than two
  samples, estimated consumption above income, contribution pressure on cash,
  missing next-month overall budget, consumption above that allocation, and
  a 20%+ consumption rise between the latest two complete months with positive
  earlier consumption. Forecast rules compare the rounded one-month estimate;
  the rise rule compares exact integer actuals before formatting its percentage.
- An overflowing historical monthly gross total clears estimates/rules. A longer
  horizon can be unavailable while a shorter one stays valid. Range-endpoint
  and net-flow overflow do not hide valid gross point estimates. Unknown data,
  zero denominators and unsupported dates have explicit unavailable states.
- ForecastViewModel observes the existing transactional BudgetSnapshot. Saved
  financial edits/deletion and budget changes update forecasts/rules; refresh
  and resume use the injected clock in the ledger zone. Errors hide old estimates
  and rules; retry preserves method/window. SavedStateHandle retains only UI
  choices; source disclosure uses rememberSaveable.
- Native Compose text/cards expose exact values without color or hover dependence.
  Home and Reports point to Plan. No goals, purchase capacity, assets, market
  data or optional health indicator is implemented in this phase.
- The user also requested English and Bahasa Indonesia throughout the interface.
  All 317 resource keys have both translations with matching format placeholders,
  including errors, explanations, type labels and accessibility text. Settings
  persists language independently of appearance; changes apply immediately.
  Existing installs default to English. Both languages ship offline, including
  in App Bundles. User category names/notes, ISO dates, IDR entry/formatting and
  stored amounts are never translated or rewritten.
- Version 0.6.0/code 6. Schema v3, financial storage/repositories, migrations, dependency
  pins and permissions are unchanged. The settings repository adds only a language
  preference. No forecast, sample or rule is persisted
  as a financial record or aggregate counter.

## Verification

Commands run in the actual Codex cloud checkout:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
# Fresh Phase 5 baseline before edits:
./gradlew --no-daemon --console=plain :app:testDebugUnitTest --rerun
# Final Phase 6 build/checks:
./gradlew --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Baseline JVM suite | 137 tests passed before Phase 6 edits |
| Debug application and Android test APK | PASS |
| Final JVM suite | 176 tests; 0 failures, errors or skips |
| Targeted Forecast storage/UI suites | 3 tests passed before localization; all included in the passing final full suite |
| Targeted language/resource/storage suites | 4 tests; 0 failures, errors or skips |
| Final complete Android suite | 27 tests; 0 failures, errors or skips |
| Lint | 0 errors; 7 existing NewerVersionAvailable advisories; none suppressed |
| Schema/storage/dependency review | v1/v2/v3 exports, financial repositories, migrations, dependency pins and permissions unchanged; DataStore adds UI language only |
| APK signature/manifest | Verified; same public debug certificate as Phase 5; min SDK 26, target SDK 36; no INTERNET permission |
| Actual APK update/restart | PASS; Phase 5→final bilingual Phase 6 retained identical rows in all five financial tables |
| Positive forecast/rule visual smoke | PASS; actual APK, light/dark, English/Indonesian; language retained after process restart |
| Artifact/source integrity | APK checksum matches final full-suite build; whitespace, resource parity and documentation links checked |

The 39 additional JVM tests cover both methods and horizons, 3/6/12-month windows,
evidence boundaries, covered zero versus absent/partial history, first/final day,
future exclusions, year rollover/leap February, strict date limits, one-rounding
behavior, exact rule thresholds/equality, zero income/budget/denominator, distinct
contribution accounting and local/aggregate overflow. ViewModel tests cover
source edits/deletion, budget updates, read failure/retry, missing ledger,
saved/invalid options and ledger-zone midnight completing a new month. Fake
repository write methods throw to verify that forecasts remain read-only.
The additional Foundation test checks that failed language writes retain the
last saved choice, successful writes clear errors, and language changes preserve theme.

ForecastStorageTest uses real Room income/expense records across completed months,
current-month exclusion, both methods, budget changes, edits, category archive/
rename, database reopen and deletion into a known-zero month. It checks that
calculation leaves the transactional source snapshot unchanged.

ForecastContentUiTest renders the real ForecastViewModel with an isolated Room
database. It checks both horizon amounts, evidence, weighted versus rolling
results, budget rules, source weights, reactive edit/delete updates and a dark
theme. ForecastUiTest checks honest unavailable history in the actual Plan tab,
option selection, Activity recreation and Settings/back navigation. Navigation
tests now assert the implemented Forecasts screen. Existing transaction, budget,
Home, Reports, migration and persistence tests remain in the final suite.

LanguageResourcesTest checks Indonesian and English resolution on API 26 without
changing the device language. LanguageUiTest switches the actual application to
Indonesian, recreates the Activity, visits Home/Budget/Plan/Reports, creates a
fractional-IDR expense with a private bilingual note, switches back to English,
verifies the amount/note and deletes its fixture. SettingsStorageTest verifies
language persistence across store recreation, theme preservation and recovery
from an invalid stored language value. The Indonesian resource qualifier follows
Android's legacy naming; both languages are bundled offline. The final lint run
has no new localization warnings and suppresses none.

Device coverage: Android 8.0/API 26 x86_64 software emulator, 720×1280 at density
320 (360dp width). No skipped, zero-test or unfinished run is counted as passed.

## APK

File: `/workspace/artifacts/finance-planner-phase6-debug.apk`
(13,073,130 bytes).

SHA-256:
`351df575aa9b8c75e9d69f9c4bcde92b0ab316eaf185039df93a72f68805bc6f`

Public debug certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`

Checksum: `/workspace/artifacts/finance-planner-phase6-debug.apk.sha256`.
Final machine-readable evidence is saved to
`/workspace/artifacts/phase6-test-summary.json`.

The delivered bytes match the application APK used in the final full Android
suite. An actual update smoke installs Phase 5, creates one synthetic Rp10,000
expense through its UI, then installs the final bilingual Phase 6 APK with
`adb install -r`. Before/after snapshots retain schema v3 and identical rows in
ledger (1), category (11), finance_transaction (1), monthly_budget (0) and
category_budget (0).

A separate temporary completed-history fixture supplies two full months of
income, consumption and contributions, a next-month overall budget, a current
expense and a future expense. It exercises positive weighted estimates, exact
rule inputs and exclusions in the real APK. Its weighted one-month consumption
is Rp23,333.33, displayed in the ledger's Indonesian notation as Rp23.333,33.
Light/dark and Indonesian/English screenshots use synthetic emulator data only;
no example transactions are bundled in the APK. The visual fixture was restored
exactly to the upgrade snapshot, the original synthetic expense was deleted
through the UI, and the database was checked empty of transactions. Original
ledger/category/budget rows remained unchanged. Appearance returned to Follow
system and language to English. Restart reached Home and Plan successfully.

Screenshots under `/workspace/artifacts`: `finance-planner-phase6-empty.png`,
`finance-planner-phase6-method-light.png`, `finance-planner-phase6-forecast-light.png`,
`finance-planner-phase6-forecast-dark.png`, `finance-planner-phase6-rules-light.png`,
`finance-planner-phase6-language-settings-id.png`, `finance-planner-phase6-rules-id.png`
and `finance-planner-phase6-method-id.png`. Earlier failing automation attempts
are not counted as successful final checks.

## Try the delivered build

Install the APK as an update, then open Plan. Switch Rolling mean/Weighted mean
and the history window; inspect both horizon dates and Show source months.
Change a saved transaction or next-month overall budget, return to Plan and
check how its estimates and rule inputs respond.

A newly initialized ledger has no complete past months. Its estimates correctly
remain unavailable until at least one fully tracked calendar month has ended;
an initial month begun mid-month never becomes a full sample. No demonstration
history is bundled in the APK and no rebasing/import feature is added.

Settings → Language switches between English and Bahasa Indonesia immediately.
Pengaturan → Bahasa offers the same choices in Indonesian. The preference
survives reopening the app; changing it preserves user data and monetary notation.

## Limits and phase boundary

This is a debug development APK. Physical ARM devices, API 35/36 runtime behavior,
release signing/minification, full TalkBack/large-font testing and Android Studio
IDE sync remain unverified. No forecast-accuracy/backtest claim is made. The
methods repeat recorded monthly totals; they do not model seasonality, pay dates,
inflation, new obligations or expected market returns. More samples can still
be incomplete or unrepresentative. The absence of a triggered rule establishes
neither complete records nor financial health. Forecasts are not a bank balance
or an affordability guarantee. An optional internal health indicator is deferred; no credit score is
calculated.

On API 26, the three-button system navigation bar retains a pale scrim with dark
app appearance; refining its icon contrast remains an existing Phase 18 concern.

Existing Phase 2–5 work was preserved, with a local source baseline before edits.
No environment configuration, publication or repository push was performed;
new-task restoration of these Phase 6 edits is not claimed.

The user's GitHub delivery preference is recorded in ROADMAP.md: branch →
commit → push → PR → merge, with no direct push to main. No push/merge was performed
in this completion.

**Phase 6 is ready. Stop here; Phase 7 requires a new instruction.**
