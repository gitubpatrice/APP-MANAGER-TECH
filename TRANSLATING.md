# Translating App Manager Tech

App Manager Tech ships in **English** (the source), **French**, **German**, **Italian** and
**Spanish**. English is the reference: a translation says what the English says, no more, no less.

## Where the texts live

- The app: `app/src/main/res/values/strings.xml` (English) and `values-fr/`, `values-de/`,
  `values-it/`, `values-es/`.
- The store listing: `fastlane/metadata/android/<locale>/` — `title.txt`, `short_description.txt`,
  `full_description.txt`, and `changelogs/<versionCode>.txt` for each release.

No user-visible text is built in Kotlin: ViewModels emit a `UiText` (a resource id and its
arguments) that the screen resolves, and errors go through `AppError.toUiText()`. A new message is a
new string resource, in every language.

## Adding a language: four gestures, all of them

1. `values-XX/strings.xml` with every key of the English file, except those marked
   `translatable="false"` (product name, URLs, identifiers).
2. `localeFilters` in `app/build.gradle.kts`. Without it the Android Gradle Plugin **strips** the new
   folder from the APK, without any error: the app stays in English.
3. `res/xml/locales_config.xml`, which feeds Android 13's per-app language picker (Settings → Apps →
   App Manager Tech → Language, also reachable from the app's own Settings → Appearance).
4. A `fastlane/metadata/android/<locale>/` folder with the three listing files and the changelog of
   the current version.

## Rules that are not style preferences

- **Format arguments** (`%1$s`, `%2$d`, `%d`, `%%`): the same set as English, key by key. Their order
  may change — that is what `1$` / `2$` are for. A lost or invented argument crashes the screen.
- **Escaping**: an apostrophe is `\'`, a straight double quote is `\"` (or use the typographic quotes
  of the language, which need no escaping). An unescaped straight quote is silently removed.
- **Plurals**: every quantity the language requires — `one`/`other` in English and German,
  `one`/`many`/`other` in French, Italian and Spanish (`many` takes the same text as `other`).
- **Register**: formal — *vous*, *Sie*, *Lei*, *usted* — as the other Files Tech apps.
- **Android's own words**: when a text names a screen or button of Android's Settings, use the exact
  label Android shows in that language (Force stop, Clear cache, Usage access, App info…).
- **Do not translate**: App Manager Tech, AMT, Files Tech, F-Droid, Google Play, Aurora, permission
  names (`android.permission.CAMERA`, `QUERY_ALL_PACKAGES`…), the technical identifiers of the expert
  view (`grantUriPermissions`, Activities / Services / Receivers / Providers), file extensions.

## Glossary — terms that must not drift

| English | French | German | Italian | Spanish |
|---|---|---|---|---|
| Quarantine | Quarantaine | Quarantäne | Quarantena | Cuarentena |
| Trash | Corbeille | Papierkorb | Cestino | Papelera |
| Exclusion list (ignored apps) | Liste des exclusions | Ausschlussliste | Elenco delle esclusioni | Lista de exclusiones |
| Zombie apps | Apps zombies | Zombie-Apps | App zombie | Apps zombi |
| Trackers | Pisteurs | Tracker | Tracker | Rastreadores |
| Privacy score | Score vie privée | Datenschutz-Score | Punteggio privacy | Puntuación de privacidad |
| Smart cleaner | Nettoyeur intelligent | Intelligente Bereinigung | Pulizia intelligente | Limpiador inteligente |
| Action journal | Journal d'actions | Aktionsprotokoll | Registro delle azioni | Registro de acciones |
| Permission changes | Changements de permissions | Berechtigungsänderungen | Modifiche alle autorizzazioni | Cambios de permisos |
| Signature clusters | Clusters de signatures | Signaturgruppen | Gruppi per firma | Grupos de firmas |
| Protected apps | Apps protégées | Geschützte Apps | App protette | Apps protegidas |
| Force stop | Forcer l'arrêt | Beenden erzwingen | Forza interruzione | Forzar detención |
| Clear cache | Vider le cache | Cache leeren | Svuota cache | Borrar caché |

Warnings deserve the most care: uninstall, real quarantine (the app's data is lost), emptying the
trash, and the critical-app dialog. A translation must never make one of them sound safer than the
English does.

## Store listing

fdroidserver counts these limits in **characters** and **cuts** a longer text there, mid-word,
without a warning: title 50, short description 80, full description 4000, changelog 500.

## Continuous integration

`tools/check-translations.py` runs in the CI gate, after its negative control
(`tools/check-translations-negative-control.py`). It fails on a missing or extra key, a changed set
of format arguments, a missing plural quantity, a language not shipped by all four gestures, a store
text over its limit, or a missing changelog for the current version. Run both before a pull request:

```bash
python3 tools/check-translations-negative-control.py
python3 tools/check-translations.py
```
