# Store listing metadata (F-Droid layout)

App Manager Tech is published on GitHub Releases only; it is not on F-Droid at
the moment. The listing is kept in F-Droid's layout all the same, in five
languages (en-US, fr-FR, de-DE, it-IT, es-ES), checked at every build by
`tools/check-translations.py` (caps in characters: title 50, short description
80, full description 4000, changelog 500).

Layout follows the F-Droid spec for triplet-T metadata
(https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/metadata.yml):

```
fastlane/metadata/android/
├── en-US/
│   ├── title.txt
│   ├── short_description.txt   ≤ 80 chars
│   ├── full_description.txt    ≤ 4000 chars
│   ├── changelogs/<versionCode>.txt   ≤ 500 chars
│   └── images/
│       ├── icon.png                   512×512
│       ├── featureGraphic.png         1024×500
│       └── phoneScreenshots/          ≥ 2, max 8
│           ├── 01_app_list.png
│           ├── 02_app_detail.png
│           └── ...
├── fr-FR/  de-DE/  it-IT/  es-ES/      same structure
```

Updating per release:

1. Bump `app/build.gradle.kts` versionName + versionCode
2. Add `changelogs/<new_versionCode>.txt` (≤ 500 characters) in all five locales
3. Tag + push the GitHub release

`fdroid-submission/com.filestech.appmanager.yml` is the draft recipe of the
closed inclusion request (fdroiddata !38925). It is not maintained.

Screenshots are absent from this commit — they must be captured on a real
device (or `gradle :app:installDebug` to an emulator) and dropped into
`images/phoneScreenshots/` before any F-Droid submission. The F-Droid
build would succeed without them but the listing page would look empty.
