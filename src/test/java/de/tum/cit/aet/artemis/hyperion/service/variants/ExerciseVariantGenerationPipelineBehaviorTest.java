package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

class ExerciseVariantGenerationPipelineBehaviorTest {

    private static final String PLAN = "{\"variantTitle\":\"Variant\",\"problemStatement\":\"Statement\",\"intendedChanges\":[\"Rename\"],\"invariants\":[]}";

    private final VariantTypeRegistryService registry = mock(VariantTypeRegistryService.class);

    private final VariantTypeAdapters adapters = mock(VariantTypeAdapters.class);

    private final VariantAgentLoopService agent = mock(VariantAgentLoopService.class);

    private final ExerciseTestRepository exercises = mock(ExerciseTestRepository.class);

    private final ExerciseDeletionService deletion = mock(ExerciseDeletionService.class);

    private final ChatModel model = mock(ChatModel.class);

    private ExerciseVariantJobService jobs;

    private ExerciseVariantGenerationPipelineService pipeline;

    private VariantJob job;

    private QuizExercise source;

    private QuizExercise variant;

    @BeforeEach
    void setup() {
        jobs = new ExerciseVariantJobService(new LocalDataProviderService(), mock(HyperionWebsocketService.class));
        jobs.init();
        source = new QuizExercise();
        source.setId(1L);
        source.setTitle("Source");
        variant = new QuizExercise();
        variant.setId(2L);
        variant.setTitle("Variant");
        User user = new User();
        user.setLogin("teacher");
        job = jobs.startJob(user, source, new VariantGenerationRequestDTO(null, "Space", null, null, null));
        when(registry.resolve(any())).thenReturn(adapters);
        when(exercises.findByIdElseThrow(1L)).thenReturn(source);
        when(adapters.renderContext(source)).thenReturn("Source context");
        when(adapters.provision(any(), any(), any())).thenReturn(variant);
        VariantToolset tools = mock(VariantToolset.class);
        when(tools.toolCallStats()).thenReturn(Map.of());
        when(adapters.createTools(any(), any())).thenReturn(tools);
        when(agent.runLoop(any(), any(), any(), any(), any(), any(), anyString())).thenReturn(new VariantAgentLoopService.AgentResult("Done", 10));
        when(adapters.verify(any(), any(), any(), any())).thenReturn(new VerificationReport(true, List.of()));
        when(adapters.finalizeVariant(any(), any())).thenReturn(List.of());
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            return response(prompt.getUserMessage().getText().contains("Write the summary") ? "Instructor summary" : PLAN);
        });
        pipeline = createPipeline(ChatClient.create(model));
    }

    private ExerciseVariantGenerationPipelineService createPipeline(ChatClient client) {
        return new ExerciseVariantGenerationPipelineService(registry, agent, jobs, new HyperionPromptTemplateService(), exercises, deletion, mock(LLMTokenUsageService.class),
                mock(UserTestRepository.class), client);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private VariantJob result() {
        return jobs.getJob(job.getJobId(), "teacher").orElseThrow();
    }

    @Test
    void completesVerifiedVariantAndRecordsPlanAndPhaseOutputs() {
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
        assertThat(result().getChangePlan().variantTitle()).isEqualTo("Variant");
        assertThat(result().getWarnings()).isEmpty();
        assertThat(result().getInstructorSummary()).isNull();
        assertThat(result().getStepOutputs()).containsKeys(VariantJobPhase.ANALYZING, VariantJobPhase.PLANNING, VariantJobPhase.PROVISIONING, VariantJobPhase.TRANSFORMING,
                VariantJobPhase.VERIFYING);
    }

    @Test
    void exhaustedBudgetKeepsDraftAndSummarizesWarningsWithoutCompilerLogs() {
        when(agent.runLoop(any(), any(), any(), any(), any(), any(), anyString())).thenReturn(new VariantAgentLoopService.AgentResult(null, 10_000_001));
        when(adapters.verify(any(), any(), any(), any())).thenReturn(new VerificationReport(false,
                List.of(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.SOLUTION_BUILD, "Compilation failed\n\nBuild logs:\ncompiler details"))));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.DRAFT_WITH_WARNINGS);
        assertThat(result().getWarnings()).anySatisfy(warning -> assertThat(warning).contains("TOKEN_BUDGET"));
        assertThat(result().getWarnings()).anySatisfy(warning -> assertThat(warning).contains("Compilation failed").doesNotContain("compiler details"));
        assertThat(result().getInstructorSummary()).isEqualTo("Instructor summary");
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
    }

    @Test
    void finalizationFailureKeepsVerifiedWorkAsDraft() {
        when(adapters.finalizeVariant(any(), any())).thenThrow(new IllegalStateException("group deleted"));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.DRAFT_WITH_WARNINGS);
        assertThat(result().getWarnings()).singleElement().satisfies(warning -> assertThat(warning).contains("FINALIZING", "group deleted", "assign the exercise"));
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
    }

    @Test
    void prePlanningCancellationAndMissingClientProduceDistinctTerminalStates() {
        jobs.requestCancel(job.getJobId(), "teacher");
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(result().getVariantExerciseId()).isNull();
        User user = new User();
        user.setLogin("teacher");
        job = jobs.startJob(user, source, job.getRequest());
        createPipeline(null).run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(result().getFailureDetail()).contains("AI chat client is not configured");
        assertThat(result().getInstructorSummary()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "{\"variantTitle\":\" \"}", "{\"variantTitle\":\"Title\",\"problemStatement\":\" \"}",
            "{\"variantTitle\":\"Title\",\"problemStatement\":\"Statement\",\"intendedChanges\":[]}",
            "{\"variantTitle\":\"Title\",\"problemStatement\":\"Statement\",\"intendedChanges\":[\"Rename\"]}" })
    void malformedPlansRetryAndFailWithUsableReason(String invalidPlan) {
        when(model.call(any(Prompt.class))).thenReturn(response(invalidPlan));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(result().getFailureDetail()).contains("planner produced no valid change plan after 3 attempts");
        assertThat(result().getVariantExerciseId()).isNull();
        verify(model, times(3)).call(argThat((Prompt prompt) -> prompt.getUserMessage().getText().contains("Produce the change plan")));
        verify(model).call(argThat((Prompt prompt) -> prompt.getUserMessage().getText().contains("Write the summary for the instructor")));
        verify(adapters, never()).provision(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void transformFailureDeletesCloneOrRetainsItsIdWhenDeletionFails(boolean deletionFails) {
        when(agent.runLoop(any(), any(), any(), any(), any(), any(), anyString())).thenThrow(new IllegalStateException("agent failed"));
        if (deletionFails) {
            doThrow(new IllegalStateException("delete failed")).when(deletion).delete(2L, true);
        }
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(result().getFailureDetail()).contains("TRANSFORMING", "agent failed");
        assertThat(result().getVariantExerciseId()).isEqualTo(deletionFails ? 2L : null);
        verify(deletion).delete(2L, true);
        verify(adapters, never()).finalizeVariant(any(), any());
        if (deletionFails) {
            assertThat(result().getFailureDetail()).contains("manually");
        }
    }

    @Test
    void cancellationDuringVerificationDeletesCloneAndNeverFinalizes() {
        when(adapters.verify(any(), any(), any(), any())).thenAnswer(_ -> {
            jobs.requestCancel(job.getJobId(), "teacher");
            return new VerificationReport(true, List.of());
        });
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(result().getVariantExerciseId()).isNull();
        verify(deletion).delete(2L, true);
        verify(adapters, never()).finalizeVariant(any(), any());
    }

    @Test
    void repeatedFindingsExhaustRepairAttemptsAndRetainDraft() {
        when(adapters.verify(any(), any(), any(), any())).thenReturn(
                new VerificationReport(false, List.of(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.CONSISTENCY, "Still inconsistent"))));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.DRAFT_WITH_WARNINGS);
        assertThat(result().getAttempt()).isEqualTo(5);
        assertThat(result().getStepOutputs().get(VariantJobPhase.REPAIRING)).hasSize(4);
        assertThat(result().getWarnings()).containsExactly("CONSISTENCY: Still inconsistent");
    }

    @Test
    void malformedPlanIsCorrectedFromItsActualValidationFeedback() {
        when(model.call(any(Prompt.class))).thenReturn(response("{\"variantTitle\":\"\"}"), response(PLAN));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getUserMessage().getText()).contains("Your previous output was invalid: variantTitle must not be blank",
                "Produce a corrected change plan");
    }

    @Test
    void failedProvisionerRetainsItsReportedSurvivingCloneForManualCleanup() {
        when(adapters.provision(any(), any(), any())).thenThrow(new LeftoverVariantExerciseException(9L, "clone survived", null));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(result().getVariantExerciseId()).isEqualTo(9L);
        assertThat(result().getFailureDetail()).contains("PROVISIONING", "clone survived", "(id 9)", "delete it manually");
        verify(adapters, never()).createTools(any(), any());
    }

    @Test
    void failedCancellationCleanupRetainsCloneIdAndManualCleanupInstruction() {
        when(adapters.verify(any(), any(), any(), any())).thenAnswer(_ -> {
            jobs.requestCancel(job.getJobId(), "teacher");
            return new VerificationReport(true, List.of());
        });
        doThrow(new IllegalStateException("cannot delete")).when(deletion).delete(2L, true);
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
        assertThat(result().getFailureDetail()).contains("delete it manually");
        verify(deletion).delete(2L, true);
        verify(adapters, never()).finalizeVariant(any(), any());
    }

    @Test
    void bookkeepingFailureAfterProvisioningDeletesCloneBeforeFailingJob() {
        jobs = org.mockito.Mockito.spy(jobs);
        doThrow(new IllegalStateException("record failed")).when(jobs).recordVariantExerciseId(job.getJobId(), 2L);
        pipeline = createPipeline(ChatClient.create(model));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(result().getVariantExerciseId()).isNull();
        assertThat(result().getFailureDetail()).isEqualTo("Unexpected error: record failed");
        verify(deletion).delete(2L, true);
    }

    @Test
    void notificationFailureAfterCompletionDoesNotDeleteVerifiedWork() {
        jobs = org.mockito.Mockito.spy(jobs);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("notification failed");
        }).when(jobs).complete(anyString(), any(), any(), any());
        pipeline = createPipeline(ChatClient.create(model));
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
        verify(deletion, never()).delete(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "", "failure", "long" })
    void warningSummaryFailureOrMissingContentDoesNotLoseDraft(String summary) {
        when(adapters.finalizeVariant(any(), any())).thenReturn(List.of("Manual review"));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            if (!prompt.getUserMessage().getText().contains("Write the summary")) {
                return response(PLAN);
            }
            if (summary.equals("failure")) {
                throw new IllegalStateException("provider unavailable");
            }
            return response(summary.equals("long") ? "x".repeat(100001) : summary);
        });
        pipeline.run(job);
        assertThat(result().getPhase()).isEqualTo(VariantJobPhase.DRAFT_WITH_WARNINGS);
        assertThat(result().getWarnings()).containsExactly("Manual review");
        assertThat(result().getVariantExerciseId()).isEqualTo(2L);
        if (summary.equals("long")) {
            assertThat(result().getInstructorSummary()).hasSize(100012).endsWith("\n[truncated]");
        }
        else {
            assertThat(result().getInstructorSummary()).isNull();
        }
    }

}
