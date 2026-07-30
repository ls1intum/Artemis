package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

class ContractWitnessExecutionTest {

    private static final Map<String, String> TESTS = Map.of("test/example/SchedulerTest.java",
            "package example; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;"
                    + " class SchedulerTest { @Test void existing() { assertTrue(true); } }");

    private static final ContractWitness MALFORMED = new ContractWitness("R1", "malformed", "@Test void malformed() { assertTrue(false); }", "does not compile");

    private static final ContractWitness VALID = new ContractWitness("R2", "validBoundary", "@Test void validBoundary() { assertEquals(1, choose()); }",
            "chooses the wrong boundary");

    @Test
    void malformedWitnessCannotPreventALaterValidWitnessFromBeingExecuted() {
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of(), List.of()), reports("solution", List.of("validBoundary"), List.of()),
                reports("template", List.of("validBoundary"), List.of("validBoundary")));
        AtomicInteger restores = new AtomicInteger();

        List<ContractWitness> result = verifier()
                .evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(MALFORMED, VALID), restores::incrementAndGet).stream()
                .filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED).map(ContractWitnessOutcome::witness).toList();

        assertThat(result).containsExactly(VALID);
        assertThat(restores).hasValue(4);
        verify(sandbox, times(3)).copyIn(eq("session"), eq("/workspace"), any());
    }

    @Test
    void evaluationPreservesANamedReferenceAssertionFailure() {
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of("validBoundary"), List.of("validBoundary")));
        AtomicInteger restores = new AtomicInteger();

        List<ContractWitnessOutcome> outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(VALID),
                restores::incrementAndGet);

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.witness()).isEqualTo(VALID);
            assertThat(outcome.disposition()).isEqualTo(Disposition.REFERENCE_TEST_FAILED);
        });
        assertThat(restores).hasValue(2);
        verify(sandbox).copyIn(eq("session"), eq("/workspace"), any());
    }

    @Test
    void evaluationDistinguishesAStarterThatPassesTheWitness() {
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of("validBoundary"), List.of()), reports("template", List.of("validBoundary"), List.of()));
        AtomicInteger restores = new AtomicInteger();

        List<ContractWitnessOutcome> outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(VALID),
                restores::incrementAndGet);

        assertThat(outcomes).singleElement().extracting(ContractWitnessOutcome::disposition).isEqualTo(Disposition.REFERENCE_PASSED_STARTER_NOT_FAILED);
        assertThat(restores).hasValue(3);
        verify(sandbox, times(2)).copyIn(eq("session"), eq("/workspace"), any());
    }

    @Test
    void evaluationKeepsCompileOrDiscoveryFailureInconclusiveAndContinues() {
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of(), List.of()), reports("solution", List.of("validBoundary"), List.of()),
                reports("template", List.of("validBoundary"), List.of("validBoundary")));
        AtomicInteger restores = new AtomicInteger();

        List<ContractWitnessOutcome> outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY,
                List.of(MALFORMED, VALID), restores::incrementAndGet);

        assertThat(outcomes).extracting(ContractWitnessOutcome::witness, ContractWitnessOutcome::disposition).containsExactly(tuple(MALFORMED, Disposition.INCONCLUSIVE),
                tuple(VALID, Disposition.REFERENCE_PASSED_STARTER_FAILED));
        assertThat(restores).hasValue(4);
    }

    @Test
    void missingExecutableHostProducesOneInconclusiveOutcomePerCandidateWithoutOpeningTheSandbox() {
        AtomicInteger restores = new AtomicInteger();

        List<ContractWitnessOutcome> outcomes = verifier().evaluateContractWitnesses(mock(InteractiveSandbox.class), "session", javaExercise(), Map.of(),
                SeededStructuralTests.EMPTY, List.of(MALFORMED, VALID), restores::incrementAndGet);

        assertThat(outcomes).extracting(ContractWitnessOutcome::disposition).containsExactly(Disposition.INCONCLUSIVE, Disposition.INCONCLUSIVE);
        assertThat(restores).hasValue(0);
    }

    private static DifferentialVerificationService verifier() {
        SandboxBuildCommandService commands = mock(SandboxBuildCommandService.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.behavioralSolutionBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh solution behavior-isolated");
        when(commands.behavioralTemplateBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh template behavior-isolated");
        return new DifferentialVerificationService(commands);
    }

    private static ProgrammingExercise javaExercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        return exercise;
    }

    private static InteractiveSandbox sandbox(TarArchiveInputStream first, TarArchiveInputStream... remaining) {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenReturn(new SandboxExecResultDTO(0, "", "", false));
        when(sandbox.copyOut(anyString(), anyString())).thenReturn(first, remaining);
        return sandbox;
    }

    private static TarArchiveInputStream reports(String assignment, List<String> tests, List<String> failed) {
        return ReportTarFixtures.junitAndScaReports(assignment, tests, failed, Map.of());
    }
}
