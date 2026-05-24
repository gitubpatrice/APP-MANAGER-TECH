# App Manager Tech — Session Log

Trace de tout le travail effectué sur App Manager Tech le 2026-05-23.
Synthèse narrative des 4 releases consécutives (v0.1.0 → v0.1.3), des
décisions design, des audits et des leçons à conserver pour la suite.

---

## Chronologie des releases

### v0.1.0 — 2026-05-23 — First public release

**Commit** `422fb58` · **Tag** `v0.1.0` · **versionCode** 1 ·
**GH release** : https://github.com/gitubpatrice/APP-MANAGER-TECH/releases/tag/v0.1.0 ·
**F-Droid MR** : https://gitlab.com/fdroid/fdroiddata/-/merge_requests/38925

Scaffold initial + 8 phases livrées en une session :

- **Phase I → IX** : Kotlin natif 2.1.0 + Jetpack Compose Material 3 + Hilt 2.55
  (KSP) + Room 2.6.1 + DataStore Preferences + WorkManager 2.10.0 + Coil 2.7.0
  + Timber 5.0.1. AGP 8.7.3, JDK 17, Gradle 8.11.1, R8 minify + 3 ABI splits
  (~1.94 MB par APK). **Aucune permission INTERNET**, 0 GMS, 0 Firebase,
  0 analytics.
- **Phase X** : Corbeille (Room v3 migration additive + soft-delete staging
  + 3-way uninstall dialog Annuler / Mettre à la corbeille / Désinstaller
  maintenant) + 3 list-by-criterion screens (Apps rarement utilisées /
  Zombies / Filtre par permission) + dialogues de confirmation pour les
  actions batch.

**Keystore** créé (`app/release.keystore`, RSA 4096, validité 100 ans,
alias `app_manager_tech`). Cert SHA-256 stable depuis :

```
76:E8:77:2E:09:95:13:69:40:5F:58:E7:0C:4A:FF:FD:41:C4:68:75:53:C6:CF:A0:3D:08:14:5F:F6:0F:F1:CF
```

Repo GitHub `gitubpatrice/APP-MANAGER-TECH` créé (public, Apache 2.0,
10 topics : android, kotlin, jetpack-compose, app-manager, cleaner, privacy,
fdroid, no-tracker, local-only, sd-maid).

Audit Phase X final 3-axes pré-tag : 0 CRITICAL, 1 HIGH (MigrationTest
v2→v3 manquant), 6 MEDIUM, 4 LOW — **11/11 fixes appliqués avant le
commit**. `lintVitalRelease` + `testReleaseUnitTest` verts.

---

### v0.1.1 — 2026-05-23 — First-launch UX hotfix + BottomNav + dark theme wired

**Commit** `d129e27` · **Tag** `v0.1.1` · **versionCode** 2 ·
**GH release** : https://github.com/gitubpatrice/APP-MANAGER-TECH/releases/tag/v0.1.1

Test sur Galaxy S24 a remonté **5 défauts UX critiques** + 3 demandes.

| # | Défaut | Cause root | Fix |
|---|---|---|---|
| 1 | Home vide au 1er lancement | `autoScanOnLaunch=false` default + aucun code n'utilisait ce flag | `MainApplication.onCreate` fire-and-forget rescan si cache vide |
| 2 | Toutes les apps à 0 octets | `PACKAGE_USAGE_STATS` déni silencieux dans `queryStats() catch SecurityException → 0` | `UsageStatsAccessBanner` + `LifecycleEventEffect(ON_RESUME)` re-probe + auto-rescan |
| 3 | Pas de bouton Refresh ni gesture | TopAppBar manquait l'action ; pas de pull-to-refresh | `RescanAppsUseCase` + IconButton Refresh + `PullToRefreshBox` Material 3 |
| 4 | UX plat tout dans Settings | Pas de navigation primaire | **BottomNav Home/Tools** : `HomeShell` + `ToolsScreen` grille 3-col cards |
| 5 | Thème dark mort | `MainActivity` n'appelait jamais `AppManagerTechTheme(darkTheme=...)` | `collectAsStateWithLifecycle` sur `settings.appearance`, map `ThemeMode/dynamicColor` → params Theme + dark palette GitHub Primer canonical |

Plus :

- Icône launcher : `ic_launcher_foreground_inset.xml` wrap avec
  `android:inset="16%"` (safe-zone adaptive-icon — texte "App Manager
  Tech" n'est plus coupé)
- Theme picker label "Suivre le système" → "Système" FR+EN
- Splash logo visible (avant `splash_transparent` drawable = invisible)

Audit 3-axes pré-tag : 0 CRITICAL, 1 HIGH (double-Scaffold inset
double-comptage masquait derniers items LazyColumn derrière NavigationBar),
4 MEDIUM, 4 LOW — **11/11 fixes appliqués**.

---

### v0.1.2 — 2026-05-23 — Premium RFT-style refresh

**Commit** `fd3eea5` · **Tag** `v0.1.2` · **versionCode** 3 ·
**GH release** : https://github.com/gitubpatrice/APP-MANAGER-TECH/releases/tag/v0.1.2

Retour user : "design pas joli, fais comme RFT". Décision : refonte UI
alignée sur Read Files Tech (Flutter) qui est le canonique design Files
Tech.

**UI / branding** :

- **ToolsScreen refactor RFT-style** : 3-col → 2-col grid + per-tool
  subtitle + `Card(onClick=...)` Material 3 + `ElevatedCard(4dp)` drop
  shadows + `ToolColors` palette vibrante (11 hues distincts).
- **AppListScreen** ajoute `OverviewCard` ("Vue d'ensemble" — apps count
  + total + cache à nettoyer) au-dessus de la liste + `SectionHeader`
  "Apps installées".
- **Settings + About** : `SettingsCard` / `AboutCard` → `ElevatedCard`
  (cohérence look-and-feel).
- **Splash background** WHITE (était brand blue — "loud").
- **Icône launcher** refresh depuis le PSD source.

**Comportement** :

- **`dynamicColor = false` par défaut** — la marque reste cohérente sous
  Material You (avant : surfaces rose sous wallpaper rose).
- **Splash min duration 700ms** (`setKeepOnScreenCondition`) pour qu'il
  soit perceptible.
- **UsageStatsAccessBanner** layout vertical (avant : Row écrasée
  affichait le body texte caractère par caractère sur français long).
- **MainTopBar** : nouveau `SelectAll` IconButton (1-tap multi-select).
- **SelectionTopBar** : icônes `SelectAll` / `Deselect` (plus parlant que
  CheckBox / CheckBoxOutlineBlank).
- **`usageAccessSettingsIntent`** ciblé via `Uri.fromParts("package",
  packageName, null)` — skip scroll dans liste de 200 apps sur OEMs qui
  honorent (Pixel, certains Samsung).

Audit 3-axes pré-tag : 3 MEDIUM + 1 LOW :

- ToolColors.DeepPurple doublon de Purple → Material DeepPurple 700
- ToolColors.Blue/Green/Cyan échouaient WCAG AA light → 700 tones
- `home_overview_title` string morte → câblée dans OverviewCard
- OverviewCard `remember(apps)` fusionnés en 1 fold pass

---

### v0.1.3 — 2026-05-23 — BrandedTitle + 3-tab nav + Smart Cleaner / Security Audit actions

**Commit** `144f4e2` (+ fdroid yml bump `97f059a`) · **Tag** `v0.1.3` ·
**versionCode** 4 ·
**GH release** : https://github.com/gitubpatrice/APP-MANAGER-TECH/releases/tag/v0.1.3

Session ultra-dense de retours user S24 → overhaul UI + actions concrètes
dans Smart Cleaner et Security Audit qui étaient "écrans sans action" +
10 audit fixes.

**UI / branding** :

- **BrandedTitle** composable créé. Damier 28dp + Column { Text app_name
  labelSmall + Text screenTitle bodyMedium Bold avec letterSpacing 0.08sp
  pour effet "caps" sans casser TalkBack }. Appliqué aux 16 TopAppBar
  (sauf AboutScreen qui est lui-même dédié au brand).
- **Logo VectorDrawable** `ic_app_logo.xml` 4 paths damier (remplace PNG
  500×500 RGBA ~1MB en mémoire pour affichage 28dp).
- **Bottom nav 3 tabs** : Accueil / Outils / À propos. Tab ABOUT →
  `AboutScreen()` rendu inline avec `onBack = null` (nullable rétro-compat).
- **ToolsScreen** : cards `aspectRatio(1f)` uniformes + icônes wrap dans
  `Surface(48dp tinté arrondi) { Box { Icon(22dp) } }` pour uniformité
  visuelle (Material icons ont paddings internes variables). **12 outils**
  (Settings + À propos retirés du grid — accessibles via overflow + tab).
- **AppListScreen** : Sort + Filter + Settings consolidés en `IconButton(MoreVert)`
  + `DropdownMenu` (3 items : Paramètres / Trier / Inclure apps système avec
  leading icon stable CheckBox / CheckBoxOutlineBlank).
- **Standalone SelectAll button retiré** (user "il sert pas").
- **`showFilterDialog` + `FilterPickerDialog` supprimés** (orphans).
- **"SD Maid" mention retirée** de About → "Gestionnaire d'applications".

**Smart Cleaner overhaul** :

- `withTimeout(20s)` autour de `getSuggestions()` — fix user "même
  actualiser ne marche pas" (Flow stallait indéfiniment, isAnalyzing=true
  bloqué, bouton Refresh désactivé).
- Re-entrancy guard `if (isAnalyzing) return`.
- `UsageStatsAccessBanner` en tête + `LifecycleEventEffect(ON_RESUME)` →
  re-probe perm + auto-analyze si grant.
- **Split init vs Refresh** : `init { analyze(rescanFirst = false) }`
  (~50 ms cache Room), Refresh button → `analyze(rescanFirst = true)`
  (~1 s rescan PackageManager + analyze).
- **Per-row 3-dot menu** : `DropdownMenu` (Voir détails / Désinstaller /
  Vider cache / Ignorer).
- **Toggle "Inclure apps système"** dans TopAppBar overflow ⋮ (param
  `includeSystemApps: Boolean` passé au `GetSmartSuggestionsUseCase`).
- **Snackbar** "Analyse terminée — N suggestions" via Event.AnalyzeDone.
- VM inject : `AppInfoRepository` + `UninstallAppUseCase` +
  `ClearAppCacheUseCase` + **`IgnoreAppUseCase`** (pas Repository direct —
  fix audit M-1 pour cap 500 + validation).
- Event renommé `LaunchUsageSettings` → `LaunchIntent` (réutilisé pour
  3 sémantiques différentes — fix audit L-3).

**Security Audit overhaul** :

- `withTimeout(10s)` + re-entrancy guard.
- `UiState.error: String?` affiché en rouge si timeout.
- `UiState(isLoading = true)` à l'init (fix audit L-2).
- VM inject : `UninstallAppUseCase` + `IntentFactory`.
- **Per-app 3-dot menu** : `DropdownMenu` (Voir détails / Désinstaller).
- **Bouton "Désactiver dans Paramètres → Sécurité / Accessibilité"** par
  section `AuditCard` (Android interdit révocation programmatique
  DeviceAdmin / AccessibilityService — on ouvre l'OS Settings).
- **Badge "Système"** via `SYSTEM_PUBLISHERS: Set<String>` whitelist
  (Google + Samsung Knox + OEM core, 13 packages connus).
- `IntentFactory.deviceAdminSettingsIntent()` + `accessibilitySettingsIntent()`
  nouvelles méthodes.
- **Snackbar** "Actualisé — X admin(s) · Y service(s)" via Event.RefreshDone
  (refresh < 50 ms invisible sans feedback).

**10 audit fixes 3-axes pré-tag** :

| ID | Fix |
|---|---|
| M-1 | SmartCleaner.ignore() → IgnoreAppUseCase (cap 500 + validation) |
| M-2 | AppList batch snackbar string externalisée FR+EN |
| M-3 | BrandedTitle `.uppercase()` retiré — TalkBack epelait lettre par lettre |
| M-4 | ToolColors.Amber #FFB300 (1.79:1 light WCAG fail) → Deep Orange 900 #E65100 |
| L-1 | Import `Column` doublon SmartCleanerScreen + imports réordonnés |
| L-2 | SecurityAuditViewModel `UiState(isLoading = true)` à l'init |
| L-3 | Event.LaunchUsageSettings → Event.LaunchIntent (sémantique) |
| L-4 | 9 strings orphelines retirées FR+EN (filter_* + tool_subtitle_settings/about) |
| L-5 | ToolColors.Brown #6D4C41 (2.49:1 dark WCAG fail) → #8D6E63 Brown 400 |
| L-6 | SecurityAudit Snackbar feedback (couvert par overhaul) |

`assembleRelease` + `lintVitalRelease` + `testReleaseUnitTest` verts.

---

## Conventions canoniques retenues (à conserver pour la suite)

1. **BrandedTitle** = composable canonique pour `TopAppBar { title = { ... } }`
   sur tous les écrans sauf `AboutScreen`. Pattern : damier 28dp + 2-ligne
   titre (app_name + screenTitle).
2. **Per-row 3-dot menu** = pattern canonique pour actions contextuelles
   dans les listes d'apps (Smart Cleaner, Security Audit). Évite l'écran
   "vide sans action possible".
3. **Timeout obligatoire** sur toute coroutine UI qui dépend d'un Flow ou
   d'un system service : `withTimeout(N_seconds)` + catch + reset isLoading
   dans `finally` ou catch.
4. **Snackbar feedback** sur tout refresh < 1 s pour confirmer l'effet à
   l'utilisateur (sinon il a l'impression "ça ne marche pas").
5. **Rescan séparé du analyze** : init = lecture Room (rapide), Refresh
   explicite = rescan PackageManager + analyze (lent mais à la demande).
6. **AboutScreen.onBack nullable** quand rendu comme tab content vs
   nav-pop.
7. **WCAG AA minimum 4.5:1** sur tous les ToolColors (validé dans les 2
   thèmes light + dark).
8. **Pas de `.uppercase()`** sur Text affiché en TopAppBar — utiliser
   `letterSpacing` + `FontWeight` pour effet visuel sans casser TalkBack.
9. **Schema Room** : toujours additif (ALTER ADD COLUMN / CREATE TABLE
   IF NOT EXISTS / CREATE INDEX IF NOT EXISTS). Jamais DROP / RENAME.
   `MigrationTest` obligatoire pour chaque bump.
10. **Cert SHA-256 ABSOLUMENT STABLE** sur toutes les releases — vérifier
    `keytool -list -v` avant chaque tag pour éviter de casser la chaîne
    F-Droid + le canal Update Android.

---

## État final v0.1.3

| Axe | État |
|---|---|
| GitHub repo | `gitubpatrice/APP-MANAGER-TECH` public Apache 2.0, 4 tags (v0.1.0 → v0.1.3), 4 GH releases avec 3 APKs splits chacun |
| F-Droid MR | !38925 ouverte, 4 Build entries (v0.1.0 → v0.1.3), CurrentVersion 0.1.3/4, en review mainteneur |
| Cert SHA-256 | Stable depuis v0.1.0 : `76:E8:77:2E:09:95:13:69:40:5F:58:E7:0C:4A:FF:FD:41:C4:68:75:53:C6:CF:A0:3D:08:14:5F:F6:0F:F1:CF` |
| Keystore | `app/release.keystore` RSA 4096, validité 100 ans, PW dans `keystore.properties` (gitignored) |
| Build | `assembleRelease` + `lintVitalRelease` + `testReleaseUnitTest` verts à chaque release |
| Install S24 | À jour v0.1.3 (RZCY41EGKYL) |
| Site files-tech.com | Page `app-manager-tech.php` créée avec hero, 18 features, captures placeholders, permissions, tech stack |

---

## Stack technique finale

- **Langage** : Kotlin 2.1.0 natif (pas Flutter, pas Compose Multiplatform)
- **UI** : Jetpack Compose Material 3 (BOM 2024.12.01) + Navigation Compose +
  `BrandedTitle` custom composable sur 16 écrans
- **DI** : Hilt 2.55 via KSP (pas kapt)
- **Persistance** : Room 2.6.1 schéma v3 (migrations additives,
  MigrationTest couvert) + DataStore Preferences
- **Concurrence** : Coroutines + Flow + StateFlow.WhileSubscribed +
  `withTimeout` sur toute coroutine UI critique
- **Background** : WorkManager 2.10.0 avec HiltWorker (init on-demand
  via `tools:node="remove"` sur Manifest WorkManagerInitializer)
- **Images** : Coil 2.7.0 (Apache 2.0)
- **Logs** : Timber 5.0.1 (DebugTree en debug, NoOpReleaseTree en release)
- **Pisteurs** : JSON offline embarqué (44 trackers Exodus-style)
- **Build** : AGP 8.7.3, JDK 17, Gradle 8.11.1, R8 minify + 3 ABI splits
  (~1.97 MB par APK arm64-v8a)
- **License** : Apache License 2.0
- **Sources** : https://github.com/gitubpatrice/APP-MANAGER-TECH
- **F-Droid** : https://gitlab.com/fdroid/fdroiddata/-/merge_requests/38925

---

## Restes post-v0.1.3 (déférés v0.2.0+)

- Widget Accueil (top apps cache / suggestions Smart Cleaner)
- CorpseFinder (fichiers orphelins post-uninstall — scoped storage)
- Action history (Room v3 → v4 log destructive actions)
- APK extract via MediaStore
- Tags utilisateur sur apps
- Migration AGP 8.7.3 → 8.13.2 (warning deprecated `resourceConfigurations`)
- Accessibilité : audit complet TalkBack + content descriptions
- LOW résiduels : `openUrl` whitelist case-sensitivity (doc), Brown sur
  dark theme marge faible (3.9:1)

---

# v0.2.0 — 2026-05-23 — Permission Drift + Quarantine + Safety Guardrails

## Vue d'ensemble

3 features privacy-first inédites dans le portfolio Files Tech :

1. **Permission Drift Tracker** — historise les permissions dangereuses par app,
   surface chaque GAINED/LOST entre 2 captures (audit log de surveillance
   supply-chain).
2. **App Quarantine** — met une app en pause avec date de réexamen.
   Deux modes : HARD (uninstall + APK backup SAF, restore via PackageInstaller)
   et SOFT (rappel uniquement, user désactive via Settings).
3. **Safety Guardrails** — couche transversale : avant tout uninstall
   ou quarantine HARD d'une app sensible (2FA, banque, password manager,
   santé, transport, messagerie E2E), affiche un dialog rouge avec
   hold-3s confirm (drag-cancel).

## Architecture livrée

### Foundations Room v4
- 2 nouvelles tables (`permission_snapshot` append-only, `quarantine_entry`
  1 row/pkg) + 3 nouveaux indices
- Migration `MIGRATION_3_4` strictement additive
- `MigrationTest_v3_v4` exhaustif (preserve data + 2 inserts + 3 indices)

### Permission Drift Tracker
- `DangerousPermissionInspector` (cache protectionLevel, exclut system par défaut)
- `CapturePermissionSnapshotsUseCase` retourne `CaptureResult(baselines, drifts)`
- `ObservePermissionDriftsUseCase` flow drift events window 30/90/all
- `PurgeOldPermissionSnapshotsUseCase` retention configurable
- `PermissionSnapshotWorker` HiltWorker 24h avec `withTimeout(8 min)`
- Screen avec carte d'état toujours visible + gros bouton labellé + feed +
  tap row deep-link Settings → App → Permissions via runtime
  `queryIntentActivities` (AOSP + Samsung + Google variants)

### App Quarantine (hybrid)
- `ApkBackupManager` SAF tree URI persistable + sanitize filename + MIME pin
- 5 UseCases : Quarantine / Restore / Drop / Observe / CheckExpired
- `QuarantineRestoreWorker` HiltWorker 24h `withTimeout(2 min)`
- Screen liste + Picker search + `QuarantineConfigDialog` partagé
- `SoftQuarantineActionDialog` avec 3 étapes claires (résout "je tombe
  sur Infos appli, je fais quoi ?")

### Safety Guardrails
- `CriticalAppDetector` whitelists FR catégorisées
  (AUTHENTICATION / PASSWORD_MANAGERS / BANKING_FR / HEALTH / MESSAGING_E2E /
  TRANSPORT_FR / USER_PROTECTED placeholder v0.3.0)
- `CriticalWarningDialog` dialog rouge + bouton hold-3s confirm avec
  drag-cancel via `pointerInput { awaitPointerEventScope }` + touchSlop
  (pattern aligné SMS Tech EmergencyHoldButton)
- Branchements : AppDetailViewModel.uninstall + .quarantine HARD,
  QuarantinePickerViewModel.quarantine HARD

### Wiring
- `IntentFactory.appPermissionsSettingsChain` runtime discovery via
  `queryIntentActivities` avec composants explicites + fallback App Info
- `WorkScheduler` 2 nouvelles méthodes + `MainApplication.syncBackgroundWorkers`
- `NotificationChannels` 2 nouveaux (DRIFT + QUARANTINE) + `NotificationHelper`
  postPermissionDriftSummary + postQuarantineExpired (return Boolean)
- `AppSettings.PrivacyMonitor` + `AppSettings.Quarantine` + DataStore keys
- Settings 2 nouvelles sections + SAF folder picker via `OpenDocumentTree`
- AppRoot 3 routes + HomeShell + ToolsScreen 2 cartes
- AppDetail : "Modifier dans Paramètres Android" + "Mettre en quarantaine"
- Strings FR+EN parité ~80 nouvelles + 1 plurals

## Iterations UX sur retours utilisateur

1. "Drift feed rien ne s'affiche" → distinction baseline vs drift
2. "État déjà capturé mais rien ne se passe" → carte d'état toujours visible
3. "Bouton refresh peu visible" → gros bouton labellé dans la carte
4. "Quarantiner n'existe pas en français" → "Mettre en quarantaine" partout
5. "SOFT mode : je tombe sur Infos appli, je fais quoi ?" → `SoftQuarantineActionDialog`
6. "Drift tap m'envoie sur AppDetail" → deep-link direct Settings Permissions
7. "Tombe sur Infos appli au lieu de Permissions" (Samsung One UI 7 Android 16
   sandbox MANAGE_APP_PERMISSIONS) → fallback App Info accepté
8. "ne touche pas à ça" → Safety Guardrails intégrés dans v0.2.0
   (et non v0.2.1) pour couvrir Quarantine HARD dès le ship

## Audit pré-tag (3-axes via android-3-axes-auditor)

**Verdict : 0 CRITICAL / 1 HIGH / 6 MEDIUM / 5 LOW**

4 fixes bloquants appliqués pré-tag :

| ID | Sujet | Correctif |
|---|---|---|
| H-1 | USER_PROTECTED enum fantôme | Ajout entrée vide WHITELISTS + CategoryRanking |
| M-1 | Notif IDs masque 15-bit (collisions ~180 pkgs) | Hash 32-bit full + bases 1M/2M non-overlap |
| M-4 | Restore HARD ConfirmDialog bleu | Remplacé par DestructiveDialog rouge |
| M-5 | Hold-3s detectTapGestures no drag-cancel | pointerInput + awaitPointerEventScope + touchSlop |

Restants reportés v0.2.1 : cap `resolveLabels`, incohérence Slider 90j vs
coerceIn(365), remember-Date par item, slider semantics, "blue button" string,
validation URI scheme.

## Validations build

- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ tous tests verts
- `assembleRelease` ✅ (R8 + lintVital + signing OK) — 3 APKs ~2.14 MB
- Install debug S24 RZCY41EGKYL ✅
- Schema v4 exporté

## Stack technique

Kotlin 2.1.0 / AGP 8.7.3 / JDK 17 / Compose BOM 2024.12.01 / Hilt 2.55 /
Room 2.6.1 / DataStore 1.1.1 / WorkManager 2.10.0 / Coil 2.7.0 / Timber 5.0.1
+ nouvelle dep `androidx.documentfile 1.0.1` (Apache 2.0, F-Droid OK)

## Cert SHA-256 (inchangée)

`76:E8:77:2E:09:95:13:69:40:5F:58:E7:0C:4A:FF:FD:41:C4:68:75:53:C6:CF:A0:3D:08:14:5F:F6:0F:F1:CF`

## v0.3.0 prévue

- #1 Lifecycle History (PackageMonitor + Room v5 + timeline + "raison de suppression")
- #3 Diagnostic PDF Export (extension ExportReportUseCase + PdfDocument natif)
- Safety Guardrails Phase B (Disable / ClearData / MoveToTrash / SmartCleaner
  uninstall / Trash uninstallNow) + Settings "Apps protégées" édition manuelle
  dark theme marge faible (3.9:1)

---

# v0.2.1 — 2026-05-24 — UX hardening + audit "peigne fin"

## Vue d'ensemble

Session intense de fixes 6 user-reported bugs + audit full-app 3-axes +
audit cohérence transversale, débouchant sur 6 vagues de corrections
chirurgicales appliquées avec build vert après chaque vague.

## User-reported bugs corrigés (motivent la release)

1. **Corbeille** : entrées restent affichées avec Restore/Uninstall après
   désinstallation système (rows fantômes). Fix : `TrashRepository.purgeOrphaned()`
   via PackageManager probe + AtomicBoolean isPurging + `LifecycleEventEffect ON_RESUME`
   + batch `deleteByPackages` DAO query.
2. **SecurityAudit refresh bloqué** en spinner permanent. Cause : initial
   `UiState(isLoading = true)` (audit L-2 v0.1.3) déclenche le guard `if (isLoading) return`
   au 1er refresh. Fix : `AtomicBoolean isRefreshing` séparé.
3. **Zombies refresh sans feedback** + tap row uniquement sur flèche.
   Fix : Event.RefreshDone snackbar + AtomicBoolean + Row.clickable.
4. **Storage refresh n'update pas tailles/cache**. Cause racine :
   PACKAGE_USAGE_STATS pas accordé → StorageStatsManager.queryStatsForUid retourne 0.
   Fix : UsageStatsAccessBanner + onResumed() re-probe + auto-rescan on grant.
5. **AppDetail "Dernière utilisation" toujours "Jamais utilisée"**.
   Même cause + même fix.
6. **Paramètres inaccessible depuis Outils**. Fix : icône gear TopAppBar.

## UX add

- **Bouton "Voir la corbeille"** (fond rouge clair, texte sombre) apparaît
  dans AppDetail ActionsCard sous Uninstall **après MoveToTrash réussi**.
  Driven par `recentlyMovedToTrash: Boolean` dans UiState, reset à load().

## Audit full-app "peigne fin" (3-axes + cohérence transversale)

Lancé 2 agents en parallèle :
- `android-3-axes-auditor` — sécurité+perf+qualité sur toute l'app
- `android-architecture-coherence-checker` — 12 patterns transversaux

Score cohérence : **72/100** post-v0.2.0.

## Plan d'attaque chirurgical 6 vagues

| Vague | Findings appliqués | Build |
|---|---|---|
| V1 Quick wins | H1 SystemClock + H3 AppRoot Settings callbacks + M2 hash positif + M5 BrandDanger + M1 require validPkg | ✅ |
| V2 withTimeout (5 sites) | C7a Trackers + C7b/M3/L4 GetZombies + C7c Storage (×2) + C7d PermissionDrift + M4 AppDetail load fan-out | ✅ |
| V3 AtomicBoolean (4 sites) | C1a Trackers + C1b SmartCleaner + C1c PermissionDrift + C1d Quarantine (manquait totalement) | ✅ |
| V4 UsageStats banner | C2a+C8a RarelyUsed + C2b+C8b Zombies (zones fragiles préservées) | ✅ |
| V5 Delta v0.2.1 reportés | H-1 purgeOrphaned batch deleteByPackages + AtomicBoolean isPurging + M-1 hasUsageStatsAccess IO (Storage + AppDetail) + M-4/L-2 Box weight au lieu de fillMaxSize | ✅ |
| V6 MEDIUM polish | C3a Trackers Event.ScanDone snackbar + C6a BatchActionUseCase filter validPackageName | ✅ |

## Audit summary

**Avant audit** : 0 CRITICAL / 8 HIGH / 12 MEDIUM / 12 LOW
**Après corrections appliquées** : tous les HIGH + 10/12 MEDIUM fixés
**Reportés v0.2.2** : 2 MEDIUM (M7 combine-5 fragile, L1 SystemClock dans Quarantine UseCases) + 5 LOW (L2 séparateur SQL, L3 emptyTrash dialogs superposés, L5 Serializable, L6 multi-classe fichier, autres LOW cosmétiques)

## CI fix

CodeQL job échouait sur v0.2.0 avec "CodeQL detected code written in
Java/Kotlin but could not process any of it" — cause : Gradle build cache
retourne `compileDebugKotlin FROM-CACHE` → aucun compilateur ne s'exécute
→ tracer CodeQL n'a rien à extraire. Fix : ajout `--no-build-cache --rerun-tasks`
au workflow `.github/workflows/codeql.yml`. Run suivant : SUCCESS.

## Memory feedback ajouté

Nouvelle règle `feedback_audit_prudence_no_regression.md` : lors des
corrections d'audit, vigilance maximale sur ne rien casser de ce qui
fonctionne déjà — chaque patch doit être chirurgical et préserver
comportement + branchements existants.

## Validations build finales

- `compileDebugKotlin` ✅ (après chaque vague)
- `testDebugUnitTest` ✅ tous tests verts
- `assembleRelease` ✅ (R8 + lintVital + signing OK)
- Install debug + release S24 RZCY41EGKYL ✅

## Cert SHA-256 (inchangée vs v0.1.x et v0.2.0)

`76e8772e09951369405f58e70c4afffd41c4687553c6cfa03d08145ff60ff1cf`

## v0.3.0 prévue (plan inchangé)

- #1 Lifecycle History (PackageMonitor + Room v5 + timeline + raison suppression)
- #3 Diagnostic PDF Export (extension ExportReportUseCase + PdfDocument natif)
- Safety Guardrails Phase B
