# Privacy Policy — App Manager Tech

**Effective date:** 2026-05-23

App Manager Tech is a local-only Android application. It does not collect,
transmit, or share any user data. This privacy policy documents that
commitment in plain terms.

---

## 1. Data we collect

**None.** The app reads metadata that already exists on your device
(installed-apps catalogue from `PackageManager`, sizes from `StorageStatsManager`,
last-used timestamps from `UsageStatsManager`) and stores a cached copy in a
private SQLite database (Room) inside the app's own private storage. This cache
never leaves the device.

The app does not request the `INTERNET` permission. It cannot open a network
socket. It cannot send anything to any server, ours or otherwise.

---

## 2. Data we store on your device

| What | Where | When deleted |
|---|---|---|
| App catalogue snapshot | `Room` DB in app private storage | App uninstall, "Clear data" in OS Settings |
| User preferences (theme, scan interval, ignore list, …) | `DataStore` Preferences in app private storage | Same |
| Notification channel registration | OS Settings | Same |

The Room database file is excluded from cloud backup
(`res/xml/backup_rules.xml` rule). DataStore preferences are excluded by the
same rule.

---

## 3. Permissions

| Permission | Why | Optional? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Enumerate installed apps | No — core feature |
| `PACKAGE_USAGE_STATS` | Sizes & last-used timestamps | Yes — graceful degradation if denied (sizes display as 0) |
| `GET_PACKAGE_SIZE` | Storage sizes via StorageStatsManager | No — install-time normal |
| `REQUEST_DELETE_PACKAGES` | Trigger system uninstall dialog | No — install-time normal |
| `KILL_BACKGROUND_PROCESSES` | Best-effort force-stop | No — install-time normal |
| `POST_NOTIFICATIONS` (Android 13+) | Cache-threshold notification | Yes — runtime grant, only requested on first opt-in |

**Never requested:** `INTERNET`, location, contacts, SMS, calendar, microphone,
camera, `MANAGE_EXTERNAL_STORAGE`.

---

## 4. Third parties

App Manager Tech embeds zero third-party SDKs that phone home:

- No Google Mobile Services (GMS)
- No Firebase
- No Google Analytics, AppsFlyer, Mixpanel, Sentry SaaS, or other telemetry
- No advertising SDK
- No crash reporter that uploads anywhere

All bundled libraries are Apache 2.0 / MIT / BSD / LGPL / GPL / AGPL — see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the complete list.

---

## 5. Logs

In debug builds the app prints log lines to Logcat (visible only with USB
debug + ADB). These include package names, sizes, and operation outcomes. No
PII per RGPD: package names are public information by Android design.

**Release builds emit no log lines at all** — Timber uses `NoOpReleaseTree`
which discards every entry.

---

## 6. Children's privacy

App Manager Tech does not collect any data from any user, including
children under 13. The app has no account system and no online presence.

---

## 7. Your rights (GDPR / French CNIL)

Since we do not collect, store off-device, or process any personal data,
the standard GDPR rights (access, rectification, erasure, portability,
restriction, objection) have no application:

- **Erasure:** uninstall the app, or use OS Settings → Apps → App Manager Tech
  → Storage → Clear data.
- **Access / portability:** the Export feature already lets you save the full
  storage report as JSON or CSV to a location of your choosing via the
  Storage Access Framework.

If you believe this app violates your privacy rights anyway, please open an
issue at https://github.com/gitubpatrice/APP-MANAGER-TECH/issues or contact
contact@files-tech.com.

---

## 8. Changes to this policy

Material changes to this policy will be reflected in the CHANGELOG.md and
announced in the corresponding release notes (`fastlane/metadata/android/.../changelogs/`).

The current version is tracked alongside the source code:
https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.md
