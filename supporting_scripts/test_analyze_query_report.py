"""Tests for analyze_query_report.sh: which files a CI run leaves behind, in particular that a run which cannot be
compared completely publishes no baseline keys and no inline comments.

Run: python3 supporting_scripts/test_analyze_query_report.py
"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPTS = Path(__file__).resolve().parent
SERVER_REPORT = SCRIPTS / "tests" / "fixtures" / "query-report.json"
OUTPUTS = ("query-report.html", "query-summary.md", "query-keys.json", "query-inline-comments.json")


class AnalyzeQueryReportTest(unittest.TestCase):

    def setUp(self):
        """A repository with only what the script reads: the tools, an empty source tree, a report and a baseline."""
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "supporting_scripts").mkdir()
        for name in ("analyze_query_report.sh", "format_query_report.py", "find_slow_queries.py"):
            shutil.copy(SCRIPTS / name, self.root / "supporting_scripts" / name)
        (self.root / "src" / "main" / "java").mkdir(parents=True)
        self.report = self.root / "src" / "test" / "playwright" / "test-reports" / "query-report.json"
        self.report.parent.mkdir(parents=True)
        shutil.copy(SERVER_REPORT, self.report)
        with zipfile.ZipFile(self.root / "query-baseline.zip", "w") as baseline:
            baseline.writestr("query-keys.json", json.dumps({"repeatedQueries": [], "static": []}))
        # the script calls python3, which is not the name of the interpreter everywhere
        (self.root / "bin").mkdir()
        shim = self.root / "bin" / "python3"
        shim.write_text(f'#!/bin/sh\nexec "{Path(sys.executable).as_posix()}" "$@"\n', encoding="utf-8", newline="\n")
        shim.chmod(0o755)

    def run_script(self, **variables):
        environment = {name: value for name, value in os.environ.items() if not name.startswith(("GITHUB_", "E2E_", "PLAYWRIGHT_"))}
        environment.update(PATH=f"{self.root / 'bin'}{os.pathsep}{environment['PATH']}", PYTHONUTF8="1", **variables)
        result = subprocess.run([shutil.which("bash"), "supporting_scripts/analyze_query_report.sh"], cwd=self.root, env=environment,
                                capture_output=True, encoding="utf-8")
        self.assertEqual(result.returncode, 0, result.stderr)

    def written(self):
        return {name for name in OUTPUTS if (self.root / name).exists()}

    def summary(self):
        return (self.root / "query-summary.md").read_text(encoding="utf-8")

    def test_a_successful_run_writes_the_report_the_keys_and_the_inline_comments(self):
        self.run_script(E2E_TESTS_OUTCOME="success")
        self.assertEqual(self.written(), set(OUTPUTS))
        self.assertIn("new repeated-query problem", self.summary())
        comments = json.loads((self.root / "query-inline-comments.json").read_text(encoding="utf-8"))
        self.assertEqual([c["line"] for c in comments], [142])

    def test_failed_tests_keep_the_report_but_publish_no_keys_and_no_inline_comments(self):
        self.run_script(E2E_TESTS_OUTCOME="failure")
        self.assertEqual(self.written(), {"query-report.html", "query-summary.md"})
        self.assertIn("Incomplete comparison", self.summary())

    def test_a_ci_run_without_a_test_outcome_counts_as_incomplete(self):
        self.run_script(GITHUB_ACTIONS="true")
        self.assertEqual(self.written(), {"query-report.html", "query-summary.md"})

    def test_a_failed_static_scan_makes_the_run_incomplete(self):
        # it fails while scanning; the formatter still imports the rule names from it
        broken_scan = 'RULE_LABELS = {}\nif __name__ == "__main__":\n    raise SystemExit(1)\n'
        (self.root / "supporting_scripts" / "find_slow_queries.py").write_text(broken_scan, encoding="utf-8")
        self.run_script(E2E_TESTS_OUTCOME="success")
        self.assertEqual(self.written(), {"query-report.html", "query-summary.md"})
        self.assertIn("the static scan is unavailable", self.summary())

    def test_without_a_report_nothing_of_an_earlier_run_survives(self):
        self.report.unlink()
        (self.root / "query-keys.json").write_text("{}", encoding="utf-8")
        (self.root / "query-inline-comments.json").write_text("[]", encoding="utf-8")
        self.run_script(E2E_TESTS_OUTCOME="success")
        self.assertEqual(self.written(), {"query-summary.md"})
        self.assertIn("Query Report Unavailable", self.summary())


if __name__ == "__main__":
    unittest.main()
