package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
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
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationRecoveryService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

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

    private ExerciseGenerationRevertService generationRevertService;

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
        generationRevertService = mock(ExerciseGenerationRevertService.class);
        taskScheduler = mock(TaskScheduler.class);
        sandbox = mock(InteractiveSandbox.class);

        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(java.time.Duration.class));

        taskService = new GenerationTaskService(orchestrator, persistenceService, recoveryService, websocket, jobService, programmingExerciseRepository, generationBudgetService,
                generationRevertService, taskScheduler, java.time.Duration.ofMinutes(30), 250_000, java.time.Duration.ofSeconds(15));

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
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main"));
        when(generationRevertService.recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(true);
    }

    /** Builds a run-completing outcome that holds the mock orchestrator + sandbox, so try-with-resources close() is observable as a destroyQuietly call. */
    private GenerationOutcome outcomeWith(AgentLoopResult.Status status, VerificationResult verification) {
        return outcomeWith(status, verification, SpecFidelityReport.empty());
    }

    private GenerationOutcome outcomeWith(AgentLoopResult.Status status, VerificationResult verification, SpecFidelityReport specFidelityReport) {
        return new GenerationOutcome(new AgentLoopResult(status, 5, "done"), verification, SESSION_ID, orchestrator, sandbox, Map.of(), "", specFidelityReport, Map.of());
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
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main"));
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        verify(jobService).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        var order = Mockito.inOrder(orchestrator, persistenceService);
        order.verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        order.verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), any(), any(), eq(JOB_ID), any());
        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any(), any(), any(), any(), any(), eq("main"));
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
        List<ExerciseGenerationEventDTO> events = sentEvents();
        assertThat(events.stream().map(ExerciseGenerationEventDTO::type)).startsWith(ExerciseGenerationEventDTO.Type.STARTED).endsWith(ExerciseGenerationEventDTO.Type.DONE);
        ExerciseGenerationEventDTO terminal = events.getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.verdict().accepted()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(events).anyMatch(event -> event.type() == ExerciseGenerationEventDTO.Type.PROGRESS);
    }

    @Test
    void transientHeartbeatFailure_doesNotSuppressLaterHeartbeatAttempts() {
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        when(jobService.heartbeat(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable")).thenReturn(true);
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.CANCELLED, null));
        verify(taskScheduler).scheduleWithFixedDelay(heartbeat.capture(), any(java.time.Duration.class));

        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();
        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();

        verify(jobService, Mockito.times(2)).heartbeat(EXERCISE_ID, JOB_ID);
        verify(jobService, never()).requestSystemCancellation(EXERCISE_ID, JOB_ID);
    }

    @Test
    void acceptedRun_usesStartTimeProblemStatementAndTitleAsPersistenceGuard() {
        exercise.setProblemStatement("Original problem statement");
        exercise.setTitle("Original title");
        GenerationStartedEvent event = new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT);
        exercise.setProblemStatement("Manual edit while Hyperion was running");
        exercise.setTitle("Manual title edit while Hyperion was running");
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any())).thenReturn(new GenerationPersistenceService.PersistResult(
                Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha"), "Persisted statement", "Persisted title", "release"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(event);

        verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), eq("Original problem statement"), eq("Original title"), eq(JOB_ID), any());
        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), eq("Original problem statement"), eq("Original title"),
                eq("Persisted statement"), eq("Persisted title"), eq("release"));
        assertThat(sentEvents().getLast().message()).contains("adapted and saved").doesNotContain("generated and saved");
    }

    @Test
    void acceptedRun_describesAdvisoryReviewNotesInPlainLanguage() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "rollback interaction",
                "A worked example would clarify how rollback preserves the previous state.")));
        when(recoveryService.surfaceAdvisoryFindings(any(), any(), any())).thenReturn(1);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        assertThat(sentEvents().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(sentEvents().getLast().liveExerciseChanged()).isTrue();
        assertThat(sentEvents().getLast().message()).contains("1 review note was added").doesNotContain("spec-fidelity").doesNotContain("note(s)");
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(recoveryService).surfaceAdvisoryFindings(eq(exercise), eq(user), eq(report));
        verify(recoveryService, never()).recover(any(), any(), any(), anyString(), any());
    }

    @Test
    void acceptedRun_whenMultiRepoPersistIsIncomplete_emitsStructuredPartial() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any()))
                .thenThrow(new GenerationIncompleteException("already-committed repositories were reverted", new RuntimeException()));
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job-1", Set.of(RepositoryType.SOLUTION, RepositoryType.TESTS)));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.verdict().accepted()).isTrue();
        assertThat(terminal.message()).contains("may already have been saved", "manual review", "hyperion-draft/job-1", "verified candidate");
        assertThat(terminal.message()).doesNotContain("already-committed repositories");
        verify(recoveryService).recover(any(), any(), any(), eq(JOB_ID), any());
    }

    @Test
    void acceptedRun_whenIncompletePersistAndRecoveryBothFail_stillRefreshesPotentiallyChangedLiveState() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any()))
                .thenThrow(new GenerationIncompleteException("live state may be partial", new RuntimeException()));
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenThrow(new RuntimeException("draft persist failed"));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("may already have been saved", "could not be fully preserved");
    }

    @Test
    void acceptedRun_whenRevertCheckpointFails_reportsSuccessfulSaveWithoutHidingTheDegradation() {
        when(generationRevertService.recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(false);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("generated and saved", "Automatic revert is unavailable");
    }

    @Test
    void acceptedRun_whenSavingFailsBeforeLiveMutation_preservesTheVerifiedCandidateForReview() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any())).thenThrow(new IllegalStateException("metadata changed"));
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job-1", Set.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION)));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        assertThat(terminal.message()).contains("Saving the live exercise failed", "verified candidate", "hyperion-draft/job-1").doesNotContain("metadata changed");
        verify(recoveryService).recover(any(), any(), any(), eq(JOB_ID), any());
    }

    @Test
    void acceptedAdaptRun_recordsExactPersistedMetadataWithoutARacyReload() {
        ProgrammingExercise exerciseToPersist = new ProgrammingExercise();
        exerciseToPersist.setId(EXERCISE_ID);
        exerciseToPersist.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseToPersist.setProblemStatement("Original problem statement");
        exerciseToPersist.setTitle("Original title");

        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise), Optional.of(exerciseToPersist));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any())).thenReturn(new GenerationPersistenceService.PersistResult(
                Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha"), "Exact persisted statement", "Exact persisted title", "release"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT));

        verify(persistenceService).persist(eq(exerciseToPersist), eq(user), any(GenerationOutcome.class), any(), any(), eq(JOB_ID), any());
        verify(generationRevertService).recordBaseline(eq(exerciseToPersist), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), eq(exercise.getProblemStatement()),
                eq(exercise.getTitle()), eq("Exact persisted statement"), eq("Exact persisted title"), eq("release"));
        verify(recoveryService).surfaceAdvisoryFindings(eq(exerciseToPersist), eq(user), any());
    }

    @Test
    void acceptedRun_doesNotReportFailureAfterPersistenceSucceeded() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise), Optional.of(exercise));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main"));

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), any(), any(), eq(exercise.getProblemStatement()),
                eq(exercise.getTitle()), eq("main"));
        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
    }

    @Test
    void nonAcceptedButRecoverableRun_savesDraftAndEmitsNeedsReview() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any())).thenReturn(
                new GenerationRecoveryService.RecoveryResult(2, "hyperion-draft/job-1", Set.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)));
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isFalse();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        assertThat(terminal.message()).contains("repository files", "problem statement did not differ from the live exercise");
        // A rejected outcome never goes through the clean persist path; recovery owns the draft save.
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(jobService).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
    }

    @Test
    void completedRun_cancelledBeforePersistence_emitsCancelledAndDoesNotSave() {
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(false);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
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
        // Only when the draft persist itself fails does the run report PARTIAL; an earlier repository branch may already exist.
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.message()).contains("partial hyperion-draft branch", "reviewed or deleted manually");
        assertThat(terminal.message()).doesNotContain("draft persist failed");
    }

    @Test
    void budgetExhaustedNotAcceptedRun_savesDraftEmitsNeedsReview_andDestroysSession() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job-1", Set.of(RepositoryType.TEMPLATE)));
        // A budget-exhausted run is still verified: a rejected verdict flows through the recovery path exactly like a COMPLETED near-miss, never the clean persist.
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.BUDGET_EXHAUSTED, new VerificationResult(false, false, true, 3, List.of("template passed a graded test"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isFalse();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        // The outcome is always closed → the sandbox is destroyed even on the budget-exhausted path, and the slot is cleared.
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void nonAcceptedAdaptDraftOnIsolatedBranch_emitsNeedsReviewWithoutLiveRefreshHint() {
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job", Set.of(RepositoryType.TEMPLATE)));
        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.liveExerciseChanged()).isFalse();
    }

    @Test
    void verifiedAdaptationWithBlockingScopeFinding_savesReviewDraftWithoutLiveMutation() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE,
                "solution removed displayName", "The feedback required preserving displayName.")));
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job", Set.of(RepositoryType.SOLUTION)));

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().accepted()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isFalse();
        assertThat(terminal.message()).contains("1 issue to review", "requirements issue", "review notes").doesNotContain("contract issue",
                "The feedback required preserving displayName");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(recoveryService).recover(any(), any(), any(), anyString(), any());
    }

    @Test
    void rejectedAdaptationWithScopeFinding_reportsVerificationFailureAsPrimaryReason() {
        SpecFidelityReport report = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE, "solution added reset()", "The feedback did not request reset().")));
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job", Set.of(RepositoryType.SOLUTION)));

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed")), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.message()).contains("did not pass verification", "exercise-quality review also found", "review notes").doesNotContain("solution failed")
                .doesNotContain("Build and grading checks passed");
    }

    @Test
    void cancelledRun_emitsCancelled_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 2, "")));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void deadlineExceeded_stopsCooperativelyAndEmitsSpecificTerminalMessage() {
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
    void deadlineExceededDuringPersistence_closesTheMutationGuard() {
        ArgumentCaptor<Runnable> deadline = ArgumentCaptor.forClass(Runnable.class);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            verify(taskScheduler).schedule(deadline.capture(), any(java.time.Instant.class));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            deadline.getValue().run();
            BooleanSupplier mutationGuard = invocation.getArgument(6);
            assertThat(mutationGuard.getAsBoolean()).isFalse();
            throw new GenerationIncompleteException("deadline closed the mutation guard", new IllegalStateException());
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
    }

    @Test
    void exerciseBecomingIneligibleDuringPersistence_closesTheMutationGuard() {
        when(jobService.isOwnedActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(EXERCISE_ID)).thenReturn(true, false);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(1, "hyperion-draft/job-1", Set.of(RepositoryType.SOLUTION)));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            BooleanSupplier mutationGuard = invocation.getArgument(6);
            assertThat(mutationGuard.getAsBoolean()).isTrue();
            assertThat(mutationGuard.getAsBoolean()).isFalse();
            throw new GenerationIncompleteException("exercise became ineligible while saving", new IllegalStateException());
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
    }

    @Test
    void tokenBudgetExceeded_stopsCooperativelyAndEmitsSpecificTerminalMessage() {
        taskService = new GenerationTaskService(orchestrator, persistenceService, recoveryService, websocket, jobService, programmingExerciseRepository, generationBudgetService,
                generationRevertService, taskScheduler, java.time.Duration.ofMinutes(30), 10, java.time.Duration.ofSeconds(15));
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
        verify(jobService, never()).requestSystemCancellation(EXERCISE_ID, JOB_ID);
    }

    @Test
    void erroredRun_emitsError_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "")));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
    }

    @Test
    void erroredRunWithChangedArtifacts_preservesAnIsolatedReviewDraft() {
        GenerationOutcome outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.ERROR, 4, "Provider stopped responding"), null, SESSION_ID, orchestrator,
                sandbox, Map.of(RepositoryType.SOLUTION, Map.of("src/Library.java", "class Library {}")), "Improved statement",
                SpecFidelityReport.qualityReviewUnavailable("The partial candidate was not verified."), Map.of());
        when(recoveryService.recover(any(), any(), any(), anyString(), any()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(2, "hyperion-draft/job-1", Set.of(RepositoryType.SOLUTION)));

        run(GenerationMode.GENERATE, outcome);

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.message()).contains("live exercise was left unchanged", "hyperion-draft/job-1", "problem statement was preserved");
        assertThat(terminal.message()).contains("not verified", "review notes").doesNotContain("stopped before verification", "within the budget");
        verify(recoveryService).recover(eq(exercise), eq(user), eq(outcome), eq(JOB_ID), any());
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any());
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
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
