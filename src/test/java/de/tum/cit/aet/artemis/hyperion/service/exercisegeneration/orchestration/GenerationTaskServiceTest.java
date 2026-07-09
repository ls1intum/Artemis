package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseAdaptationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationRecoveryService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit test for {@link GenerationTaskService}'s terminal-state contract (the class javadoc's guarantee): every path ends in exactly one distinct terminal event — {@code SUCCESS}
 * (verified and saved), {@code NEEDS_REVIEW} (best-effort draft saved with review comments), {@code PARTIAL}/{@code CANCELLED}/{@code ERROR} (nothing persisted) — and the
 * {@link GenerationOutcome} is always closed so the sandbox is destroyed on every path. Collaborators are mocked; the outcome holds the mock orchestrator so closing it is
 * observable as a {@code destroyQuietly} call.
 */
class GenerationTaskServiceTest {

    private static final long EXERCISE_ID = 55L;

    private static final String JOB_ID = "job-1";

    private static final String SESSION_ID = "sess-1";

    private GenerationOrchestrationService orchestrator;

    private GenerationPersistenceService persistenceService;

    private GenerationRecoveryService recoveryService;

    private HyperionWebsocketService websocket;

    private GenerationJobService jobService;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private HyperionGenerationBudgetService generationBudgetService;

    private ExerciseAdaptationRevertService adaptationRevertService;

    private TaskScheduler taskScheduler;

    private InteractiveSandbox sandbox;

    private GenerationTaskService taskService;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        orchestrator = mock(GenerationOrchestrationService.class);
        persistenceService = mock(GenerationPersistenceService.class);
        recoveryService = mock(GenerationRecoveryService.class);
        websocket = mock(HyperionWebsocketService.class);
        jobService = mock(GenerationJobService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        generationBudgetService = mock(HyperionGenerationBudgetService.class);
        adaptationRevertService = mock(ExerciseAdaptationRevertService.class);
        taskScheduler = mock(TaskScheduler.class);
        sandbox = mock(InteractiveSandbox.class);

        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(java.time.Duration.class));

        taskService = new GenerationTaskService(orchestrator, persistenceService, recoveryService, websocket, jobService, programmingExerciseRepository, generationBudgetService,
                adaptationRevertService, taskScheduler, java.time.Duration.ofMinutes(30), 250_000, java.time.Duration.ofSeconds(15));

        user = new User();
        user.setLogin("instructor1");
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
        when(jobService.isActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(jobService.tokenUsageSink(any(), any(), any())).thenReturn(response -> {
        });
    }

    /** Builds a run-completing outcome that holds the mock orchestrator + sandbox, so try-with-resources close() is observable as a destroyQuietly call. */
    private GenerationOutcome outcomeWith(AgentLoopResult.Status status, VerificationResult verification) {
        return new GenerationOutcome(new AgentLoopResult(status, 5, "done"), verification, SESSION_ID, orchestrator, sandbox, Map.of(), "", SpecFidelityReport.empty(), Map.of());
    }

    private void run(GenerationMode mode, GenerationOutcome outcome) {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(outcome);
        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", mode));
    }

    private static ChatResponse responseWithTokens(long promptTokens, long completionTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn((int) promptTokens);
        when(usage.getCompletionTokens()).thenReturn((int) completionTokens);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        ChatResponse response = mock(ChatResponse.class);
        when(response.getMetadata()).thenReturn(metadata);
        return response;
    }

    /** The events pushed to the live client, in order. */
    private List<ExerciseGenerationEventDTO> sentEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(websocket, org.mockito.Mockito.atLeastOnce()).send(eq("instructor1"), anyString(), captor.capture());
        return captor.getAllValues().stream().filter(ExerciseGenerationEventDTO.class::isInstance).map(ExerciseGenerationEventDTO.class::cast).toList();
    }

    @Test
    void acceptedRun_persistsAndEmitsSuccess_thenClosesTheOutcome() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        List<ExerciseGenerationEventDTO> events = sentEvents();
        assertThat(events.stream().map(ExerciseGenerationEventDTO::type)).startsWith(ExerciseGenerationEventDTO.Type.STARTED).endsWith(ExerciseGenerationEventDTO.Type.DONE);
        ExerciseGenerationEventDTO terminal = events.getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.verdict()).isNotNull();
        assertThat(terminal.verdict().accepted()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isTrue();
        // At least one PROGRESS line (e.g. the "saving" step) precedes the terminal DONE on the accepted path.
        assertThat(events).anyMatch(event -> event.type() == ExerciseGenerationEventDTO.Type.PROGRESS);
    }

    @Test
    void mixedProgressAndFileSnapshots_arePushedInProductionOrderOnTheSameStream() {
        GenerationOutcome outcome = outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(6);
            @SuppressWarnings("unchecked")
            Consumer<ExerciseGenerationFileSnapshotDTO> fileSnapshotSink = invocation.getArgument(7);
            progress.accept("planning files");
            fileSnapshotSink.accept(new ExerciseGenerationFileSnapshotDTO(ExerciseGenerationFileSnapshotDTO.TYPE, "solution/src/Counter.java",
                    ExerciseGenerationFileSnapshotDTO.REPOSITORY_SOLUTION, ExerciseGenerationFileSnapshotDTO.ACTION_EDIT, "class Counter {}", "sha", 16, false, 1, null));
            progress.accept("running verifier");
            return outcome;
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(websocket, Mockito.atLeastOnce()).send(eq("instructor1"), anyString(), captor.capture());
        assertThat(captor.getAllValues()).extracting(message -> {
            if (message instanceof ExerciseGenerationEventDTO event) {
                return event.type().name();
            }
            if (message instanceof ExerciseGenerationFileSnapshotDTO) {
                return "FILE_SNAPSHOT";
            }
            return message.getClass().getSimpleName();
        }).containsSubsequence("STARTED", "PROGRESS", "FILE_SNAPSHOT", "PROGRESS", "PROGRESS", "DONE");
    }

    @Test
    void acceptedRun_persistsExactlyOnceAndDestroysTheSandbox() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of()));
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        verify(jobService).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), any(), any(), any());
        // A GENERATE run records no revert baseline.
        verify(adaptationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any());
        // The outcome is always closed → the sandbox is destroyed on the accepted path.
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void acceptedRun_usesStartTimeProblemStatementAndTitleAsPersistenceGuard() {
        exercise.setProblemStatement("Original problem statement");
        exercise.setTitle("Original title");
        GenerationStartedEvent event = new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT);
        exercise.setProblemStatement("Manual edit while Hyperion was running");
        exercise.setTitle("Manual title edit while Hyperion was running");
        when(persistenceService.persist(any(), any(), any(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha")));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(event);

        verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), eq("Original problem statement"), eq("Original title"), any());
        verify(adaptationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), any(), any(), eq("Original problem statement"), eq("Original title"));
    }

    @Test
    void acceptedRun_whenMultiRepoPersistIsIncomplete_emitsStructuredPartial() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), any()))
                .thenThrow(new GenerationIncompleteException("already-committed repositories were reverted", new RuntimeException()));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.verdict().accepted()).isTrue();
    }

    @Test
    void acceptedAdaptRun_recordsARevertBaseline() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha")));
        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        // Only an accepted ADAPT applied in place records a revertible baseline.
        verify(adaptationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), any(), any(), any(), any());
    }

    @Test
    void acceptedAdaptRun_recordsBaselineFromFreshlyReloadedPersistedExercise() {
        ProgrammingExercise exerciseToPersist = new ProgrammingExercise();
        exerciseToPersist.setId(EXERCISE_ID);
        exerciseToPersist.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseToPersist.setProblemStatement("Original problem statement");
        exerciseToPersist.setTitle("Original title");

        ProgrammingExercise persistedExercise = new ProgrammingExercise();
        persistedExercise.setId(EXERCISE_ID);
        persistedExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        persistedExercise.setProblemStatement("Persisted problem statement after save hooks");
        persistedExercise.setTitle("Original title");

        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise), Optional.of(exerciseToPersist),
                Optional.of(persistedExercise));
        when(persistenceService.persist(any(), any(), any(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha")));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT));

        verify(persistenceService).persist(eq(exerciseToPersist), eq(user), any(GenerationOutcome.class), any(), any(), any());
        verify(adaptationRevertService).recordBaseline(eq(persistedExercise), eq(JOB_ID), any(), any(), eq(exercise.getProblemStatement()), eq(exercise.getTitle()));
        verify(recoveryService).surfaceAdvisoryFindings(eq(persistedExercise), any());
    }

    @Test
    void nonAcceptedButRecoverableRun_savesDraftAndEmitsNeedsReview() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenReturn(new GenerationRecoveryService.RecoveryResult(2, true, "hyperion-draft/job-1"));
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isFalse();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        // A rejected outcome never goes through the clean persist path; recovery owns the draft save.
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
        verify(jobService).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
    }

    @Test
    void completedRun_cancelledBeforePersistence_emitsCancelledAndDoesNotSave() {
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(false);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void staleAsyncWorkerStart_emitsCancelledWithoutReloadingOrSettingUpSandbox() {
        when(jobService.isActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(false);

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("superseded or expired");
        verify(programmingExerciseRepository, never()).findWithAllParticipationsAndBuildConfigById(EXERCISE_ID);
        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void admissionDeadlineExpiredBeforeAsyncWorkerStarts_emitsCancelledWithoutReloadingOrSettingUpSandbox() {
        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(),
                java.time.Instant.now().minusSeconds(1)));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("time limit");
        verify(programmingExerciseRepository, never()).findWithAllParticipationsAndBuildConfigById(EXERCISE_ID);
        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void recoveryPersistFailure_downgradesToPartial() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenThrow(new RuntimeException("draft persist failed"));
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        // Only when the draft persist itself fails does the run report PARTIAL (nothing durable saved; the instructor can retry).
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
    }

    @Test
    void budgetExhaustedNotAcceptedRun_savesDraftEmitsNeedsReview_andDestroysSession() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenReturn(new GenerationRecoveryService.RecoveryResult(1, true, "hyperion-draft/job-1"));
        // A budget-exhausted run is still verified: a rejected verdict flows through the recovery path exactly like a COMPLETED near-miss, never the clean persist.
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.BUDGET_EXHAUSTED, new VerificationResult(false, false, true, 3, List.of("template passed a graded test"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isFalse();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
        // The outcome is always closed → the sandbox is destroyed even on the budget-exhausted path, and the slot is cleared.
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void nonAcceptedAdaptDraftOnIsolatedBranch_emitsNeedsReviewWithoutLiveRefreshHint() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenReturn(new GenerationRecoveryService.RecoveryResult(1, true, "hyperion-draft/job"));
        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.liveExerciseChanged()).isFalse();
    }

    @Test
    void cancelledRun_emitsCancelled_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 2, "")));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void deadlineExceeded_requestsSystemCancellationAndEmitsSpecificTerminalMessage() {
        ArgumentCaptor<Runnable> deadline = ArgumentCaptor.forClass(Runnable.class);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            verify(taskScheduler).schedule(deadline.capture(), any(java.time.Instant.class));
            deadline.getValue().run();
            return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("time limit");
        verify(jobService).requestSystemCancellation(EXERCISE_ID, JOB_ID);
    }

    @Test
    void tokenBudgetExceeded_requestsSystemCancellationAndEmitsSpecificTerminalMessage() {
        taskService = new GenerationTaskService(orchestrator, persistenceService, recoveryService, websocket, jobService, programmingExerciseRepository, generationBudgetService,
                adaptationRevertService, taskScheduler, java.time.Duration.ofMinutes(30), 10, java.time.Duration.ofSeconds(15));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("token budget");
        verify(jobService).requestSystemCancellation(EXERCISE_ID, JOB_ID);
    }

    @Test
    void erroredRun_emitsError_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "")));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
    }

    @Test
    void deletedExercise_failsClosedWithError_withoutRunningTheOrchestrator() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.empty());

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        // The run must not start the expensive orchestration for an exercise that no longer exists.
        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void cancellationBeforeAsyncWorkerStarts_emitsCancelledWithoutReloadingOrSettingUpSandbox() {
        when(jobService.isCancelled(JOB_ID)).thenReturn(true);

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(programmingExerciseRepository, never()).findWithAllParticipationsAndBuildConfigById(EXERCISE_ID);
        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void cleanup_releasesBudgetReservationEvenWhenClearJobFailsOnEarlyExit() {
        when(jobService.isActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(false);
        Mockito.doThrow(new RuntimeException("clear failed")).when(jobService).clearJob(EXERCISE_ID, JOB_ID);

        assertThatThrownBy(() -> taskService.runAsync(
                new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null, "reservation-1")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("clear failed");

        verify(generationBudgetService).releaseReservation("reservation-1");
    }

    @Test
    void cleanup_releasesBudgetReservationEvenWhenFinalClearJobFails() {
        Mockito.doThrow(new RuntimeException("clear failed")).when(jobService).clearJob(EXERCISE_ID, JOB_ID);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, "")));

        assertThatThrownBy(() -> taskService.runAsync(
                new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null, "reservation-2")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("clear failed");

        verify(generationBudgetService).releaseReservation("reservation-2");
    }
}
