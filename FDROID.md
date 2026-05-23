# F-Droid distribution policy

App Manager Tech ships **only on F-Droid**. There is no Google Play release
and there will never be one. This file documents the constraints that flow
from that policy so every contributor (human or LLM) keeps the door closed
to proprietary dependencies.

---

## Hard constraints (must NEVER be added)

| Forbidden | Reason | F-Droid antifeature it triggers |
|---|---|---|
| `com.google.android.gms:*` | Google Mobile Services is proprietary | `NonFreeDep` |
| `com.google.firebase:*` | Firebase SDK is proprietary | `NonFreeDep` |
| `com.google.android.play:*` | Play Core / Play Services | `NonFreeDep` |
| `com.crashlytics.*` / Firebase Crashlytics | Proprietary telemetry | `NonFreeDep` + `Tracking` |
| Google Analytics, AppsFlyer, Mixpanel, Sentry SaaS | Telemetry SDKs | `Tracking` |
| AdMob, Unity Ads, Facebook Audience | Ad SDKs | `Ads` |
| Facebook SDK, GoogleSignIn, Microsoft AAD | OAuth lock-in to proprietary platforms | `NonFreeDep` |
| Google Maps SDK | Proprietary, requires API key | `NonFreeNet` |
| Closed-source binary asset (`.aar` without sources) | Cannot be reproduced | `NonFreeAssets` |

Any dependency on the list above is a **release blocker**. The grep
`firebase|gms|play-services|crashlytics|admob` MUST return 0 matches on the
project tree before tagging a release.

---

## Allowed equivalents

| Need | Use this | Why F-Droid-OK |
|---|---|---|
| Crash reporting | [ACRA](https://github.com/ACRA/acra) (self-hosted endpoint) | Apache 2.0, no telemetry SaaS |
| Push notifications | [UnifiedPush](https://unifiedpush.org/) or none | Open-source distributor protocol |
| Maps | [OpenStreetMap via osmdroid](https://github.com/osmdroid/osmdroid) | Apache 2.0, no API key |
| Auth | Username/password local or [Bitwarden CLI](https://github.com/bitwarden/clients) | No SaaS lock-in |
| In-app updates | F-Droid client handles it | Don't bundle a proprietary updater |
| Background jobs | WorkManager (already in the catalog) | Native AndroidX, no GMS |
| Analytics | Don't. App Manager Tech does no analytics. | Trust > metrics |

---

## Inclusion checklist (run before every tag)

```
[ ] grep -r --include='*.kt' --include='*.kts' --include='*.toml' \
      -E '(firebase|gms|play-services|crashlytics|admob|tealium|appsflyer|mixpanel)' .
      → must return 0 matches
[ ] No `INTERNET` permission in AndroidManifest.xml unless a feature truly needs it
    (currently absent — the app is strictly local)
[ ] All dependencies in `gradle/libs.versions.toml` are Apache 2.0, MIT, BSD,
    LGPL, GPL or AGPL (check each on Maven Central → "Licenses" tab)
[ ] No `compileOnly` proprietary stubs that hide a runtime classpath leak
[ ] `app/build.gradle.kts` has no `googleServices()` apply, no `firebase {}` block
[ ] Splash, icons, sounds: all locally produced or under a free licence
[ ] Fastlane metadata under `fastlane/metadata/android/{en-US,fr-FR}/` is
    complete (title, short/full description, changelogs/<versionCode>.txt
    ≤ 500 chars, screenshots without proprietary OEM watermarks)
[ ] F-Droid recipe metadata yml in `fdroiddata/metadata/com.filestech.appmanager.yml`
    declares: SourceCode, IssueTracker, Changelog, License, AutoUpdateMode,
    Builds[].versionName/versionCode/commit/gradle: yes, no Builds[].srclibs
    referring to closed-source code
```

---

## Permissions narrative for the F-Droid maintainer

App Manager Tech is a **fully local** app manager / cleaner inspired by
SD Maid. Every permission below has a single justified use, documented in
SECURITY.md:

- `QUERY_ALL_PACKAGES` — enumerate installed apps (core feature).
- `PACKAGE_USAGE_STATS` — last-used timestamps & accurate cache/data sizes
  via `StorageStatsManager` (the ONLY way to get cache size for another app
  without root). Granted by the user via OS Settings; the app degrades
  gracefully if denied.
- `GET_PACKAGE_SIZE` — install/data/cache size (normal, install-time).
- `REQUEST_DELETE_PACKAGES` — uninstall flow (user confirms in OS dialog).
- `KILL_BACKGROUND_PROCESSES` — stop background processes of other apps.

Never requested: `INTERNET`, `ACCESS_FINE_LOCATION`, `READ_CONTACTS`,
`READ_SMS`, `MANAGE_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`.

---

## Reproducible builds

Target: every F-Droid build is **byte-for-byte reproducible** from the
tagged commit.

- Pin AGP, Kotlin, Gradle, JDK in `gradle/libs.versions.toml` and
  `gradle/wrapper/gradle-wrapper.properties`.
- Pin every dependency version explicitly — no `+` or `latest.release`.
- No `BuildConfig.BUILD_TIME = System.currentTimeMillis()` or any other
  build-time timestamp baked into the APK.
- Signing: F-Droid signs the AAB themselves; we provide an unsigned APK in
  the GitHub release for users who prefer to sideload our own signature.

---

## Last-resort policy

If a future feature genuinely needs a SaaS dependency (e.g. WebDAV cloud
backup of the action history), the corresponding code path MUST be:

1. Behind a build flavour (`gplay` vs `fdroid`) so F-Droid builds exclude it.
2. Behind a runtime feature flag so the user opts in explicitly.
3. Documented in the F-Droid metadata `AntiFeatures:` section.
4. Reviewed by a maintainer before merge.

When in doubt: don't add it. Trust > convenience. Local > cloud.
