package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

class AlwaysFailingStudentTestProbeTest {

    private static final String PATH = "src/example/LearnerTest.java";

    private static final String TEMPLATE = """
            package example;
            class LearnerTest {
                @org.junit.jupiter.api.Test void supplied() { assertTrue(true); }
                @org.junit.jupiter.api.Test void checksSum() {}
                @org.junit.jupiter.api.Test void checksSize() {}
                int helper() { return 9; }
            }
            """;

    private static final String SOLUTION = TEMPLATE.replace("void checksSum() {}", "void checksSum() { assertEquals(5, Support.sum()); }").replace("void checksSize() {}",
            "void checksSize() { assertEquals(3, Support.size()); }");

    private static final List<String> TESTS = List.of("positive", "rejectsWrongSum", "rejectsWrongSize");

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aSeparatePositiveCheckDoesNotHideUndeservedCredit(boolean positiveIsGraded) {
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), plan(positiveIsGraded), (mutant, _) -> {
            if (mutant == null) {
                return build(List.of(), TESTS);
            }
            assertThat(mutant.mutantSource()).contains("void supplied() { assertTrue(true); }", "int helper() { return 9; }").doesNotContain("Support.sum()", "Support.size()");
            return build(List.of("positive"), TESTS);
        });

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).isEqualTo(Disposition.SURVIVED_GRADED_SUITE);
            assertThat(outcome.mutant().evidence()).isEqualTo(SemanticMutant.Evidence.ALWAYS_FAILING_STUDENT_TESTS);
            assertThat(outcome.mutant().testsLearnerAssertions()).isTrue();
            assertThat(outcome.diagnostic()).contains("2 weighted", "rejectsWrongSum", "rejectsWrongSize").doesNotContain("positive");
        });
    }

    @Test
    void pairedPositiveAndNegativeChecksRejectAnAlwaysFailingLearner() {
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), plan(false),
                (mutant, _) -> build(mutant == null ? List.of() : TESTS, TESTS));

        assertThat(outcomes).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
    }

    @Test
    void aCompilationFailureOrMissingGradedResultIsInconclusive() {
        for (var mutation : List.of(new BuildSummary(0, 0, 1, false, List.of(), List.of(), List.of(), "compilation failed"),
                build(List.of(), List.of("positive", "rejectsWrongSum")), new BuildSummary(3, 0, 0, true, TESTS, List.of(), List.of(), "timed out"),
                new BuildSummary(3, 0, 1, false, TESTS, List.of(), List.of(), "build failed outside tests"),
                new BuildSummary(3, 1, 1, false, TESTS, List.of(), List.of(), "failure details missing"),
                new BuildSummary(3, 1, 1, false, TESTS, List.of("unexecuted"), List.of(), "inconsistent failure"))) {
            var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), plan(false),
                    (mutant, _) -> mutant == null ? build(List.of(), TESTS) : mutation);
            assertThat(outcomes).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
        }
    }

    @Test
    void anInvalidBaselineCannotEstablishAWeakGrader() {
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), plan(false), (_, _) -> build(List.of("positive"), TESTS));

        assertThat(outcomes).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
    }

    @Test
    void unchangedSupportDoesNotPreventThePureTestWritingProbe() {
        String support = "package example; class Support { static int sum() { return 5; } }";
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE, "src/example/Support.java", support),
                Map.of(PATH, SOLUTION, "src/example/Support.java", support), plan(false), (mutant, _) -> build(mutant == null ? List.of() : TESTS, TESTS));

        assertThat(outcomes).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
    }

    @Test
    void otherLearnerImplementationWorkDoesNotCreateAFalsePositive() {
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE, "src/example/Implementation.java", "class Implementation {}"),
                Map.of(PATH, SOLUTION, "src/example/Implementation.java", "class Implementation { int value() { return 3; } }"), plan(false), (_, _) -> {
                    throw new AssertionError("Mixed learner work must not enter this probe");
                });

        assertThat(outcomes).isEmpty();
    }

    @Test
    void otherChangedWorkInsideTheTestClassDoesNotCreateAFalsePositive() {
        String changedHelper = SOLUTION.replace("int helper() { return 9; }", "int helper() { return 10; }");
        assertThat(AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, changedHelper), plan(false), (_, _) -> {
            throw new AssertionError("The additional learner work must not be ignored");
        })).isEmpty();
    }

    @Test
    void unsupportedLifecycleOrUnchangedTestsAreNotRewritten() {
        String withSetup = SOLUTION.replace("class LearnerTest {", "class LearnerTest { @org.junit.jupiter.api.BeforeEach void setup() { Support.reset(); }");
        for (var sources : List.of(Map.of(PATH, withSetup), Map.of(PATH, TEMPLATE))) {
            assertThat(AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), sources, plan(false), (_, _) -> {
                throw new AssertionError("No supported assigned method was available");
            })).isEmpty();
        }
    }

    @Test
    void anAdaptationWithoutAPlanCountsOnlyTheSavedCredit() {
        var savedCredit = OutputOnlyMainProbe.gradedTest(null, Set.of("rejectsWrongSum", "rejectsWrongSize()"));
        var outcomes = AlwaysFailingStudentTestProbe.evaluate(Map.of(PATH, TEMPLATE), Map.of(PATH, SOLUTION), savedCredit,
                (mutant, _) -> build(mutant == null ? List.of() : List.of("rejectsWrongSum", "rejectsWrongSize"), TESTS));

        assertThat(outcomes).as("the passing zero-credit check awards nothing").singleElement().extracting(SemanticMutantOutcome::disposition)
                .isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
    }

    private static Predicate<String> plan(boolean positiveIsGraded) {
        String positive = positiveIsGraded ? "{\"name\":\"positive\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}"
                : "{\"name\":\"positive\",\"purpose\":\"PRESERVATION\",\"seamWeightTier\":0,\"visibility\":\"ALWAYS\"}";
        return OutputOnlyMainProbe.gradedTest("{\"tests\":[" + positive + ",{\"name\":\"rejectsWrongSum\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"rejectsWrongSize\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}");
    }

    private static BuildSummary build(List<String> failures, List<String> tests) {
        return new BuildSummary(tests.size(), failures.size(), failures.isEmpty() ? 0 : 1, false, tests, failures, List.of(), "");
    }
}
