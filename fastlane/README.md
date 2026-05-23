# Fastlane metadata for F-Droid

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
└── fr-FR/                             same structure, French
```

Updating per release:

1. Bump `app/build.gradle.kts` versionName + versionCode
2. Add `changelogs/<new_versionCode>.txt` (≤ 500 chars) in BOTH `en-US/` and `fr-FR/`
3. Tag + push the GitHub release
4. Update `fdroid-submission/com.filestech.appmanager.yml` with new Builds[] entry
5. Open MR on fdroiddata fork

Screenshots are absent from this commit — they must be captured on a real
device (or `gradle :app:installDebug` to an emulator) and dropped into
`images/phoneScreenshots/` before the first F-Droid submission. The F-Droid
build will succeed without them but the listing page will look empty.
