#!/usr/bin/env python3
"""NEGATIVE control of check-translations.py - the proof that the instrument can go red.

Why this exists
---------------
A parity check that returned 0 whatever happened would be worse than none: it would hand out a
false guarantee at every release. So every failure mode check-translations.py claims to catch is
REPRODUCED here in a throwaway tree, and two things are verified:
  - the check returns 1, not 0;
  - its message names the RIGHT cause. Failing for another reason is a false green: the day the
    real cause disappears, the check keeps failing and nobody believes it any more.

A POSITIVE WITNESS opens the series: a perfectly healthy tree must return 0. Without it, a
check-translations.py broken into returning 1 on everything would pass the whole series.

No dependencies, never touches the repository: everything happens in a temporary directory,
deleted at the end.

    python3 tools/check-translations-negative-control.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
CHECK = os.path.join(HERE, "check-translations.py")

EN = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">App Manager Tech</string>
    <string name="source_url" translatable="false">https://example.org/source</string>
    <string name="hello">Hello</string>
    <string name="promise">100% local — zero network</string>
    <string name="moved">Moved to %1$s at %2$s</string>
    <string name="about_privacy_policy_url">https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.md</string>
    <plurals name="events">
        <item quantity="one">%1$d event</item>
        <item quantity="other">%1$d events</item>
    </plurals>
</resources>
"""

# Note the REORDERING of %1$s and %2$s: it is LEGITIMATE (that is what 1$ / 2$ are for) and the
# positive witness therefore requires it to pass. French cannot be a copy of German: CLDR requires
# `many` in French and not in German.
FR_OK = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">App Manager Tech</string>
    <string name="hello">Bonjour</string>
    <string name="promise">100 % local — zéro réseau</string>
    <string name="moved">Déplacé le %2$s vers %1$s</string>
    <string name="about_privacy_policy_url">https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.fr.md</string>
    <plurals name="events">
        <item quantity="one">%1$d événement</item>
        <item quantity="many">%1$d d\\'événements</item>
        <item quantity="other">%1$d événements</item>
    </plurals>
</resources>
"""

DE_OK = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">App Manager Tech</string>
    <string name="hello">Hallo</string>
    <string name="promise">100 % lokal — null Netzwerk</string>
    <string name="moved">Am %2$s nach %1$s verschoben</string>
    <string name="about_privacy_policy_url">https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.md</string>
    <plurals name="events">
        <item quantity="one">%1$d Termin</item>
        <item quantity="other">%1$d Termine</item>
    </plurals>
</resources>
"""

GRADLE = """android {
    defaultConfig {
        versionCode = 58
    }
    androidResources {
        localeFilters += listOf("en", "fr", "de")
    }
}
"""

LOCALES_CONFIG = """<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="fr" />
    <locale android:name="de" />
</locale-config>
"""

MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="${appLabel}"
        android:localeConfig="@xml/locales_config">
    </application>
</manifest>
"""

base = None


def write(path_, content):
    os.makedirs(os.path.dirname(path_), exist_ok=True)
    with open(path_, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def write_bytes(path_, data):
    """Write WITHOUT line-ending translation - Windows text mode would rewrite it."""
    os.makedirs(os.path.dirname(path_), exist_ok=True)
    with open(path_, "wb") as f:
        f.write(data)


def path(*parts):
    return os.path.join(base, *parts)


def res(*parts):
    return path("app", "src", "main", "res", *parts)


def build():
    """A HEALTHY tree: German complete, every gesture made, French present."""
    if os.path.exists(base):
        shutil.rmtree(base)
    os.makedirs(path("tools"))
    shutil.copy(CHECK, path("tools", "check-translations.py"))
    write(path("app", "build.gradle.kts"), GRADLE)
    write(path("app", "src", "main", "AndroidManifest.xml"), MANIFEST)
    write(res("values", "strings.xml"), EN)
    write(res("xml", "locales_config.xml"), LOCALES_CONFIG)
    # French is present so that the CONVERSE (language announced without translation) does not
    # fire on its own and mask the defect each case targets.
    write(res("values-de", "strings.xml"), DE_OK)
    write(res("values-fr", "strings.xml"), FR_OK)
    # The documents the privacy links point at.
    write(path("PRIVACY.md"), "policy\n")
    write(path("PRIVACY.fr.md"), "politique\n")
    # versionCode 58 comes with GRADLE above: without it, the changelog checks would skip silently,
    # and their cases below would pass for green without having measured anything.
    for locale in ("en-US", "de-DE"):
        fastlane = path("fastlane", "metadata", "android", locale)
        write(os.path.join(fastlane, "title.txt"), "App Manager Tech\n")
        write(os.path.join(fastlane, "short_description.txt"), "A short description.\n")
        write(os.path.join(fastlane, "full_description.txt"), "A long description.\n")
        # 500 characters exactly, 1000 bytes: the cap counts characters, and the positive witness
        # must stay green on it.
        write(os.path.join(fastlane, "changelogs", "58.txt"), "é" * 500 + "\n")


def de(content):
    write(res("values-de", "strings.xml"), content)


# --- One mutation per claimed failure mode ---------------------------------------------------

def missing_key():
    de(DE_OK.replace('    <string name="hello">Hallo</string>\n', ""))
    return "MISSING key: hello"


def extra_key():
    de(DE_OK.replace("</resources>", '    <string name="unknown">x</string>\n</resources>'))
    return "EXTRA key"


def duplicate_key():
    de(DE_OK.replace('    <string name="hello">Hallo</string>\n',
                     '    <string name="hello">Hallo</string>\n'
                     '    <string name="hello">Servus</string>\n'))
    return "DUPLICATE key: hello"


def lost_argument():
    de(DE_OK.replace("Am %2$s nach %1$s verschoben", "Nach %1$s verschoben"))
    return "format arguments differ on moved"


def invented_argument():
    de(DE_OK.replace("Am %2$s nach %1$s verschoben", "Am %2$s nach %1$s via %3$s verschoben"))
    return "format arguments differ on moved"


def malformed_argument():
    """A %1$p is not a Java conversion: the argument is LOST, not translated."""
    de(DE_OK.replace("Am %2$s nach %1$s verschoben", "Am %2$s nach %1$p verschoben"))
    return "format arguments differ on moved"


def invented_argument_in_plural():
    de(DE_OK.replace('<item quantity="other">%1$d Termine</item>',
                     '<item quantity="other">%1$d von %2$d Terminen</item>'))
    return "INVENTED argument in plural events[other]"


def missing_quantity():
    de(DE_OK.replace('        <item quantity="one">%1$d Termin</item>\n', ""))
    return "MISSING quantity in plural events"


def cldr_category_of_the_language_missing():
    """French without `many`, which ENGLISH does not have - the defect this repository had."""
    write(res("values-fr", "strings.xml"),
          FR_OK.replace('        <item quantity="many">%1$d d\\\'événements</item>\n', ""))
    return "CLDR category MISSING for this language: many"


def useless_cldr_category():
    de(DE_OK.replace('        <item quantity="other">%1$d Termine</item>',
                     '        <item quantity="two">%1$d Termine</item>\n'
                     '        <item quantity="other">%1$d Termine</item>'))
    return "CLDR category USELESS in de"


def absent_from_locale_filters():
    write(path("app", "build.gradle.kts"), GRADLE.replace('"en", "fr", "de"', '"en", "fr"'))
    return "absent from localeFilters"


def locale_filters_block_gone():
    write(path("app", "build.gradle.kts"), "android {\n}\n")
    return "localeFilters block not found"


def absent_from_locales_config():
    write(res("xml", "locales_config.xml"),
          LOCALES_CONFIG.replace('    <locale android:name="de" />\n', ""))
    return "absent from res/xml/locales_config.xml"


def locales_config_gone():
    os.remove(res("xml", "locales_config.xml"))
    return "file missing - there is no Android 13+ language picker"


def manifest_does_not_point_at_locales_config():
    """The file exists, but nothing references it: no picker, and nothing else would say so."""
    write(path("app", "src", "main", "AndroidManifest.xml"),
          MANIFEST.replace('\n        android:localeConfig="@xml/locales_config"', ""))
    return "android:localeConfig"


def language_announced_without_translation():
    shutil.rmtree(res("values-de"))
    return "would offer an empty language"


def link_to_a_missing_document():
    """PRIVACY.de.md named in a string, but never written: a German user gets a 404."""
    de(DE_OK.replace("blob/main/PRIVACY.md", "blob/main/PRIVACY.de.md"))
    return "links to PRIVACY.de.md, which does not exist"


def store_listing_too_long():
    """81 characters for a cap of 80. (41 accented letters - 82 bytes - must NOT fail: see the
    positive witness, whose changelog is 500 characters and 1000 bytes.)"""
    write(path("fastlane", "metadata", "android", "de-DE", "short_description.txt"),
          "a" * 81 + "\n")
    return "fastlane/short_description.txt: 81 characters for a cap of 80"


def changelog_too_long():
    """F-Droid cuts the changelog at 500 characters, mid-word, without a warning."""
    write(path("fastlane", "metadata", "android", "de-DE", "changelogs", "58.txt"), "é" * 501 + "\n")
    return "fastlane/changelogs/58.txt: 501 characters for a cap of 500"


def store_file_missing():
    os.remove(path("fastlane", "metadata", "android", "de-DE", "title.txt"))
    return "fastlane/title.txt MISSING"


def current_changelog_missing():
    os.remove(path("fastlane", "metadata", "android", "de-DE", "changelogs", "58.txt"))
    return "fastlane/changelogs/58.txt MISSING"


def doubled_carriage_return():
    write_bytes(res("values-de", "strings.xml"), DE_OK.encode("utf-8").replace(b"\n", b"\r\r\n"))
    return "carries \\r\\r\\n line endings"


def mixed_line_endings():
    data = DE_OK.encode("utf-8")
    cut = data.index(b"\n", len(data) // 2) + 1
    write_bytes(res("values-de", "strings.xml"), data[:cut].replace(b"\n", b"\r\n") + data[cut:])
    return "mixes CRLF"



def non_translatable_key_translated():
    """English marks source_url translatable="false"; German carries it anyway. (Its ABSENCE from
    German and French is the positive witness: no language owes it.)"""
    de(DE_OK.replace("</resources>",
                     '    <string name="source_url">https://example.org/quelle</string>\n</resources>'))
    return 'translates a key English marks translatable="false": source_url'


CASES = [
    ("missing key", missing_key),
    ("extra key", extra_key),
    ("translatable=false key translated", non_translatable_key_translated),
    ("duplicate key", duplicate_key),
    ("format argument LOST", lost_argument),
    ("format argument INVENTED", invented_argument),
    ("format argument MALFORMED (%1$p)", malformed_argument),
    ("argument invented in a plural", invented_argument_in_plural),
    ("plural quantity missing", missing_quantity),
    ("plural: CLDR category of the LANGUAGE missing", cldr_category_of_the_language_missing),
    ("plural: useless CLDR category", useless_cldr_category),
    ("gesture b: absent from localeFilters", absent_from_locale_filters),
    ("gesture b: localeFilters block gone", locale_filters_block_gone),
    ("gesture c: absent from locales_config", absent_from_locales_config),
    ("gesture c: locales_config.xml gone", locales_config_gone),
    ("gesture d: manifest does not reference it", manifest_does_not_point_at_locales_config),
    ("converse: language announced, not translated", language_announced_without_translation),
    ("link to a repository document that is missing", link_to_a_missing_document),
    ("store listing: too long (characters)", store_listing_too_long),
    ("store listing: changelog over 500 characters", changelog_too_long),
    ("store listing: a file MISSING", store_file_missing),
    ("store listing: current changelog missing", current_changelog_missing),
    ("line endings: one carriage return too many", doubled_carriage_return),
    ("line endings: two conventions in one file", mixed_line_endings),
]


def run():
    r = subprocess.run([sys.executable, path("tools", "check-translations.py")],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def main():
    global base
    if not os.path.exists(CHECK):
        print("ERROR: %s not found." % CHECK, file=sys.stderr)
        return 2

    temp_root = tempfile.mkdtemp(prefix="check-translations-")
    base = os.path.join(temp_root, "tree")
    failures = []
    try:
        build()
        code, output = run()
        if code == 0:
            print("  OK    POSITIVE WITNESS: healthy tree (reordered arguments included) -> 0")
        else:
            print("  FAIL  POSITIVE WITNESS: healthy tree -> %d" % code)
            print("        " + output.strip().replace("\n", "\n        "))
            failures.append("positive witness")

        for name, mutate in CASES:
            build()
            expected = mutate()
            code, output = run()
            if code != 1:
                print("  FAIL  %-48s code=%d (expected 1) NOT DETECTED" % (name, code))
                failures.append(name)
            elif expected not in output:
                print("  FAIL  %-48s fails, but for ANOTHER reason" % name)
                print("        expected: %s" % expected)
                print("        " + output.strip().replace("\n", "\n        "))
                failures.append(name)
            else:
                print("  OK    %-48s detected, right cause" % name)
    finally:
        shutil.rmtree(temp_root, ignore_errors=True)

    total = len(CASES) + 1
    print()
    if failures:
        print("NEGATIVE CONTROL: %d/%d -- FAILED: %s"
              % (total - len(failures), total, ", ".join(failures)))
        return 1
    print("NEGATIVE CONTROL: %d/%d -- every defect is detected, and for the right reason."
          % (total, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
