# Changelog

All notable changes to App Manager Tech will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
