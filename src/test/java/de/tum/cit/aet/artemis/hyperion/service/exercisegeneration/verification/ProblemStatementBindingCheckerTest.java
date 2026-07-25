package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class ProblemStatementBindingCheckerTest {

    @Test
    void canonicalArtemisBubbleSortStatementUsesResolvableProductionTaskBindings() throws IOException {
        String statement = new ClassPathResource("templates/java/maven_maven/readme").getContentAsString(StandardCharsets.UTF_8);
        List<String> testNames = List.of("testBubbleSort", "testMergeSort", "testClass[SortStrategy]", "testMethods[SortStrategy]", "testAttributes[Context]",
                "testMethods[Context]", "testConstructors[Policy]", "testAttributes[Policy]", "testMethods[Policy]", "testClass[MergeSort]", "testUseMergeSortForBigList",
                "testClass[BubbleSort]", "testUseBubbleSortForSmallList");

        assertThat(ProblemStatementBindingChecker.boundTestNames(statement)).containsExactlyElementsOf(testNames);
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings(statement, testNames, testNames.size(), Set.of())).isEmpty();
    }

    @Test
    void hasTaskBindings_trueWhenAWellFormedBindingIsPresent() {
        assertThat(ProblemStatementBindingChecker.hasTaskBindings("# Sort\n[task][Sort an array](testSorts)\n")).isTrue();
    }

    @Test
    void hasTaskBindings_falseWhenNoBindingIsPresent() {
        assertThat(ProblemStatementBindingChecker.hasTaskBindings("# Sort\nImplement the sort method.\n")).isFalse();
    }

    @Test
    void hasTaskBindings_falseForABareTaskMarkerThatIsNotABinding() {
        assertThat(ProblemStatementBindingChecker.hasTaskBindings("## [tasks]\nImplement the sort method.\n")).isFalse();
    }

    @Test
    void boundTestNames_parsesTrimmedNamesInOrderPreservingDuplicates() {
        String statement = "[task][A]( testA , testB )\n[task][B](testA)\n";
        assertThat(ProblemStatementBindingChecker.boundTestNames(statement)).containsExactly("testA", "testB", "testA");
    }

    @Test
    void boundTestNames_capturesTrailingParenthesesOnTheNamesThemselves() {
        // The greedy capture keeps the reported testFoo() form; normalization collapses it when matching.
        assertThat(ProblemStatementBindingChecker.boundTestNames("[task][Sort](testBubbleSort(),testMergeSort())\n")).containsExactly("testBubbleSort()", "testMergeSort()");
    }

    @Test
    void seamTaskGrouping_acceptsOneTaskPerStudentWorkSeam() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"testTypical","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testBoundary","seam":"S1","seamWeightTier":2,"visibility":"ALWAYS"},
                  {"name":"testDelegates","seam":"S2","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testHidden","seam":"S2","seamWeightTier":1,"visibility":"AFTER_DUE_DATE"}
                ]}
                """);
        String statement = "[task][Compute values](testTypical,testBoundary)\n[task][Delegate](testDelegates)\n";

        assertThat(ProblemStatementBindingChecker.seamTaskGroupingReasons(statement, plan)).isEmpty();
    }

    @Test
    void seamTaskGrouping_rejectsTestShapedTasksAndMixedSeams() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"testTypical","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testBoundary","seam":"S1","seamWeightTier":2,"visibility":"ALWAYS"},
                  {"name":"testDelegates","seam":"S2","seamWeightTier":3,"visibility":"ALWAYS"}
                ]}
                """);

        assertThat(ProblemStatementBindingChecker.seamTaskGroupingReasons("[task][Typical](testTypical)\n[task][Boundary and delegation](testBoundary,testDelegates)\n", plan))
                .anySatisfy(reason -> assertThat(reason).contains("S1", "split")).anySatisfy(reason -> assertThat(reason).contains("mixes", "S1", "S2"));
    }

    @Test
    void seamTaskGrouping_rejectsMissingSeamMetadataWithoutGuessing() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}");

        assertThat(ProblemStatementBindingChecker.seamTaskGroupingReasons("[task][Foo](testFoo)\n", plan)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("testFoo", "no seam"));
    }

    @Test
    void seamTaskGrouping_rejectsATaskThatOmitsOneVisibleTestFromItsSeam() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"testTypical","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testBoundary","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}
                ]}
                """);

        assertThat(ProblemStatementBindingChecker.seamTaskGroupingReasons("[task][Compute values](testTypical)\n", plan)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("S1", "omits visible tests", "testBoundary"));
    }

    @Test
    void malformedTaskKeywords_flagsTheWrongKeywordDistinctly() {
        assertThat(ProblemStatementBindingChecker.malformedTaskKeywords("[tasks][A](testA)\n[Task][B](testB)\n[tasks][C](testC)\n")).containsExactly("tasks", "Task");
    }

    @Test
    void malformedTaskKeywords_emptyForAWellFormedBinding() {
        assertThat(ProblemStatementBindingChecker.malformedTaskKeywords("[task][A](testA)\n")).isEmpty();
    }

    @Test
    void unresolvedTaskBindings_requiresProductionExactNames() {
        List<String> actual = List.of("testBubbleSort()", "testMergeSort()");
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Sort](testBubbleSort,testMergeSort)\n", actual, 2, Set.of())).containsExactly("testBubbleSort",
                "testMergeSort");
    }

    @Test
    void unresolvedTaskBindings_flagsADisplayNameThatMatchesNoRealTest() {
        List<String> actual = List.of("testSortsAscending", "testSortsDescending");
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Sort](aDisplayNameNotAMethodName)\n", actual, 2, Set.of()))
                .containsExactly("aDisplayNameNotAMethodName");
    }

    @Test
    void unresolvedTaskBindings_rejectsUnseededStructuralShapedNames() {
        // A structural-looking name only resolves if the authoritative seeder produced it. Otherwise the statement can invent non-grading structural tasks.
        List<String> actual = List.of("testSortsAscending");
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Structure](testClass[Sorter])\n", actual, 1, Set.of())).containsExactly("testClass[Sorter]");
    }

    @Test
    void unresolvedTaskBindings_exemptsAuthoritativeStructuralSeededNamesFromResolution() {
        List<String> actual = List.of("testSortsAscending");
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Structure](testClass[Sorter])\n", actual, 1, Set.of("testClass[Sorter]"))).isEmpty();
    }

    @Test
    void unresolvedTaskBindings_treatsAnAuthoritativeSeededNameAsResolved() {
        List<String> actual = List.of("testSortsAscending");
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Seeded](someSeededName)\n", actual, 1, Set.of("someSeededName"))).isEmpty();
    }

    @Test
    void unboundGradableTestNames_includesAuthoritativeStructuralTests() {
        List<String> actual = List.of("sortsAscending", "testClass[Sorter]");

        assertThat(ProblemStatementBindingChecker.unboundGradableTestNames("[task][Sort](sortsAscending)\n", actual, 2)).containsExactly("testClass[Sorter]");
    }

    @Test
    void unresolvedTaskBindings_failsOpenWhenTheNameSetIsShorterThanTheTestCount() {
        // An incomplete emitted name list must not be used to reject; in the live pipeline fromReports guarantees the set is complete (pinned by
        // DifferentialVerificationServiceTest.buildSummary_fromReports_recordsACompleteSoundPerTestView), so this fail-open branch is a pure-function guard, not a runtime path.
        assertThat(ProblemStatementBindingChecker.unresolvedTaskBindings("[task][Sort](nothingMatches)\n", List.of("onlyOne"), 5, Set.of())).isEmpty();
    }

    @Test
    void proseHygieneLeaks_flagsABareTaskMarkerOutsideABinding() {
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks("## [tasks]\n[task][A](testA)\n"))
                .contains("a bare [task]/[tasks] marker outside a [task][Title](testName) binding");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Raise NotImplementedError to make the tests fail.", "Your method name must match the exact test name reported by the test runner.",
            "The stubs are generated by the test suite.", "This example assumes a different command ordering; adjust accordingly in tests.", "See SPEC.md for the remaining rules.",
            "Copy the names from test-plan.json.", "Inspect /workspace/solution.", "Follow reference/style/final-statement.md.",
            // Observed live: the statement told students what the grader does to their code, which existed only because the design exposed a public mutable static for the
            // oracle to swap.
            "Tests may replace this field via reflection to exercise the word policy.", "The tests will inspect the private field via reflection." })
    void proseHygieneLeaks_flagsHighPrecisionGraderMechanicsPhrasings(String leakyProse) {
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks("[task][A](testA)\n" + leakyProse)).isNotEmpty();
    }

    @Test
    void proseHygieneLeaks_emptyForLegitimateInstructionsThatMentionTheTemplateOrFailingTests() {
        String clean = "[task][A](testA)\nImplement the `sort` method in the template file. Until you do, the empty placeholder bodies will make the tests fail — that is expected.";
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks(clean)).isEmpty();
    }

    @Test
    void proseHygieneLeaks_keepsAStudentFacingRequirementThatMerelyExplainsWhyAConstructorIsNeeded() {
        // The grader-internals rule must not swallow a real requirement on the student's own code. Taken verbatim from a generated exercise that was correct here.
        String clean = "[task][A](testA)\nEach concrete strategy class must provide a public no-argument constructor so that it can be instantiated via reflection in the tests.";
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks(clean)).isEmpty();
    }

    @Test
    void normalizeTestName_trimsOnlyToMatchProductionTaskExtraction() {
        assertThat(ProblemStatementBindingChecker.normalizeTestName("  testFoo()  ")).isEqualTo("testFoo()");
        assertThat(ProblemStatementBindingChecker.normalizeTestName("testFoo")).isEqualTo("testFoo");
    }
}
