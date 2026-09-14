package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.WorkerUsageRecorder;

class GenerationOutputCaptureTest {

    private static GenerationAssignment assignment() {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());
        var brief = new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
        var seed = new WorkspaceSnapshot(List.of(new WorkspaceFile("tests/gradlew", "old".getBytes(StandardCharsets.UTF_8), true),
                new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false),
                new WorkspaceFile("tests/deleted.java", "removed".getBytes(StandardCharsets.UTF_8), false)));
        return new GenerationAssignment(identity, brief, parameters, seed, Instant.now().plusSeconds(300), "sha256:" + "a".repeat(64), new GradingContext(false, Set.of()));
    }

    private static WorkerUsageRecorder usage() {
        return new WorkerUsageRecorder(100_000, 1, true, ignored -> {
        });
    }

    @Test
    void sealsCapturedTextBinaryAndExecutableMetadataRatherThanTheMutableWorkspace() {
        var tests = new HashMap<>(Map.of("gradlew", "new wrapper", "test/StackTest.java", "test source"));
        var files = new HashMap<RepositoryRole, Map<String, String>>();
        files.put(RepositoryRole.TESTS, tests);
        files.put(RepositoryRole.SOLUTION, Map.of("src/Stack.java", "solution"));
        files.put(RepositoryRole.TEMPLATE, Map.of("src/Stack.java", "template"));
        var outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 2, "done"), new VerificationResult(true, true, true, 1, List.of()), files,
                "statement", SpecFidelityReport.empty(), "specification", "plan").withTermination(TerminationReason.CONVERGED);
        tests.put("gradlew", "changed after capture");
        files.clear();

        var output = GenerationOutputCapture.capture(assignment(), outcome, usage(), false);

        assertThat(output.verifiedDigest()).isEqualTo(output.candidate().sha256());
        assertThat(output.candidate().files()).extracting(WorkspaceFile::path).containsExactlyInAnyOrder("tests/gradlew", "tests/test/StackTest.java",
                "tests/gradle/wrapper/gradle-wrapper.jar", "solution/src/Stack.java", "template/src/Stack.java", "problem-statement.md", "SPEC.md", "test-plan.json");
        assertThat(output.candidate().files()).filteredOn(file -> file.path().equals("tests/gradlew")).singleElement().satisfies(file -> {
            assertThat(file.content()).isEqualTo("new wrapper".getBytes(StandardCharsets.UTF_8));
            assertThat(file.executable()).isTrue();
        });
        assertThat(output.candidate().files()).filteredOn(file -> file.path().endsWith(".jar")).singleElement()
                .satisfies(file -> assertThat(file.content()).containsExactly(0, 1, 2));
        assertThat(output.terminationReason()).isEqualTo("CONVERGED");
        assertThat(output.effortProfile()).isEqualTo("standard");
        assertThat(output.accountingState()).isEqualTo(AccountingState.COMPLETE);
    }

    @Test
    void checkpointsDoNotClaimTerminalAccountingAndErrorsDoNotClaimVerification() {
        var outcome = GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "failed"), "build failed");
        var recorder = usage();
        recorder.markUncertain();
        var checkpoint = GenerationOutputCapture.capture(assignment(), outcome, recorder, true);
        var terminal = GenerationOutputCapture.capture(assignment(), outcome, recorder, false);

        assertThat(checkpoint.accountingState()).isEqualTo(AccountingState.PENDING);
        assertThat(terminal.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(terminal.candidate().files()).isEmpty();
        assertThat(terminal.verifiedDigest()).isNull();
        assertThat(terminal.verification().mechanicallyVerified()).isFalse();
        assertThat(terminal.verification().reasons()).containsExactly("build failed");
    }
}
