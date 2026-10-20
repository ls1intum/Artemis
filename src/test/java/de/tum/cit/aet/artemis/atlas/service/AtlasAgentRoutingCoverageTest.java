package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;

import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;
import de.tum.cit.aet.artemis.atlas.service.ExecutionPlanStateManagerService.AgentType;
import de.tum.cit.aet.artemis.atlas.service.ExecutionPlanStateManagerService.NextStepContext;
import de.tum.cit.aet.artemis.atlas.service.ExecutionPlanStateManagerService.StepResult;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@ExtendWith(MockitoExtension.class)
class AtlasAgentRoutingCoverageTest {

    @Mock
    private ChatMemory memory;

    @Mock
    private AtlasAgentDelegationService delegation;

    @Mock
    private ExecutionPlanStateManagerService plans;

    @Mock
    private AtlasAgentSessionCacheService cache;

    private AtlasAgentService service;

    private CompetencyExpertToolsService expert;

    @BeforeEach
    void setUp() {
        var surface = new AtlasToolSurface(mock(ToolCallbackProvider.class));
        service = new AtlasAgentService(mock(ChatClient.class), memory, delegation, surface, surface, surface, surface, plans, cache, new AtlasAgentPreviewService(memory));
        expert = new CompetencyExpertToolsService(JsonObjectMapper.get(), null, null, cache);
        CompetencyMappingToolsService.clearAllPreviews();
        CompetencyExpertToolsService.getAndClearPreviews();
    }

    @ParameterizedTest
    @ValueSource(strings = { " CANCEL PLAN ", "stop plan", "cancel workflow", "stop workflow" })
    void cancelVariantsCancelActivePlanAndClearRequestState(String command) {
        when(plans.hasPlan("s")).thenReturn(true);
        var result = service.processChatMessage(command, 1L, "s");
        assertThat(result.message()).isEqualTo("Plan cancelled.");
        assertThat(result.competenciesModified()).isFalse();
        verify(plans).cancelPlan("s");
        verify(cache).clearCachedExerciseMappingPreview("s");
        assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();
    }

    @Test
    void failedDelegationCancelsActivePlanAndRemovesCachedPreview() {
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenThrow(new IllegalStateException("unavailable"));
        when(plans.hasPlan("s")).thenReturn(true);
        var result = service.processChatMessage("Create Java competency", 1L, "s");
        assertThat(result.message()).contains("having trouble processing your request");
        assertThat(result.competencyPreviews()).isNull();
        verify(plans).cancelPlan("s");
        verify(cache).clearCachedExerciseMappingPreview("s");
    }

    @Test
    void assistantApprovalMarkerDelegatesToExpertAndClearsPendingOperations() {
        var operation = new CompetencyOperation(5L, "Java", "Language", CompetencyTaxonomy.APPLY);
        when(cache.getCachedPendingCompetencyOperations("s")).thenReturn(List.of(operation));
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("Created")));
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            if (call.getArgument(0, String.class).contains("competency_expert")) {
                expert.previewCompetencies(1L, List.of(operation), true);
                return "Created Java";
            }
            return "  [CREATE_APPROVED_COMPETENCY]  ";
        });
        var result = service.processChatMessage("Looks good", 1L, "s");
        assertThat(result.message()).isEqualTo("Created Java");
        assertThat(result.competenciesModified()).isTrue();
        assertThat(result.competencyPreviews()).isNull();
        verify(cache, org.mockito.Mockito.atLeastOnce()).clearCachedPendingCompetencyOperations("s");
        var captured = ArgumentCaptor.forClass(AtlasAgentSessionCacheService.MessagePreviewData.class);
        verify(cache).storePreviewForMessage(eq("s"), eq(0), captured.capture());
        assertThat(captured.getValue().competencyPreviews()).singleElement().satisfies(preview -> {
            assertThat(preview.title()).isEqualTo("Java");
            assertThat(preview.viewOnly()).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(AgentType.class)
    void approvalContinuesPlanWithAgentSpecificBriefAndPreviousResults(AgentType nextAgent) {
        var operation = new CompetencyOperation(5L, "Java", "Language", CompetencyTaxonomy.APPLY);
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("Created")));
        when(plans.hasPlan("s")).thenReturn(true);
        var previous = List.of(new StepResult(List.of(5L, 6L), "Created Java"), new StepResult(List.of(), "Reviewed"));
        when(plans.completeStepAndGetNext(eq("s"), any())).thenReturn(Optional.of(new NextStepContext(nextAgent, "Teach Java", previous, 8L, "Classes")));
        var exercisePreview = new ExerciseCompetencyMappingDTO(8L, "Classes", List.of(), false);
        if (nextAgent == AgentType.EXERCISE_MAPPER) {
            when(cache.getCachedExerciseMappingPreview("s")).thenReturn(exercisePreview);
        }
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            if (call.getArgument(4, Boolean.class)) {
                expert.previewCompetencies(1L, List.of(operation), false);
                return "Created";
            }
            String brief = call.getArgument(1);
            assertThat(brief).contains("ACTION:", "ORIGINAL USER REQUEST: Teach Java", "Created Java (IDs: 5, 6)", "- Reviewed");
            if (nextAgent == AgentType.EXERCISE_MAPPER) {
                assertThat(brief).contains("EXERCISE_ID: 8 | EXERCISE_TITLE: Classes");
            }
            else if (nextAgent == AgentType.COMPETENCY_EXPERT) {
                expert.previewCompetencies(1L, List.of(operation), false);
            }
            return "Next preview";
        });
        var result = service.processChatMessage("[CREATE_APPROVED_COMPETENCY]", 1L, "s");
        assertThat(result.message()).isEqualTo("Created\n\n---\n\nContinuing to next step...\n\nNext preview");
        if (nextAgent == AgentType.EXERCISE_MAPPER) {
            assertThat(result.exerciseMappingPreview()).isSameAs(exercisePreview);
        }
        if (nextAgent == AgentType.COMPETENCY_EXPERT) {
            assertThat(result.competencyPreviews()).singleElement().satisfies(preview -> assertThat(preview.competencyId()).isEqualTo(5L));
        }
        var step = ArgumentCaptor.forClass(StepResult.class);
        verify(plans).completeStepAndGetNext(eq("s"), step.capture());
        assertThat(step.getValue().ids()).containsExactly(5L);
        assertThat(step.getValue().summary()).isEqualTo("Created competencies: Java");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"exerciseId\":9,\"mappings\":null}", "{\"exerciseId\":9,\"mappings\":[]}" })
    void mappingApprovalWithoutSelectionsUsesExerciseContextWhenPresent(String payload) {
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenReturn("Saved");
        var result = service.processChatMessage("[CREATE_APPROVED_EXERCISE_MAPPING]:" + payload, 1L, "s");
        assertThat(result.message()).isEqualTo("Saved");
        var brief = ArgumentCaptor.forClass(String.class);
        verify(delegation).delegateToAgent(eq("/prompts/atlas/exercise_mapper_system_prompt.st"), brief.capture(), eq(1L), eq("s"), eq(true), any());
        assertThat(brief.getValue()).isEqualTo(payload.equals("{}") ? "[CREATE_APPROVED_EXERCISE_MAPPING]" : "[CREATE_APPROVED_EXERCISE_MAPPING]\nEXERCISE_ID: 9");
    }

    @Test
    void relationApprovalUsesCachedOperationsAndReturnsRelationPreviews() {
        var relation = new de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO(3L, 1L, 2L, RelationType.EXTENDS);
        when(cache.getCachedRelationData("s")).thenReturn(List.of(relation));
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenReturn("Saved relation");
        var result = service.processChatMessage("[CREATE_APPROVED_RELATION]", 1L, "s");
        assertThat(result.message()).isEqualTo("Saved relation");
        assertThat(result.relationPreviews()).isNull();
        verify(cache).clearCachedRelationOperations("s");
    }

    @Test
    void historySkipsInternalMessagesButKeepsPreviewIndexAligned() {
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("TOPICS: internal"), new AssistantMessage("ACTION: internal"),
                new AssistantMessage("x REQUIREMENTS: y CONSTRAINTS: z CONTEXT: internal"), new AssistantMessage("[CREATE_APPROVED_COMPETENCY]"),
                new UserMessage("[CREATE_APPROVED_EXERCISE_MAPPING]:{}"), new AssistantMessage("MULTI-STEP PLAN CONTINUATION internal"), new SystemMessage("system"),
                new UserMessage("Visible"), new AssistantMessage("Answer")));
        var relation = new CompetencyRelationPreviewDTO(3L, 1L, "A", 2L, "B", RelationType.EXTENDS, false);
        when(cache.getPreviewHistory("s")).thenReturn(Map.of(5, new AtlasAgentSessionCacheService.MessagePreviewData(null, List.of(relation), null, null)));
        var history = service.getConversationHistoryAsDTO("s");
        assertThat(history).extracting(message -> message.content()).containsExactly("Visible", "Answer");
        assertThat(history.get(1).relationPreviews()).containsExactly(relation);
    }

    @Test
    void ordinaryResponseRecoversCrossNodeExercisePreviewAndCachesAtAssistantIndex() {
        var preview = new ExerciseCompetencyMappingDTO(8L, "Classes", List.of(), false);
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenReturn("Preview");
        when(cache.getCachedExerciseMappingPreview("s")).thenReturn(preview);
        when(memory.get("s")).thenReturn(List.of(new UserMessage("Map"), new AssistantMessage("Preview")));
        var result = service.processChatMessage("Map", 1L, "s");
        assertThat(result.exerciseMappingPreview()).isSameAs(preview);
        var captured = ArgumentCaptor.forClass(AtlasAgentSessionCacheService.MessagePreviewData.class);
        verify(cache).storePreviewForMessage(eq("s"), eq(0), captured.capture());
        assertThat(captured.getValue().exerciseMappingPreview()).isSameAs(preview);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "Classes" })
    void exerciseContinuationHandlesMissingAndPresentExerciseMetadata(String title) {
        when(plans.hasPlan("s")).thenReturn(true);
        when(plans.completeStepAndGetNext(eq("s"), any()))
                .thenReturn(Optional.of(new NextStepContext(AgentType.EXERCISE_MAPPER, "Teach", List.of(), title.isEmpty() ? null : 8L, title)));
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            if (!call.getArgument(4, Boolean.class)) {
                String brief = call.getArgument(1);
                assertThat(brief).contains(title.isBlank() ? "to exercises in the course" : "EXERCISE_TITLE: Classes");
                assertThat(brief).doesNotContain("CONTEXT FROM PREVIOUS STEPS");
            }
            return "Done";
        });
        var result = service.processChatMessage("[CREATE_APPROVED_EXERCISE_MAPPING]", 1L, "s");
        assertThat(result.message()).contains("Continuing to next step");
        assertThat(result.exerciseMappingPreview()).isNull();
    }

    @Test
    void approvalAtEndOfPlanReturnsSavedResultWithoutFurtherDelegation() {
        when(plans.hasPlan("s")).thenReturn(true);
        when(plans.completeStepAndGetNext(eq("s"), any())).thenReturn(Optional.empty());
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenReturn("Saved");
        assertThat(service.processChatMessage("[CREATE_APPROVED_EXERCISE_MAPPING]", 1L, "s").message()).isEqualTo("Saved");
        verify(delegation).delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), eq(true), any());
    }

    @Test
    void planMarkerPreservesExerciseMetadataAndOriginalGoal() {
        String response = "%%ARTEMIS_PLAN:CREATE_AND_MAP_EXERCISES:exerciseId=8:exerciseTitle= Classes %%";
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenReturn(response);
        assertThat(service.processChatMessage("Teach Java", 1L, "s").message()).isEqualTo(response);
        verify(plans).initializePlan("s", ExecutionPlanStateManagerService.PlanTemplate.CREATE_AND_MAP_EXERCISES, "Teach Java", 8L, "Classes");
    }

    @ParameterizedTest
    @ValueSource(strings = { "[CREATE_APPROVED_RELATION]", "[CREATE_APPROVED_EXERCISE_MAPPING]" })
    void mainAgentApprovalMarkersRouteToAppropriateSpecialist(String marker) {
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            return call.getArgument(0, String.class).equals("/prompts/atlas/agent_system_prompt.st") ? marker : "Specialist saved";
        });
        var result = service.processChatMessage("Approve", 1L, "s");
        assertThat(result.message()).isEqualTo("Specialist saved");
        assertThat(result.competenciesModified()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void competencyPreviewsAreReturnedAndCachedOnlyWhenAssistantMessageExists(boolean hasAssistantMessage) {
        var operation = new CompetencyOperation(null, "Java", "Language", null);
        when(memory.get("s")).thenReturn(hasAssistantMessage ? List.of(new AssistantMessage("Preview")) : List.of());
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            expert.previewCompetencies(1L, List.of(operation), false);
            return "Preview";
        });
        var result = service.processChatMessage("Preview Java", 1L, "s");
        assertThat(result.competencyPreviews()).singleElement().satisfies(preview -> assertThat(preview.title()).isEqualTo("Java"));
        verify(cache, org.mockito.Mockito.times(hasAssistantMessage ? 1 : 0)).storePreviewForMessage(eq("s"), any(Integer.class), any());
        assertThat(CompetencyExpertToolsService.getAndClearPreviews()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void relationApprovalCachesActualPreviewAndCompletesPlanWithCreatedRelationIds(boolean batch) {
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("Saved")));
        when(plans.hasPlan("s")).thenReturn(true);
        when(plans.completeStepAndGetNext(eq("s"), any())).thenReturn(Optional.empty());
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            populateRelationPreview(batch);
            return "Saved relations";
        });
        var result = service.processChatMessage("[CREATE_APPROVED_RELATION]", 1L, "s");
        assertThat(result.message()).isEqualTo("Saved relations");
        assertThat(result.relationPreviews()).hasSize(batch ? 2 : 1);
        assertThat(result.relationGraphPreview().edges()).hasSize(batch ? 2 : 1);
        var step = ArgumentCaptor.forClass(StepResult.class);
        verify(plans).completeStepAndGetNext(eq("s"), step.capture());
        assertThat(step.getValue().ids()).containsExactly(5L);
        assertThat(step.getValue().summary()).isEqualTo("Created " + (batch ? 2 : 1) + " relation(s)");
        var cached = ArgumentCaptor.forClass(AtlasAgentSessionCacheService.MessagePreviewData.class);
        verify(cache).storePreviewForMessage(eq("s"), eq(0), cached.capture());
        assertThat(cached.getValue().relationPreviews()).isEqualTo(result.relationPreviews());
        assertThat(CompetencyMappingToolsService.getRelationGraphPreview()).isNull();
    }

    @Test
    void mapperContinuationReturnsRelationPreviewAndPersistsCombinedHistory() {
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("Saved")));
        when(plans.hasPlan("s")).thenReturn(true);
        when(plans.completeStepAndGetNext(eq("s"), any())).thenReturn(Optional.of(new NextStepContext(AgentType.COMPETENCY_MAPPER, "Map Java", List.of(), null, null)));
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            if (!call.getArgument(4, Boolean.class)) {
                populateRelationPreview(false);
            }
            return call.getArgument(4, Boolean.class) ? "Saved" : "Mapping preview";
        });
        var result = service.processChatMessage("[CREATE_APPROVED_EXERCISE_MAPPING]", 1L, "s");
        assertThat(result.relationPreviews()).singleElement().satisfies(relation -> assertThat(relation.relationId()).isEqualTo(5L));
        assertThat(result.message()).isEqualTo("Saved\n\n---\n\nContinuing to next step...\n\nMapping preview");
        var added = ArgumentCaptor.forClass(AssistantMessage.class);
        verify(memory).add(eq("s"), added.capture());
        assertThat(added.getValue().getText()).isEqualTo(result.message());
    }

    @Test
    void ordinaryMessageReturnsRelationGraphPreviewAndCachesItsExactPayload() {
        when(memory.get("s")).thenReturn(List.of(new AssistantMessage("Preview")));
        when(delegation.delegateToAgent(anyString(), anyString(), eq(1L), eq("s"), anyBoolean(), any())).thenAnswer(call -> {
            populateRelationPreview(false);
            return "Preview";
        });
        var result = service.processChatMessage("Map competencies", 1L, "s");
        assertThat(result.relationPreviews()).hasSize(1);
        assertThat(result.relationGraphPreview().nodes()).hasSize(2);
        var cached = ArgumentCaptor.forClass(AtlasAgentSessionCacheService.MessagePreviewData.class);
        verify(cache).storePreviewForMessage(eq("s"), eq(0), cached.capture());
        assertThat(cached.getValue().relationGraphPreview()).isEqualTo(result.relationGraphPreview());
    }

    private void populateRelationPreview(boolean batch) {
        var competencies = mock(de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository.class);
        var relations = mock(de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository.class);
        var course = new de.tum.cit.aet.artemis.course.domain.Course();
        course.setId(1L);
        var first = new de.tum.cit.aet.artemis.atlas.domain.competency.Competency();
        first.setId(1L);
        first.setTitle("Java");
        first.setCourse(course);
        var second = new de.tum.cit.aet.artemis.atlas.domain.competency.Competency();
        second.setId(2L);
        second.setTitle("OOP");
        second.setCourse(course);
        when(competencies.findById(1L)).thenReturn(Optional.of(first));
        when(competencies.findById(2L)).thenReturn(Optional.of(second));
        when(competencies.findAllById(java.util.Set.of(1L, 2L))).thenReturn(List.of(first, second));
        when(relations.findAllWithHeadAndTailByCourseId(1L)).thenReturn(java.util.Set.of());
        var tools = new CompetencyMappingToolsService(JsonObjectMapper.get(), competencies, relations, null, null, cache, Optional.empty(), null, null);
        var existing = new de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO(5L, 1L, 2L, RelationType.EXTENDS);
        var unsaved = new de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO(null, 2L, 1L, RelationType.ASSUMES);
        assertThat(tools.previewRelationMappings(1L, batch ? List.of(existing, unsaved) : List.of(existing), false)).contains("Preview generated successfully");
    }
}
