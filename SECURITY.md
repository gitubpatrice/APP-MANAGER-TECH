# App Manager Tech — Security model

Current release: **v0.5.1**

## v0.5.1 — Quarantine restore, legal texts

- **One permission added: `REQUEST_INSTALL_PACKAGES`.** Restoring a quarantined app hands the
  backed-up APK to the system installer, which since Android 8 aborts a request from an app that
  does not declare this permission (AOSP `InstallStart`): every restore since v0.2.0 did nothing.
  The permission grants nothing on its own: Android requires the user to allow "Install unknown
  apps" for App Manager Tech, and shows its own confirmation for each install. The CI allowlist
  (`tools/check-manifest-permissions.py`) and the privacy policy list it; the release APK carries
  twelve permissions, still no `INTERNET`.
- **The quarantine record survives a refused or cancelled restore.** It was dropped as soon as the
  install intent was produced; it is now dropped only once the package is installed again, read
  live from PackageManager (`ApkBackupManager.isInstalled`), not from the cache.
- **Real quarantine refused for an app it could never restore** (split APKs, system app), before
  anything is backed up or uninstalled: the user lost the app and its data for a backup that could
  not reinstall it.
- **The protected-app hold now covers every uninstall path**: batch uninstall and the security
  audit skipped it.
- **Notifications**: `POST_NOTIFICATIONS` is requested when a notification is turned on; it was
  declared but never requested, so nothing appeared on Android 13+. No new permission.
- **A quarantine backup is restored only if it is the very file that was saved.** Its SHA-256 is
  computed while it is written and stored with the entry (`quarantine_entry.apk_sha256`, Room
  schema 9). A restore copies the backup into the app's private cache, checks the copy's
  SHA-256, and hands the system installer that verified copy through a non-exported FileProvider
  (`cache/restore/` only), never the folder's document: no window between check and install. A
  file hash, not the signer: an older version of the same app, signed with the same key, would
  pass a signer check. Entries saved before 0.5.1 have no fingerprint and are refused. Measured on
  a Galaxy S9: a backup with bytes appended is refused; the original file is restored. The backup is
  read back and hashed when written; the copy is hashed in a folder nothing serves
  (`cache/restore-work/`) and published by rename under a fresh name only on a match, so no
  unverified byte is ever reachable through the provider and a second restore never rewrites a file
  an installer is reading; restores are serialised (mutex).
- **A history turned off is erased** (lifecycle, action journal, permission changes); turning one
  off used to cancel its purge and keep its data forever.
- **Legal texts rewritten from the code** and published in five languages (French prevails), and
  linked from About: data kept and for how long, every permission with what it actually does,
  every exchange the user can trigger (exports, APK backups, web pages, Android screens).

## v0.5.0 — Build verification, German / Italian / Spanish, audit fixes

- **Every build is now verified in CI** (`.github/workflows/ci.yml`), on `main`, `fix/**`, `feat/**`
  and every pull request: build, unit tests, Android lint, detekt (blocking, no baseline), an R8
  release build, and instrumented Room tests on an API 29 emulator. Each guard has a negative control
  that proves it can fail, and fails for the right reason.
- **The "no network" promise is asserted on the release APK itself**, not only on the source
  manifest: `apkanalyzer` reads the permissions of the built APK, which must hold no `INTERNET` and
  nothing outside a reviewed list of 11 (the same check runs on the merged release manifest).
- **Toolchain**: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.10 pinned with a `strictly` constraint (CodeQL
  refuses 2.4.20+), compileSdk 37. No new dependency outside AndroidX / Kotlin / Hilt / Room.
- **Coroutine cancellation is no longer swallowed** by the catch-alls around the quarantine and trash
  writes, nor by the permission-snapshot purge: a cancelled operation is no longer reported as a
  failed one.
- **Backup and device-to-device transfer**: `data_extraction_rules.xml` and `backup_rules.xml` named
  the `sharedpref` domain, where a DataStore file never lives, so they excluded nothing. They now
  exclude the settings DataStore (it holds the backup-folder grant of THIS device) and the Room
  database (it describes THIS device's apps). `allowBackup="false"` already disabled cloud backup; an
  app targeting Android 12+ is still subject to device-to-device transfer.
- **APK backup (real quarantine)**: a copy that fails midway deletes the partial file; a backup folder
  for which Android refuses a persistable grant is refused when picked, instead of failing later.
- **Expert view**: an app-op is read together with the permission behind it. An op at its default
  mode was shown "Allowed" for an app that never declared the permission.
- **No new permission.** The release APK declares the same 11 permissions as v0.4.0.

## v0.4.0 — Action Journal (forensic timeline) + full-app polish

- **AMT Action Journal (new opt-in feature)** — new
  `amt_action_event` table (Room v7 → v8, strictly additive
  `CREATE TABLE` + 3 `CREATE INDEX IF NOT EXISTS`). Records every
  destructive / state-changing action AMT itself dispatches
  (uninstall, force-stop, disable/enable, clear cache/data,
  move-to-trash, restore, quarantine HARD/SOFT). Distinct boundary
  from `app_lifecycle_event` which logs OS broadcasts from ANY
  installer ; this surface answers "what did AMT do on my behalf"
  for forensic post-mortems.
- **Privacy model**: opt-in default OFF, retention 30–365 days
  (clamped on read AND write), Room private DB excluded from cloud
  backup. Never leaves the device, no INTERNET permission, no GMS,
  no proprietary dependency. The `AmtActionLogger` shims every call
  site through a single `AtomicBoolean` gate fed by a
  `distinctUntilChanged` collector — zero DataStore IPC at hot path
  when the journal is OFF.
- **Defence-in-depth on the journal write path** — validation
  `isValidPackageName()` at both the `RecordAmtActionUseCase`
  entrance AND the `AmtActionRepositoryImpl.insert` entrance
  (double-gate). `toDomainOrNull` mapping silently drops rows whose
  enum values the running build does not recognise (downgrade
  tolerance ; the row stays on disk for a future re-upgrade — never
  silently mutated). Append-only by design : the DAO exposes only
  `insert`, `purgeOlderThan`, `count`, `deleteAll` — no UPDATE path
  whatsoever, audit trail stays monotonic.
- **Defence-in-depth on the worker scheduling** —
  `AmtActionJournalPurgeWorker` mirrors `LifecyclePurgeWorker`
  exactly : 2-min `withTimeout` cap, `if (!enabled) Result.success()`
  guard re-read from DataStore on every tick (so a stale
  scheduled instance cannot leak rows after the user opts out).
  `applyAmtActionJournalScheduling(enabled = false)` cancels the
  unique work outright. Cold-start re-sync from
  `MainApplication.syncBackgroundWorkers` recovers from device
  reboot / OEM aggressive killer.
- **Audit fixes shipped together (v0.3.4 deferred + v0.4.0 audit)** :
    - `HashUtils.kt` extracted (`core/ext/`) — single source of
      truth for SHA-256 digest helpers (cert fingerprint format +
      content-hash format + APK streamed file hash). Two prior
      private implementations now delegate ; the APK policy
      (canonical resolve + install-root whitelist + 500 MB cap)
      stays at the call site.
    - `@IoDispatcher` injected across **9 ViewModels +
      2 data-layer classes** that previously used
      `Dispatchers.IO` hardcoded (StorageVM, ExpertVM, AppDetailVM,
      AppListVM, RarelyUsedVM, ZombiesVM, SmartCleanerVM,
      TrashRepositoryImpl, ApkBackupManager). **Zero
      `Dispatchers.IO` hardcoded remain in the codebase** —
      uniform testability + dispatcher discipline.
    - Every `hasUsageStatsAccess()` AppOps IPC call is now off the
      main thread (4 ViewModels migrated from synchronous
      constructor call → `viewModelScope.launch { withContext(io)
      { … } }`). No more jank potential on slow OEM AppOps binder.
    - `FilterChip` parity across every filter-row surface
      (LifecycleHistory + PermissionDrift WindowPickers switched
      from semantically-wrong `AssistChip`).
    - Smart Cleaner + Security Audit uninstall paths now feed the
      Action Journal (audit D2 — coverage was incomplete).
    - `applyLifecyclePurgeScheduling` added to
      `syncBackgroundWorkers` cold-start path (audit D3 — symmetry
      with the new action journal scheduling).
- **No new permission, no new dependency, no INTERNET, no GMS.**
- Room schema v7 → v8 (single ALTER + 3 indices, strict additive).
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

## v0.3.4 — Multi-tags + tag filter + tag stats + APK SHA-256 forensics

- **Multi-tag refactor (DataStore wire format)** — `AppSettings.appTags`
  widens from `Map<String, AppTag>` to `Map<String, Set<AppTag>>`.
  Encoded as a `stringSetPreferencesKey` of `pkg=TAG1|TAG2|…` entries
  (the `|` separator is invalid inside an `AppTag.name`, so the
  encoding is unambiguous). Decoder is **tolerant + defence-in-depth** :
  malformed entries are silently dropped, unknown enum names are
  skipped, the per-entry tag arity is capped at
  `MAX_TAG_ARITY_PER_ENTRY = 8` (a tampered DataStore feeding
  `pkg=WORK|WORK|…|WORK` × 10 000 is bounded), and the `pkg` key is
  validated via `isValidPackageName()` for read-path symmetry with
  the existing `setAppTags(pkg, Set)` write-path. Backward-compatible
  with the v0.3.3 single-tag format (a suffix without `|` deserialises
  as a one-element Set), so an upgrade preserves every existing tag.
- **APK SHA-256 forensics on Lifecycle events** — new `apk_sha256`
  column on `app_lifecycle_event` (Room v7, ALTER TABLE ADD COLUMN
  TEXT NULLable). `RecordLifecycleEventUseCase.computeApkSha256` reads
  the base APK at `ApplicationInfo.sourceDir`, hashes it with
  `MessageDigest.getInstance("SHA-256")` streamed at 64 KB, and stores
  the hex digest on INSTALLED / REPLACED / BASELINE rows. UNINSTALLED
  rows leave the hash NULL (the APK is gone by the time the broadcast
  fires). **Defence-in-depth on every failure path** :
    - `canonicalFile` resolves symlinks BEFORE the read so a hostile
      link is caught by the prefix check rather than being followed.
    - Whitelist of canonical install roots (`/data/app/`,
      `/system/app/`, `/system/priv-app/`, `/product/app/`,
      `/vendor/app/`) — stock AOSP `sourceDir` is trustworthy, but
      root/MOD ROMs can lie; the prefix check fences off the worst
      cases (`/dev/urandom` via symlink).
    - 500 MB hard cap on the APK size we hash — above this we log +
      skip the hash rather than monopolise the IO pool for tens of
      seconds. The lifecycle row still inserts with `apkSha256 = null`
      so the audit trail is never lost.
    - IO / Security exceptions swallowed + Timber-logged (no PII in
      the log message — only the canonical path).
  Tamper detection lives entirely in the UI layer (`computeTamperedIds`,
  pure O(n log n) `remember(events)`) — no extra Room query, no
  background work, no IPC.
- **Tag filter on AppList** — `AppListViewModel` injects the existing
  `SettingsRepository` (no new permission, no new IPC). Filter is a
  pure in-memory post-filter on the existing `getInstalledApps` flow ;
  chip toggles do NOT re-subscribe to the heavy Room query.
- **TagPickerDialog double-toggle hardening** — `Checkbox` now passes
  `onCheckedChange = null` so the parent `Row.toggleable` is the
  single source of click handling. `role = Role.Checkbox` set on the
  Row for TalkBack semantics. Prevents the double-toggle no-op that
  could surface on some Compose versions.
- **No new permission, no new dependency, no INTERNET, no GMS.**
- Room schema v6 → v7 (single ALTER TABLE ADD COLUMN, NULLable).
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

## v0.3.3 — Hibernation parity + Signature clusters + Custom tags

- **Signature clusters** : reuses the existing `getSignatureSha256(pkg)`
  PM call already shipped since v0.1.0 — no new permission, no new IPC
  surface. Renders read-only — tapping an app drills down to AppDetail
  (no destructive action accessible from the new screen).
- **Custom user tags** : new `AppSettings.appTags Map<pkg, AppTag>`
  persisted in DataStore as a `Set<String>` of `pkg=ENUM_NAME` entries.
  Decoding is tolerant — malformed / unknown entries silently dropped.
  Tags are device-local only ; no network, no GMS.
- **No new permission, no new dependency, no INTERNET, no GMS.**
- Room schema unchanged (still v6).
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

## v0.3.2 — Hibernation + Perm delta + Lifecycle PDF

- **OS hibernation read** — `UsageStatsManager.isAppInactive(pkg)`
  (API 23+). Requires PACKAGE_USAGE_STATS (already declared since v0.1.0
  for the storage / usage features). Defensive against SecurityException
  and IllegalArgumentException — both fall back to `false` rather than
  poisoning the scan.
- **Permission delta on REPLACED events** — `DetectPermissionDeltaUseCase`
  compares the granted-dangerous-perms snapshot of a `REPLACED` lifecycle
  event against the closest preceding baseline (BASELINE/INSTALLED/
  earlier REPLACED). Surfaces supply-chain creep signal in the
  LifecycleHistoryScreen row. Pure domain — no Android dep, no new I/O.
- **Lifecycle journal in PDF** — capped to 200 entries (defensive PDF
  size cap). The full history stays in-app via LifecycleHistoryScreen.
  Same SAF destination as the v0.2.2 PDF export — no
  `MANAGE_EXTERNAL_STORAGE`.
- **Room schema v6** — strict-additive `ALTER TABLE ADD COLUMN
  is_hibernated INTEGER NOT NULL DEFAULT 0`. Pre-existing rows read
  `0` (false) via DEFAULT; the next scan repopulates the real OS state.
  `MigrationTest_v5_v6` ships.
- No new permission, no new dependency, no INTERNET, no GMS.
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

## v0.3.1 — Safety Guardrails Phase B + Lifecycle polish

- **Safety Guardrails extended** — the hold-3s `CriticalWarningDialog`
  now also gates `disable` (AppDetail), `uninstallNow` + `emptyTrash`
  (Trash), and `uninstall` (SmartCleaner). Same threat model as v0.2.0
  Phase A (uninstall + quarantine HARD) — protection against accidental
  destructive action on a 2FA / banking / password-manager app.
- **User-customisable whitelist** — `safety.userProtectedPackages` set
  persisted in DataStore + reactive `AtomicReference` cache in
  `CriticalAppDetector` so `classify(packageName)` stays synchronous on
  the ViewModel hot path. Capped at 200 entries (defensive — same
  intent as the ignore-list cap).
- **`ProtectedAppsScreen`** + companion picker — read-only sections per
  built-in CriticalCategory (full transparency: the user sees exactly
  which apps AMT ships hardcoded) + editable user-added list with
  explicit-tap removal (no swipe, dialog confirmation).
- **No new permission, no new dependency.** The picker reuses
  `appInfoRepository.observeApps(includeSystemApps=false)` — the same
  authorised reads as the rest of the app.
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

## v0.3.0 — App Lifecycle History

- **PackageMonitor BroadcastReceiver** — runtime-registered (NOT manifest),
  gated by `lifecycle.enabled`. Idempotent register/unregister via
  `AtomicBoolean`. The receiver is registered with `RECEIVER_NOT_EXPORTED`
  (broadcasts are system-only on Android 8+, the flag is required on API 33+).
  Defence-in-depth: `isValidPackageName()` validation of the broadcast's
  `data.schemeSpecificPart` before any DB write.
- **Append-only Room v5 log** — `app_lifecycle_event` table. Insert is the
  only mutation, except `setReasonForId(id, reason)` which carries an
  explicit narrow `WHERE id = :id` to keep the audit trail monotonic.
- **No data egress** — no new INTERNET permission, no GMS, no FCM. The
  Lifecycle History DB lives in the app's private files dir and is excluded
  from cloud backup via the existing `backup_rules.xml`.
- **Retention** — clamped `[30, 365]` days in the picker; the
  `LifecyclePurgeWorker` cancels itself when the master toggle is OFF so
  no background work survives the feature being disabled.
- **Self-exclusion** — the receiver ignores events for App Manager Tech
  itself (self-updates would otherwise pollute the timeline).
- Cert SHA-256 stable (`76:E8:77...60FF1CF`) since v0.1.0.

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
| `GET_PACKAGE_SIZE` | install-time (normal) | App sizes |
| `REQUEST_DELETE_PACKAGES` | install-time (normal) | Launch the OS uninstall flow; the OS asks for user confirmation per package |
| `REQUEST_INSTALL_PACKAGES` | install-time, plus "Install unknown apps" granted by the user (since v0.5.1) | Restore a quarantined app: hand the backed-up APK to the OS installer, which asks for confirmation |
| `KILL_BACKGROUND_PROCESSES` | install-time (normal) | Best-effort force-stop of background processes |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | Cache threshold, permission changes, end of a quarantine |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | install-time (normal), added by `androidx.work` | Run the background jobs; reschedule them after a reboot |
| `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` | install-time (normal), added by `androidx.work` | Unused: no expedited job, no network constraint |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature, added by `androidx.core` | Keeps the runtime package receiver closed to other apps |
| `INTERNET` | **not declared** | Strictly local app — Android enforces no socket can be opened |
| `MANAGE_EXTERNAL_STORAGE` | **not declared** | Out of scope; everything goes through SAF (Storage Access Framework) |

---

## Audit history

| Version | Date | Scope | Findings |
|---|---|---|---|
| v0.5.1 | 2026-10-09 | Every public statement (privacy policy, terms, Transparency screen, README, store listings) and every dialog, warning and setting of the app checked against the code and the release APK; Claude API review of the restore fix, GPT review of the legal translations, a security review of `REQUEST_INSTALL_PACKAGES` | Quarantine restore never worked since v0.2.0 (installer refused, measured on a Galaxy S9) and dropped its entry before the install; real quarantine of split-APK and system apps could never be restored; notifications never requested on Android 13+; the protected-app hold missing on two uninstall paths; three settings with no effect; permission history losing changes before their retention; misleading texts — **all fixed before tag**. Security review: no path to the installer other than the user's own restore; restore intent pinned to the system installer. The integrity check of a backed-up APK, deferred since v0.5.0, is done (SHA-256 recorded at backup, Room schema 9). |
| v0.5.0 | 2026-10-08 | Pre-release 3-axes audit of v0.4.0..v0.5.0 (CI, toolchain, detekt, German / Italian / Spanish), plus Claude API and GPT reviews of each change and of each translation | 0 CRITICAL, 0 HIGH, 3 MEDIUM (expert app-op state read without the permission behind it, cache notification re-alerting at every scan, release hygiene) + 7 LOW — **the 3 MEDIUM and 5 LOW fixed before tag**. Deferred: integrity check of a backed-up APK before restore (needs a schema column), English deduction labels of the privacy score (internal, never displayed, pinned by tests), CI actions pinned by tag rather than SHA. |
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
