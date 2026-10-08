"""Tests for the grouping, static confirmation and baseline comparison of the E2E query report.

Run: python3 supporting_scripts/test_format_slow_query_report.py
"""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import format_slow_query_report as report  # noqa: E402


def repeated(test="A.spec", executions=3, **overrides):
    finding = {"type": "N_PLUS_ONE", "normalizedSql": "select * from result where participation_id=?", "executions": executions, "distinctParameterSets": executions,
               "repositoryMethod": "ResultRepository.findByParticipationId", "callerMethod": "ResultService.loadAll", "httpMethod": "GET",
               "httpEndpoint": "/api/assessment/exercises/{exerciseId}/results", "testName": test, "phase": "action"}
    finding.update(overrides)
    return finding


LOOP_FINDING = {"type": "repository_call_in_loop", "key": "repository_call_in_loop:ResultService.loadAll:ResultRepository.findByParticipationId",
                "member": "ResultService.loadAll", "repositoryMethod": "ResultRepository.findByParticipationId", "detail": "called once per iteration (for)"}
OTHER_FINDING = {"type": "eager_to_many", "key": "eager_to_many:Post.answers", "member": "Post.answers", "detail": "EAGER"}


class GroupingTest(unittest.TestCase):

    def test_same_shape_in_different_tests_and_counts_is_one_group(self):
        groups = report.group_repeated_queries([repeated("A.spec", 3), repeated("B.spec", 40)])
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0]["max_executions"], 40)
        self.assertEqual(groups[0]["tests"], {"A.spec", "B.spec"})

    def test_shape_endpoint_and_issuing_code_each_separate_groups(self):
        groups = report.group_repeated_queries([repeated(), repeated(type="DUPLICATE"), repeated(httpEndpoint="/api/other"), repeated(repositoryMethod="ResultRepository.other")])
        self.assertEqual(len(groups), 4)


class StaticConfirmationTest(unittest.TestCase):

    def test_loop_finding_for_the_same_caller_and_repository_method_confirms(self):
        groups = report.group_repeated_queries([repeated(), repeated(callerMethod="OtherService.run")])
        report.link_static_confirmations(groups, [OTHER_FINDING, LOOP_FINDING])
        matches = {g["callerMethod"]: g["static_matches"] for g in groups}
        self.assertEqual(matches, {"ResultService.loadAll": [1], "OtherService.run": []})

    def test_query_shape_finding_on_the_repository_method_confirms(self):
        groups = report.group_repeated_queries([repeated()])
        shape = {"type": "multiple_collection_fetch", "key": "k", "member": "ResultRepository.findByParticipationId"}
        report.link_static_confirmations(groups, [shape])
        self.assertEqual(groups[0]["static_matches"], [0])


class BaselineTest(unittest.TestCase):

    def test_only_keys_missing_from_the_baseline_are_new(self):
        old, new = repeated(), repeated(type="DUPLICATE")
        baseline_groups = report.group_repeated_queries([old])
        baseline = report.collect_keys(baseline_groups, [OTHER_FINDING])

        groups = report.group_repeated_queries([old, new])
        static_findings = [dict(OTHER_FINDING), dict(LOOP_FINDING)]
        report.mark_new(groups, static_findings, baseline)
        self.assertEqual({g["type"]: g["is_new"] for g in groups}, {"N_PLUS_ONE": False, "DUPLICATE": True})
        self.assertEqual([f["is_new"] for f in static_findings], [False, True])

    def test_without_baseline_nothing_is_new_and_the_comment_says_so(self):
        groups = report.group_repeated_queries([repeated()])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], None)
        self.assertFalse(any(g.get("is_new") for g in groups))
        markdown = report.build_report({"generatedAt": "2026-10-08T00:00:00Z"}, groups, [], None)
        self.assertIn("No develop baseline", markdown)

    def test_comment_lists_only_new_findings(self):
        old, new = repeated(), repeated(type="DUPLICATE", httpEndpoint="/api/new")
        baseline = report.collect_keys(report.group_repeated_queries([old]), [])
        groups = report.group_repeated_queries([old, new])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], baseline)
        markdown = report.build_report({"generatedAt": "2026-10-08T00:00:00Z"}, groups, [], baseline)
        self.assertIn("/api/new", markdown)
        self.assertNotIn("/api/assessment/exercises/{exerciseId}/results` |", markdown)

    def test_static_findings_without_own_key_get_a_stable_derived_one(self):
        finding = {"type": "wide_join_fetch", "file": "./src/main/java/de/x/ExamRepository.java", "snippet": "SELECT e FROM Exam e"}
        self.assertEqual(report.static_key(finding), report.static_key(dict(finding)))
        self.assertTrue(report.static_key(finding).startswith("wide_join_fetch:ExamRepository.java:"))


class HtmlTest(unittest.TestCase):

    def test_html_report_renders_every_section(self):
        groups = report.group_repeated_queries([repeated()])
        report.link_static_confirmations(groups, [LOOP_FINDING])
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, [LOOP_FINDING], None)
        for fragment in ('id="repeated-queries-table"', 'id="static-findings-table"', 'data-finding-indices="0"', "static rule agrees"):
            self.assertIn(fragment, page)


if __name__ == "__main__":
    unittest.main()
