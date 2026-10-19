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


def problems_of(*findings, static_findings=()):
    groups = report.group_repeated_queries(list(findings))
    report.link_static_confirmations(groups, list(static_findings))
    return report.build_problems(groups)


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

    def test_only_problems_beyond_the_top_ones_are_rows(self):
        findings = [repeated(callerMethod=f"Service{i}.run", executions=50 - i) for i in range(report.TOP_PROBLEMS + 3)]
        groups = report.group_repeated_queries(findings)
        report.link_static_confirmations(groups, [])
        page = report.build_html_report({"generatedAt": "2026-10-08T00:00:00Z", "thresholdMs": 100, "slowQueries": []}, groups, [], None)
        self.assertEqual(page.count('<article class="problem '), report.TOP_PROBLEMS)
        self.assertEqual(page.count('<tr class="problem"'), 3)


if __name__ == "__main__":
    unittest.main()
