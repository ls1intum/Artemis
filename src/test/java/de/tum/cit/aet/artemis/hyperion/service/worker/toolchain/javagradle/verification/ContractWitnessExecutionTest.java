package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.SandboxBuildCommands;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

class ContractWitnessExecutionTest {

    private static final Map<String, String> TESTS = Map.of("test/example/SchedulerTest.java",
            "package example; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;"
                    + " class SchedulerTest { @Test void existing() { assertTrue(true); } }",
            "SecurityPolicy.yaml", """
                    regardingTheSupervisedCode:
                      theFollowingClassesAreTestClasses:
                        - "example.SchedulerTest"
                      theFollowingResourceAccessesArePermitted: { }
                    """);

    private static final ContractWitness MALFORMED = new ContractWitness("R1", "malformed", "@Test void malformed() { assertTrue(false); }", "does not compile");

    private static final ContractWitness VALID = new ContractWitness("R2", "validBoundary", "@Test void validBoundary() { assertEquals(1, choose()); }",
            "chooses the wrong boundary");

    @Test
    void adoptedWitnessRunsUnderADistinctNameAndRetainsItsOriginalIdentityAndDiagnostic() {
        String executionName = "existingHyperionProbe";
        ContractWitness adopted = new ContractWitness("R1", "existing", "@Test void existing() { assertTrue(false, \"existing\"); }", "retained counterexample");
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of(executionName), List.of()), reports("template", List.of(executionName), List.of(executionName)));

        var outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(adopted), () -> {
        });

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.witness()).isEqualTo(adopted);
            assertThat(outcome.disposition()).isEqualTo(Disposition.REFERENCE_PASSED_STARTER_FAILED);
            assertThat(outcome.diagnostic()).contains("expected behaviour not met");
        });
        verify(sandbox, times(2)).copyOut(anyString(), anyString());
    }

    @Test
    void adoptedWitnessCannotUseOnlyTheOriginalGradedTestsExecutionAsProbeEvidence() {
        ContractWitness adopted = new ContractWitness("R1", "existing", "@Test void existing() { assertTrue(false); }", "counterexample");
        InteractiveSandbox sandbox = sandbox(reports("solution", List.of("existing"), List.of()));

        var outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(adopted), () -> {
        });

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.witness()).isEqualTo(adopted);
            assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE);
        });
        verify(sandbox).copyOut(anyString(), anyString());
    }

    @Test
    void duplicateProposalsRemainInconclusiveWithoutOpeningTheSandbox() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);

        var outcomes = verifier().evaluateContractWitnesses(sandbox, "session", javaExercise(), TESTS, SeededStructuralTests.EMPTY, List.of(VALID, VALID), () -> {
        });

        assertThat(outcomes).hasSize(2).allSatisfy(outcome -> assertThat(outcome.disposition()).isEqualTo(Disposition.INCONCLUSIVE));
        org.mockito.Mockito.verifyNoInteractions(sandbox);
    }

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

    private static DifferentialVerifier verifier() {
        SandboxBuildCommands commands = mock(SandboxBuildCommands.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.behavioralSolutionBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh solution behavior-isolated");
        when(commands.behavioralTemplateBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh template behavior-isolated");
        return new DifferentialVerifier(commands);
    }

    private static GenerationInput javaExercise() {
        return new GenerationInput(42, "de.tum", "", false, Set.of(), Set.of(), GenerationBuildTool.GRADLE);
    }

    private static InteractiveSandbox sandbox(TarArchiveInputStream first, TarArchiveInputStream... remaining) {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenReturn(new SandboxExecResultDTO(0, "", "", false));
        when(sandbox.copyOut(anyString(), anyString())).thenReturn(first, remaining);
        doAnswer(invocation -> {
            try (var archive = new TarArchiveInputStream(invocation.getArgument(2, InputStream.class))) {
                Map<String, String> overlay = WorkspaceArchive.readTar(archive, "");
                assertThat(overlay).containsKeys("tests/test/example/HyperionContractWitnessProbeTest.java", "tests/SecurityPolicy.yaml");
                assertThat(overlay.get("tests/SecurityPolicy.yaml")).contains("    - \"example.HyperionContractWitnessProbeTest\"");
                assertThat(AresSecurityPolicy.onlyTestClassesChanged(TESTS.get("SecurityPolicy.yaml"), overlay.get("tests/SecurityPolicy.yaml"))).isTrue();
            }
            return null;
        }).when(sandbox).copyIn(anyString(), eq("/workspace"), any());
        return sandbox;
    }

    private static TarArchiveInputStream reports(String assignment, List<String> tests, List<String> failed) {
        return ReportTarFixtures.junitAndScaReports(assignment, tests, failed, Map.of());
    }
}
