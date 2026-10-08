#!/usr/bin/env python3
"""Fail when the unit-test run executed ZERO tests.

Why this exists
---------------
A green `testDebugUnitTest` does not prove that a single test ran. When the JUnit Platform no longer
discovers the tests, Gradle reports success on an empty run. This was measured on a sibling app of
the portfolio (Notes Tech): after a JUnit 6 bump, no test was discovered any more and the CI stayed
green. Dependabot proposes that very bump here (JUnit 5.11.3 -> 6.x).

The JUnit XML reports are the evidence: one `TEST-*.xml` per executed class, each carrying `tests`
and `skipped` counts. This script sums them and fails when nothing was executed.

It prints the total, and under GitHub Actions writes it to the job summary, so the number of tests
executed by two runs (for instance before and after a dependency bump) can be compared at a glance.

Usage
-----
    python3 tools/check-test-results.py [results-directory]

Run it right after `testDebugUnitTest`: on a developer machine, a directory left over from an earlier
run would be read as this run's.

Exit code 0 when at least one test was executed, 1 otherwise.
"""

from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEFAULT_RESULTS = os.path.join("app", "build", "test-results", "testDebugUnitTest")

IN_CI = os.environ.get("GITHUB_ACTIONS") == "true"


def fail(lines: list[str]) -> None:
    prefix = "::error::" if IN_CI else "ERROR: "
    for line in lines:
        print(f"{prefix}{line}")


def count(value: str | None) -> int:
    return int(value) if value else 0


def main(argv: list[str]) -> int:
    results_dir = argv[1] if len(argv) > 1 else DEFAULT_RESULTS

    if not os.path.isdir(results_dir):
        fail([
            f"test results directory not found: {results_dir}",
            "run first: ./gradlew testDebugUnitTest",
        ])
        return 1

    reports = sorted(glob.glob(os.path.join(results_dir, "TEST-*.xml")))
    if not reports:
        # The JUnit 6 symptom: the task runs, discovers nothing, and writes no report at all.
        fail([
            f"no TEST-*.xml report in {results_dir}: 0 tests executed.",
            "The test task succeeded without running anything - a test engine that no longer "
            "discovers the tests looks exactly like this.",
        ])
        return 1

    total = skipped = 0
    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError as error:
            fail([f"test report unreadable ({report}): {error}"])
            return 1
        suites = list(root.iter("testsuite"))
        if not suites:
            fail([f"no <testsuite> element in {report}: not a JUnit XML report."])
            return 1
        for suite in suites:
            tests = count(suite.get("tests"))
            ignored = count(suite.get("skipped"))
            total += tests
            skipped += ignored
            print(f"  {tests - ignored:4d} executed  {ignored:3d} skipped  {suite.get('name')}")

    executed = total - skipped
    summary = f"Unit tests executed: {executed} ({skipped} skipped, {len(reports)} reports)"
    print(summary)

    if IN_CI and os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
            out.write(f"**{summary}**\n")

    if executed == 0:
        fail([
            f"0 tests executed in {results_dir}: every test was skipped or no suite counted any.",
        ])
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
