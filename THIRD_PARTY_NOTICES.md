# Third-party notices

App Manager Tech bundles the following third-party open-source libraries
(versions from `gradle/libs.versions.toml`, v0.5.1). All are under the Apache
License 2.0; none collects or transmits user data.

| Library | Version | License | Source |
|---|---|---|---|
| Kotlin standard library | 2.4.10 | Apache 2.0 | https://github.com/JetBrains/kotlin |
| Kotlinx Coroutines | 1.11.0 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Kotlinx Serialization (core, pulled by Navigation) | 1.8.1 | Apache 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| AndroidX Core | 1.19.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX Activity Compose | 1.13.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX Lifecycle | 2.11.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX Navigation Compose | 2.10.2 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Jetpack Compose (UI, Material 3, icons) | BOM 2026.09.00 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Hilt / Dagger | 2.60.1 | Apache 2.0 | https://github.com/google/dagger |
| AndroidX Hilt (Navigation Compose, Work) | 1.4.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Room | 2.8.5 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| DataStore Preferences | 1.1.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| WorkManager | 2.12.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| DocumentFile | 1.0.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Core SplashScreen | 1.2.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Timber | 5.0.1 | Apache 2.0 | https://github.com/JakeWharton/timber |
| Okio (file I/O, used by DataStore) | 3.9.0 | Apache 2.0 | https://github.com/square/okio |

Declared in the build but **not shipped**: Coil Compose 2.7.0 (Apache 2.0) is
not used by the code yet, so R8 removes it, and OkHttp 4.12.0 with it: the R8
mapping of the release build lists no `coil.*` and no `okhttp3.*` class. Okio
stays, for DataStore; Coil's dependency graph is what raises it to 3.9.0.

Build tools, not shipped either: Android Gradle Plugin 9.4.1, Gradle 9.8.1, KSP
2.3.12, detekt 1.23.8.

## Test-only dependencies (not shipped in the APK)

| Library | License |
|---|---|
| JUnit Jupiter (JUnit 6) | EPL 2.0 |
| Truth | Apache 2.0 |
| MockK | Apache 2.0 |
| Turbine | Apache 2.0 |
| Robolectric | MIT |
| Room Testing | Apache 2.0 |

---

The complete Apache License 2.0 text accompanies the source tree as
[LICENSE](LICENSE). Each library above ships its own license file inside its
artifact; running `./gradlew :app:licensee` (if the Licensee plugin is added)
would regenerate this list from the live dependency graph.

**No proprietary SDK.** No Google Mobile Services, no Firebase, no
Crashlytics, no AppsFlyer, no advertising library, no telemetry SDK.
