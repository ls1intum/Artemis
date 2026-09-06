package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.INTERNAL_ERROR;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.FAILED;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.IN_PROGRESS;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.NO_OP;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.PARTIAL;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService.RunInfo;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLShortlistService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/** Unit tests for the fast-paths and lock-release behavior of {@link CompetencyOrchestrationService#run(long)}. */
@ExtendWith(MockitoExtension.class)
class CompetencyOrchestrationServiceTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private ContentExtractionService contentExtractionService;

    @Mock
    private OrchestratorPlanningToolsService orchestratorPlanningToolsService;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private AtlasAgentDelegationService delegationService;

    @Mock
    private ToolCallbackProvider orchestratorReadToolCallbackProvider;

    @Mock
    private ToolCallbackProvider orchestratorPlanningToolCallbackProvider;

    @Mock
    private ToolCallbackProvider creatorToolCallbackProvider;

    @Mock
    private ToolCallbackProvider editorToolCallbackProvider;

    @Mock
    private ToolCallbackProvider assignerToolCallbackProvider;

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private ContentChangeAccumulatorService contentChangeAccumulatorService;

    // Spy over a real node-local map so the lock-guarded claim/release read-modify-write is exercised
    // statefully (a Mockito mock cannot return the value a previous put stored), while still allowing
    // interaction verification.
    private DistributedMap<Long, RunInfo> runMap;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AtlasMLShortlistService shortlistService;

    private AtlasOrchestratorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AtlasOrchestratorProperties("gpt-test-orchestrator", 1.0, "", 300, 10, 30000L, 10);
        runMap = spy(new LocalMap<>());
        // The shortlist never returns null in production; stub it leniently so render-reaching tests that do
        // not care about the shortlist still get a non-null prompt variable (Map.of rejects null values). The
        // empty string mirrors the off path, where the section is omitted entirely.
        lenient().when(shortlistService.renderShortlist(any())).thenReturn("");
    }

    @Test
    void run_noChatClient_returnsFailedNoChatClient() {
        when(exerciseRepository.findByIdElseThrow(11L)).thenReturn(courseExercise(11L));

        CompetencyOrchestrationResultDTO result = createService(null).run(11L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(NO_CHAT_CLIENT);
        verify(runMap, never()).put(anyLong(), any());
    }

    @Test
    void run_examExercise_returnsUnsupportedExercise() {
        when(exerciseRepository.findByIdElseThrow(12L)).thenReturn(examExercise(12L));

        CompetencyOrchestrationResultDTO result = createService(null).run(12L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(UNSUPPORTED_EXERCISE);
        verify(runMap, never()).put(anyLong(), any());
    }

    @Test
    void run_alreadyInProgress_returnsInProgress() {
        when(exerciseRepository.findByIdElseThrow(13L)).thenReturn(courseExercise(13L));
        stubRunMap();
        doReturn(new RunInfo("other-run", 99L, Instant.now())).when(runMap).get(COURSE_ID);

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(13L);

        assertThat(result.status()).isEqualTo(IN_PROGRESS);
        verify(runMap, never()).remove(anyLong());
    }

    @Test
    void run_releasesLockOnException() {
        ProgrammingExercise exercise = courseExercise(14L);
        when(exerciseRepository.findByIdElseThrow(14L)).thenReturn(exercise);
        stubRunMap();
        when(contentExtractionService.extractContent(exercise)).thenThrow(new RuntimeException("boom"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(14L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runWithQueuedFlush_alreadyInProgress_skipsAccumulator() {
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(courseExercise(20L));
        stubRunMap();
        doReturn(new RunInfo("other-run", 99L, Instant.now())).when(runMap).get(COURSE_ID);

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runWithQueuedFlush(20L);

        assertThat(result.status()).isEqualTo(IN_PROGRESS);
        verify(contentChangeAccumulatorService, never()).claimBatchNow(anyLong());
        verify(runMap, never()).remove(anyLong());
    }

    @Test
    void runWithQueuedFlush_skipsQueuedExerciseFromDifferentCourse() {
        ProgrammingExercise clicked = courseExercise(20L);
        ProgrammingExercise foreignQueued = new ProgrammingExercise();
        foreignQueued.setId(77L);
        Course otherCourse = new Course();
        otherCourse.setId(COURSE_ID + 1);
        foreignQueued.setCourse(otherCourse);

        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(clicked);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(foreignQueued, clicked));
        stubRunMap();
        when(contentChangeAccumulatorService.claimBatchNow(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(77L))));
        when(contentExtractionService.extractContent(clicked)).thenThrow(new RuntimeException("stop after queued"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runWithQueuedFlush(20L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // Wrong-course queued exercise was inspected but never orchestrated (no content extraction).
        verify(exerciseRepository).findAllById(any());
        verify(contentExtractionService, never()).extractContent(foreignQueued);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runWithQueuedFlush_batchesQueuedAndClickedInOneRun() {
        ProgrammingExercise clicked = courseExercise(20L);
        ProgrammingExercise queued = courseExercise(33L);

        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(clicked);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(queued, clicked));
        stubRunMap();
        when(contentChangeAccumulatorService.claimBatchNow(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(33L))));
        when(contentExtractionService.extractContent(any(ProgrammingExercise.class))).thenReturn(new ExtractedContentDTO("Title", "Body", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        // Fail at render so we exercise the single-batch preparation path (queued + clicked) without driving the LLM.
        when(templateService.render(anyString(), anyMap())).thenThrow(new RuntimeException("stop after prepare"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runWithQueuedFlush(20L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // Queued change is rendered before the clicked exercise, but it is a single batched run:
        // the course index is fetched once and only one prompt is rendered.
        InOrder order = inOrder(contentExtractionService);
        order.verify(contentExtractionService).extractContent(queued);
        order.verify(contentExtractionService).extractContent(clicked);
        verify(orchestratorPlanningToolsService).listCompetencyIndex(COURSE_ID);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void run_partialWhenExceptionAfterAppliedActions() {
        ProgrammingExercise exercise = courseExercise(15L);
        when(exerciseRepository.findByIdElseThrow(15L)).thenReturn(exercise);
        stubRunMap();
        when(contentExtractionService.extractContent(exercise)).thenReturn(new ExtractedContentDTO("Test Exercise", "Learn loops", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        when(templateService.render(anyString(), anyMap())).thenReturn("system prompt");

        // The harness appends an action to the tool-context buffer (mirroring a successful write tool)
        // and then throws to model the LLM round failing mid-flight after one committed mutation.
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ctx = invocation.getArgument(3);
                    OrchestratorToolContextKeys.AppliedActionsBuffer buffer = (OrchestratorToolContextKeys.AppliedActionsBuffer) ctx
                            .get(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY);
                    buffer.actions().add(AppliedActionDTO.create(1L, "Loops", "Created competency", "Exercise teaches loops"));
                    throw new RuntimeException("LLM connection lost");
                });

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(15L);

        assertThat(result.status()).isEqualTo(PARTIAL);
        assertThat(result.failureReason()).isEqualTo(LLM_ERROR);
        assertThat(result.appliedActions()).hasSize(1);
        assertThat(result.appliedActions().getFirst().type()).isEqualTo(AppliedActionDTO.ActionType.CREATE);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runBatch_noChatClient_returnsFailedNoChatClient() {
        CompetencyOrchestrationResultDTO result = createService(null).runBatch(COURSE_ID, Set.of(10L, 11L));

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(NO_CHAT_CLIENT);
        verify(exerciseRepository, never()).findAllById(any());
        verify(runMap, never()).put(anyLong(), any());
    }

    @Test
    void runBatch_onlyNonApplicableExercises_returnsNoOpWithoutClaimingLock() {
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(examExercise(12L)));

        CompetencyOrchestrationResultDTO result = createService(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(12L, 99L));

        // No applicable exercise was processed — a NO_OP (not SUCCESS) so the scheduler does not report
        // the claimed ids as successfully orchestrated.
        assertThat(result.status()).isEqualTo(NO_OP);
        assertThat(result.appliedActions()).isEmpty();
        // Exam and missing exercises are dropped before the per-course lock is even claimed.
        verify(runMap, never()).put(anyLong(), any());
    }

    @Test
    void runBatch_alreadyInProgress_returnsInProgress() {
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(courseExercise(10L)));
        stubRunMap();
        doReturn(new RunInfo("other-run", 99L, Instant.now())).when(runMap).get(COURSE_ID);

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(10L));

        assertThat(result.status()).isEqualTo(IN_PROGRESS);
        verify(runMap, never()).remove(anyLong());
    }

    @Test
    void runBatch_extractsAllExercisesInOneRun_thenReleasesLock() {
        ProgrammingExercise first = courseExercise(10L);
        ProgrammingExercise second = courseExercise(11L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(first, second));
        stubRunMap();
        when(contentExtractionService.extractContent(any(ProgrammingExercise.class))).thenReturn(new ExtractedContentDTO("Title", "Body", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        // Fail at render so we exercise the single-run preparation path without driving the LLM.
        when(templateService.render(anyString(), anyMap())).thenThrow(new RuntimeException("stop after prepare"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(10L, 11L));

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // Both exercises are extracted, but the course index is fetched only once — one batched run, not one per exercise.
        verify(contentExtractionService).extractContent(first);
        verify(contentExtractionService).extractContent(second);
        verify(orchestratorPlanningToolsService).listCompetencyIndex(COURSE_ID);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void run_success_tracksTokenUsage() {
        ProgrammingExercise exercise = courseExercise(16L);
        when(exerciseRepository.findByIdElseThrow(16L)).thenReturn(exercise);
        stubRunMap();
        when(contentExtractionService.extractContent(exercise)).thenReturn(new ExtractedContentDTO("Test Exercise", "Learn loops", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        when(templateService.render(anyString(), anyMap())).thenReturn("system prompt");

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Run summary"))));
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenReturn(chatResponse);

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(16L);

        assertThat(result.status()).isEqualTo(SUCCESS);
        assertThat(result.summary()).isEqualTo("Run summary");
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(chatResponse), eq(LLMServiceType.ATLAS), eq("ATLAS_ORCHESTRATION"), any());
        verify(runMap).remove(COURSE_ID);

        // Regression guard: the orchestrator must expose ALL FIVE tool providers to the LLM — the read and
        // planning surfaces plus the three write surfaces (creator/editor/assigner). If the write providers
        // are ever unwired again, the orchestrator silently loses the ability to mutate competencies, so we
        // capture the varargs and assert every expected provider (and specifically the three write ones).
        ArgumentCaptor<ToolCallbackProvider> providerCaptor = ArgumentCaptor.forClass(ToolCallbackProvider.class);
        verify(delegationService).delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), providerCaptor.capture(),
                providerCaptor.capture(), providerCaptor.capture(), providerCaptor.capture(), providerCaptor.capture());
        assertThat(providerCaptor.getAllValues()).containsExactly(orchestratorReadToolCallbackProvider, orchestratorPlanningToolCallbackProvider, creatorToolCallbackProvider,
                editorToolCallbackProvider, assignerToolCallbackProvider);
        assertThat(providerCaptor.getAllValues()).contains(creatorToolCallbackProvider, editorToolCallbackProvider, assignerToolCallbackProvider);
    }

    @Test
    void run_staleInProgressClaim_isReclaimed() {
        ProgrammingExercise exercise = courseExercise(18L);
        when(exerciseRepository.findByIdElseThrow(18L)).thenReturn(exercise);
        stubRunMap();
        // A crashed node left a claim older than the 30-min run lease; on a TTL-less map (Redis/Local)
        // this would otherwise block the course in IN_PROGRESS forever. Seed it directly into the map.
        runMap.put(COURSE_ID, new RunInfo("crashed-run", 99L, Instant.now().minus(Duration.ofMinutes(31))));
        when(contentExtractionService.extractContent(exercise)).thenThrow(new RuntimeException("stop after reclaim"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(18L);

        // The stale claim was expired and reclaimed (the run proceeded past the guard), not rejected as IN_PROGRESS.
        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runWithQueuedFlush_failedRun_requeuesDrainedIds() {
        ProgrammingExercise clicked = courseExercise(20L);
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(clicked);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(clicked));
        stubRunMap();
        when(contentChangeAccumulatorService.claimBatchNow(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(33L))));
        // A transient failure before any competency mutation -> FAILED.
        when(contentExtractionService.extractContent(any(ProgrammingExercise.class))).thenThrow(new RuntimeException("transient"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runWithQueuedFlush(20L);

        assertThat(result.status()).isEqualTo(FAILED);
        // claimBatchNow drained and reset the bucket; on FAILED the drained queued id (33) and the clicked id (20)
        // must be requeued so the course's other pending changes are not silently lost.
        verify(contentChangeAccumulatorService).requeueAfterFailedRun(COURSE_ID, Set.of(33L, 20L));
    }

    @Test
    void runBatch_mixedExerciseTypes_extractsEachTypeInOneRun() {
        ProgrammingExercise programming = courseExercise(10L);
        TextExercise text = textExercise(11L);
        QuizExercise quiz = quizExercise(12L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(programming, text, quiz));
        stubRunMap();
        when(contentExtractionService.extractContent(any(Exercise.class))).thenReturn(new ExtractedContentDTO("Title", "Body", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        // Fail at render so we exercise the single-run preparation path (all types extracted) without driving the LLM.
        when(templateService.render(anyString(), anyMap())).thenThrow(new RuntimeException("stop after prepare"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(10L, 11L, 12L));

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // Every exercise type is extracted, and the course index is fetched only once — one batched run for the mixed batch.
        verify(contentExtractionService).extractContent(programming);
        verify(contentExtractionService).extractContent(text);
        verify(contentExtractionService).extractContent(quiz);
        verify(orchestratorPlanningToolsService).listCompetencyIndex(COURSE_ID);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runBatch_oneExerciseExtractionThrows_isSkippedAndBatchProceeds() {
        // A quiz deleted mid-run makes its refetch throw; that must not drop the rest of the batch.
        QuizExercise doomedQuiz = quizExercise(12L);
        ProgrammingExercise healthy = courseExercise(10L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(doomedQuiz, healthy));
        stubRunMap();
        when(contentExtractionService.extractContent(doomedQuiz)).thenThrow(new RuntimeException("quiz deleted mid-run"));
        when(contentExtractionService.extractContent(healthy)).thenReturn(new ExtractedContentDTO("Survivor Title", "Survivor problem body", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        // Fail at render so we stop after preparation without driving the LLM.
        when(templateService.render(anyString(), anyMap())).thenThrow(new RuntimeException("stop after prepare"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(10L, 12L));

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // The throwing quiz is skipped, but the healthy exercise is still extracted and the batch proceeds to prepare
        // the prompt (index fetched, render reached) — proving one bad exercise no longer poisons the whole batch.
        verify(contentExtractionService).extractContent(healthy);
        verify(orchestratorPlanningToolsService).listCompetencyIndex(COURSE_ID);
        // The survivor's extracted content must actually reach the rendered batch, and the skipped quiz (id 12) must not —
        // a "batch proceeds" assertion alone would still pass if the survivor's change were silently dropped.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> modelCaptor = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(anyString(), modelCaptor.capture());
        String renderedChanges = modelCaptor.getValue().get("exerciseChanges");
        assertThat(renderedChanges).contains("[UPDATE id=10]").contains("Survivor Title").contains("Survivor problem body").doesNotContain("[UPDATE id=12]");
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runBatch_mixedBatchOneExtractionThrows_requeuesSkippedIdOnSuccess() {
        // A quiz deleted mid-run fails extraction, but the programming exercise succeeds so the batch reaches SUCCESS.
        // Because claimDueBatch already drained the bucket, the skipped id would be lost unless it is requeued here.
        ProgrammingExercise healthy = courseExercise(10L);
        QuizExercise doomedQuiz = quizExercise(12L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(healthy, doomedQuiz));
        stubRunMap();
        when(contentExtractionService.extractContent(healthy)).thenReturn(new ExtractedContentDTO("Survivor", "Survivor body", Map.of()));
        when(contentExtractionService.extractContent(doomedQuiz)).thenThrow(new RuntimeException("quiz deleted mid-run"));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        when(templateService.render(anyString(), anyMap())).thenReturn("system prompt");

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Run summary"))));
        ChatClient mockChatClient = mock(ChatClient.class);
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenReturn(chatResponse);

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mockChatClient).runBatch(COURSE_ID, Set.of(10L, 12L));

        assertThat(result.status()).isEqualTo(SUCCESS);
        // Only the extraction-failed quiz (12) is requeued — the healthy exercise was orchestrated, not requeued.
        verify(contentChangeAccumulatorService).requeueAfterFailedRun(COURSE_ID, Set.of(12L));
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runBatch_requeueOfSkippedIdThrowsAfterCommittedActions_isSwallowedAndSuccessPreserved() {
        // Same shape as the SUCCESS-requeue test, but the post-mutation requeue itself fails (e.g. Hazelcast down).
        // By this point the LLM has already committed its competency mutations. The requeue failure must NOT escape:
        // if it did, ContentChangeScheduler would treat it as a pre-mutation error and re-requeue the whole batch,
        // re-applying the committed changes on the next tick. So the failure is swallowed and SUCCESS is preserved.
        ProgrammingExercise healthy = courseExercise(10L);
        QuizExercise doomedQuiz = quizExercise(12L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(healthy, doomedQuiz));
        stubRunMap();
        when(contentExtractionService.extractContent(healthy)).thenReturn(new ExtractedContentDTO("Survivor", "Survivor body", Map.of()));
        when(contentExtractionService.extractContent(doomedQuiz)).thenThrow(new RuntimeException("quiz deleted mid-run"));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        when(templateService.render(anyString(), anyMap())).thenReturn("system prompt");
        // The requeue of the skipped id (12) blows up — this runs only after mutations have committed.
        doThrow(new RuntimeException("hazelcast down")).when(contentChangeAccumulatorService).requeueAfterFailedRun(COURSE_ID, Set.of(12L));

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Run summary"))));
        ChatClient mockChatClient = mock(ChatClient.class);
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenReturn(chatResponse);

        CompetencyOrchestrationService service = createServiceWithRunMap(mockChatClient);
        // The requeue exception must not escape runBatch after committed actions; capture the result to assert on it.
        AtomicReference<CompetencyOrchestrationResultDTO> result = new AtomicReference<>();
        assertThatCode(() -> result.set(service.runBatch(COURSE_ID, Set.of(10L, 12L)))).doesNotThrowAnyException();

        // The committed-mutation result is preserved as SUCCESS despite the requeue failure.
        assertThat(result.get().status()).isEqualTo(SUCCESS);
        verify(contentChangeAccumulatorService).requeueAfterFailedRun(COURSE_ID, Set.of(12L));
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void runBatch_allExercisesExtractionThrow_returnsInternalErrorWithoutDrivingLlm() {
        // When every exercise in the batch fails extraction, the run must short-circuit to INTERNAL_ERROR
        // before fetching the competency index or rendering the prompt — never touching the LLM — so an
        // all-broken batch does not waste the course's daily-run slot on a guaranteed-empty prompt.
        QuizExercise firstDoomed = quizExercise(12L);
        TextExercise secondDoomed = textExercise(13L);
        when(exerciseRepository.findAllById(any())).thenReturn(List.<Exercise>of(firstDoomed, secondDoomed));
        stubRunMap();
        when(contentExtractionService.extractContent(firstDoomed)).thenThrow(new RuntimeException("quiz deleted mid-run"));
        when(contentExtractionService.extractContent(secondDoomed)).thenThrow(new RuntimeException("text deleted mid-run"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).runBatch(COURSE_ID, Set.of(12L, 13L));

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        // No extractable content -> no course index, no prompt render, no LLM call; the lock is still released.
        verify(orchestratorPlanningToolsService, never()).listCompetencyIndex(anyLong());
        verify(templateService, never()).render(anyString(), anyMap());
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void run_nonProgrammingExercise_isOrchestrated() {
        TextExercise exercise = textExercise(17L);
        when(exerciseRepository.findByIdElseThrow(17L)).thenReturn(exercise);
        stubRunMap();
        // A non-programming exercise now passes the precheck (only exam / no-chat-client are rejected) and is orchestrated.
        when(contentExtractionService.extractContent(exercise)).thenThrow(new RuntimeException("stop after extract"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(17L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(INTERNAL_ERROR);
        verify(contentExtractionService).extractContent(exercise);
        verify(runMap).remove(COURSE_ID);
    }

    @Test
    void run_injectsAtlasMLShortlistIntoExecutePrompt() {
        ProgrammingExercise exercise = courseExercise(21L);
        when(exerciseRepository.findByIdElseThrow(21L)).thenReturn(exercise);
        stubRunMap();
        when(contentExtractionService.extractContent(exercise)).thenReturn(new ExtractedContentDTO("Loops", "Learn loops", Map.of()));
        when(orchestratorPlanningToolsService.listCompetencyIndex(COURSE_ID)).thenReturn(new CompetencyIndexResponseDTO(List.of(), List.of()));
        // Sentinel map so we can prove the exact instance fetched is the one handed to renderShortlist (not a fresh/empty map).
        Map<Long, List<AtlasMLCompetencyDTO>> fetched = Map.of(21L, List.of(new AtlasMLCompetencyDTO(99L, "Loops", "desc", COURSE_ID)));
        when(shortlistService.fetchShortlists(eq(COURSE_ID), anyList())).thenReturn(fetched);
        when(shortlistService.renderShortlist(same(fetched))).thenReturn("SHORTLIST_BLOCK");
        // Stop right after the prompt is rendered so we assert the render inputs without driving the LLM.
        when(templateService.render(anyString(), anyMap())).thenThrow(new RuntimeException("stop after prepare"));

        createServiceWithRunMap(mock(ChatClient.class)).run(21L);

        // The cleaned learning text is forwarded to AtlasML keyed by the exercise id...
        verify(shortlistService).fetchShortlists(eq(COURSE_ID),
                argThat(extracts -> extracts.size() == 1 && extracts.getFirst().exerciseId() == 21L && "Learn loops".equals(extracts.getFirst().description())));
        // ...the exact fetched map (not some other/empty map) is passed to renderShortlist...
        verify(shortlistService).renderShortlist(same(fetched));
        // ...and the rendered block is wired into the execute prompt under the atlasMLShortlist variable.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(anyString(), captor.capture());
        assertThat(captor.getValue()).containsEntry("atlasMLShortlist", "SHORTLIST_BLOCK");
    }

    private CompetencyOrchestrationService createService(@Nullable ChatClient chatClient) {
        return new CompetencyOrchestrationService(exerciseRepository, contentExtractionService, orchestratorPlanningToolsService, templateService, delegationService, chatClient,
                new AtlasToolSurface(orchestratorReadToolCallbackProvider), new AtlasToolSurface(orchestratorPlanningToolCallbackProvider),
                new AtlasToolSurface(creatorToolCallbackProvider), new AtlasToolSurface(editorToolCallbackProvider), new AtlasToolSurface(assignerToolCallbackProvider),
                Optional.of(distributedDataProvider), properties, contentChangeAccumulatorService, llmTokenUsageService, userRepository, shortlistService);
    }

    private CompetencyOrchestrationService createServiceWithRunMap(@Nullable ChatClient chatClient) {
        return createService(chatClient);
    }

    private void stubRunMap() {
        when(distributedDataProvider.<Long, RunInfo>getMap("atlas-orchestrator-runs")).thenReturn(runMap);
    }

    private static ProgrammingExercise courseExercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        Course course = new Course();
        course.setId(COURSE_ID);
        exercise.setCourse(course);
        return exercise;
    }

    private static ProgrammingExercise examExercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setExerciseGroup(new ExerciseGroup());
        return exercise;
    }

    private static TextExercise textExercise(long id) {
        TextExercise exercise = new TextExercise();
        exercise.setId(id);
        Course course = new Course();
        course.setId(COURSE_ID);
        exercise.setCourse(course);
        return exercise;
    }

    private static QuizExercise quizExercise(long id) {
        QuizExercise exercise = new QuizExercise();
        exercise.setId(id);
        Course course = new Course();
        course.setId(COURSE_ID);
        exercise.setCourse(course);
        return exercise;
    }
}
