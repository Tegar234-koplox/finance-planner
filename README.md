# Finance Planner

Private, local-first Kotlin Android app. The current implementation is the
Phase 1 foundation: Material 3 Home/Budget/Plan/Market navigation, separate
Settings, persistent appearance preference, Hilt, and a versioned Room database.
Transaction entry, budgets, goals, analytics, and market APIs are later phases.
The demo contains empty states, not invented financial values.

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

The downloadable Phase 1 preview is a navigation/storage foundation, with no
transaction entry, budgeting calculations, or market feeds yet. Its build,
34 unit tests, lint, and debug signature have passed. All four Android
instrumentation tests and app launch passed on Android 8.0. See
[Phase 1 status](docs/PHASE_1_STATUS.md) for exact results and remaining coverage.

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
