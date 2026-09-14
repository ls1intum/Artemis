package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationUsage;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationFileUpdate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationOrchestrationServiceTest {

    private final GenerationSeedService seeds = mock();

    private final GenerationWorkerRegistryService workers = mock();

    private final GenerationWorkerClientService client = mock();

    private final GenerationJobService jobs = mock();

    private final HyperionAgentProperties properties = new HyperionAgentProperties();

    private final HyperionEffortProfileService profiles = mock();

    private final ProviderUsageSink usage = mock();

    private final GenerationProgressSink progress = mock();

    private final GenerationOrchestrationService service = new GenerationOrchestrationService(seeds, workers, client, jobs, properties, profiles);

    private final GenerationWorkerRegistryService.Claim claim = new GenerationWorkerRegistryService.Claim(
            new ExecutionIdentity("job", 42, UUID.randomUUID(), "worker", UUID.randomUUID()), "sha256:" + "a".repeat(64));

    private final HyperionGenerationSettings settings = new HyperionGenerationSettings("draft", "Draft", 20, Duration.ofMinutes(10), 100_000, true, "CONTINUOUS", 128_000, null,
            false, false);

    private final WorkspaceSnapshot snapshot = new WorkspaceSnapshot(List.of(new WorkspaceFile("problem-statement.md", "statement".getBytes(StandardCharsets.UTF_8), false)));

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    private final User user = new User();

    private final List<GenerationFileUpdate> fileUpdates = new ArrayList<>();

    private final ArrayDeque<WorkerEvent> deliveries = new ArrayDeque<>();

    @BeforeEach
    void setup() {
        exercise.setId(42L);
        exercise.setTitle("Stack");
        exercise.setShortName("stack");
        exercise.setPackageName("de.example.stack");
        user.setLogin("editor");
        when(seeds.capture(exercise)).thenReturn(new GenerationSeedService.Seed(snapshot, Map.of()));
        when(workers.claim("job", 42)).thenReturn(claim);
        when(workers.renew(claim)).thenReturn(true);
        when(jobs.isOwnedActiveJob(42, "job")).thenReturn(true);
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.empty());
        when(profiles.resolve(null)).thenReturn(settings);
        doAnswer(invocation -> {
            Consumer<WorkerEvent> callback = invocation.getArgument(1);
            callback.accept(deliveries.remove());
            return null;
        }).when(client).receive(eq(claim), any());
    }

    @Test
    void finishedResultUsesFrozenInputAndReleasesWorkerBeforeReturning() {
        WorkerEvent finished = event(1, WorkerEvent.Type.FINISHED, output(true, "draft"));
        deliveries.add(finished);
        var outcome = run(() -> false);
        assertThat(outcome.isMechanicallyVerified()).isTrue();
        assertThat(outcome.producedProblemStatement()).isEqualTo("statement");
        var lifecycle = org.mockito.Mockito.inOrder(workers);
        lifecycle.verify(workers).recordCompletion(claim, finished);
        lifecycle.verify(workers).release(claim);
        var commands = ArgumentCaptor.forClass(WorkerCommand.class);
        verify(client, times(3)).send(commands.capture());
        assertThat(commands.getAllValues()).extracting(WorkerCommand::type).containsExactly(WorkerCommand.Type.START, WorkerCommand.Type.RENEW, WorkerCommand.Type.CANCEL);
        var assignment = commands.getAllValues().getFirst().assignment();
        assertThat(assignment.seed()).isSameAs(snapshot);
        assertThat(assignment.brief().sourceBrief()).isEqualTo("original brief");
        assertThat(assignment.parameters().effortProfile()).isEqualTo("draft");
        verify(usage).markUncertain();
    }

    @Test
    void repeatedCheckpointIsProjectedAndRetainedOnlyOnce() {
        WorkerEvent checkpoint = event(1, WorkerEvent.Type.CHECKPOINT, output(true, "draft"));
        deliveries.add(checkpoint);
        deliveries.add(checkpoint);
        deliveries.add(event(2, WorkerEvent.Type.FINISHED, output(true, "draft")));
        assertThat(run(() -> false).isMechanicallyVerified()).isTrue();
        verify(jobs).retainUnsavedArtifacts(eq(42L), eq("job"), eq("editor"), any());
        verify(progress, times(2)).accept("progress");
    }

    @Test
    void disconnectedWorkerKeepsVerifiedCheckpointWithReviewFinding() {
        deliveries.add(event(1, WorkerEvent.Type.CHECKPOINT, output(true, "draft")));
        var outcome = run(() -> false);
        assertThat(outcome.isMechanicallyVerified()).isTrue();
        assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        verify(usage).markUncertain();
        verify(workers).release(claim);
    }

    @Test
    void unverifiedCheckpointCannotBecomeFallbackAfterDisconnect() {
        deliveries.add(event(1, WorkerEvent.Type.CHECKPOINT, output(false, "draft")));
        assertThat(run(() -> false).isMechanicallyVerified()).isFalse();
        verify(workers).release(claim);
    }

    @Test
    void budgetStopPreservesCheckpointButInstructorCancellationPreventsSave() {
        deliveries.add(event(1, WorkerEvent.Type.FINISHED, output(true, "draft")));
        assertThat(run(() -> true).loopResult().status()).isEqualTo(de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult.Status.COMPLETED);
        verify(client).send(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.STOP_AUTHORING, claim.identity(), null));
        when(jobs.isCancelled("job")).thenReturn(true);
        deliveries.add(event(2, WorkerEvent.Type.CANCELLED, output(true, "draft")));
        assertThat(run(() -> true).loopResult().status()).isEqualTo(de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult.Status.CANCELLED);
    }

    @Test
    void lostOwnershipNeverReturnsCheckpointForPersistence() {
        when(jobs.isOwnedActiveJob(42, "job")).thenReturn(false);
        assertThat(run(() -> false).isMechanicallyVerified()).isFalse();
        verify(client, never()).receive(any(), any());
        verify(workers).release(claim);
    }

    @Test
    void mismatchedEffortAttestationIsRejectedBeforeRetention() {
        deliveries.add(event(1, WorkerEvent.Type.FINISHED, output(true, "unassigned")));
        assertThat(run(() -> false).isMechanicallyVerified()).isFalse();
        verify(jobs, never()).retainUnsavedArtifacts(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
        verify(workers).release(claim);
    }

    @Test
    void invalidBriefDoesNotReserveWorkerCapacity() {
        exercise.setPackageName(null);
        assertThatThrownBy(() -> run(() -> false)).isInstanceOf(IllegalArgumentException.class);
        verify(workers, never()).claim(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void failedStartStillReleasesClaimEvenWhenCleanupDeliveryFails() {
        doThrow(new IllegalStateException("broker unavailable")).when(client).send(any());
        assertThat(run(() -> false).isMechanicallyVerified()).isFalse();
        verify(workers).release(claim);
    }

    @Test
    void structuredProgressAndUsageReachExistingPublicProjectionWithoutRecountingDuplicates() {
        deliveries.add(event(1, WorkerEvent.Type.PROGRESS, null).withProgress(new GenerationProgress("VERIFYING", null, null, null)));
        var repair = new GenerationProgress.RepairRound(1, 2, 3, 4, 5, 6, 7);
        deliveries.add(event(2, WorkerEvent.Type.PROGRESS, null).withProgress(new GenerationProgress(null, repair, null, null)));
        var file = new GenerationProgress.FileChange("solution/src/App.java", "write", 3, Instant.now(), "class App {}");
        deliveries.add(event(3, WorkerEvent.Type.PROGRESS, null).withProgress(new GenerationProgress(null, null, file, null)));
        var call = new ProviderUsageUpdate.Call("model", "request-1", 30, 10, 5L);
        WorkerEvent response = event(4, WorkerEvent.Type.PROGRESS, null)
                .withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.RESPONSE, 0, call)));
        deliveries.add(response);
        deliveries.add(response);
        deliveries.add(event(5, WorkerEvent.Type.PROGRESS, null)
                .withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.TOOL_CALLS, 2, null))));
        deliveries.add(
                event(6, WorkerEvent.Type.PROGRESS, null).withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.TURN, 1, null))));
        deliveries.add(event(7, WorkerEvent.Type.PROGRESS, null)
                .withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.ATTEMPT, 1, null))));
        deliveries.add(event(8, WorkerEvent.Type.PROGRESS, null)
                .withProgress(new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.UNCERTAIN, 0, null))));
        deliveries.add(event(9, WorkerEvent.Type.FINISHED, output(true, "draft")));
        run(() -> false);
        verify(progress).phase(de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.Phase.VERIFYING, "progress");
        verify(progress).progress(eq("progress"), any());
        assertThat(fileUpdates).singleElement().satisfies(update -> {
            assertThat(update.change().repo()).isEqualTo("solution");
            assertThat(update.content()).isEqualTo("class App {}");
        });
        var recorded = ArgumentCaptor.forClass(org.springframework.ai.chat.model.ChatResponse.class);
        verify(usage).accept(recorded.capture());
        assertThat(recorded.getValue().getMetadata().getId()).isEqualTo("request-1");
        assertThat(recorded.getValue().getMetadata().getUsage().getPromptTokens()).isEqualTo(30);
        verify(usage).recordToolCalls(2);
        verify(usage).recordTurn();
        verify(usage).recordAttempt();
        verify(usage, times(2)).markUncertain();
    }

    @Test
    void matchingCompleteAccountDoesNotGetMarkedUncertainButMismatchedTotalsDo() {
        var reported = new GenerationUsage(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true);
        var status = mock(ExerciseGenerationStatusDTO.class);
        when(status.usage()).thenReturn(new ExerciseGenerationUsageDTO(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true));
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(status));
        var output = new GenerationOutput(snapshot, new VerificationResult(true, true, true, 1, List.of()), snapshot.sha256(), SpecFidelityReport.empty(), "CONVERGED", reported,
                GenerationOutput.AccountingState.COMPLETE, "draft");
        deliveries.add(event(1, WorkerEvent.Type.FINISHED, output));
        run(() -> false);
        verify(usage, never()).markUncertain();
        when(status.usage()).thenReturn(new ExerciseGenerationUsageDTO(1, 2, 1, 1, 31, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true));
        deliveries.add(event(2, WorkerEvent.Type.FINISHED, output));
        run(() -> false);
        verify(usage).markUncertain();
    }

    @Test
    void earlySpecificationFailurePreservesCompleteAccountingWithoutRepositoryCapture() {
        var binary = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", new byte[] { 0, 1, 2 }, false);
        when(seeds.capture(exercise)).thenReturn(new GenerationSeedService.Seed(new WorkspaceSnapshot(List.of(binary)), Map.of()));
        var reported = new GenerationUsage(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true);
        var status = mock(ExerciseGenerationStatusDTO.class);
        when(status.usage()).thenReturn(new ExerciseGenerationUsageDTO(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true));
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(status));
        var output = new GenerationOutput(new WorkspaceSnapshot(List.of()), new VerificationResult(false, false, false, 0, List.of("Specification gate failed")), null,
                SpecFidelityReport.empty(), "RUN_FAILED", reported, GenerationOutput.AccountingState.COMPLETE, "draft");
        deliveries.add(event(1, WorkerEvent.Type.FINISHED, output));

        var outcome = run(() -> false);

        assertThat(outcome.isMechanicallyVerified()).isFalse();
        assertThat(outcome.hasCapturedArtifacts()).isFalse();
        assertThat(outcome.errorMessage()).contains("Specification gate failed");
        verify(usage, never()).markUncertain();
        verify(workers).release(claim);
    }

    @Test
    void rejectingTerminalArtifactsDoesNotInvalidateReconciledAccounting() {
        var reported = new GenerationUsage(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true);
        var status = mock(ExerciseGenerationStatusDTO.class);
        when(status.usage()).thenReturn(new ExerciseGenerationUsageDTO(1, 2, 1, 1, 30, 10, 5, true, 0, false, List.of("model"), List.of("request-1"), true));
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(status));
        var invalid = new WorkspaceSnapshot(List.of(new WorkspaceFile("unexpected.md", "content".getBytes(StandardCharsets.UTF_8), false)));
        var output = new GenerationOutput(invalid, new VerificationResult(true, true, true, 1, List.of()), invalid.sha256(), SpecFidelityReport.empty(), "CONVERGED", reported,
                GenerationOutput.AccountingState.COMPLETE, "draft");
        deliveries.add(event(1, WorkerEvent.Type.FINISHED, output));

        var outcome = run(() -> false);

        assertThat(outcome.isMechanicallyVerified()).isFalse();
        assertThat(outcome.hasCapturedArtifacts()).isFalse();
        assertThat(outcome.errorMessage()).contains("rejected");
        verify(usage, never()).markUncertain();
        verify(jobs, never()).retainUnsavedArtifacts(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
        verify(workers).release(claim);
    }

    private GenerationOutcome run(BooleanSupplier stop) {
        return service.generate(exercise, user, "generate a stack", "job", GenerationMode.GENERATE, stop, progress, fileUpdates::add, usage, "original brief",
                Instant.now().plusSeconds(600), null);
    }

    private GenerationOutput output(boolean verified, String profile) {
        return new GenerationOutput(snapshot, new VerificationResult(verified, verified, verified, verified ? 1 : 0, List.of()), verified ? snapshot.sha256() : null,
                SpecFidelityReport.empty(), "CONVERGED", null, GenerationOutput.AccountingState.INCOMPLETE, profile);
    }

    private WorkerEvent event(long sequence, WorkerEvent.Type type, GenerationOutput output) {
        return new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", claim.identity().workerIncarnation(), sequence, Instant.now(),
                type, claim.identity(), false, claim.imageDigest(), "progress", null, output);
    }
}
