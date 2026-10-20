# Architecture

## Repository and restoration

The initial repository was empty: no commits, source, Gradle, Compose, tests,
dependencies, or conventions. Phase 0 established the design and an Android
build scaffold. A later cloud reconnection restored an empty checkout; this task
reconstructs the foundation and implements the authorized Phase 1. Do not claim
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

Room version 1 stores the singleton ledger and categories, with exported schema
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

## Validation

The current debug APK and Android test APK build successfully. All 34 JVM tests
pass (18 money, 8 date boundaries, 4 ViewModel/error/retry, 4 storage-error and
cancellation cases). `lintDebug` has 0 errors and 7 `NewerVersionAvailable`
advisories for the intentional Kotlin/Compose-compiler, Hilt, and coroutines pins.
No lint check was disabled. Prebuilt dependency native libraries are packaged
without additional stripping because this project does not compile native code.

The debug signature verifies and its manifest declares min SDK 26, target SDK
36, a launcher, and no internet permission. The exported Room v1 schema, XML,
local documentation links, and Git whitespace checks are valid.

**Device checks remain pending.** The API 35 emulator could not finish starting
without hardware virtualization; a minimum-SDK emulator is being prepared.
The four compiled instrumentation tests cover atomic/idempotent Room bootstrap
and reopening, DataStore recreation, four-tab/settings/back navigation, and
Activity recreation. Compilation is not counted as executing these tests.
Do not call Phase 1 complete until those checks and app launch are verified.
