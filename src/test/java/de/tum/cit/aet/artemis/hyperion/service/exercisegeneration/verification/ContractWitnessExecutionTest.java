package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
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

        List<ContractWitness> result = verifier().validateContractWitnesses(sandbox, "session", javaExercise(), TESTS, List.of(MALFORMED, VALID), restores::incrementAndGet);

        assertThat(result).containsExactly(VALID);
        assertThat(restores).hasValue(3);
    }

    private static DifferentialVerificationService verifier() {
        SandboxBuildCommandService commands = mock(SandboxBuildCommandService.class);
        when(commands.verifyScriptContent(any())).thenReturn("#!/bin/sh\n");
        when(commands.pristineSolutionBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh solution");
        when(commands.pristineTemplateBuildCommand()).thenReturn("sh /hyperion-pristine/verify.sh template");
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
