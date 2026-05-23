# Third-party notices

App Manager Tech bundles the following third-party open-source libraries.
All are licensed under permissive or weak-copyleft licences compatible with
F-Droid distribution; none collects or transmits user data.

| Library | Version | License | Source |
|---|---|---|---|
| Kotlin standard library | 2.1.0 | Apache 2.0 | https://github.com/JetBrains/kotlin |
| Android Gradle Plugin | 8.7.3 | Apache 2.0 | https://android.googlesource.com/platform/tools/base |
| AndroidX Core / Activity / Lifecycle / Navigation | various | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Jetpack Compose (Material 3, UI, runtime) | BOM 2024.12.01 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Hilt / Dagger | 2.55 | Apache 2.0 | https://github.com/google/dagger |
| Room | 2.6.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| DataStore Preferences | 1.1.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| WorkManager | 2.10.0 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Coil Compose | 2.7.0 | Apache 2.0 | https://github.com/coil-kt/coil |
| Accompanist Permissions | 0.36.0 | Apache 2.0 | https://github.com/google/accompanist |
| Timber | 5.0.1 | Apache 2.0 | https://github.com/JakeWharton/timber |
| AndroidX core-splashscreen | 1.0.1 | Apache 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Kotlinx Coroutines | 1.9.0 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |

## Test-only dependencies (not shipped in the APK)

| Library | License |
|---|---|
| JUnit Jupiter (JUnit 5) | EPL 2.0 |
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
