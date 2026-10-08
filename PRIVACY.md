# Privacy Policy — App Manager Tech

_Last updated: 8 October 2026_ · 🇫🇷 [Français](PRIVACY.fr.md) · 🇩🇪 [Deutsch](PRIVACY.de.md) · 🇮🇹 [Italiano](PRIVACY.it.md) · 🇪🇸 [Español](PRIVACY.es.md)

> This is a translation. **In case of discrepancy, the [French version](PRIVACY.fr.md) prevails**:
> the publisher is based in France and the French text is the one he writes and answers for.

App Manager Tech (`com.filestech.appmanager`) is an **entirely local** app manager: it examines the
apps installed on your phone from the phone itself, and sends nothing anywhere.

## In short

- **No data collected, no data transmitted.** The app does not declare the `INTERNET` permission:
  it is technically unable to send anything over a network. An automated check verifies this on the
  published APK itself, at every continuous-integration run.
- **No account, no sign-up, no identifier.**
- **No advertising, no tracker, no analytics**, no crash report sent.
- **No cloud backup**: `allowBackup=false`, and both the database and the settings are excluded
  from Android's automatic backups and from device-to-device transfers.

## What data, and where

The app reads what Android already knows about the installed apps, and keeps a copy in its
**private storage**, which no other app can access. This database is not encrypted: it describes
your apps, not your content.

| Data | Where it comes from | How long |
|---|---|---|
| List of apps: name, version, sizes, install, update and last-used dates, store of origin, state (enabled, hibernated) | Android (`PackageManager`, `StorageStatsManager`, `UsageStatsManager`) | Replaced at every scan |
| Trackers detected, privacy score | Computed on the phone (see below) | Recomputed every time they are shown |
| History of the permissions granted to each app — **off by default** | Periodic capture, if you turn it on | 90 days by default, adjustable from 7 to 365 |
| History of installs, updates and uninstalls, with the SHA-256 fingerprint of each APK and the uninstall reason you choose to give — **off by default** | Android's announcements, if you turn it on | 180 days by default, adjustable from 30 to 365 |
| Journal of the actions started from App Manager Tech — **off by default** | Your actions, if you turn it on | 180 days by default, adjustable from 30 to 365 |
| Trash and quarantines | Your actions | Until you empty or restore them |
| Settings: theme, thresholds, ignored or protected apps, tags, and the access grant to the backup folder you picked | You | Until uninstall |

**Trackers** are spotted by comparing the names of the components each app declares to Android
with a list built into App Manager Tech, taken from Exodus Privacy's public database. That list is
updated with the app; it is never downloaded.

Uninstalling App Manager Tech, or clearing its data in Android's settings, deletes all of the
above. Files that **you** had written elsewhere (see "Sharing") stay where you put them.

The developer has **no access** to this data and receives **no copy** of it.

## Permissions requested, and why

This list is **exhaustive**: these are the twelve permissions the published APK carries, as read in
its manifest. It therefore includes the ones no line of our code asks for, but that a library
brought with it. An automated check rejects any build whose APK departs from this list
(`tools/check-manifest-permissions.py`, run at every continuous-integration run).

### Declared by the app

| Permission | Use | Network? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | See every installed app: this is what the app is for. | No |
| `PACKAGE_USAGE_STATS` | Special access "Usage data access", which you grant yourself in Android's settings. It provides each app's sizes and last-used date. If refused, sizes show 0 and apps show as "Never used"; nothing else stops working. | No |
| `GET_PACKAGE_SIZE` | Read the size of apps. | No |
| `REQUEST_DELETE_PACKAGES` | Open Android's uninstall window. Android asks for confirmation and uninstalls. | No |
| `REQUEST_INSTALL_PACKAGES` | Restore a quarantined app: hand the backed-up APK to Android's installer. Android additionally requires you to allow App Manager Tech to install apps yourself, and asks for confirmation at every install. It serves nothing else. | No |
| `KILL_BACKGROUND_PROCESSES` | Stop an app's background processes, at your request. | No |
| `POST_NOTIFICATIONS` | Three optional notifications: cache threshold reached, permissions changed, end of a quarantine. Requested at run time, can be refused. | No |

### Brought by the libraries used

| Permission | Origin | What it **actually** does here | Network? |
|---|---|---|---|
| `WAKE_LOCK` | `androidx.work` | Held briefly while a background task runs: automatic scan, permission capture, history purge, quarantine reminder — the ones you turned on. | No |
| `RECEIVE_BOOT_COMPLETED` | `androidx.work` | Reschedule those tasks after a reboot. | No |
| `FOREGROUND_SERVICE` | `androidx.work` | **Nothing.** `androidx.work` declares it for "expedited" tasks; the app schedules none. | No |
| `ACCESS_NETWORK_STATE` | `androidx.work` | **Nothing.** `androidx.work` declares it for tasks that wait for a network; every task of the app is scheduled with no network condition. It would only tell whether a network is present: without `INTERNET`, none can be used. | No |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | **Self-granted** permission, at "signature" level: only an app signed with our key can obtain it. It closes to other apps the receiver the app registers to follow installs. | No |

The app **never** asks for access to the Internet, location, contacts, SMS, calendar, microphone,
camera, or your files (`MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`).

## Sharing with third parties

**None.** No data is shared, sold or transmitted to anyone — the app has no technical means to do
so.

The only possible exchanges are the ones **you** trigger, and they stay on your phone:

- **Export (JSON, CSV, PDF)**: a report on your apps, written to the location you pick in Android's
  file picker. It reveals which apps are installed on your phone; what becomes of the file then is
  up to you alone, including if you save it to a folder synced to a cloud.
- **Quarantine with backup**: the app copies an app's installation file (APK) to the folder you
  picked, before uninstalling it. An APK contains the app, **never its data**. That folder is not
  deleted when you uninstall App Manager Tech.
- **Web pages** ("Check for updates", source code, report an issue, licence, this policy): the app
  asks your phone's browser to open a fixed address. The browser connects, under its own privacy
  policy; App Manager Tech gives it nothing but that address.
- **Android screens** (uninstall, an app's info page, settings): the app gives them the name of the
  package concerned, and nothing else.

## Logs

Published versions write **no log line**: every message is discarded before being written. Only
development builds write any, visible through ADB alone.

## Your rights (GDPR)

As the app processes no personal data outside your device, there is no remote processing to
access, rectify or erase. You keep full control: turning a history off, emptying the trash, or
uninstalling the app deletes the corresponding data from the device. The export gives you a
readable copy of what the app knows about your apps.

## Children

The app collects no data and is suitable for all audiences.

## Changes

This policy may change with the app; the date at the top of the document shows the latest revision,
and the history is public in this repository. The version of 23 May 2026 left out the histories and
their retention periods, the files written outside the app, and half of the APK's permissions. The version of 8 October 2026 adds `REQUEST_INSTALL_PACKAGES`,
without which Android refused every restore of a quarantine.

## Publisher and contact

App Manager Tech is published by **Patrice Haltaya** (France), data controller within the meaning
of the GDPR — even though, as explained above, no data ever reaches him. Contact:
**contact@files-tech.com**.

Question or report: open an [issue](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues) on the
repository, or go through [files-tech.com](https://files-tech.com). For security, see
[SECURITY.md](SECURITY.md).
