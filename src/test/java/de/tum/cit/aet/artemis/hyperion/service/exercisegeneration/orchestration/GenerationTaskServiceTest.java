package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationReviewService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import io.micrometer.observation.ObservationRegistry;

class GenerationTaskServiceTest {

    private static final long EXERCISE_ID = 55L;

    private static final String JOB_ID = "job-1";

    private static final String SESSION_ID = "sess-1";

    private enum PostSaveStop {
        HEARTBEAT_BEFORE_BASELINE, OWNERSHIP_BEFORE_BASELINE, HEARTBEAT_BEFORE_SUCCESS
    }

    private GenerationOrchestrationService orchestrator;

    private GenerationPersistenceService persistenceService;

    private GenerationReviewService reviewService;

    private HyperionWebsocketService websocket;

    private GenerationJobService jobService;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

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
        reviewService = mock(GenerationReviewService.class);
        websocket = mock(HyperionWebsocketService.class);
        jobService = mock(GenerationJobService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        auxiliaryRepositoryRepository = mock(de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository.class);
        when(auxiliaryRepositoryRepository.findByExerciseId(any())).thenReturn(List.of());
        generationBudgetService = mock(HyperionGenerationBudgetService.class);
        generationRevertService = mock(ExerciseGenerationRevertService.class);
        taskScheduler = mock(TaskScheduler.class);
        sandbox = mock(InteractiveSandbox.class);

        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        org.mockito.Mockito.doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(java.time.Duration.class));

        taskService = new GenerationTaskService(orchestrator, persistenceService, reviewService, websocket, jobService, programmingExerciseRepository,
                auxiliaryRepositoryRepository, generationBudgetService, generationRevertService, taskScheduler, ObservationRegistry.NOOP, java.time.Duration.ofMinutes(30), 250_000,
                java.time.Duration.ofSeconds(15));

        user = new User();
        user.setLogin("instructor1");
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
        when(jobService.isActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(jobService.isOwnedActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(jobService.recordEvent(anyLong(), anyString(), any(), anyBoolean())).thenReturn(true);
        when(jobService.recordFileChange(anyLong(), anyString(), any())).thenReturn(true);
        when(jobService.tokenUsageSink(any(), any(), any(), any(), any())).thenReturn(response -> {
        });
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(Map.of(),
                Map.of(RepositoryType.SOLUTION, "solution-commit"), exercise.getProblemStatement(), exercise.getTitle(), "main", true, 17L));
        when(generationRevertService.recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(true);
    }

    @Test
    void reloadedExerciseWithAuxiliaryRepositoryStopsBeforeOrchestration() {
        // Auxiliary repositories are queried explicitly rather than through the reloaded entity's lazy collection, which cannot be initialized on a detached instance.
        ProgrammingExercise reloadedExercise = new ProgrammingExercise();
        reloadedExercise.setId(EXERCISE_ID);
        reloadedExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        reloadedExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(reloadedExercise));
        when(auxiliaryRepositoryRepository.findByExerciseId(EXERCISE_ID)).thenReturn(List.of(new AuxiliaryRepository()));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
    }

    /** Builds a run-completing outcome that holds the mock orchestrator + sandbox, so try-with-resources close() is observable as a destroyQuietly call. */
    private GenerationOutcome outcomeWith(AgentLoopResult.Status status, VerificationResult verification) {
        return outcomeWith(status, verification, SpecFidelityReport.empty());
    }

    private GenerationOutcome outcomeWith(AgentLoopResult.Status status, VerificationResult verification, SpecFidelityReport specFidelityReport) {
        return new GenerationOutcome(new AgentLoopResult(status, 5, "done"), verification, SESSION_ID, orchestrator, sandbox, Map.of(), "", specFidelityReport, Map.of());
    }

    private void run(GenerationMode mode, GenerationOutcome outcome) {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(outcome);
        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", mode));
    }

    @Test
    void originalSourceBriefIsForwardedToOrchestration() {
        GenerationOutcome outcome = GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, ""));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("original instructor brief"), any())).thenReturn(outcome);
        GenerationStartedEvent event = new GenerationStartedEvent(JOB_ID, user, exercise, "resolved instruction", GenerationMode.GENERATE, exercise.getProblemStatement(),
                exercise.getTitle(), null, null, "original instructor brief");

        taskService.runAsync(event);

        verify(orchestrator).generate(eq(exercise), eq(user), eq("resolved instruction"), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any(), any(), any(),
                eq("original instructor brief"), any());
    }

    private static ChatResponse responseWithTokens(long promptTokens, long completionTokens) {
        return responseWithTokens(promptTokens, completionTokens, null);
    }

    private static ChatResponse responseWithTokens(long promptTokens, long completionTokens, @Nullable Long cachedInputTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn((int) promptTokens);
        when(usage.getCompletionTokens()).thenReturn((int) completionTokens);
        when(usage.getCacheReadInputTokens()).thenReturn(cachedInputTokens);
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
    void mixedProgressAndFileChanges_arePushedInProductionOrderOnTheSameStream() {
        GenerationOutcome outcome = outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(6);
            @SuppressWarnings("unchecked")
            Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink = invocation.getArgument(7);
            progress.accept("planning files");
            fileChangeSink.accept(new ExerciseGenerationFileChangeDTO(ExerciseGenerationFileChangeDTO.TYPE, "solution/src/Counter.java",
                    ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION, ExerciseGenerationFileChangeDTO.ACTION_EDIT, 1, Instant.now()));
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
            if (message instanceof ExerciseGenerationFileChangeDTO) {
                return "FILE_CHANGE";
            }
            return message.getClass().getSimpleName();
        }).containsSubsequence("STARTED", "PROGRESS", "FILE_CHANGE", "PROGRESS", "PROGRESS", "DONE");
    }

    @Test
    void rejectedFileChange_isNotBroadcast() {
        ExerciseGenerationFileChangeDTO fileChange = ExerciseGenerationFileChangeDTO.of("solution/src/Counter.java", ExerciseGenerationFileChangeDTO.ACTION_EDIT, 1);
        when(jobService.recordFileChange(EXERCISE_ID, JOB_ID, fileChange)).thenReturn(false);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink = invocation.getArgument(7);
            fileChangeSink.accept(fileChange);
            return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(websocket, Mockito.atLeastOnce()).send(eq("instructor1"), anyString(), captor.capture());
        assertThat(captor.getAllValues()).doesNotContain(fileChange);
        verify(jobService).recordFileChange(EXERCISE_ID, JOB_ID, fileChange);
    }

    @Test
    void mechanicallyVerifiedRun_persistsExactlyOnceAndDestroysTheSandbox() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Runnable beforeDurableMutation = invocation.getArgument(8);
            beforeDurableMutation.run();
            return new GenerationPersistenceService.PersistResult(Map.of(), Map.of(RepositoryType.SOLUTION, "solution-commit"), exercise.getProblemStatement(), exercise.getTitle(),
                    "main");
        });
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        verify(jobService).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        var order = Mockito.inOrder(orchestrator, persistenceService);
        order.verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        order.verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), any(), any(), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any());
        verify(generationRevertService).invalidateBaseline(EXERCISE_ID);
        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any(), any(), any(), any(), any(), eq("main"));
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
        List<ExerciseGenerationEventDTO> events = sentEvents();
        assertThat(events.stream().map(ExerciseGenerationEventDTO::type)).startsWith(ExerciseGenerationEventDTO.Type.STARTED).endsWith(ExerciseGenerationEventDTO.Type.DONE);
        ExerciseGenerationEventDTO terminal = events.getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.verdict().mechanicallyVerified()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.savedRepositoryCommits()).containsExactlyEntriesOf(Map.of("solution", "solution-commit"));
        assertThat(events).anyMatch(event -> event.type() == ExerciseGenerationEventDTO.Type.PROGRESS);
    }

    @Test
    void mechanicallyVerifiedRun_recordsTheCapturedSpecDocumentAsSoonAsTheOutcomeLands() {
        GenerationOutcome outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 5, "done"), new VerificationResult(true, true, true, 3, List.of()),
                SESSION_ID, orchestrator, sandbox, Map.of(), "", SpecFidelityReport.empty(), Map.of(), "## Rules\n- R1: computes", null);

        run(GenerationMode.GENERATE, outcome);

        verify(jobService).recordSpecDocument(EXERCISE_ID, JOB_ID, "## Rules\n- R1: computes");
    }

    @Test
    void run_withoutACapturedSpecDocument_neverRecordsOne() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.CANCELLED, null));

        verify(jobService, never()).recordSpecDocument(anyLong(), anyString(), any());
    }

    @Test
    void unsavedRunThatProducedFiles_retainsThemForInspectionWithoutTouchingTheExercise() {
        GenerationOutcome outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 5, "done"),
                new VerificationResult(false, true, false, 0, List.of()), SESSION_ID, orchestrator, sandbox,
                Map.of(RepositoryType.SOLUTION, Map.of("src/Stack.java", "class Stack {}")), "# Bounded stack", SpecFidelityReport.empty(), Map.of(),
                "## Rules\n- R1: push then pop", null);

        run(GenerationMode.GENERATE, outcome);

        ArgumentCaptor<ExerciseGenerationRetainedArtifactsDTO> retained = ArgumentCaptor.forClass(ExerciseGenerationRetainedArtifactsDTO.class);
        verify(jobService).retainUnsavedArtifacts(eq(EXERCISE_ID), eq(JOB_ID), eq(user.getLogin()), retained.capture());
        assertThat(retained.getValue().files()).singleElement().satisfies(file -> assertThat(file.path()).isEqualTo("src/Stack.java"));
        assertThat(retained.getValue().problemStatement()).isEqualTo("# Bounded stack");
        assertThat(retained.getValue().specDocument()).contains("push then pop");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(sentEvents().getLast().liveExerciseChanged()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void savedRun_retainsNothingBecauseTheWorkIsAlreadyInTheExercise() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(Map.of(),
                Map.of(RepositoryType.SOLUTION, "solution-commit"), exercise.getProblemStatement(), exercise.getTitle(), "main"));
        GenerationOutcome outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 5, "done"), new VerificationResult(true, true, true, 3, List.of()),
                SESSION_ID, orchestrator, sandbox, Map.of(RepositoryType.SOLUTION, Map.of("src/Stack.java", "class Stack {}")), "# Bounded stack", SpecFidelityReport.empty(),
                Map.of());

        run(GenerationMode.GENERATE, outcome);

        verify(jobService, never()).retainUnsavedArtifacts(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void unsavedRunThatProducedNothing_retainsNothing() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.CANCELLED, null));

        verify(jobService, never()).retainUnsavedArtifacts(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void heartbeatFailure_doesNotSuppressLaterHeartbeatAttempts() {
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        when(jobService.heartbeat(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable")).thenReturn(true);
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.CANCELLED, null));
        verify(taskScheduler).scheduleWithFixedDelay(heartbeat.capture(), any(java.time.Duration.class));

        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();
        assertThatCode(heartbeat.getValue()::run).doesNotThrowAnyException();

        verify(jobService, Mockito.times(2)).heartbeat(EXERCISE_ID, JOB_ID);
        verify(jobService).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), argThat(message -> message.contains("lost ownership")));
    }

    @Test
    void heartbeatOwnershipValidationException_abortsBeforePersistenceOrReviewAttachment() {
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        when(jobService.heartbeat(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            verify(taskScheduler).scheduleWithFixedDelay(heartbeat.capture(), any(java.time.Duration.class));
            heartbeat.getValue().run();
            BooleanSupplier shouldCancel = invocation.getArgument(5);
            assertThat(shouldCancel.getAsBoolean()).isTrue();
            return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("lost ownership");
        verify(jobService).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), argThat(message -> message.contains("lost ownership")));
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void persistenceOwnershipValidationException_abortsWithoutReviewAttachment() {
        when(jobService.isOwnedActiveJob(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            BooleanSupplier stillOwnsMutationSlot = invocation.getArgument(7);
            assertThat(stillOwnsMutationSlot.getAsBoolean()).isFalse();
            throw new IllegalStateException("ownership guard closed");
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("ownership");
        verify(jobService).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), argThat(message -> message.contains("lost ownership")));
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void incompletePersistenceWithOwnershipLoss_reportsPartialAndSkipsReviewAttachment() {
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        when(jobService.heartbeat(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable"));
        when(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(EXERCISE_ID)).thenReturn(true);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            verify(taskScheduler).scheduleWithFixedDelay(heartbeat.capture(), any(java.time.Duration.class));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            BooleanSupplier stillOwnsMutationSlot = invocation.getArgument(7);
            assertThat(stillOwnsMutationSlot.getAsBoolean()).isTrue();
            Runnable beforeDurableMutation = invocation.getArgument(8);
            beforeDurableMutation.run();
            heartbeat.getValue().run();
            throw new GenerationIncompleteException("live save did not complete", new IllegalStateException());
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("Saving did not complete", "manual review is required");
        verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(generationRevertService).invalidateBaseline(EXERCISE_ID);
    }

    @ParameterizedTest(name = "post-save continuation stops at {0}")
    @EnumSource(PostSaveStop.class)
    void postSaveContinuationLoss_stopsLaterSideEffectsAndRequiresManualReview(PostSaveStop stop) {
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> deadline = ArgumentCaptor.forClass(Runnable.class);
        when(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(EXERCISE_ID)).thenReturn(true);
        if (stop == PostSaveStop.OWNERSHIP_BEFORE_BASELINE) {
            when(jobService.isOwnedActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true, false);
        }
        if (stop == PostSaveStop.HEARTBEAT_BEFORE_BASELINE || stop == PostSaveStop.HEARTBEAT_BEFORE_SUCCESS) {
            when(jobService.heartbeat(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("cluster temporarily unavailable"));
        }
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            verify(taskScheduler).schedule(deadline.capture(), any(java.time.Instant.class));
            verify(taskScheduler).scheduleWithFixedDelay(heartbeat.capture(), any(java.time.Duration.class));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            BooleanSupplier stillOwnsMutationSlot = invocation.getArgument(7);
            assertThat(stillOwnsMutationSlot.getAsBoolean()).isTrue();
            if (stop == PostSaveStop.HEARTBEAT_BEFORE_BASELINE) {
                heartbeat.getValue().run();
            }
            return new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main");
        });
        when(generationRevertService.recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(true);
        when(reviewService.attachFindings(any(), any(), any())).thenAnswer(invocation -> {
            if (stop == PostSaveStop.HEARTBEAT_BEFORE_SUCCESS) {
                heartbeat.getValue().run();
            }
            return 0;
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("save may already have completed", "manual review is required");
        switch (stop) {
            case HEARTBEAT_BEFORE_BASELINE, OWNERSHIP_BEFORE_BASELINE -> {
                verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
                verify(reviewService, never()).attachFindings(any(), any(), any());
            }
            case HEARTBEAT_BEFORE_SUCCESS -> {
                verify(generationRevertService).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
                verify(reviewService).attachFindings(any(), any(), any());
            }
        }
    }

    @Test
    void mechanicallyVerifiedRun_usesStartTimeProblemStatementAndTitleAsPersistenceGuard() {
        exercise.setProblemStatement("Original problem statement");
        exercise.setTitle("Original title");
        GenerationStartedEvent event = new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT);
        exercise.setProblemStatement("Manual edit while Hyperion was running");
        exercise.setTitle("Manual title edit while Hyperion was running");
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(
                Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha"), "Persisted statement", "Persisted title", "release"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(event);

        verify(persistenceService).persist(eq(exercise), eq(user), any(GenerationOutcome.class), eq("Original problem statement"), eq("Original title"), eq(JOB_ID),
                eq(GenerationMode.ADAPT), any(), any());
        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), eq("Original problem statement"), eq("Original title"),
                eq("Persisted statement"), eq("Persisted title"), eq("release"));
        assertThat(sentEvents().getLast().message()).contains("adapted and saved").doesNotContain("generated and saved");
    }

    @Test
    void mechanicallyVerifiedRun_describesAdvisoryReviewNotesInPlainLanguage() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "rollback interaction",
                "A worked example would clarify how rollback preserves the previous state.")));
        when(reviewService.attachFindings(any(), any(), any(), anyLong(), anyMap())).thenReturn(1);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        assertThat(sentEvents().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(sentEvents().getLast().liveExerciseChanged()).isTrue();
        assertThat(sentEvents().getLast().message()).contains("1 review note was added").doesNotContain("spec-fidelity").doesNotContain("note(s)");
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService).attachFindings(eq(exercise), eq(user), eq(report), eq(17L), eq(Map.of(RepositoryType.SOLUTION, "solution-commit")));
    }

    @Test
    void mechanicallyVerifiedRunWithBlockingQualityFinding_persistsLiveForInstructorReview() {
        SpecFidelityReport report = SpecFidelityReport.qualityReviewUnavailable("The reviewer returned no usable verdict.");
        when(reviewService.attachFindings(any(), any(), any(), anyLong(), anyMap())).thenReturn(1);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.savedExerciseVersionId()).isEqualTo(17L);
        assertThat(terminal.message()).contains("saved", "instructor review", "1 review note");
        verify(persistenceService).persist(any(), any(), any(), any(), any(), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any());
        verify(generationRevertService).recordBaseline(any(), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any(), any(), any(), any(), any(), anyString());
        verify(reviewService).attachFindings(eq(exercise), eq(user), eq(report), eq(17L), eq(Map.of(RepositoryType.SOLUTION, "solution-commit")));
    }

    @Test
    void blockingReviewWithoutExactSavedVersionKeepsExerciseVisibleAndDoesNotAttachToLatestVersion() {
        SpecFidelityReport report = SpecFidelityReport.qualityReviewUnavailable("The reviewer returned no usable verdict.");
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(Map.of(),
                Map.of(RepositoryType.SOLUTION, "solution-commit"), exercise.getProblemStatement(), exercise.getTitle(), "main"));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("saved", "Review notes could not be attached", "inspect the generated exercise manually");
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any(), anyLong(), anyMap());
    }

    @Test
    void mechanicallyVerifiedRun_whenIncompletePersistWasFullyCompensated_reportsErrorAfterInvalidatingPreviousBaseline() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Runnable beforeDurableMutation = invocation.getArgument(8);
            beforeDurableMutation.run();
            throw new GenerationIncompleteException("already-committed repositories were reverted", new RuntimeException(), false, Map.of());
        });

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("could not be saved", "All changes were reverted");
        assertThat(terminal.completionStatus()).isNull();
        assertThat(terminal.verdict()).isNull();
        assertThat(terminal.liveExerciseChanged()).isNull();
        assertThat(terminal.savedRepositoryCommits()).isNull();
        assertThat(terminal.savedExerciseVersionId()).isNull();
        verify(generationRevertService).invalidateBaseline(EXERCISE_ID);
    }

    @Test
    void incompleteFinalizationReportsTheExactSavedRepositoryCommits() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Runnable beforeDurableMutation = invocation.getArgument(8);
            beforeDurableMutation.run();
            throw new GenerationIncompleteException("version failed", new RuntimeException(), true,
                    Map.of(RepositoryType.SOLUTION, "solution-commit", RepositoryType.TESTS, "tests-commit"));
        });

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.savedRepositoryCommits()).containsExactlyInAnyOrderEntriesOf(Map.of("solution", "solution-commit", "tests", "tests-commit"));
        verify(generationRevertService).invalidateBaseline(EXERCISE_ID);
    }

    @Test
    void noOpPersist_reportsHonestlyWithoutRecordingABaselineOrAttachingReviewFindings() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            // A no-op persist never reaches a durable mutation, so it never invokes the callback; the sibling branch that does is covered by
            // mechanicallyVerifiedRun_persistsExactlyOnceAndDestroysTheSandbox.
            Runnable beforeDurableMutation = invocation.getArgument(8);
            assertThat(beforeDurableMutation).isNotNull();
            return new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main", false, null);
        });

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.liveExerciseChanged()).isFalse();
        assertThat(terminal.message()).contains("No changes were needed");
        assertThat(terminal.savedRepositoryCommits()).isNullOrEmpty();
        verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(generationRevertService, never()).invalidateBaseline(EXERCISE_ID);
        verify(reviewService, never()).attachFindings(any(), any(), any());
    }

    @Test
    void noOpPersist_preservesBlockingReviewVerdictAndAttachesFindingsToTheCurrentExercise() {
        SpecFidelityReport report = SpecFidelityReport.qualityReviewUnavailable("The reviewer returned no usable verdict.");
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main", false, null));
        when(reviewService.attachFindings(exercise, user, report)).thenReturn(1);

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.liveExerciseChanged()).isFalse();
        assertThat(terminal.message()).contains("No changes were needed", "instructor review", "1 review note");
        verify(reviewService).attachFindings(exercise, user, report);
        verify(generationRevertService, never()).recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void testPlanOnlyPersistIsReportedAsALiveChangeWhenItCreatesAnExerciseVersion() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main", false, 23L));

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.savedExerciseVersionId()).isEqualTo(23L);
        assertThat(terminal.message()).contains("adapted and saved").doesNotContain("No changes were needed");
        verify(generationRevertService).recordBaseline(any(), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void mechanicallyVerifiedRun_whenRevertCheckpointFails_reportsSuccessfulSaveWithoutHidingTheDegradation() {
        when(generationRevertService.recordBaseline(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(false);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("generated and saved", "Automatic revert is unavailable");
    }

    @Test
    void mechanicallyVerifiedRun_whenSavingFailsBeforeLiveMutation_reportsErrorWithoutClaimingMutation() {
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenThrow(new IllegalStateException("metadata changed"));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("passed verification", "could not be saved", "Nothing was changed").doesNotContain("metadata changed");
        verify(reviewService, never()).attachFindings(any(), any(), any());
    }

    @Test
    void mechanicallyVerifiedAdaptRun_recordsExactPersistedMetadataWithoutARacyReload() {
        ProgrammingExercise exerciseToPersist = new ProgrammingExercise();
        exerciseToPersist.setId(EXERCISE_ID);
        exerciseToPersist.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseToPersist.setProblemStatement("Original problem statement");
        exerciseToPersist.setTitle("Original title");

        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise), Optional.of(exerciseToPersist));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(new GenerationPersistenceService.PersistResult(
                Map.of(RepositoryType.SOLUTION, "head-sha"), Map.of(RepositoryType.SOLUTION, "post-head-sha"), "Exact persisted statement", "Exact persisted title", "release"));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.ADAPT));

        verify(persistenceService).persist(eq(exerciseToPersist), eq(user), any(GenerationOutcome.class), any(), any(), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any());
        verify(generationRevertService).recordBaseline(eq(exerciseToPersist), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), eq(exercise.getProblemStatement()),
                eq(exercise.getTitle()), eq("Exact persisted statement"), eq("Exact persisted title"), eq("release"));
        verify(reviewService).attachFindings(eq(exerciseToPersist), eq(user), any());
    }

    @Test
    void mechanicallyVerifiedRun_doesNotReportFailureAfterPersistenceSucceeded() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.of(exercise), Optional.of(exercise));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(new GenerationPersistenceService.PersistResult(Map.of(), Map.of(), exercise.getProblemStatement(), exercise.getTitle(), "main"));

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        verify(generationRevertService).recordBaseline(eq(exercise), eq(JOB_ID), eq(GenerationMode.ADAPT), any(), any(), any(), any(), eq(exercise.getProblemStatement()),
                eq(exercise.getTitle()), eq("main"));
        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
    }

    @Test
    void mechanicallyRejectedRun_emitsErrorAndPersistsNothing() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("did not pass mechanical verification", "Nothing was saved", "solution failed");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(jobService, never()).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
    }

    @Test
    void completedRun_thatLostOwnershipBeforePersistence_emitsErrorAndDoesNotSave() {
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(false);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("job ownership was lost");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void completedRun_thatWasAlreadyCancelledWhenEnteringTheNonCancellablePhase_reportsCancelledAndDoesNotSave() {
        // enterNonCancellablePhase returns false for two reasons; when the job-map lock resolved a cancel-first race the run is cancelled, not a save failure.
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenReturn(false);
        when(jobService.isCancelled(JOB_ID)).thenReturn(true);

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("cancelled", "Nothing was changed");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void staleAsyncWorkerStart_emitsCancelledWithoutReloadingOrSettingUpSandbox() {
        when(jobService.isActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(false);

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("superseded or expired");
        assertThat(terminal.terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.NOT_STARTED);
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
        assertThat(terminal.terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.DEADLINE_EXCEEDED);
        verify(programmingExerciseRepository, never()).findWithAllParticipationsAndBuildConfigById(EXERCISE_ID);
        verify(orchestrator, never()).generate(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void budgetExhaustedMechanicallyRejectedRun_emitsErrorAndDestroysSession() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.BUDGET_EXHAUSTED, new VerificationResult(false, false, true, 3, List.of("template passed a graded test"))));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("Nothing was saved", "template passed a graded test");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void verifiedAdaptationWithBlockingScopeFinding_savesCanonicalVersionForInstructorReview() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE,
                "solution removed displayName", "The feedback required preserving displayName.")));
        when(reviewService.attachFindings(any(), any(), any(), anyLong(), anyMap())).thenReturn(1);

        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.verdict().mechanicallyVerified()).isTrue();
        assertThat(terminal.liveExerciseChanged()).isTrue();
        assertThat(terminal.message()).contains("adapted and saved", "instructor review", "1 review note");
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService).attachFindings(eq(exercise), eq(user), eq(report), eq(17L), eq(Map.of(RepositoryType.SOLUTION, "solution-commit")));
    }

    @Test
    void rejectedAdaptationWithScopeFinding_reportsVerificationFailureAsPrimaryReason() {
        SpecFidelityReport report = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE, "solution added reset()", "The feedback did not request reset().")));
        run(GenerationMode.ADAPT, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, true, 3, List.of("solution failed")), report));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("did not pass mechanical verification", "solution failed", "Nothing was saved");
    }

    @Test
    void cancelledRun_emitsCancelled_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 2, "")));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void deadlineExceeded_stopsCooperativelyAndDiscardsOnlyWhenNoVerifiedCandidateExists() {
        ArgumentCaptor<Runnable> deadline = ArgumentCaptor.forClass(Runnable.class);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            BooleanSupplier shouldCancel = invocation.getArgument(5);
            verify(taskScheduler).schedule(deadline.capture(), any(java.time.Instant.class));
            assertThat(shouldCancel.getAsBoolean()).isFalse();
            deadline.getValue().run();
            assertThat(shouldCancel.getAsBoolean()).isTrue();
            return GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("time limit");
        // The deadline stops the run cooperatively but never force-cancels the job, which would discard a verified checkpoint.
        verify(jobService, never()).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), anyString());
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void deadlineExceeded_mechanicallyVerifiedCandidate_isSavedInsteadOfDiscarded() {
        // The deadline stops further model work, but a candidate that already passed verification is paid for and persisting it costs no further provider calls.
        ArgumentCaptor<Runnable> deadline = ArgumentCaptor.forClass(Runnable.class);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            BooleanSupplier shouldCancel = invocation.getArgument(5);
            verify(taskScheduler).schedule(deadline.capture(), any(java.time.Instant.class));
            assertThat(shouldCancel.getAsBoolean()).isFalse();
            deadline.getValue().run();
            assertThat(shouldCancel.getAsBoolean()).isTrue();
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 1, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        verify(jobService, never()).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), anyString());
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        assertThat(sentEvents()).anyMatch(event -> event.message() != null && event.message().contains("time budget was reached; keeping and saving"));
        assertThat(sentEvents().getLast().type()).isNotEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void exerciseBecomingIneligibleDuringPersistence_closesTheMutationGuard() {
        when(jobService.isOwnedActiveJob(EXERCISE_ID, JOB_ID)).thenReturn(true);
        when(programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(EXERCISE_ID)).thenReturn(true, false);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));
        when(persistenceService.persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            BooleanSupplier mutationGuard = invocation.getArgument(7);
            assertThat(mutationGuard.getAsBoolean()).isTrue();
            Runnable beforeDurableMutation = invocation.getArgument(8);
            beforeDurableMutation.run();
            assertThat(mutationGuard.getAsBoolean()).isFalse();
            throw new GenerationIncompleteException("exercise became ineligible while saving", new IllegalStateException());
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        verify(generationRevertService).invalidateBaseline(EXERCISE_ID);
    }

    @Test
    void tokenBudgetExceeded_mechanicallyVerifiedCandidate_isSavedInsteadOfDiscarded() {
        // The budget stops further model calls, but saving a candidate that already passed verification consumes no provider tokens.
        taskService = new GenerationTaskService(orchestrator, persistenceService, reviewService, websocket, jobService, programmingExerciseRepository,
                auxiliaryRepositoryRepository, generationBudgetService, generationRevertService, taskScheduler, ObservationRegistry.NOOP, java.time.Duration.ofMinutes(30), 10,
                java.time.Duration.ofSeconds(15));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        verify(jobService, never()).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), argThat(message -> message.contains("token budget")));
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        assertThat(sentEvents()).anyMatch(event -> event.message() != null && event.message().contains("token budget was reached; keeping and saving"));
        assertThat(sentEvents().getLast().type()).isNotEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void tokenAccountingFailure_mechanicallyVerifiedCandidate_isSavedInsteadOfDiscarded() {
        // Observed in a live campaign: a single failed critic request made the usage account indeterminate, and an exercise that had already passed differential verification
        // and been checkpointed was thrown away. Saving consumes no provider tokens, so an account that cannot be closed is a reason to stop spending, not to destroy work.
        // The run is still reported with an incomplete accounting state.
        when(jobService.tokenUsageSink(any(), any(), any(), any(), any())).thenReturn(response -> {
            throw new GenerationJobService.TokenUsageAccountingException();
        });
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-accounting-verified"));

        verify(jobService).markTokenAccountingIncomplete(JOB_ID);
        verify(jobService, never()).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), anyString());
        verify(persistenceService).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        assertThat(sentEvents()).anyMatch(event -> event.message() != null && event.message().contains("keeping and saving the already-verified exercise"));
        assertThat(sentEvents().getLast().type()).isNotEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void liveSpendOnAStreamedEvent_isTheSameBillableTotalTheBudgetGuardCharges() {
        // One number, two consumers: the bar the instructor watches and the guard that stops the run must be the same total, cached input discounted alike.
        taskService = new GenerationTaskService(orchestrator, persistenceService, reviewService, websocket, jobService, programmingExerciseRepository,
                auxiliaryRepositoryRepository, generationBudgetService, generationRevertService, taskScheduler, ObservationRegistry.NOOP, java.time.Duration.ofMinutes(30), 700,
                java.time.Duration.ofSeconds(15));
        when(jobService.tokenUsageSink(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<LLMRequest> liveUsageSink = invocation.getArgument(4);
            return (Consumer<ChatResponse>) response -> liveUsageSink.accept(new LLMRequest("model", 1000, 1f, 100, 2f, "pipeline", "provider-id", 800L, 0.1f, true));
        });
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            ProviderUsageSink usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(1000, 100, 800L));
            GenerationProgressSink progress = invocation.getArgument(6);
            progress.activity("Thinking about the next step.", new ExerciseGenerationActivityDTO("artifacts", 1, 2, true, 1, 0, 0));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO stamped = sentEvents().stream().filter(event -> event.activity() != null).findFirst().orElseThrow();
        // 200 uncached input plus half of 800 cache reads plus 100 output.
        assertThat(stamped.liveUsage().billableTokens()).isEqualTo(700);
        assertThat(stamped.liveUsage().tokenBudget()).isEqualTo(700);
        assertThat(stamped.liveUsage().inputTokens()).isEqualTo(1000);
        assertThat(stamped.liveUsage().cachedInputTokens()).isEqualTo(800);
        assertThat(stamped.liveUsage().modelCalls()).isEqualTo(1);
        // The same 700 is what the guard weighed against the 700-token bound.
        assertThat(sentEvents()).anyMatch(event -> event.message() != null && event.message().contains("token budget was reached; keeping and saving"));
    }

    @Test
    void liveSpendIsStampedOnPhaseBoundariesFromTheRunStart() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        ExerciseGenerationEventDTO firstPhase = sentEvents().stream().filter(event -> event.phase() != null).findFirst().orElseThrow();
        assertThat(firstPhase.liveUsage().tokenBudget()).isEqualTo(250_000);
        assertThat(firstPhase.liveUsage().billableTokens()).isZero();
        assertThat(firstPhase.liveUsage().estimatedCostEur()).isZero();
    }

    @Test
    void tokenBudgetExceeded_withoutAVerifiedCandidate_endsCancelledWithoutPersisting() {
        taskService = new GenerationTaskService(orchestrator, persistenceService, reviewService, websocket, jobService, programmingExerciseRepository,
                auxiliaryRepositoryRepository, generationBudgetService, generationRevertService, taskScheduler, ObservationRegistry.NOOP, java.time.Duration.ofMinutes(30), 10,
                java.time.Duration.ofSeconds(15));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.CANCELLED, null);
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(terminal.message()).contains("token budget");
        verify(jobService, never()).enterNonCancellablePhase(EXERCISE_ID, JOB_ID);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void tokenAccountingFailure_cancelsTheJobAndRetainsItsWorstCaseReservation() {
        when(jobService.tokenUsageSink(any(), any(), any(), any(), any())).thenReturn(response -> {
            throw new GenerationJobService.TokenUsageAccountingException();
        });
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.CANCELLED, new VerificationResult(false, false, false, 0, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-accounting-failed"));

        assertThat(sentEvents().getLast().message()).contains("token usage could not be accounted for");
        verify(jobService).markTokenAccountingIncomplete(JOB_ID);
        // The local stop flag already ends every further model call. A hard system cancellation would additionally mark the job cancelled, which is what used to destroy a
        // verified candidate the provider had already been paid for.
        verify(jobService, never()).requestSystemCancellation(eq(EXERCISE_ID), eq(JOB_ID), anyString());
        verify(generationBudgetService).retainReservationForBudgetWindow("reservation-accounting-failed");
        verify(generationBudgetService, never()).releaseReservation("reservation-accounting-failed");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void workerExit_sealsAccountingBeforeReleasingTheJob_andNeverSealsUnconditionallyAsComplete() {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcomeWith(AgentLoopResult.Status.ERROR, new VerificationResult(false, false, false, 0, List.of())));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-sealed"));

        InOrder order = inOrder(jobService);
        order.verify(jobService).sealTokenAccountingOnWorkerExit(EXERCISE_ID, JOB_ID);
        order.verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    @Test
    void tokenAccountingFailure_stillSealsOnWorkerExit_soTheAccountIsNeverLeftPendingForever() {
        when(jobService.tokenUsageSink(any(), any(), any(), any(), any())).thenReturn(response -> {
            throw new GenerationJobService.TokenUsageAccountingException();
        });
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.CANCELLED, new VerificationResult(false, false, false, 0, List.of()));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-accounting-failed"));

        verify(jobService).markTokenAccountingIncomplete(JOB_ID);
        verify(jobService).sealTokenAccountingOnWorkerExit(EXERCISE_ID, JOB_ID);
    }

    @Test
    void agentTurnsAndAttempts_arePushedToTheJobServiceFromTheRunSink() {
        // The counters reach the usage accumulator through the sink the orchestrator is handed, not the outcome it returns, so a run abandoned at a gate still reports them.
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            ProviderUsageSink usageSink = invocation.getArgument(8);
            usageSink.recordAttempt();
            usageSink.recordTurn();
            usageSink.recordTurn();
            return GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 0, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-turns"));

        verify(jobService, times(2)).recordAgentTurn(JOB_ID);
        verify(jobService).recordAttempt(JOB_ID);
    }

    @Test
    void providerFailureWithoutUsageResponseRetainsTheWorstCaseReservation() {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            ProviderUsageSink usageSink = invocation.getArgument(8);
            usageSink.markUncertain();
            return GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, ""));
        });

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null,
                "reservation-provider-failed"));

        assertThat(sentEvents().getLast().message()).contains("token usage could not be accounted for");
        verify(generationBudgetService).retainReservationForBudgetWindow("reservation-provider-failed");
        verify(generationBudgetService, never()).releaseReservation("reservation-provider-failed");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void erroredRun_emitsError_andPersistsNothing() {
        run(GenerationMode.GENERATE, GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "")));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
    }

    @Test
    void erroredRunWithChangedArtifacts_reportsUnverifiedAndPersistsNothing() {
        GenerationOutcome outcome = new GenerationOutcome(new AgentLoopResult(AgentLoopResult.Status.ERROR, 4, "Provider stopped responding"), null, SESSION_ID, orchestrator,
                sandbox, Map.of(RepositoryType.SOLUTION, Map.of("src/Library.java", "class Library {}")), "Improved statement",
                SpecFidelityReport.qualityReviewUnavailable("The partial candidate was not verified."), Map.of());

        run(GenerationMode.GENERATE, outcome);

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("before mechanical verification completed", "Nothing was saved");
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
        verify(reviewService, never()).attachFindings(any(), any(), any());
        verify(orchestrator).destroyQuietly(sandbox, SESSION_ID);
    }

    @Test
    void deletedExercise_failsClosedWithError_withoutRunningTheOrchestrator() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(EXERCISE_ID)).thenReturn(Optional.empty());

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        assertThat(sentEvents().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
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
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(GenerationOutcome.cancelled(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, "")));

        assertThatThrownBy(() -> taskService.runAsync(
                new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null, "reservation-2")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("clear failed");

        verify(generationBudgetService).releaseReservation("reservation-2");
    }

    /** Whatever the attempt loop concluded must survive onto the terminal event for every value of the closed enum, since that status is all a later reader has. */
    @ParameterizedTest
    @EnumSource(ExerciseGenerationEventDTO.TerminationReason.class)
    void terminalEvent_carriesTheOutcomesTerminationReason(ExerciseGenerationEventDTO.TerminationReason reason) {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())).withTermination(reason));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.terminationReason()).isEqualTo(reason);
        assertThat(terminal.completionStatus()).as("the reason is orthogonal to what became of the result").isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
    }

    @Test
    void errorTerminalEvent_carriesTheOutcomesTerminationReason() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(false, false, false, 0, List.of("the template passed every test")))
                .withTermination(ExerciseGenerationEventDTO.TerminationReason.MECHANICAL_REPAIR_EXHAUSTED));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.MECHANICAL_REPAIR_EXHAUSTED);
    }

    @Test
    void unexpectedFailureWhileHandlingTheOutcome_reportsTheRunAsFailed() {
        when(jobService.enterNonCancellablePhase(EXERCISE_ID, JOB_ID)).thenThrow(new IllegalStateException("the job map is unreachable"));

        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of()))
                .withTermination(ExerciseGenerationEventDTO.TerminationReason.CONVERGED));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).isEqualTo("Generation failed.");
        assertThat(terminal.terminationReason()).as("the task's own failure is not the loop's conclusion about the candidate")
                .isEqualTo(ExerciseGenerationEventDTO.TerminationReason.RUN_FAILED);
    }

    @Test
    void unexpectedFailureBeforeAnOutcome_reportsTheRunAsFailed() {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("checkpoint replay drift"));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).isEqualTo("Generation failed.");
        assertThat(terminal.terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.RUN_FAILED);
    }

    @Test
    void linkageFailureBeforeAnOutcome_stillTerminalizesTheJob() {
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new NoClassDefFoundError("GenerationAttemptLoop$ExtractedCandidate"));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE));

        ExerciseGenerationEventDTO terminal = sentEvents().getLast();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).isEqualTo("Generation failed.");
        assertThat(terminal.terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.RUN_FAILED);
        verify(jobService).clearJob(EXERCISE_ID, JOB_ID);
    }

    /**
     * The attempt loop sees only a cooperative stop flag, so a deadline, a token budget and an instructor pressing cancel all reach it as {@code CANCELLED} and only the task
     * knows which fired. Every other reason is the loop's own conclusion about the candidate and must survive untouched.
     */
    @ParameterizedTest
    @EnumSource(ExerciseGenerationEventDTO.TerminationReason.class)
    void runLevelBudgets_refineOnlyACooperativeStop(ExerciseGenerationEventDTO.TerminationReason reason) {
        ExerciseGenerationEventDTO.TerminationReason refined = GenerationTaskService.refineTerminationReason(reason, true, true);

        if (reason == ExerciseGenerationEventDTO.TerminationReason.CANCELLED) {
            assertThat(refined).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.DEADLINE_EXCEEDED);
        }
        else {
            assertThat(refined).isEqualTo(reason);
        }
    }

    @Test
    void aCooperativeStopUnderNoBudgetPressure_staysACancellation() {
        assertThat(GenerationTaskService.refineTerminationReason(ExerciseGenerationEventDTO.TerminationReason.CANCELLED, false, false))
                .isEqualTo(ExerciseGenerationEventDTO.TerminationReason.CANCELLED);
    }

    @Test
    void aCooperativeStopUnderTokenPressureAlone_reportsTheTokenBudget() {
        assertThat(GenerationTaskService.refineTerminationReason(ExerciseGenerationEventDTO.TerminationReason.CANCELLED, false, true))
                .isEqualTo(ExerciseGenerationEventDTO.TerminationReason.TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void anOutcomeThatNothingStamped_leavesTheReasonAbsentRatherThanGuessing() {
        run(GenerationMode.GENERATE, outcomeWith(AgentLoopResult.Status.COMPLETED, new VerificationResult(true, true, true, 3, List.of())));

        assertThat(sentEvents().getLast().terminationReason()).isNull();
    }

    @Test
    void tokenBudget_isTheRunsOwnBoundRatherThanTheDeploymentDefault() {
        // Admission reserved the run's own bound; spending the deployment default would overshoot the reservation other jobs are admitted against.
        taskService = new GenerationTaskService(orchestrator, persistenceService, reviewService, websocket, jobService, programmingExerciseRepository,
                auxiliaryRepositoryRepository, generationBudgetService, generationRevertService, taskScheduler, ObservationRegistry.NOOP, java.time.Duration.ofMinutes(30),
                1_000_000, java.time.Duration.ofSeconds(15));
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer((Answer<GenerationOutcome>) invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatResponse> usageSink = invocation.getArgument(8);
            usageSink.accept(responseWithTokens(7, 3));
            return outcomeWith(AgentLoopResult.Status.COMPLETED, null);
        });
        HyperionGenerationSettings narrowed = new HyperionGenerationSettings("draft", "Quick draft", 20, java.time.Duration.ofMinutes(12), 10L, true, "CONTINUOUS", 128_000, null,
                false, false);

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null, null,
                null, narrowed));

        // Ten tokens spent against a ten-token run bound stops the run, even though the deployment default of a million is nowhere near reached.
        assertThat(sentEvents()).anyMatch(event -> event.message() != null && event.message().contains("token budget"));
    }

    @Test
    void resolvedSettings_areForwardedToOrchestrationSoTheEngineRunsTheProfile() {
        HyperionGenerationSettings thorough = new HyperionGenerationSettings("thorough", "Thorough", 90, java.time.Duration.ofMinutes(60), 6_000_000L, true, "CONTINUOUS", 256_000,
                null, false, false);
        when(orchestrator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(thorough)))
                .thenReturn(GenerationOutcome.error(new AgentLoopResult(AgentLoopResult.Status.ERROR, 1, "")));

        taskService.runAsync(new GenerationStartedEvent(JOB_ID, user, exercise, "make it", GenerationMode.GENERATE, exercise.getProblemStatement(), exercise.getTitle(), null, null,
                null, thorough));

        verify(orchestrator).generate(eq(exercise), eq(user), any(), eq(JOB_ID), eq(GenerationMode.GENERATE), any(), any(), any(), any(), any(), eq(thorough));
    }
}
