# Changelog

All notable changes to App Manager Tech will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
