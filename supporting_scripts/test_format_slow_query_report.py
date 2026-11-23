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

    def test_query_shape_finding_on_the_same_repository_method_does_not_confirm(self):
        groups = report.group_repeated_queries([repeated()])
        shape = {"type": "multiple_collection_fetch", "key": "k", "member": "ResultRepository.findByParticipationId"}
        report.link_static_confirmations(groups, [shape])
        self.assertEqual(groups[0]["static_matches"], [])


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

    def test_without_baseline_nothing_is_new_and_the_comment_is_one_line(self):
        groups = report.group_repeated_queries([repeated()])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], None)
        self.assertFalse(any(g.get("is_new") for g in groups))
        markdown = report.build_report({"generatedAt": "2026-10-08T00:00:00Z"}, groups, [], None, "https://ci/run/1")
        self.assertIn("no develop baseline", markdown)
        self.assertIn("[HTML report artifact](https://ci/run/1)", markdown)
        self.assertNotIn("/api/assessment", markdown)

    def test_without_anything_new_the_comment_is_one_line(self):
        groups = report.group_repeated_queries([repeated()])
        baseline = report.collect_keys(groups, [])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], baseline)
        markdown = report.build_report({"generatedAt": "2026-10-08T00:00:00Z"}, groups, [], baseline, "https://ci/run/1", "phase1")
        self.assertIn("**✅ Query Report (Phase 1):** no new query anti-patterns", markdown)
        self.assertIn("[HTML report artifact](https://ci/run/1)", markdown)
        self.assertNotIn("<details>", markdown)

    def test_comment_lists_only_new_findings(self):
        old, new = repeated(), repeated(type="DUPLICATE", httpEndpoint="/api/new")
        baseline = report.collect_keys(report.group_repeated_queries([old]), [])
        groups = report.group_repeated_queries([old, new])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], baseline)
        markdown = report.build_report({"generatedAt": "2026-10-08T00:00:00Z"}, groups, [], baseline, phase="all")
        self.assertIn("## ⚠️ Query Report (all tests)", markdown)
        self.assertIn("/api/new", markdown)
        self.assertNotIn("/api/assessment/exercises/{exerciseId}/results` |", markdown)

    def test_static_findings_without_own_key_get_a_stable_derived_one(self):
        finding = {"type": "wide_join_fetch", "file": "./src/main/java/de/x/ExamRepository.java", "snippet": "SELECT e FROM Exam e"}
        self.assertEqual(report.static_key(finding), report.static_key(dict(finding)))
        self.assertTrue(report.static_key(finding).startswith("wide_join_fetch:ExamRepository.java:"))


def problems_of(*findings, static_findings=()):
    groups = report.group_repeated_queries(list(findings))
    report.link_static_confirmations(groups, list(static_findings))
    return report.build_problems(groups)


class InlineCommentTest(unittest.TestCase):

    LOCATION = {"sourceFile": "src/main/java/de/tum/CourseService.java", "line": 121}

    def comments(self, findings, static=(), baseline_findings=()):
        baseline = report.collect_keys(report.group_repeated_queries(list(baseline_findings)), [])
        groups = report.group_repeated_queries(findings)
        static = [dict(f) for f in static]
        report.link_static_confirmations(groups, static)
        report.mark_new(groups, static, baseline)
        return report.build_inline_comments({}, groups, static, baseline, "https://example/run")

    def test_a_new_problem_is_anchored_to_the_line_that_issues_it(self):
        [comment] = self.comments([repeated(**self.LOCATION)])
        self.assertEqual((comment["path"], comment["line"]), ("src/main/java/de/tum/CourseService.java", 121))
        self.assertIn("N+1", comment["body"])
        self.assertIn(report.INLINE_MARKER.format(key=comment["key"]), comment["body"])

    def test_existing_problems_and_problems_without_a_line_get_no_comment(self):
        self.assertEqual(self.comments([repeated(**self.LOCATION)], baseline_findings=[repeated(**self.LOCATION)]), [])
        self.assertEqual(self.comments([repeated()]), [])

    def test_without_a_baseline_no_inline_file_is_written(self):
        # an empty file would read as "everything is fixed" and delete the comments of an earlier run
        import json
        import os
        import tempfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as tmp:
            source, inline = os.path.join(tmp, "report.json"), os.path.join(tmp, "inline.json")
            with open(source, "w", encoding="utf-8") as f:
                json.dump({"repeatedQueries": [repeated(**self.LOCATION)]}, f)
            for extra, expected in (([], False), (["--baseline", os.path.join(tmp, "missing.json")], False)):
                with mock.patch.object(sys, "argv", ["x", source, "--inline-out", inline, *extra]), mock.patch("builtins.print"):
                    report.main()
                self.assertEqual(os.path.exists(inline), expected)

    def test_the_line_does_not_change_the_key(self):
        first = self.comments([repeated(**self.LOCATION)])[0]["key"]
        moved = self.comments([repeated(sourceFile=self.LOCATION["sourceFile"], line=130)])[0]["key"]
        self.assertEqual(first, moved)

    def test_without_a_baseline_nothing_is_new(self):
        groups = report.group_repeated_queries([repeated(**self.LOCATION)])
        self.assertEqual(report.build_inline_comments({}, groups, [], None), [])

    def test_a_new_static_finding_with_a_line_is_commented_and_a_dot_slash_path_is_normalized(self):
        static = {"type": "repository_call_in_loop", "key": "k", "file": "./src/main/java/A.java", "line": 7, "member": "A.m", "repositoryMethod": "R.f", "detail": "called once per iteration"}
        [comment] = self.comments([], static=[static], baseline_findings=[])
        self.assertEqual((comment["path"], comment["line"]), ("src/main/java/A.java", 7))

    LOOP = dict(LOOP_FINDING, file="./src/main/java/de/tum/CourseService.java", line=120)

    def test_a_confirming_static_finding_is_part_of_the_comment_of_its_problem(self):
        [comment] = self.comments([repeated(**self.LOCATION)], static=[self.LOOP], baseline_findings=[])
        self.assertEqual((comment["path"], comment["line"]), ("src/main/java/de/tum/CourseService.java", 121))
        self.assertIn("Static check", comment["body"])
        self.assertIn("called once per iteration", comment["body"])
        self.assertEqual(comment["fallback"], {"path": "src/main/java/de/tum/CourseService.java", "line": 120})

    def test_the_comment_keeps_the_key_of_the_problem_whether_or_not_a_static_finding_confirms_it(self):
        alone = self.comments([repeated(**self.LOCATION)])[0]["key"]
        merged = self.comments([repeated(**self.LOCATION)], static=[self.LOOP])[0]["key"]
        self.assertEqual(alone, merged)

    def test_a_confirming_static_finding_that_is_not_new_still_explains_the_problem(self):
        static = [dict(self.LOOP)]
        baseline_static = {"repeatedQueries": [], "static": [report.static_key(self.LOOP)]}
        groups = report.group_repeated_queries([repeated(**self.LOCATION)])
        report.link_static_confirmations(groups, static)
        report.mark_new(groups, static, baseline_static)
        [comment] = report.build_inline_comments({}, groups, static, baseline_static)
        self.assertIn("Static check", comment["body"])

    def test_a_new_static_finding_on_the_same_line_joins_the_comment_instead_of_stacking(self):
        other = {"type": "eager_to_many", "key": "eager_to_many:X.y", "file": "src/main/java/de/tum/CourseService.java", "line": 121, "member": "X.y", "detail": "EAGER"}
        [comment] = self.comments([repeated(**self.LOCATION)], static=[other])
        self.assertIn("EAGER", comment["body"])
        self.assertEqual(comment.get("fallback"), None)

    def test_the_problem_and_its_static_finding_on_one_line_are_one_comment_without_fallback(self):
        loop = dict(self.LOOP, line=121)
        comments = self.comments([repeated(**self.LOCATION)], static=[loop])
        self.assertEqual(len(comments), 1)
        self.assertNotIn("fallback", comments[0])

    def test_inserts_have_no_single_line_and_stay_in_the_summary(self):
        insert = repeated(normalizedSql="insert into result (id) values (?)", **self.LOCATION)
        self.assertEqual(self.comments([insert]), [])


class ProblemTest(unittest.TestCase):

    def test_queries_of_one_caller_form_one_problem_at_the_caller(self):
        problems = problems_of(repeated(), repeated(repositoryMethod="SubmissionRepository.findByParticipationId", normalizedSql="select * from submission where participation_id=?"))
        self.assertEqual(len(problems), 1)
        self.assertEqual((problems[0]["kind"], problems[0]["anchor"], problems[0]["category"]), ("function", "ResultService.loadAll", "n_plus_one"))
        self.assertEqual(len(problems[0]["queries"]), 2)

    def test_duplicate_across_several_places_is_a_problem_of_its_endpoint_listing_them(self):
        sites = [{"repositoryMethod": "UserRepository.findById", "callerMethod": "A.run"}, {"repositoryMethod": "UserRepository.findById", "callerMethod": "B.run"}]
        problems = problems_of(repeated(type="DUPLICATE", repositoryMethod=None, callerMethod=None, callSites=sites),
                               repeated(type="DUPLICATE", repositoryMethod=None, callerMethod=None, callSites=[{"callerMethod": "C.run"}], test="B.spec"))
        self.assertEqual(len(problems), 1)
        self.assertEqual((problems[0]["kind"], problems[0]["category"]), ("endpoint", "duplicate_several_places"))
        self.assertEqual(problems[0]["anchor"], "GET /api/assessment/exercises/{exerciseId}/results")
        self.assertEqual(problems[0]["queries"][0]["call_sites"], {"UserRepository.findById ← A.run", "UserRepository.findById ← B.run", "C.run"})

    def test_inserts_are_grouped_by_table(self):
        insert = "insert into plagiarism_submission_element (column,file,line) values (?,?,?)"
        problems = problems_of(repeated(normalizedSql=insert, repositoryMethod="PlagiarismResultRepository.save", callerMethod="PlagiarismService.run"),
                               repeated(normalizedSql=insert, repositoryMethod="PlagiarismResultRepository.save", callerMethod="OtherService.run"))
        self.assertEqual(len(problems), 1)
        self.assertEqual((problems[0]["category"], problems[0]["anchor"], len(problems[0]["queries"])), ("insert", "plagiarism_submission_element", 2))

    def test_avoidable_executions_rank_by_the_biggest_single_repeat(self):
        cascade = [repeated(callerMethod="DeletionService.delete", normalizedSql=f"delete from t{i} where id=?", executions=6) for i in range(5)]
        loop = repeated(executions=12)
        problems = problems_of(*cascade, loop)
        self.assertEqual([(p["anchor"], p["avoidable"]) for p in problems], [("ResultService.loadAll", 11), ("DeletionService.delete", 5)])

    def test_a_duplicate_can_save_all_but_one_execution_per_distinct_parameter_set(self):
        self.assertEqual(report.avoidable_executions("DUPLICATE", 100, 2), 98)
        self.assertEqual(report.avoidable_executions("N_PLUS_ONE", 100, 2), 99)

    def test_the_query_that_ranks_a_problem_also_labels_it(self):
        # an N+1 with few repeats and a duplicate with many, in one method: the duplicate decides the rank, so also the label
        problems = problems_of(repeated(executions=3),
                               repeated(type="DUPLICATE", normalizedSql="select * from result where 1=0", executions=20, distinctParameterSets=1))
        self.assertEqual((problems[0]["category"], problems[0]["avoidable"]), ("duplicate_one_place", 19))
        self.assertEqual([q["shape"] for q in problems[0]["queries"]], ["DUPLICATE", "N_PLUS_ONE"])

    def test_a_problem_is_new_when_any_of_its_queries_is(self):
        old, new = repeated(), repeated(repositoryMethod="SubmissionRepository.findAll")
        groups = report.group_repeated_queries([old, new])
        report.link_static_confirmations(groups, [])
        report.mark_new(groups, [], report.collect_keys(report.group_repeated_queries([old]), []))
        self.assertTrue(report.build_problems(groups)[0]["is_new"])

    def test_sql_summary_names_table_joins_and_condition(self):
        sql = "select u1_0.id,a1_0.name from jhi_user u1_0 left join jhi_user_authority a1_0 on u1_0.id=a1_0.user_id where u1_0.id= ?"
        self.assertEqual(report.summarize_sql(sql), "select from jhi_user (+1 join) where id = ?")
        self.assertEqual(report.summarize_sql("insert into result (id,score) values (?,?)"), "insert into result")


INDEXES = {
    "saved_post": [{"name": "pk_saved_post", "columns": ["id"], "unique": True},
                   {"name": "idx_saved_post_user_id_post_id_post_type", "columns": ["user_id", "post_id", "post_type"], "unique": True}],
    "user_course_notification_status": [{"name": "pk_user_course_notification_status", "columns": ["course_notification_id", "user_id"], "unique": True}],
    "course_notification": [{"name": "pk_course_notification", "columns": ["id"], "unique": True}],
    "exercise": [{"name": "pk_exercise", "columns": ["id"], "unique": True}],
    "participation": [{"name": "fk_participation_exercise_id", "columns": ["exercise_id"], "unique": False}],
}


class IndexLookupTest(unittest.TestCase):

    def verdict(self, sql):
        return report.index_lookup(sql, INDEXES)

    def test_a_filter_on_the_leading_columns_of_an_index_is_indexed(self):
        lookup = self.verdict("select sp1_0.post_id from saved_post sp1_0 where sp1_0.user_id= ? and sp1_0.post_type= ?")
        self.assertEqual((lookup["verdict"], lookup["index"], lookup["columns"]), ("indexed", "idx_saved_post_user_id_post_id_post_type", ["user_id"]))

    def test_a_filter_on_a_later_column_of_a_composite_key_is_a_scan(self):
        sql = ("select count(*) from user_course_notification_status ucns1_0 join course_notification cn1_0 on cn1_0.id=ucns1_0.course_notification_id "
               "where ucns1_0.user_id= ? and cn1_0.course_id= ? and ucns1_0.status= ?")
        lookup = self.verdict(sql)
        self.assertEqual((lookup["verdict"], lookup["table"], lookup["columns"]), ("scan", "user_course_notification_status", ["status", "user_id"]))

    def test_statements_without_an_alias_and_wrapped_in_exists_are_judged(self):
        self.assertEqual(self.verdict("delete from saved_post where user_id= ?")["verdict"], "indexed")
        self.assertEqual(self.verdict("select exists ( select sp1_0.id from saved_post sp1_0 where sp1_0.id= ? )")["verdict"], "indexed")

    def test_the_inheritance_subselect_reads_its_table(self):
        sql = "select p1_0.id from participation p1_0 left join ( select * from exercise t where t.discriminator IN ( ? )) e1_0 on e1_0.id=p1_0.exercise_id where e1_0.id= ?"
        self.assertEqual(self.verdict(sql)["index"], "pk_exercise")

    def test_shapes_it_cannot_judge_are_not_checked(self):
        self.assertEqual(self.verdict("select * from saved_post sp1_0 where sp1_0.user_id= ? or sp1_0.post_id= ?"), {"verdict": "unknown", "reason": "OR condition"})
        self.assertEqual(self.verdict("select * from saved_post sp1_0 where lower(sp1_0.post_type)= ?")["verdict"], "unknown")
        self.assertEqual(self.verdict("insert into saved_post (user_id) values (?)"), {"verdict": "n/a"})

    def test_a_select_without_a_filter_reads_the_whole_table(self):
        self.assertEqual(self.verdict("select e1_0.id from exercise e1_0")["reason"], "no filter")

    def test_a_problem_with_an_unindexed_query_ranks_first_among_equally_avoidable_ones(self):
        indexed = repeated(callerMethod="A.run", normalizedSql="select * from saved_post sp1_0 where sp1_0.user_id= ?")
        scan = repeated(callerMethod="B.run", normalizedSql="select * from user_course_notification_status u1_0 where u1_0.user_id= ?")
        groups = report.group_repeated_queries([indexed, scan])
        report.link_static_confirmations(groups, [])
        problems = report.build_problems(groups, INDEXES)
        self.assertEqual([(p["anchor"], p["index_state"]) for p in problems], [("B.run", "scan"), ("A.run", "indexed")])
        self.assertEqual({p["index_state"] for p in report.build_problems(groups)}, {""})


class HtmlTest(unittest.TestCase):

    def test_html_report_renders_every_section(self):
        groups = report.group_repeated_queries([repeated(), repeated(normalizedSql="insert into result (id) values (?)")])
        report.link_static_confirmations(groups, [LOOP_FINDING])
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, [LOOP_FINDING], None)
        for fragment in ('class="problem problem-card cat-n_plus_one"', "Row-by-row inserts", 'id="static-findings-table"', 'data-finding-indices="0"', "static rule agrees"):
            self.assertIn(fragment, page)

    def test_with_a_baseline_the_report_opens_on_the_new_findings(self):
        old, new = repeated(), repeated(callerMethod="NewService.loop")
        baseline = report.collect_keys(report.group_repeated_queries([old]), [OTHER_FINDING])
        groups = report.group_repeated_queries([old, new])
        static_findings = [dict(OTHER_FINDING), dict(LOOP_FINDING)]
        report.link_static_confirmations(groups, static_findings)
        report.mark_new(groups, static_findings, baseline)
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, static_findings, baseline, phase="phase1")
        self.assertIn("<title>Query Report (Phase 1)</title>", page)
        self.assertIn('aria-selected="true" aria-controls="panel-new">🆕 New in this PR <span class="tab-count">2</span>', page)
        new_panel = page[page.index('id="panel-new"'):page.index('id="panel-repeated-queries"')]
        self.assertIn("NewService.loop", new_panel)
        self.assertNotIn("<code>ResultService.loadAll</code>", new_panel)
        self.assertIn("Repository call in a loop", new_panel)
        self.assertEqual(page.count('id="static-finding-1"'), 1)

    def test_without_a_baseline_there_is_no_new_tab(self):
        groups = report.group_repeated_queries([repeated()])
        report.link_static_confirmations(groups, [])
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, [], None)
        self.assertNotIn("panel-new", page)
        self.assertIn('aria-selected="true" aria-controls="panel-repeated-queries"', page)

    def test_only_problems_beyond_the_top_ones_are_rows(self):
        findings = [repeated(callerMethod=f"Service{i}.run", executions=50 - i) for i in range(report.TOP_PROBLEMS + 3)]
        groups = report.group_repeated_queries(findings)
        report.link_static_confirmations(groups, [])
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, [], None)
        self.assertEqual(page.count('<article class="problem '), report.TOP_PROBLEMS)
        self.assertEqual(page.count('<tr class="problem"'), 3)

    def test_a_duplicate_from_many_places_folds_them_one_per_line(self):
        sites = [{"repositoryMethod": f"Repository{i}.load", "callerMethod": f"Service{i}.delete"} for i in range(5)]
        finding = repeated(type="DUPLICATE", repositoryMethod=None, callerMethod=None, distinctParameterSets=1, callSites=sites)
        problem = report.build_problems(report.group_repeated_queries([finding]))[0]
        item = report.query_item_html(problem["queries"][0], problem)
        self.assertIn('<details class="query-source"><summary>loaded by 5 places</summary><span>Repository0.load ← Service0.delete</span>', item)
        self.assertEqual(item.count("<span>Repository"), 5)

    def test_requests_of_one_test_share_a_row(self):
        requests = [
            {"endpoint": "GET /api/courses", "test": "A", "phase": "action", "executions": 26, "distinct": 26},
            {"endpoint": "GET /api/courses", "test": "B", "phase": "action", "executions": 26, "distinct": 26},
            {"endpoint": "GET /api/courses", "test": "A", "phase": "action", "executions": 25, "distinct": 25},
            {"endpoint": "GET /api/courses", "test": "A", "phase": "setup", "executions": 25, "distinct": 25},
        ]
        rows = report.requests_per_test(requests)
        self.assertEqual([(r["test"], r["phase"], r["executions"]) for r in rows], [("A", "action", 26), ("B", "action", 26), ("A", "setup", 25)])


if __name__ == "__main__":
    unittest.main()
