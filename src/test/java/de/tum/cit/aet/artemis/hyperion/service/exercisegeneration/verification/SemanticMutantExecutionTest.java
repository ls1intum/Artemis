package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

class SemanticMutantExecutionTest {

    private static final String PATH = "src/example/Scheduler.java";

    private static final String ORIGINAL = "package example; public class Scheduler { public int choose() { return 1; } }";

    private static final String MUTANT_SOURCE = "package example; public class Scheduler { public int choose() { return 2; } }";

    private static final ContractWitness COUNTEREXAMPLE = new ContractWitness("R1", "globalChoice",
            "@Test void globalChoice() { assertEquals(1, new Scheduler().choose(), \"global choice\"); }", "chooses only within the first batch");

    private static final SemanticMutant MUTANT = new SemanticMutant("R1", PATH, ORIGINAL, MUTANT_SOURCE, COUNTEREXAMPLE);

    private static final Map<String, String> TESTS = Map.of("test/example/SchedulerTest.java", """
            package example;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            class SchedulerTest {
                @Test void existing() { assertEquals(1, new Scheduler().choose(), "existing behavior"); }
            }
            """);

    @Test
    void validatesOnlyAfterAllThreeEnvironmentProbesAndRestoresAroundEach() {
        InteractiveSandbox sandbox = sandbox(reports(List.of("existing"), List.of()), reports(List.of("existing", "globalChoice"), List.of()),
                reports(List.of("existing", "globalChoice"), List.of("globalChoice")));
        AtomicInteger restores = new AtomicInteger();

        List<SemanticMutant> result = verifier().validateSemanticMutants(sandbox, "session", javaExercise(), TESTS, Map.of(PATH, ORIGINAL), List.of(MUTANT),
                restores::incrementAndGet);

        assertThat(result).containsExactly(MUTANT);
        assertThat(restores).hasValue(6);
    }

    @Test
    void rejectsAMutantTheExistingSuiteAlreadyKills() {
        InteractiveSandbox sandbox = sandbox(reports(List.of("existing"), List.of("existing")), reports(List.of("existing", "globalChoice"), List.of()),
                reports(List.of("existing", "globalChoice"), List.of("existing", "globalChoice")));
        AtomicInteger restores = new AtomicInteger();

        assertThat(verifier().evaluateSemanticMutants(sandbox, "session", javaExercise(), TESTS, Map.of(PATH, ORIGINAL), List.of(MUTANT), restores::incrementAndGet))
                .singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(restores).hasValue(6);
    }

    @Test
    void aKilledMutantWithANonDiscriminatingCounterexampleIsInconclusive() {
        InteractiveSandbox sandbox = sandbox(reports(List.of("existing"), List.of("existing")), reports(List.of("existing", "globalChoice"), List.of()),
                reports(List.of("existing", "globalChoice"), List.of("existing")));

        assertThat(verifier().evaluateSemanticMutants(sandbox, "session", javaExercise(), TESTS, Map.of(PATH, ORIGINAL), List.of(MUTANT), () -> {
        })).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
    }

    @Test
    void compileFailureIsInconclusiveRatherThanAKill() {
        InteractiveSandbox sandbox = sandbox(reports(List.of(), List.of()));

        assertThat(verifier().evaluateSemanticMutants(sandbox, "session", javaExercise(), TESTS, Map.of(PATH, ORIGINAL), List.of(MUTANT), () -> {
        })).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
    }

    @Test
    void ordinaryRecheckRequiresAnExecutedFailingTestRatherThanACompileFailure() {
        AtomicInteger killedRestores = new AtomicInteger();
        assertThat(verifier().checkSemanticMutants(sandbox(reports(List.of("globalChoice"), List.of("globalChoice"))), "session", javaExercise(), Map.of(PATH, ORIGINAL),
                List.of(MUTANT), killedRestores::incrementAndGet)).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
        assertThat(killedRestores).hasValue(2);

        AtomicInteger compileFailureRestores = new AtomicInteger();
        assertThat(verifier().checkSemanticMutants(sandbox(reports(List.of(), List.of())), "session", javaExercise(), Map.of(PATH, ORIGINAL), List.of(MUTANT),
                compileFailureRestores::incrementAndGet)).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
        assertThat(compileFailureRestores).hasValue(2);
    }

    @Test
    void anAdaptedOrRenamedFailingTestKillsTheProvenMutant() {
        assertThat(verifier().checkSemanticMutants(sandbox(reports(List.of("strongerRenamedTest"), List.of("strongerRenamedTest"))), "session", javaExercise(),
                Map.of(PATH, ORIGINAL), List.of(MUTANT), () -> {
                })).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.KILLED_BY_GRADED_SUITE);
    }

    @Test
    void recheckInfrastructureFailureDoesNotClaimThePreviouslyProvenMutantWasKilled() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenReturn(new SandboxExecResultDTO(0, "", "", false));
        when(sandbox.copyOut(anyString(), anyString())).thenThrow(new IllegalStateException("reports unavailable"));
        AtomicInteger restores = new AtomicInteger();

        assertThatThrownBy(() -> verifier().checkSemanticMutants(sandbox, "session", javaExercise(), Map.of(PATH, ORIGINAL), List.of(MUTANT), restores::incrementAndGet))
                .isInstanceOf(DifferentialVerificationService.VerificationInfrastructureException.class);
        assertThat(restores).hasValue(2);
    }

    @Test
    void validationPropagatesAFinalRestoreFailureAfterMutatingTheLiveWorkspace() {
        InteractiveSandbox sandbox = sandbox(reports(List.of("existing"), List.of()));
        AtomicInteger restores = new AtomicInteger();
        Runnable failingFinalRestore = () -> {
            if (restores.incrementAndGet() == 2) {
                throw new IllegalStateException("session lost during restore");
            }
        };

        assertThatThrownBy(() -> verifier().validateSemanticMutants(sandbox, "session", javaExercise(), TESTS, Map.of(PATH, ORIGINAL), List.of(MUTANT), failingFinalRestore))
                .isInstanceOf(DifferentialVerificationService.VerificationInfrastructureException.class).hasMessageContaining("could not restore");
        assertThat(restores).hasValue(2);
    }

    @Test
    void rejectsCounterexampleNamesAlreadyDeclaredByTheGradedSuite() {
        Map<String, String> collidingTests = Map.of("test/example/SchedulerTest.java",
                "package example; import org.junit.jupiter.api.Test; class SchedulerTest { @Test void globalChoice() {} }");
        AtomicInteger probes = new AtomicInteger();

        List<SemanticMutant> result = SemanticMutantExecution.validate(collidingTests, Map.of(PATH, ORIGINAL), List.of(MUTANT), (mutant, probe) -> {
            probes.incrementAndGet();
            throw new AssertionError("a name collision must be rejected before execution");
        });

        assertThat(result).isEmpty();
        assertThat(probes).hasValue(0);
    }

    @Test
    void rejectsANondeterministicCounterexampleBeforeExecution() {
        ContractWitness randomCounterexample = new ContractWitness("R1", "randomChoice",
                "@Test void randomChoice() { assertEquals(1, choose(new Random()), \"R1 global choice\"); }", "chooses within the first batch");
        SemanticMutant randomMutant = new SemanticMutant("R1", PATH, ORIGINAL, MUTANT_SOURCE, randomCounterexample);
        AtomicInteger probes = new AtomicInteger();

        List<SemanticMutant> result = SemanticMutantExecution.validate(TESTS, Map.of(PATH, ORIGINAL), List.of(randomMutant), (mutant, probe) -> {
            probes.incrementAndGet();
            throw new AssertionError("a nondeterministic counterexample must be rejected before execution");
        });

        assertThat(result).isEmpty();
        assertThat(probes).hasValue(0);
    }

    @Test
    void aMutantProvenAgainstAnotherSolutionRevisionIsRetiredWithoutRunning() {
        AtomicInteger probes = new AtomicInteger();

        List<SemanticMutantOutcome> result = SemanticMutantExecution.recheck(Map.of(PATH, ORIGINAL + "\n// changed"), List.of(MUTANT), (mutant, probe) -> {
            probes.incrementAndGet();
            throw new AssertionError("stale evidence must not be applied to another solution revision");
        });

        assertThat(result).singleElement().extracting(SemanticMutantOutcome::disposition).isEqualTo(Disposition.INCONCLUSIVE);
        assertThat(probes).hasValue(0);
    }

    private static DifferentialVerificationService verifier() {
        SandboxBuildCommandService commands = mock(SandboxBuildCommandService.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.pristineSolutionBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh solution");
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

    private static TarArchiveInputStream reports(List<String> tests, List<String> failed) {
        return ReportTarFixtures.junitAndScaReports("solution", tests, failed, Map.of());
    }
}
