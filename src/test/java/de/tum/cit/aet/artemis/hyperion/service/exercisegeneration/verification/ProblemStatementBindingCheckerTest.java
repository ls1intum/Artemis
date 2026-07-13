package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProblemStatementBindingCheckerTest {

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
            "The stubs are generated by the test suite.", "This example assumes a different command ordering; adjust accordingly in tests." })
    void proseHygieneLeaks_flagsHighPrecisionGraderMechanicsPhrasings(String leakyProse) {
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks("[task][A](testA)\n" + leakyProse)).isNotEmpty();
    }

    @Test
    void proseHygieneLeaks_emptyForLegitimateInstructionsThatMentionTheTemplateOrFailingTests() {
        String clean = "[task][A](testA)\nImplement the `sort` method in the template file. Until you do, the empty placeholder bodies will make the tests fail — that is expected.";
        assertThat(ProblemStatementBindingChecker.proseHygieneLeaks(clean)).isEmpty();
    }

    @Test
    void normalizeTestName_trimsOnlyToMatchProductionTaskExtraction() {
        assertThat(ProblemStatementBindingChecker.normalizeTestName("  testFoo()  ")).isEqualTo("testFoo()");
        assertThat(ProblemStatementBindingChecker.normalizeTestName("testFoo")).isEqualTo("testFoo");
    }
}
