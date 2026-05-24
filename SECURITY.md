# App Manager Tech — Security model

Current release: **v0.2.2**

## v0.2.2 — Expert mode + Diagnostic PDF

- **Mode Expert**: read-only inspection of internal components
  (activities, services, receivers, providers + `exported` flag, declared
  permissions + grant state + dangerous-protection flag, signature SHA-256
  + debug-keystore detection via X.509 Subject DN, UID, SDK envelope, ABI,
  APK paths / splits, curated AppOps probed via
  `AppOpsManager.unsafeCheckOpNoThrow`). Strictly read-only — no PackageManager
  mutation, no app-op rewrite, no component enable/disable (those require
  ADB, Shizuku or root and are intentionally out of scope for an F-Droid
  release).
- **PDF diagnostic export**: built via `android.graphics.pdf.PdfDocument`
  (native AOSP, API 19+), 0 external dependency. Output stream is the
  user-picked SAF Uri — no `MANAGE_EXTERNAL_STORAGE`. Report contents
  never include APK Manager's own runtime state, only what we read from
  the OS surface (`PackageManager`, `StorageStatsManager`,
  `UsageStatsManager`, `DevicePolicyManager`, `AccessibilityManager`).
- No new permission declared in the manifest. No new dependency added.
- Same threat-model envelope as v0.2.1.

## Signing certificate

| Field | Value |
|---|---|
| Algorithm | RSA 4096 / SHA384withRSA |
| Alias | `app_manager_tech` |
| Validity | 100 years from 2026-05-23 |
| **SHA-256** | `76:E8:77:2E:09:95:13:69:40:5F:58:E7:0C:4A:FF:FD:41:C4:68:75:53:C6:CF:A0:3D:08:14:5F:F6:0F:F1:CF` |
| SHA-1 | `3B:93:44:DC:7E:72:47:92:FD:31:CA:24:EB:38:8C:C0:34:63:BA:0E` |

This fingerprint must remain stable across releases — verify before publishing
each new GitHub release that the SHA-256 matches the previous one.
F-Droid's reproducible-build pipeline pins to this fingerprint via the
`AllowedAPKSigningKeys` field of the metadata yml.

---

## Threat model

App Manager Tech reads installed package metadata, storage statistics and
usage timestamps from the Android OS. It does **not** transmit any data
off-device — no INTERNET permission is declared, the OS itself enforces this.

Primary threats and mitigations:

| Threat | Mitigation |
|---|---|
| Malicious caller feeding forged package names | `isValidPackageName()` guard in every use case that accepts a packageName from outside |
| Privilege escalation via PackageManager | Only public PackageManager / StorageStatsManager / UsageStatsManager APIs; no shell, no root, no reflection on hidden APIs |
| Data leakage via screenshots / recents | `FLAG_SECURE` opt-in setting (default OFF — user controls) |
| Backup exfiltration | DataStore preferences + Room DB excluded from cloud backup via `backup_rules.xml` + `dataExtractionRules` |
| Tracker SDK supply-chain | Zero Google Mobile Services, zero analytics, zero crash reporter — every dependency is Apache 2.0 / MIT / BSD open source |
| Cert pinning bypass | N/A — no network requests are made (no INTERNET permission) |
| Tampering with the soft-delete Trash | `MoveAppToTrashUseCase` wraps Room writes in try/catch → typed `AppError.DatabaseError` ; upsert is idempotent (REPLACE) so double-tap is safe |
| **v0.2.0** — Permission Drift snapshot tampering | Read-only access to `PackageInfo.requestedPermissionsFlags` via PackageManager; no write path to other apps' permission state; Room writes append-only on internal DB |
| **v0.2.0** — Quarantine APK backup exfiltration | User explicitly picks the backup folder via SAF `OpenDocumentTree` (persistable grant) — backup lives outside the app's private dir but in user-owned storage; filename whitelisted to `[a-zA-Z0-9._-]` (no path traversal); MIME pinned to `application/vnd.android.package-archive` |
| **v0.2.0** — Accidental destructive action on critical app | `CriticalAppDetector` whitelist-classifies authenticators / banking / password managers / health / E2E messaging / transport apps; `CriticalWarningDialog` requires a 3-second hold-to-confirm with drag-cancel (touchSlop), aligned with SMS Tech EmergencyHoldButton pattern |
| **v0.2.0** — Quarantine HARD data wipe surprise | Pre-confirm dialog explicitly states "App data will be permanently lost on uninstall" with the confirm button repainted in BrandDanger; SOFT mode (reminder only, no destruction) is the default selection |

---

## Cryptographic primitives

None. App Manager Tech performs no cryptographic operations: no encrypted
storage (the Room DB is private app-data, protected by Android sandboxing),
no PIN-lock, no message signing, no network = no TLS.

If a future release adds a PIN-locked view, it will use Android Keystore +
Tink AEAD (AES-256-GCM). No home-grown crypto.

---

## Permissions inventory

| Permission | Type | Reason |
|---|---|---|
| `QUERY_ALL_PACKAGES` | install-time (normal) | Enumerate every installed app via PackageManager |
| `PACKAGE_USAGE_STATS` | granted by user in OS Settings | Last-used timestamps via UsageStatsManager + cache / data sizes via StorageStatsManager |
| `REQUEST_DELETE_PACKAGES` | install-time (normal) | Launch the OS uninstall flow; the OS asks for user confirmation per package |
| `KILL_BACKGROUND_PROCESSES` | install-time (normal) | Best-effort force-stop of background processes |
| `RECEIVE_BOOT_COMPLETED` | install-time (normal) | Reschedule the background-scan WorkManager job after device reboot |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | Display the single cache-threshold notification |
| `INTERNET` | **not declared** | Strictly local app — Android enforces no socket can be opened |
| `MANAGE_EXTERNAL_STORAGE` | **not declared** | Out of scope; everything goes through SAF (Storage Access Framework) |

---

## Audit history

| Version | Date | Scope | Findings |
|---|---|---|---|
| v0.2.1 | 2026-05-24 | FULL-APP "peigne fin" audit (3-axes + cohérence transversale) post-v0.2.0 + 6 user-reported UX bugs (Trash ghost rows, SecurityAudit refresh stuck, Zombies row tap, Storage refresh, AppDetail "last used", Settings shortcut Outils) | 0 CRITICAL, 8 HIGH (H1 SystemClock vs wall-clock in hold-3s timer, H3 SettingsScreen missing PermissionDrift+Quarantine callbacks, C2a/b + C8a/b RarelyUsed+Zombies missing UsageStats banner+ON_RESUME, C7a/b/c/d 4 withTimeout sites missing) + 12 MEDIUM (C1a/b/c/d AtomicBoolean replacing racy guards, M1 IntentFactory require validPkg, M2 notif ID negative hashCode mask, M4 AppDetail load fan-out timeout, M5 SecurityAuditScreen BrandDanger, M-1 delta hasUsageStatsAccess off-main, M-4/L-2 Box weight, C3a Trackers ScanDone snackbar, C6a BatchAction filter) + 12 LOW — **all blocking findings fixed before tag**. CI fix: CodeQL `--no-build-cache --rerun-tasks` so tracer observes compilation. |
| v0.2.0 | 2026-05-23 | v0.2.0 delta — Permission Drift Tracker + App Quarantine (HARD APK backup + SOFT reminder) + Safety Guardrails (`CriticalAppDetector` + `CriticalWarningDialog` hold-3s) + Room v3→v4 migration + new IntentFactory.appPermissionsSettingsChain + SAF backup folder | 0 CRITICAL, 1 HIGH (USER_PROTECTED enum phantom — fixed by adding empty Set + ranking entry), 4 MEDIUM (notif hash collision 0x7FFF → full 32-bit, restore HARD ConfirmDialog → DestructiveDialog, detectTapGestures no drag-cancel → pointerInput awaitPointerEventScope with touchSlop, label resolution cap deferred v0.2.1) + 5 LOW — **all blocking findings fixed before tag** |
| v0.1.0 | 2026-05-23 | Phase X final — Trash feature (Room v3 migration + 3-way uninstall dialog) + batch confirmation dialogs + 3 list-by-criterion screens (Rarely-used / Zombies / Permission-filter) + brand-discipline red | 0 CRITICAL, 1 HIGH (MigrationTest_2_3 missing — fixed), 6 MEDIUM (try/catch dao.upsert, fillMaxWidth(0f) invisible label, Spacer.padding anti-pattern, 3 IconButton contentDescription, dead strings settings_trash_default_*, UninstallChoiceDialog M3-deviation doc) + 4 LOW — **all fixed before tag** |
| v0.1.0 | 2026-05-23 | Phases I→IX — scaffold + Room v2 + Storage analyser + WorkManager + Smart Cleaner + Trackers scan + Transparency screen + i18n FR+EN + signed release infra | All audit findings resolved per phase (see git history) |

---

## Out of scope

- System-partition modification
- Root / ADB operations
- Network access (no internet permission, not planned)
- Access to other apps' private data directories

---

## Reporting

Security disclosure: contact@files-tech.com

Please include:
- Android version and device model
- App version (shown in Settings > About)
- Reproducible steps
- Perceived impact

We target a 72-hour initial response for credible vulnerability reports.
