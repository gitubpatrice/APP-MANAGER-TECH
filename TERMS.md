# Terms of use — App Manager Tech

_Last updated: 8 October 2026_ · 🇫🇷 [Français](TERMS.fr.md) · 🇩🇪 [Deutsch](TERMS.de.md) · 🇮🇹 [Italiano](TERMS.it.md) · 🇪🇸 [Español](TERMS.es.md)

> This is a translation. **In case of discrepancy, the [French version](TERMS.fr.md) prevails**:
> the publisher is based in France and these terms are governed by French law.

## Publisher

App Manager Tech (`com.filestech.appmanager`) is part of the **Files Tech** suite, published by
**Patrice Haltaya**. Contact: **contact@files-tech.com**.

## Licence

App Manager Tech is free software released under the **Apache License 2.0**. You may use, modify
and redistribute it under the terms of that licence. The full text is in the `LICENSE` file of the
source repository (https://github.com/gitubpatrice/APP-MANAGER-TECH). Only the English text of the
licence is authoritative.

## Use

The app is made available **free of charge** and provided **as is, without warranty of any kind**
(Apache License 2.0, section 7). It helps you examine the apps installed on your phone and act on
them; the decisions you make on that basis — uninstalling, disabling, quarantining — remain yours.

By using it, you confirm that you own the phone or are authorised to manage it. Copying an app's
installation file (APK) gives you no right over that app: do not redistribute it if its licence
forbids it.

## Limitations

- **Actions go through Android.** App Manager Tech does not uninstall, disable or clear the cache of
  any app itself: it opens Android's uninstall window, or the app's page in Android's settings, and
  Android asks for confirmation and acts. It never bypasses those confirmations.
- **"Force stop" only stops background processes.** Android does not let an app stop another one
  that is visible or running a foreground service.
- **Tracker detection is not a security audit.** It compares the components each app declares with
  a list frozen at the date of the release: it can miss a tracker absent from that list, renamed or
  embedded differently, and report one that is present but never used.
- **The privacy score is an estimate.** It is computed from the declared permissions, some special
  accesses and the install source. It measures how much access an app has, not what it intends: a
  low score does not mean malware, a high score guarantees nothing.
- **The "critical app" warning is not exhaustive.** Before an action on an app it recognises as
  critical, or that you protected, the app requires a three-second press. It does not recognise
  them all: disabling or uninstalling a system app can make the phone unstable.
- Without "Usage data access", sizes show 0 and apps show as "Never used". Android can delay or
  block notifications; the app cannot guarantee their delivery.

## Data loss

- **Uninstalling an app, or clearing its data, is irreversible.** Android does it, after its own
  confirmation; App Manager Tech has no way to undo it.
- **The trash uninstalls nothing** until you empty it: emptying it starts the uninstall of each app
  it holds.
- **A quarantine with backup keeps the APK only.** The app's data — accounts, preferences, files —
  is lost when it is uninstalled, and restoring it reinstalls a blank app. Restoring goes through
  Android's installer, which asks for your consent and, the first time, for permission to install
  apps from App Manager Tech.
- **App Manager Tech does not yet check that a backed-up APK has not been modified** in its folder.
  Only restore files you backed up yourself, in a folder no other app modifies.
- Uninstalling App Manager Tech deletes its histories, its trash and its quarantines. Backed-up
  APKs and exports stay in the folders where you put them.

## Data

Everything the app keeps stays **on your phone** — see the [privacy policy](PRIVACY.md).
App Manager Tech sends nothing over the Internet and has no technical permission to do so (no
Android `INTERNET` permission). For the same reason, nobody can disable it or remove it from your
phone remotely.

## Updates

Updates are published on the official GitHub repository, signed with the same key as every
previous version. The app never updates itself and never checks whether an update exists: the
"Check for updates" button opens the releases page in your browser, and installing a new version
is up to you.

## Liability

To the extent permitted by law, the publisher cannot be held liable for any direct or indirect
damage resulting from the use of the app (Apache License 2.0, section 8). In particular, **any data
loss following an uninstall, a data clearing, a disabling, a quarantine or a restore that you
triggered is the user's sole responsibility**. Nothing in these terms limits a liability that the
law forbids limiting.

## Governing law

These terms are governed by **French law**, without prejudice to the mandatory consumer-protection
rules of your country of residence. Where the law allows, the French courts have jurisdiction over
any dispute.

## Changes

These terms may change with the app; the date at the top of the document shows the latest
revision, and the history is public in this repository. The version of 23 May 2026 announced
distribution through F-Droid: that was false, App Manager Tech is not published there.

## Contact

**contact@files-tech.com** · [repository issues](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
· [files-tech.com](https://files-tech.com/app-manager-tech.php)
