package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

class StudentTestAssessmentHistoryTest {

    private final SemanticMutant probe = SemanticMutant.nonFailingStudentTests("src/LearnerTest.java", "original", "assertions suppressed");

    @Test
    void anAlwaysFailingClassMustBeRecheckedAndCanBeClearedAfterGraderChanges() {
        var history = new StudentTestAssessmentHistory();
        var old = SemanticMutant.alwaysFailingStudentTests("src/LearnerTest.java", "old learner source", "always fails");
        history.record("session", new SemanticMutantOutcome(old, Disposition.SURVIVED_GRADED_SUITE));
        assertThat(history.complete("session", List.of())).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);

        var current = SemanticMutant.alwaysFailingStudentTests(old.solutionPath(), "current learner source", "still always fails");
        assertThat(history.complete("session", List.of(new SemanticMutantOutcome(current, Disposition.KILLED_BY_GRADED_SUITE)))).singleElement()
                .extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
    }

    @Test
    void removingTheLearnerTestSurfaceCannotDischargeAnExecutedCounterexample() {
        var history = new StudentTestAssessmentHistory();
        history.complete("job", List.of(new SemanticMutantOutcome(probe, Disposition.SURVIVED_GRADED_SUITE)));

        assertThat(history.complete("job", List.of())).singleElement().satisfies(outcome -> {
            assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE);
            assertThat(outcome.mutant()).isEqualTo(probe);
            assertThat(outcome.diagnostic()).contains("src/LearnerTest.java", "Preserve the assigned learner work");
        });
    }

    @Test
    void aConclusiveKillResolvesTheCurrentCandidateButDoesNotEraseTheFutureObligation() {
        var history = new StudentTestAssessmentHistory();
        history.complete("job", List.of(new SemanticMutantOutcome(probe, Disposition.SURVIVED_GRADED_SUITE)));
        var repaired = SemanticMutant.nonFailingStudentTests(probe.solutionPath(), "repaired source", "new suppressed assertions");
        var killed = new SemanticMutantOutcome(repaired, Disposition.KILLED_BY_GRADED_SUITE);

        assertThat(history.complete("job", List.of(killed))).containsExactly(killed);
        assertThat(history.complete("job", List.of())).singleElement().satisfies(outcome -> assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE));
    }

    @Test
    void aDifferentTestClassCannotSilentlyReplaceThePreviouslyFailingAssessmentSurface() {
        var history = new StudentTestAssessmentHistory();
        history.complete("job", List.of(new SemanticMutantOutcome(probe, Disposition.SURVIVED_GRADED_SUITE)));
        var replacement = SemanticMutant.nonFailingStudentTests("src/OtherTest.java", "source", "suppressed assertions");
        var killed = new SemanticMutantOutcome(replacement, Disposition.KILLED_BY_GRADED_SUITE);

        assertThat(history.complete("job", List.of(killed))).contains(killed).anySatisfy(outcome -> {
            assertThat(outcome.mutant()).isEqualTo(probe);
            assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE);
        });
    }

    @Test
    void aStrongMethodCannotRetireTheCounterexampleForAnotherMethodInTheSameClass() {
        var history = new StudentTestAssessmentHistory();
        var weak = probe.forStudentTestMethod("weak");
        history.complete("job", List.of(new SemanticMutantOutcome(weak, Disposition.SURVIVED_GRADED_SUITE)));
        var strong = new SemanticMutantOutcome(probe.forStudentTestMethod("strong"), Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(history.complete("job", List.of(strong))).contains(strong).anySatisfy(outcome -> {
            assertThat(outcome.mutant()).isEqualTo(weak);
            assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE);
        });
    }

    @Test
    void unrelatedSessionsAndUnprovenProbeKindsDoNotInheritAnObligation() {
        var history = new StudentTestAssessmentHistory();
        history.complete("job", List.of(new SemanticMutantOutcome(probe, Disposition.SURVIVED_GRADED_SUITE)));
        assertThat(history.complete("another-job", List.of())).isEmpty();
        var empty = SemanticMutant.emptyStudentTests(probe.solutionPath(), "source", "empty bodies");
        var killed = new SemanticMutantOutcome(empty, Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(history.complete("another-job", List.of(killed))).containsExactly(killed);
        assertThat(history.complete("another-job", List.of())).isEmpty();
    }
}
