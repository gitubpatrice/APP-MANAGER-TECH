# Changelog

All notable changes to App Manager Tech will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.2] — 2026-05-24 — Expert mode + Diagnostic PDF export

### Added — Expert Mode (advanced inspector)
- New **Mode expert / Expert mode** screen reachable from any
  AppDetail via an "Mode expert (composants internes)" outlined button
  at the bottom of the detail content. Surfaces, in read-only:
  - **Identity**: label, package, version + code, UID, installer,
    first install + last update timestamps, system/user flag, enabled
    state.
  - **SDK envelope**: minSdk / targetSdk / compileSdk (compileSdk
    available only on Android 12+, surfaced as "Non disponible" below).
  - **Native code**: primaryCpuAbi (read via reflection on the public
    `ApplicationInfo` field — null when the app ships no native code)
    + nativeLibraryDir.
  - **APK paths**: base APK, public APK path, split APKs (each split
    listed with monospace font).
  - **Signature**: signer count + SHA-256 fingerprint. Detects debug-
    signed APKs by parsing the X.509 certificate Subject DN
    (`CN=Android Debug`) — more robust than a hardcoded SHA-256.
  - **Components**: activities / services / receivers / providers
    with the `exported` flag, `enabled` state, and per-component
    permission gate. Unprotected-exported components are tinted red
    (real attack-surface signal). Providers additionally surface
    authority + read/write permission + `grantUriPermissions` flag.
  - **Permissions**: full declared list with grant state +
    dangerous-protection flag, monospace font.
  - **App ops**: curated subset (location, mic, camera, contacts, SMS,
    body sensors, draw-over-other-apps, modify settings, usage stats)
    probed via `AppOpsManager.unsafeCheckOpNoThrow`. A banner explains
    the OS hides most ops from non-root callers, surfaced as "Default"
    rather than misleading "Denied".
- A clear info banner at the top of the screen sets expectations:
  read-only inspector; editing components / denying app-ops / rewriting
  permissions requires ADB, Shizuku or root and is intentionally out of
  scope for the F-Droid release.

### Added — Diagnostic PDF export
- New "Exporter le diagnostic PDF" button on the **Export** screen
  (Outils → Export). Builds a printable diagnostic report suitable for
  family / professional tech support.
- Sections rendered:
  - **Device**: manufacturer, model, Android release + SDK, total +
    free internal storage.
  - **App Manager Tech**: app version, our own signature SHA-256,
    number of apps tracked.
  - **Detected issues**: zombies (disabled / never used), rarely used
    (≥ 60 days), oversized (≥ 200 MB), sideloaded count.
  - **Inventory**: every user-installed app with label, package,
    version, installer, total size.
  - **Apps with dangerous permissions granted**: per app, the sorted
    list of granted dangerous-protection permissions (short names).
  - **Apps installed outside known stores**: sideloaded provenance.
  - **Apps with sensitive access**: device admins + accessibility
    services.
- Native AOSP `android.graphics.pdf.PdfDocument` — zero external
  dependency (no iText / Foxit / Adobe SDK). A4 portrait, paginated
  with footer "Page N", monospace for technical fields. SAF-only
  destination (user picks the folder); no `MANAGE_EXTERNAL_STORAGE`.

### Changed
- `ExportFormat` enum gains a `PDF` value, dispatched separately from
  JSON/CSV by `ExportReportUseCase`. The JSON/CSV format picker no
  longer offers PDF as a persistable default — PDF has its own
  dedicated launcher because the SAF MIME differs.

### Notes
- Room schema unchanged (still v4 — no migration needed).
- Cert SHA-256 stable:
  `76:E8:77:2E:09:95:13:69:40:5F:58:E7:0C:4A:FF:FD:41:C4:68:75:53:C6:CF:A0:3D:08:14:5F:F6:0F:F1:CF`
- 0 GMS dependency, 0 Internet permission, no new runtime permission.
- APK size: ~2.18 MB (+0.29 MB vs v0.2.1, fits Expert Mode + PDF
  builder).
- Strings FR ↔ EN parity 100% (~ 70 new keys total).

---

## [0.2.1] — 2026-05-24 — UX hardening + audit fixes

### Fixed — User-reported bugs (v0.2.0 polish)
- **Trash**: rows for apps the user already uninstalled stayed in the list
  with stale Restore/Uninstall buttons. New `TrashRepository.purgeOrphaned()`
  sweeps via `PackageManager` at ViewModel `init` + every screen
  `ON_RESUME`, batches the deletes via a new `deleteByPackages` DAO query,
  and is guarded by an `AtomicBoolean` so the `init` + `ON_RESUME` double-
  fire at first composition is now a single sweep.
- **SecurityAudit**: refresh button stuck on the spinner forever. The
  initial `UiState(isLoading = true)` (audit L-2 fix for empty-flash
  prevention) was tripping the `if (_state.value.isLoading) return`
  re-entrancy guard before the first refresh could ever run.
  Re-entrancy now uses a dedicated `AtomicBoolean isRefreshing`,
  decoupled from the UI `isLoading` flag.
- **Zombies**: tapping refresh gave no visible feedback (use case
  resolves in < 50 ms so the spinner is invisible). Added an explicit
  `Event.RefreshDone(count)` snackbar. Also the entire row is now
  clickable (was only the trailing chevron `IconButton` — unintuitive).
- **Storage**: refresh button did not update the cache/data sizes
  because `PACKAGE_USAGE_STATS` was not granted (`StorageStatsManager`
  silently returns 0). Added `UsageStatsAccessBanner` with a deep-link
  to OS Settings → Usage access + `ON_RESUME` re-probe + auto-rescan
  when the permission flips from denied → granted.
- **AppDetail**: "Dernière utilisation" was always "Jamais utilisée"
  for the same root cause as Storage. Same fix: banner + re-probe +
  reload-on-grant.
- **AppDetail Permissions section**: added a "Modifier dans Paramètres
  Android" button — one-tap deep-link to the OS App-permissions page
  (uses runtime `queryIntentActivities` to discover the
  `MANAGE_APP_PERMISSIONS` handler on this specific device + falls back
  to App Info if not exposed by the OEM).
- **AppDetail → Move to trash**: a "Voir la corbeille" shortcut now
  appears under the Uninstall button right after the trash staging
  (per user request: "ce serait bien que dessous désinstaller apparaisse
  un bouton voir la corbeille").

### Added — UX
- **ToolsScreen TopAppBar**: gear shortcut to Settings (was only
  reachable via the home overflow menu).
- **SettingsScreen → Outils**: 2 missing NavigationRows for
  "Changements de permissions" + "Quarantaine" (v0.2.0 routes existed
  in AppRoot but were never wired into Settings — silent dead links).
- **Snackbar feedback** on Trackers scan completion
  (`X apps analysées · Y pisteurs détectés`).

### Fixed — Audit findings (full-app peigne fin)
- **HIGH H1**: `CriticalWarningDialog` hold-3s timer was using
  `System.currentTimeMillis()` which can jump backward/forward on NTP
  sync or DST change → user could either skip the 3s safety window or
  never complete it. Switched to `SystemClock.elapsedRealtime()`
  (monotone, immune to wall-clock changes).
- **HIGH C2a + C8a**: RarelyUsed screen had no `UsageStatsAccessBanner`
  and no `ON_RESUME` probe → without `PACKAGE_USAGE_STATS`, every app's
  `lastUsedTime == 0` and the use case classified the entire catalogue
  as "rarely used" (noise). Same fix as Storage / AppDetail.
- **HIGH C2b + C8b**: Zombies screen had the same bug for
  `NEVER_OPENED` classification. Same fix.
- **HIGH C7a/b/c/d + M4**: 5 sites missing `withTimeout` (Trackers
  scan, GetZombieAppsUseCase, Storage analyze + rescan, PermissionDrift
  capture, AppDetail load fan-out) → an OEM-stalled PackageManager
  could freeze the corresponding refresh button forever. All now
  wrapped with sensible caps (60s / 10s / 30s / 90s / 30s / 15s).
- **MEDIUM C1a/b/c/d**: re-entrancy guards via
  `if (_state.value.isXxx) return` were racy across rapid double-taps.
  Replaced with `AtomicBoolean.compareAndSet` + `finally` in
  Trackers / SmartCleaner / PermissionDrift / Quarantine VMs.
  Quarantine VM had NO guard at all (KDoc mentioned one but it was
  never implemented) — double-tap on "Restaurer" was firing 2
  PackageInstaller intents.
- **MEDIUM M1**: `IntentFactory.uninstallIntent` now `require`s
  `isValidPackageName(packageName)` defensively. Every existing call
  site already validates, but the require makes any future regression
  fail-fast instead of producing a malformed URI.
- **MEDIUM M2**: notification IDs in `postQuarantineExpired` were
  computed via `packageName.hashCode()` which can return Int.MIN_VALUE
  (negative). `notify(negativeId, ...)` is silently dropped or aliased
  to ID 0 on some OEMs. Now masked with `and Int.MAX_VALUE`.
- **MEDIUM M5**: `SecurityAuditScreen` error text used
  `colorScheme.error` (drifts to pink/purple under Material You).
  Switched to `BrandDanger` per brand discipline.
- **MEDIUM M-1 delta**: `StorageViewModel.init` and
  `AppDetailViewModel.load()` were calling `hasUsageStatsAccess()`
  (AppOps Binder IPC) on the main thread. Moved into
  `withContext(Dispatchers.IO)` parallel coroutine.
- **MEDIUM M-4 / LOW L-2 delta**: `Box(Modifier.fillMaxSize())` inside
  `Column(Modifier.fillMaxSize())` in Storage + AppDetail bodies could
  squeeze the `UsageStatsAccessBanner` out. Replaced with
  `Modifier.weight(1f)` on the inner Box so Column lays out the banner
  at its natural height first, then the Box absorbs the remainder.
- **MEDIUM C3a**: TrackersViewModel was missing the standard
  `Event.RefreshDone` snackbar — added.
- **MEDIUM C6a**: `BatchActionUseCase` now `filter`s
  `isValidPackageName()` BEFORE the per-app loop so invalid input is
  pre-rejected with a clear reason instead of buried in per-app
  failures.

### CI
- **CodeQL workflow**: was failing with "CodeQL detected code written
  in Java/Kotlin but could not process any of it" because the Gradle
  build cache returned `compileDebugKotlin FROM-CACHE` — when the task
  hits the cache no compiler runs, so the CodeQL tracer has no
  bytecode to extract. Added `--no-build-cache --rerun-tasks` to force
  fresh execution.

---

## [0.2.0] — 2026-05-23 — Permission Drift + Quarantine + Safety Guardrails

### Added — Permission Drift Tracker (new tool)
- Daily snapshot of every user-app's dangerous-protection permissions, stored
  append-only in a new Room `permission_snapshot` table (schema v4).
- Chronological feed showing every permission GAINED / LOST event between
  snapshots, with 30 / 90 / all-days window picker.
- Always-visible "Surveillance active" status card with capture stats
  (N permissions × M apps + last capture date) and prominent
  "Démarrer la surveillance" / "Capturer maintenant" button (replaces
  the icon-only refresh that was easy to miss).
- Tap on a drift row deep-links straight to Android Settings → App →
  Permissions for that app (one-tap path to re-grant). Multi-handler
  runtime discovery via `queryIntentActivities` covers AOSP + Samsung
  One UI + Google permission-controller variants, with App Info as the
  always-available fallback.
- Snapshot capture distinguishes **baselines** (first-ever row for a
  (pkg, perm) pair — not a drift) from **drifts** (actual change). The
  3 snackbar cases cover: drift detected / first-capture baseline / no
  change since last capture (each with action guidance in French).
- Opt-in periodic worker (24h, withTimeout 8 min) drives daily snapshots
  + retention purge (configurable 30 / 90 / 180 / 365 days). Notification
  fires only on real drifts, never on baseline-only ticks.

### Added — App Quarantine (new tool, hybrid HARD / SOFT)
- New `quarantine_entry` Room table holds apps set aside with a review date.
- **HARD_UNINSTALL** mode: backs up the app's base APK to a user-picked
  SAF folder (`OpenDocumentTree` with persistable read+write grant), then
  fires the OS uninstall intent. Restore = `ACTION_VIEW` on the backup APK
  → PackageInstaller reinstall. App data is permanently lost — the in-
  dialog warning makes this explicit.
- **SOFT_REMINDER** mode: just persists the entry + dedicated post-confirm
  explanation dialog (3-step guide "1. Touchez Ouvrir Paramètres, 2. tap
  DÉSACTIVER ou ARCHIVER, 3. l'app est en pause") so the user knows what
  to do when they land on the OS App-info page. No data loss.
- Picker screen with search + already-quarantined exclusion filter.
- Restore dialog uses `DestructiveDialog` (red) for HARD restore (launches
  PackageInstaller, modifies device state), `ConfirmDialog` (blue) for SOFT.
- Backup-missing edge case: dedicated dialog offering "Drop entry" recovery.
- Periodic worker (24h, withTimeout 2 min) fires expiry notifs once per
  entry (unique 32-bit stable ID per package name — collision-free).

### Added — Safety Guardrails (cross-feature)
- New `CriticalAppDetector` classifies installed apps against curated FR
  whitelists per category: AUTHENTICATION (Aegis / andOTP / Microsoft
  Authenticator / Authy / etc.), PASSWORD_MANAGERS (Bitwarden / KeePassDX
  / 1Password / Proton Pass), BANKING_FR (BNP / Boursorama / Crédit
  Agricole / CIC / Société Générale / Revolut / Lydia / etc.), HEALTH
  (Health Connect / Samsung Health / Doctolib / Mon Espace Santé),
  MESSAGING_E2E (Signal / WhatsApp / Telegram / Threema / Element / Molly
  / SimpleX / Briar), TRANSPORT_FR (SNCF Connect / RATP / IDFM / Navigo
  / BlaBlaCar). Plus a USER_PROTECTED placeholder for v0.3.0 customisation.
- Before any **uninstall** or **quarantine HARD** of a classified app,
  a `CriticalWarningDialog` appears: red warning icon, category-specific
  body explaining what will be lost, and a **3-second hold-to-confirm**
  button (with `touchSlop` drag-cancel — release or finger-glide cancels).
  Anti-tap-réflexe pattern aligned with SMS Tech's EmergencyHoldButton.

### Added — Misc
- AppDetail Permissions section gets a prominent "Modifier dans Paramètres
  Android" button — one-tap deep-link via `appPermissionsSettingsChain`.
- AppDetail Actions card gets a "Mettre en quarantaine" row right under
  "Désactiver" (replaces having to go to Quarantine → FAB).
- Settings → 2 new sections: "Suivi vie privée" (drift toggle + retention
  picker + include-system + notify) and "Quarantaine" (SAF folder picker
  + restore reminders toggle).
- 3 new bottom-tools cards: "Changements de permissions" + "Quarantaine".

### Schema migration — Room v3 → v4
- Additive only: 2 new tables (`permission_snapshot` + `quarantine_entry`)
  with 3 indices. `MIGRATION_3_4` ships with a matching `MigrationTest`
  verifying preserve-data + inserts + index presence.

### Audit fixes applied pre-tag (3-axes 0 CRITICAL / 1 HIGH / 6 MEDIUM / 5 LOW)
- H-1: `USER_PROTECTED` enum branch was referenced in dialog + strings but
  never alimented — added to `WHITELISTS` + `CategoryRanking` with empty
  Set placeholder so end-to-end plumbing exists (v0.3.0 will load the
  user-customised set from DataStore).
- M-1: notif IDs were masked to 15 bits (`hashCode() and 0x7FFF` =
  birthday collision at ~180 packages). Now full 32-bit hash + non-
  overlapping base offsets.
- M-4: restore HARD now uses `DestructiveDialog` (red) instead of
  `ConfirmDialog` (blue) — restore launches PackageInstaller, brand
  discipline requires red.
- M-5: `CriticalWarningDialog` hold-3s used `detectTapGestures` which
  didn't detect finger-slide → drag-during-hold still fired confirm.
  Replaced with `pointerInput { awaitPointerEventScope }` + touchSlop
  check, matching SMS Tech EmergencyHoldButton pattern.

### Dependencies
- `androidx.documentfile:documentfile:1.0.1` (SAF tree URI wrapping for
  APK backups — Apache 2.0, F-Droid friendly).

---

## [0.1.3] — 2026-05-23 — UI polish & a11y

### Added
- New `BrandedTitle` composable: every TopAppBar (except About) now shows the
  Files Tech checkered mark + "App Manager Tech" + the screen title.
- "À propos" as a 3rd bottom-navigation tab (right of Outils).
- 3-dots overflow menu in the home TopAppBar consolidating Paramètres / Trier /
  Inclure apps système (replaces the separate Sort + Filter + Settings icons).
- "Tout sélectionner / Tout désélectionner" icon toggle in the SelectionTopBar.
- `withTimeout(20 s)` on Smart Cleaner analyse + `UsageStatsAccessBanner` so
  the screen never freezes if a Flow stalls (was reported "même actualiser
  ne marche pas").
- `withTimeout(10 s)` on Security Audit refresh + re-entrancy guard.

### Changed
- Brand mark is now a `VectorDrawable` (was a 500×500 PNG that decoded to ~1 MB
  of RGBA bitmap just to render at 28 dp).
- `NavigationBarItem` icons now expose an explicit `contentDescription` for
  TalkBack stability when the OS theme overrides `alwaysShowLabel`.
- About URL launcher rejects non-http(s) schemes (defence in depth).
- `AboutScreen.onBack` is now nullable so the screen can be rendered as bottom-
  nav tab content without a redundant back arrow.

### Removed
- Standalone "Select all" toolbar button on the home (was redundant — long-press
  + the SelectionTopBar toggle covers the same need).

### Audit fixes (3-axes, all MEDIUM + LOW)
- P-1 / Q-1: brand mark PNG → VectorDrawable (memory + KDoc).
- Q-2 / Q-3 / Q-4: stale comments referencing "12 tools" / "2-tab nav".
- U-1: NavigationBarItem TalkBack contentDescription.
- U-2: deduplicated Settings / About card colours from the Tools grid.
- S-1: scheme whitelist on `AboutScreen.openUrl`.
- Q-5: removed redundant `colors = TopAppBarDefaults.topAppBarColors()` on TrashScreen.
- Q-6: this entry.

---

## [0.1.2] — 2026-05-23 — Premium RFT-style refresh

### Added
- Tools tab redesigned: 2-column grid + vibrant icons + per-tool subtitle +
  ElevatedCard drop shadows (mirrors Read Files Tech).
- Home overview card ("Vue d'ensemble" — apps count + total size + cache to clean).
- Section headers above the apps list.

### Changed
- All cards in Tools / Overview / Settings / About → `ElevatedCard` (4 dp shadow).
- Splash background → white (was brand blue).
- Launcher icon foreground refreshed.
- Dynamic colour OFF by default (no more rose surfaces under pink wallpapers).
- "Suivre le système" / "Follow system" simplified to "Système" / "System".
- Manual `Refresh` IconButton + Material 3 `PullToRefreshBox` on the home.
- 1-tap `SelectAll` shortcut in the MainTopBar (later removed in v0.1.3).

### Audit fixes (3-axes pre-tag)
- ToolColors.DeepPurple was a duplicate of Purple → Material DeepPurple 700.
- Blue / Green / Cyan failed WCAG AA on light surface → 700 tones.
- `home_overview_title` was a dead string → wired as the OverviewCard label.
- OverviewCard's two `remember(apps)` fused into one fold pass.

---

## [0.1.1] — 2026-05-23 — First-launch UX hotfix

### Fixed
- Home was empty on first launch — `MainApplication.onCreate` now auto-rescans
  when the catalogue is empty or `autoScanOnLaunch` is true.
- All sizes shown as 0 — new `UsageStatsAccessBanner` makes the silent
  PACKAGE_USAGE_STATS denial visible; `ON_RESUME` re-probe + auto-rescan when
  the permission is granted.
- BottomNav Home / Tools shell (mirroring RFT) so every tool has a discoverable
  entry beyond Settings.
- Theme picker was wired but had no effect — `MainActivity` now collects
  `settings.appearance` into `MaterialTheme`.
- Dark palette rewritten with GitHub Primer canonical tokens (deep slate).
- Launcher icon foreground wrapped in 16% inset drawable (the "App Manager Tech"
  text was being cropped by the adaptive-icon mask).
- Splash screen now shows the brand logo (was a transparent drawable that
  rendered as just a coloured window).

### Audit fixes (3-axes pre-tag)
- HIGH: double-Scaffold consumed window-insets twice → last list item was
  hidden behind the NavigationBar on gesture-nav. Fixed via
  `consumeWindowInsets(innerPadding)` + `contentWindowInsets = WindowInsets(0)`
  on child Scaffolds.
- MEDIUM: `collectAsState` → `collectAsStateWithLifecycle`; `ToolCard`
  `Card(onClick=…)` (M3 idiom + TalkBack semantics); atomic `compareAndSet`
  guard on `refresh()`; `withTimeout(5 s)` on `settingsRepository.flow.first()`.

---

## [0.1.0] — 2026-05-23 — First public release

### Added
- Installed-apps inventory (label / version / sizes / install source / last used / category)
- Multi-select batch actions (uninstall, clear cache, force stop)
- Storage analyser (per-category breakdown, top-N largest, top-N cache hogs)
- Background scan WorkManager job (daily / weekly) with cache-threshold notification
- Ignore list (DataStore Set capped at 500 entries)
- Export storage report to JSON / CSV via Storage Access Framework
- Security audit (Device administrators + Accessibility services)
- Rarely-used apps detection (7 / 14 / 30 / 60 / 90 / 180 days)
- Zombie-apps detection (NEVER_OPENED + UNUSED_SINCE)
- Permission filter ("apps that declare PERM")
- APK extract to Documents via SAF
- Action history (local audit log)
- Material You theme (dynamic colour, light / dark / system)
- French and English UI (parity 226/226 strings)
- FLAG_SECURE opt-in to block screenshots and recents previews

### Built with
- Kotlin 2.1.0 + Compose Material 3 + Hilt 2.55 (KSP) + Room 2.6.1 + WorkManager 2.10.0
- 0 GMS / 0 Firebase / 0 INTERNET permission
- R8 release minify — 3 APK splits ~1.86 MB each (10× reduction vs debug)

### Security
- All data stays on device. No network socket.
- Cryptographic primitives: none required by v0.1.0 features.
- Apache License 2.0.
