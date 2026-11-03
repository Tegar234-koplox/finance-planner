# Phase 10 — provider boundary, HTTPS transport and offline quote cache

Application readiness: PENDING final Android/upgrade gates. Stop after Phase 10;
Phase 11 has not started. GitHub API access still returns Forbidden; PR/squash
merge is not claimed. Phase 9 is now present in main through PR #1 (1a8a516),
and this branch starts from that exact tree.

## Result and boundaries

- Provider-neutral instrument identities include provider, venue and symbol.
  Typed success, empty and sanitized failure outcomes separate no data from zero.
  Registry IDs must be unique. The production registry is deliberately empty:
  no sample feed, real quote endpoint, secret, symbol list or watchlist is shipped.
- Platform HTTPS transport sends one explicit GET, retains TLS validation,
  disables redirects/cleartext/HTTP caching, limits bodies to 1 MiB UTF-8, and
  configures 10-second connect/read timeouts plus a 20-second body-read deadline.
  Headers are validated; error outcomes contain no raw body, URL, header or
  exception message. Streams/connections close and cancellation propagates.
  Retry-After seconds and HTTP dates support a bounded one-day rate-limit pause.
- MarketDatabase v1 (`market-cache.db`) is independent of FinanceDatabase v6
  (`finance.db`). One atomic row stores quote and sync metadata under the composite
  instrument key. Financial entities, migrations, schema exports and calculations
  are unchanged. Market storage/read failures cannot gate the finance foundation.
- Exact decimal prices retain their source currency, observed/fetched timestamps
  and source name. Provider payloads accept up to 20 integer and 18 fractional
  digits, never Double or ledger minor-unit conversion. Mismatched identity,
  malformed currency/price, future/negative time, older observations and unexpected
  currency changes cannot replace a known quote. Zero remains a known value.
- Empty/offline/timeout/auth/rate-limit/provider/invalid results preserve the last
  successful quote and timestamp. Last attempt/failure and retry time persist.
  Cooldowns prevent a new request; expiry does not automatically start one.
  Cancellation preserves data without leaving a stored busy state.
- Missing/recent/outdated is derived from both source and retrieval age. The
  15-minute boundary is outdated, as are clock rollback and an old observation
  refetched now. “Recent” never claims a live exchange price.
- Market prioritizes price/currency, source/time and a short state/error. Detailed
  help opens via the info icon. Empty state links to manual investments; failed
  reads clear unsupported old display values and offer retry. A visible-screen
  age timer/resume hook only recalculates age, without any network refresh.
- English and Indonesian resources remain available offline. No market price
  changes cash, manual holdings, cost, goals, forecasts or net worth automatically.
  There is no silent USD→IDR conversion. All personal records remain local inputs
  to finance only; transport has no financial repository dependency.

## Verification

Commands used in cloud:

```sh
source /workspace/.finance-tooling/activate.sh
cd /workspace/finance-planner
./gradlew --offline --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew --offline --no-daemon --console=plain connectedDebugAndroidTest -Pandroid.injected.device.serial=emulator-5556
```

| Gate | Result |
| --- | --- |
| JVM suite | 320 tests, zero failures/errors/skips; 35 new Phase 10 tests |
| Lint | 0 errors, 7 existing NewerVersionAvailable advisories |
| Debug app/test APK build | PASS |
| Targeted market Android suite | PENDING |
| Entire Android suite | PENDING |
| Actual APK 0.9.0→0.10.0 update | PENDING |
| Git commit identity | Tegar Putra, tegarsangputra2@gmail.com; no attribution trailers |
| GitHub PR/squash merge | BLOCKED by API access; not claimed |

QuoteRepositoryTest (18) covers exact decimals/currencies/attribution, known zero,
empty/error preservation, invalid payloads, independent venues/providers, old/future
times, currency mismatch, successful retries, persistent bounded cooldown, no
configured provider, cancellation, sanitized unexpected exceptions, storage errors,
serialized refresh and exact freshness boundaries/rollback. HttpTransportTest (11)
executes the real transport with deterministic HttpURLConnection doubles to check
GET options, closure, timeouts, redirect rejection, declared/streaming limits,
empty/malformed UTF-8, status classification, Retry-After, sanitized connection/TLS
errors, cancellation and header/endpoint validation. MarketViewModelTest (6)
covers empty/no-request state, read recovery, reactive ages, duplicate-click guard,
cooldown expiry without HTTP, and write errors remaining visible until retry.

MarketStorageTest (4) exercises real API-26 Room storage, exact 38-digit prices,
atomic upsert, composite keys, zero/missing distinction, failure/cooldown persistence,
file close/reopen, malformed partial stored rows and financial database isolation.
MarketContentUiTest (2) covers full 18-decimal rendering, currency, source/time,
outdated/offline/unknown states, both languages, optional help, empty/error states
and useful actions. MarketUiTest (1) drives the actual MainActivity through
Market→manual portfolio→back, language persistence and Activity recreation.
NavigationTest's old placeholder expectation now checks the real empty screen;
all previous financial assertions are retained.

The first build passed 320 JVM tests and identified an unused old offline caption.
Removing it also removed a still-needed dp import; compilation caught the mistake,
the import was restored, and the complete build/unit/lint checks passed again.
Failed attempts are not counted as passes. The separate update emulator required
software acceleration because KVM is unavailable; an initial hardware-mode startup
failure is not counted as a device test.

## APK and manual testing / Uji manual

Development APK: `/workspace/artifacts/finance-planner-phase10-debug.apk`.
Version 0.10.0, code 10; minSdk 26, targetSdk 36; 13,965,558 bytes.
SHA-256: `214f5cc056750241e9264d4c20857657a8d49ad7be016199829a9992e02ef147`.
Signing certificate SHA-256:
`8d5371f1873f08db2511df399485c7d870c9aaa8671a7fdcbbd7184b09fe215e`.
It matches the previously delivered debug APK, allowing an in-place update.

Install over 0.9.0 without uninstalling or clearing data. Open Market: there is
no connected price source in this build, so expect “No market prices yet”. Open
the information icon, dismiss it, then Open portfolio and navigate back. Change
language/theme in Settings and restart. Financial records and manual values
remain available offline. Do not expect watchlist editing or live quotes yet.

Pasang APK di atas versi 0.9.0 tanpa menghapus aplikasi/data. Buka Pasar: layar
menampilkan “Belum ada harga pasar”, karena penyedia harga belum terhubung.
Ikon info membuka penjelasan, dan Buka portofolio membuka investasi manual.
Coba ganti bahasa/tema, tutup-buka aplikasi dan aktifkan mode pesawat. Catatan
keuangan tetap tersedia; harga langsung/watchlist masuk fase berikutnya.

## Delivery and limits

Branch: `feat/phase10-market-foundation`, based on current main 1a8a516.
All new commit authors/committers use the user's configured, historically verified
identity, with no bot/GPT/co-author/contributor trailers. No direct main push.
The GitHub user endpoint still returns Forbidden. The environment's allowed hosts
do not include api.github.com; there is no GitHub connector/browser tool here.
Environment settings/publication were not modified to bypass this restriction.
Authenticated API access is required to complete PR creation and squash merge.

Evidence is limited to an API-26 x86_64 software emulator at 720×1280 and JVM tests.
Real market API/credentials/availability/licensing, physical ARM/newer Android,
release signing/minification and background scheduling are not claimed. No
provider-specific adapter/US watchlist (11), IHSG (12), IPO (13), WorkManager (14),
full settings (15), export/import or release work was started. No dependency was
added; Internet permission and cleartext prohibition are explicit.
