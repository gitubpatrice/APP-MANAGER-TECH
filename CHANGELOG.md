# Changelog

All notable changes to App Manager Tech will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
