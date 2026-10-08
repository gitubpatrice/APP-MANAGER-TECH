#!/usr/bin/env python3
"""NEGATIVE control of the two CI guards - the proof that each can go red, and for the right reason.

Why this exists
---------------
A guard that returned 0 whatever happened would be worse than none: it would hand out a false
guarantee on every run. So every failure each guard claims to catch is REPRODUCED here, in a
throwaway directory, and two things are verified:
  - the guard returns 1, not 0;
  - its output names the RIGHT cause. Failing for another reason is a false green in disguise: the
    day the real cause appears, nobody can tell it from the noise.

A POSITIVE WITNESS opens each series: a healthy input must return 0. Without it, a guard broken into
returning 1 on everything would pass the whole series.

Guards covered:
  - tools/check-manifest-permissions.py  (the "no Internet permission" promise)
  - tools/check-test-results.py          (a test run that executed zero tests)

With `--real-manifest PATH`, two more cases run against a REAL manifest - the merged release
manifest, or the manifest of the release APK as printed by apkanalyzer: it must pass as built, and
fail once `INTERNET` is injected into a copy of it. That proves the guard reads the artefact the build
actually produces, not only a fixture written to resemble it.

No dependencies, never touches the repository: everything happens in a temporary directory.

    python3 tools/check-guards-negative-control.py [--real-manifest PATH]
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST_CHECK = os.path.join(HERE, "check-manifest-permissions.py")
TESTS_CHECK = os.path.join(HERE, "check-test-results.py")

# The guards print `::error::` annotations and write the job summary under GitHub Actions. The
# failures provoked here are EXPECTED: left in that environment, they would paint red annotations on
# a green run, and the positive witness would write a fake test count into the summary.
GUARD_ENV = {k: v for k, v in os.environ.items() if k not in ("GITHUB_ACTIONS", "GITHUB_STEP_SUMMARY")}

# --- Manifest fixtures ---------------------------------------------------------------------------

PERMISSIONS = [
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.GET_PACKAGE_SIZE",
    "android.permission.KILL_BACKGROUND_PROCESSES",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.ACCESS_NETWORK_STATE",
]

# One permission is written across several lines, the shape the manifest merger produces for
# elements carrying more than one attribute: the positive witness proves it is still READ.
MANIFEST_OK = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    package="com.filestech.appmanager">\n'
    + "".join(f'    <uses-permission android:name="{p}" />\n' for p in PERMISSIONS)
    + '    <uses-permission\n'
      '        android:name="com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />\n'
      '    <application android:label="App Manager Tech" />\n'
      '</manifest>\n'
)

INTERNET_ONE_LINE = '    <uses-permission android:name="android.permission.INTERNET" />\n'
INTERNET_MULTI_LINE = (
    '    <uses-permission\n'
    '        android:name="android.permission.INTERNET"\n'
    '        android:maxSdkVersion="99" />\n'
)
INTERNET_SDK_23 = '    <uses-permission-sdk-23 android:name="android.permission.INTERNET" />\n'
INTERNET_SDK_M = '    <uses-permission-sdk-m android:name="android.permission.INTERNET" />\n'


def with_before_application(manifest: str, extra: str) -> str:
    return manifest.replace("    <application", extra + "    <application", 1)


# --- Test report fixtures ------------------------------------------------------------------------

def report(name: str, tests: int, skipped: int = 0) -> str:
    cases = "".join(f'  <testcase name="t{i}" classname="{name}" time="0.001"/>\n' for i in range(tests))
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<testsuite name="{name}" tests="{tests}" skipped="{skipped}" failures="0" errors="0">\n'
        f"{cases}</testsuite>\n"
    )


REPORTS_OK = {
    "TEST-com.example.ATest.xml": report("com.example.ATest", 3),
    "TEST-com.example.BTest.xml": report("com.example.BTest", 2, skipped=1),
}


# --- Harness -------------------------------------------------------------------------------------

def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def run(script: str, target: str) -> tuple[int, str]:
    r = subprocess.run(
        [sys.executable, script, target],
        capture_output=True, text=True, encoding="utf-8", errors="replace", env=GUARD_ENV,
    )
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def manifest_case(work: str, content: str | None) -> str:
    """Writes the manifest (None = no file at all) and returns its path."""
    path = os.path.join(work, "manifest", "AndroidManifest.xml")
    shutil.rmtree(os.path.dirname(path), ignore_errors=True)
    if content is not None:
        write(path, content)
    return path


def results_case(work: str, reports: dict[str, str] | None) -> str:
    """Writes the reports (None = no directory at all) and returns the directory."""
    path = os.path.join(work, "test-results")
    shutil.rmtree(path, ignore_errors=True)
    if reports is not None:
        # Gradle always writes a `binary/` directory next to the reports: an empty run keeps it.
        os.makedirs(os.path.join(path, "binary"))
        for name, content in reports.items():
            write(os.path.join(path, name), content)
    return path


def main(argv: list[str]) -> int:
    real_manifest = None
    if len(argv) == 3 and argv[1] == "--real-manifest":
        real_manifest = argv[2]
    elif len(argv) != 1:
        print(__doc__)
        return 2

    for script in (MANIFEST_CHECK, TESTS_CHECK):
        if not os.path.isfile(script):
            print(f"ERROR: {script} not found.")
            return 2
    if real_manifest is not None and not os.path.isfile(real_manifest):
        # Asked for and absent is a failure, never a skip: a skipped case would pass for a green one.
        print(f"ERROR: --real-manifest {real_manifest} not found. Run :app:processReleaseMainManifest first.")
        return 1

    work = tempfile.mkdtemp(prefix="ci-guards-")
    network = "NETWORK permission in the merged release manifest"

    # (label, guard, setup returning the target, expected exit code, text the output must contain)
    cases = [
        ("manifest: POSITIVE WITNESS (multi-line element included)", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK), 0, "OK: no INTERNET"),
        ("manifest: INTERNET on one line", MANIFEST_CHECK,
         lambda: manifest_case(work, with_before_application(MANIFEST_OK, INTERNET_ONE_LINE)), 1, network),
        ("manifest: INTERNET across several lines", MANIFEST_CHECK,
         lambda: manifest_case(work, with_before_application(MANIFEST_OK, INTERNET_MULTI_LINE)), 1, network),
        ("manifest: INTERNET via uses-permission-sdk-23", MANIFEST_CHECK,
         lambda: manifest_case(work, with_before_application(MANIFEST_OK, INTERNET_SDK_23)), 1, network),
        ("manifest: INTERNET via uses-permission-sdk-m", MANIFEST_CHECK,
         lambda: manifest_case(work, with_before_application(MANIFEST_OK, INTERNET_SDK_M)), 1, network),
        ("manifest: debug variant (package .debug)", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK.replace(
             'package="com.filestech.appmanager"', 'package="com.filestech.appmanager.debug"')),
         1, "is not the release manifest of com.filestech.appmanager"),
        ("manifest: no package attribute", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK.replace('\n    package="com.filestech.appmanager"', "")),
         1, "is not the release manifest of com.filestech.appmanager"),
        ("manifest: permission outside the reviewed list", MANIFEST_CHECK,
         lambda: manifest_case(work, with_before_application(
             MANIFEST_OK, '    <uses-permission android:name="android.permission.READ_CONTACTS" />\n')),
         1, "outside the reviewed list"),
        ("manifest: no permission at all", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK.split("    <uses-permission")[0]
                               + '    <application android:label="x" />\n</manifest>\n'),
         1, "no permission found"),
        ("manifest: not an application manifest", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK.replace('    <application android:label="App Manager Tech" />\n', "")),
         1, "is not an application manifest"),
        ("manifest: file missing", MANIFEST_CHECK,
         lambda: manifest_case(work, None), 1, "merged manifest not found"),
        ("manifest: malformed XML", MANIFEST_CHECK,
         lambda: manifest_case(work, MANIFEST_OK.replace("</manifest>", "")), 1, "merged manifest unreadable"),

        ("tests: POSITIVE WITNESS (4 executed, 1 skipped)", TESTS_CHECK,
         lambda: results_case(work, REPORTS_OK), 0, "Tests executed: 4 (1 skipped, 2 reports)"),
        ("tests: no report at all (the JUnit 6 symptom)", TESTS_CHECK,
         lambda: results_case(work, {}), 1, "no TEST-*.xml report"),
        ("tests: directory missing", TESTS_CHECK,
         lambda: results_case(work, None), 1, "test results directory not found"),
        ("tests: suites that count zero tests", TESTS_CHECK,
         lambda: results_case(work, {"TEST-com.example.ATest.xml": report("com.example.ATest", 0)}),
         1, "0 tests executed in"),
        ("tests: every test skipped", TESTS_CHECK,
         lambda: results_case(work, {"TEST-com.example.ATest.xml": report("com.example.ATest", 3, skipped=3)}),
         1, "0 tests executed in"),
        ("tests: malformed report", TESTS_CHECK,
         lambda: results_case(work, {"TEST-com.example.ATest.xml": "<testsuite tests="}),
         1, "test report unreadable"),
        ("tests: not a JUnit report", TESTS_CHECK,
         lambda: results_case(work, {"TEST-com.example.ATest.xml": "<coverage/>\n"}),
         1, "no <testsuite> element"),
    ]

    if real_manifest is not None:
        with open(real_manifest, encoding="utf-8") as f:
            real = f.read()
        if "<application" not in real:
            print(f"ERROR: {real_manifest} has no <application> element to inject before.")
            shutil.rmtree(work, ignore_errors=True)
            return 1
        cases += [
            ("REAL manifest: POSITIVE WITNESS", MANIFEST_CHECK,
             lambda: manifest_case(work, real), 0, "OK: no INTERNET"),
            ("REAL manifest + INTERNET", MANIFEST_CHECK,
             lambda: manifest_case(work, real.replace("<application", INTERNET_ONE_LINE.strip() + "\n    <application", 1)),
             1, network),
        ]

    failures = []
    try:
        for label, guard, setup, expected_code, expected_text in cases:
            code, output = run(guard, setup())
            if code != expected_code:
                verdict = "NOT DETECTED" if expected_code else "REFUSED a healthy input"
                print(f"  FAIL  {label:58s} code={code} (expected {expected_code}) {verdict}")
                print("        " + output.strip().replace("\n", "\n        "))
                failures.append(label)
            elif expected_text not in output:
                print(f"  FAIL  {label:58s} right code, but for ANOTHER reason")
                print(f"        expected: {expected_text}")
                print("        " + output.strip().replace("\n", "\n        "))
                failures.append(label)
            else:
                print(f"  OK    {label:58s} {'passes' if expected_code == 0 else 'detected, right cause'}")
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    if failures:
        print(f"NEGATIVE CONTROL: {len(cases) - len(failures)}/{len(cases)} -- FAILED: {', '.join(failures)}")
        return 1
    print(f"NEGATIVE CONTROL: {len(cases)}/{len(cases)} -- every defect is detected, and for the right reason.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
