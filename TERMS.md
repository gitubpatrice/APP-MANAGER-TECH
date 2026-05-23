# Terms of Use — App Manager Tech

**Effective date:** 2026-05-23

By installing or using App Manager Tech, you agree to these Terms. If you
do not agree, please do not install or use the app.

---

## 1. The app

App Manager Tech is a free and open-source Android application that lets
you inspect, batch-act on, clean, and audit applications installed on your
own Android device. It is distributed under the Apache License 2.0
(see [LICENSE](LICENSE)). The full source code is available at
https://github.com/gitubpatrice/APP-MANAGER-TECH.

---

## 2. No warranty

App Manager Tech is provided **"AS IS"**, without any warranty — express
or implied — including merchantability, fitness for a particular purpose,
or non-infringement. See section 7 of the Apache License 2.0 for the full
disclaimer.

The author and contributors are not liable for any direct, indirect,
special, incidental, or consequential damages arising from the use or
inability to use the app — including, without limitation:

- Data loss caused by uninstalling apps (the OS confirmation dialog is
  always shown, but the app does not maintain a global undo);
- Behaviour changes after disabling or force-stopping an installed app;
- Notifications missed because `POST_NOTIFICATIONS` was denied;
- Sizes displayed as 0 because `PACKAGE_USAGE_STATS` was not granted;
- Any other operational consequence of an action you triggered.

---

## 3. Your responsibilities

By using the app you confirm that:

- You own or are authorised to manage the Android device the app runs on;
- You understand that uninstalling an app or clearing its data is
  irreversible — the OS does the actual deletion; we only initiate it via
  the public `Intent.ACTION_DELETE` and `ApplicationDetails` settings
  flows;
- You will not use the app to violate the terms of other apps' licences
  (e.g. extracting an APK for redistribution if the original license
  forbids it).

---

## 4. Permissions and OS behaviour

The app degrades gracefully when a permission is denied:

- `PACKAGE_USAGE_STATS` denied → sizes display as 0; no crash;
- `POST_NOTIFICATIONS` denied (Android 13+) → notifications silently
  dropped, app keeps running;
- `KILL_BACKGROUND_PROCESSES` is a normal permission (no runtime grant)
  but `ActivityManager.killBackgroundProcesses` only stops background
  processes — foreground apps and services keep running, by Android design.

Operations that change OS state (uninstall, disable, clear data) always
go through the system UI. We never bypass system confirmations.

---

## 5. Changes to the app

We may release new versions that change features, remove features, or
modify the user interface. Updates are distributed via F-Droid and via
GitHub releases. You may decline an update by not installing it; the
existing version will continue to work until Android compatibility
requirements force you to update or to uninstall.

---

## 6. Termination

You may stop using the app at any time by uninstalling it. We have no
remote disable mechanism (no Internet permission) and cannot revoke a
copy of the app from your device.

---

## 7. Governing law

These Terms are governed by French law and any dispute that cannot be
resolved amicably falls under the jurisdiction of the competent French
courts at the author's domicile, unless mandatory law of your country
of residence provides otherwise.

---

## 8. Contact

- Source code & issues: https://github.com/gitubpatrice/APP-MANAGER-TECH
- Email: contact@files-tech.com
- Project home: https://files-tech.com/app-manager-tech.php
