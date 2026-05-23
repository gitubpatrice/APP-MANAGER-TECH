# App Manager Tech

A local-only Android app manager / cleaner inspired by SD Maid, for non-root
devices. Inspect, batch-act on, and audit every installed app — without
sending a single byte off the device.

**F-Droid only. No Google Mobile Services, no Firebase, no Internet permission.**

---

## Highlights

- **Inspect** every installed app: label, version, install / data / cache sizes,
  install source (Play / F-Droid / sideload), last-used timestamp, signing
  certificate SHA-256, requested + granted permissions.
- **Batch act**: select multiple apps and uninstall / clear cache / force stop
  in a guided sequence (the OS shows its own per-app confirmation; we never
  bypass it).
- **Background scan**: a periodic WorkManager job refreshes the catalogue
  daily or weekly and posts a single local notification when the total cache
  size crosses a user-configured threshold.
- **Storage analyser**: per-category breakdown, top-N largest apps,
  top-N cache hogs.
- **Export**: storage report to JSON or CSV via Storage Access Framework —
  no `MANAGE_EXTERNAL_STORAGE` needed.
- **Ignore list**: opt-out specific packages from batch actions and
  notifications (capped at 500 entries).
- **Security audit**: surfaces apps with Device Administrator privileges or
  enabled Accessibility services — common abuse vectors.

---

## Stack

| Layer | Tooling |
|---|---|
| Language | Kotlin 2.1.0 |
| Build | Android Gradle Plugin 8.7.3, Gradle 8.11.1, JDK 17 |
| UI | Jetpack Compose Material 3 (BOM 2024.12.01), edge-to-edge |
| DI | Hilt 2.55 (KSP, no kapt) + Hilt-Work for `@HiltWorker` |
| Storage | Room 2.6.1 (`app_info` v2, additive migrations only) + DataStore Preferences |
| Background | WorkManager 2.10.0 (on-demand init, no androidx.startup) |
| Coroutines | kotlinx-coroutines 1.9.0 |
| Logging | Timber 5 (DebugTree in debug, NoOpReleaseTree in release) |
| Splash | androidx.core:core-splashscreen (transparent icon workaround Android 12+) |
| Tests | JUnit 5 + Truth + MockK + Turbine + Room-testing + Robolectric |

All dependencies are Apache 2.0 / MIT / BSD. **Zero proprietary SDK**.

---

## Architecture

```
ui/
├── screens/{applist, appdetail, storage, settings, about,
│            cleaner, ignorelist, export, securityaudit}/
│   ├── XxxScreen.kt           Compose UI (TopAppBar + Scaffold + sections)
│   └── XxxViewModel.kt        @HiltViewModel, StateFlow<UiState>, Events Channel
├── components/{dialogs, settings, state}/  Reusable composables
└── theme/                     Material 3 + BrandBlue (#2460AB) + BrandDanger (#C62828)

domain/
├── model/         AppInfo, AppCategory, FilterOptions, StorageReport,
│                  AppAction, AppDetail, ZombieApp, ExportReport,
│                  ScanInterval, ExportFormat, ThemeMode, AppSortOrder
├── repository/    AppInfoRepository, IgnoreListRepository (interfaces only)
└── usecase/       19 use cases — one operation per file, returns Outcome<T>

data/
├── local/
│   ├── db/{entity, dao, dto}/   Room AppInfoEntity v2 + AppInfoDao + AggregateRow
│   ├── datastore/               SettingsRepository (DataStore Preferences)
│   └── db/Migrations.kt         Strictly additive Room migrations
├── repository/    AppInfoRepositoryImpl, IgnoreListRepositoryImpl, AppInfoMapper
└── system/        IntentFactory, NotificationChannels, NotificationHelper,
                   WorkScheduler, workers/BackgroundScanWorker (@HiltWorker)

core/
├── ext/           StringExt (anti-ReDoS regex), FlowExt (oneShotEvents),
│                  TimeConstants (MS_PER_DAY, STATEFLOW_STOP_TIMEOUT_MS, MAX_IGNORED_PACKAGES)
└── result/        Outcome<T> sealed (Success/Failure/Loading) + extensions

di/                4 Hilt @Module: AppModule, CoroutineModule, DatabaseModule, RepositoryModule
```

**Rules** (enforced by code review):

1. UI layer never imports `android.content.pm.*` / `android.app.usage.*` /
   anything from `data/`. Everything goes through use cases.
2. Use cases return `Outcome<T>` — no exception ever crosses the repository
   boundary.
3. Repository implementations stay in `data/repository/`; their interfaces
   live in `domain/repository/`. Exception: `SettingsRepository` is in
   `data/local/datastore/` (documented in code).
4. Room migrations are **always additive** (`ALTER ADD`, `CREATE INDEX IF NOT EXISTS`).
   `fallbackToDestructiveMigration` is never used.
5. `BrandBlue` (#2460AB) for confirmation dialogs and primary CTAs. `BrandDanger`
   (#C62828) for destructive intent (uninstall, force stop, disable, delete).
   Cross-theme constant — never `colorScheme.error`.

---

## Build

```bash
# Prerequisites: JDK 17, Android SDK 35
./gradlew :app:assembleDebug         # debug APK (3 ABI splits ~18.9 MB each)
./gradlew :app:assembleRelease       # release APK (R8 minify+shrink, 3 splits ~1.86 MB each)
./gradlew :app:testDebugUnitTest     # JUnit 5 unit tests
./gradlew :app:connectedDebugAndroidTest  # Room CRUD + migration tests (emulator/device)
```

The release build runs `lintVitalRelease`, R8 minify with shrink resources,
ART profile compilation. APK reduction is **~10×** (debug → release) thanks
to dead-code elimination and tree-shaking.

---

## F-Droid compliance

See [FDROID.md](FDROID.md) for the full distribution policy. Summary:

- **No `INTERNET` permission**. The app is strictly local.
- **No Google Mobile Services**, no Firebase, no Crashlytics, no Play Core.
- **No telemetry SDKs** of any kind.
- **All deps Apache 2.0 / MIT / BSD / LGPL / GPL / AGPL** (no proprietary AAR).
- **Reproducible builds** — pinned AGP / Kotlin / Gradle / JDK; no
  `BuildConfig.BUILD_TIME` baked into the APK.

The build is signed by F-Droid; we publish unsigned APKs in GitHub releases
for users who want to sideload with our own signature.

---

## Permissions

| Permission | When | Why |
|---|---|---|
| `QUERY_ALL_PACKAGES` | install-time | Enumerate installed apps via PackageManager |
| `PACKAGE_USAGE_STATS` | granted in OS Settings (user opt-in) | Last-used timestamps + StorageStats sizes |
| `GET_PACKAGE_SIZE` | install-time | StorageStatsManager cache / data sizes |
| `REQUEST_DELETE_PACKAGES` | install-time | Trigger system uninstall dialog |
| `KILL_BACKGROUND_PROCESSES` | install-time | Best-effort force-stop |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | Cache-threshold notification |

Never requested: `INTERNET`, location, contacts, SMS, calendar,
`MANAGE_EXTERNAL_STORAGE`. The app degrades gracefully if any optional
permission is denied (sizes / last-used fall back to 0; notifications stay
silent).

---

## Security

See [SECURITY.md](SECURITY.md) for threat model, cryptographic primitives,
audit history, and disclosure policy.

Highlights:

- `FLAG_SECURE` opt-in to block screenshots and recent-apps previews.
- Room schemas exported and checked into `schemas/` for migration tests.
- All `Outcome.Failure` paths use typed `AppError` — no PII in error strings.
- Timber is silent in release builds (NoOpReleaseTree).
- R8 with shrink resources for release; ProGuard keep rules audited.

---

## Project history

7 phases of structured delivery, each closed with a passing release build and
a multi-axis audit (quality + coherence + security):

| Phase | Scope |
|---|---|
| I | Scaffold (Kotlin + Compose + Hilt + KSP) — 49 files, 6 audit fixes |
| II | Data layer (Room v2 + PackageManager + StorageStats + UsageStats + SD Maid enrichments) |
| III | 8 use cases + filter/sort/storage report models + IntentFactory |
| IV | 4 ViewModels (combine reactive on AppList) + brand identity + splash + logo |
| V | 5 screens Compose + 10 reusable components + i18n FR+EN |
| VI | 11 use cases (zombies, permission filter, ignore list, export, schedule scan) + WorkManager + Notifications |
| VII | Unit tests + Room CRUD/migration tests + release WorkManager init fix + 9 audit fixes |
| VIII | 4 dedicated screens (Cleaner, IgnoreList, Export, SecurityAudit) + AboutScreen links + 12 audit fixes |

Total: **226 i18n strings** EN+FR parity 100%, **3 release APK splits 1.86 MB**,
**0 GMS dependency**.

---

## Licence

Apache 2.0 — see [LICENSE](LICENSE).

Files Tech — France.
