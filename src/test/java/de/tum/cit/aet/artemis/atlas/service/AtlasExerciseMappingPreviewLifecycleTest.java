package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AtlasExerciseMappingPreviewLifecycleTest {

    private static final String SESSION_ID = "preview-replacement";

    private final AtlasAgentSessionCacheService sessionCache = mock(AtlasAgentSessionCacheService.class);

    private final AtlasAgentDelegationService delegation = mock(AtlasAgentDelegationService.class);

    private ExerciseMappingToolsService tools;

    private AtlasAgentService agent;

    @BeforeEach
    void setUp() {
        ExerciseMappingToolsService.clearExerciseMappingPreview();
        var courseRepository = mock(CourseTestRepository.class);
        var exerciseRepository = mock(ExerciseTestRepository.class);
        var competencyRepository = mock(CourseCompetencyRepository.class);
        var linkRepository = mock(CompetencyExerciseLinkTestRepository.class);
        tools = new ExerciseMappingToolsService(exerciseRepository, competencyRepository, linkRepository, courseRepository, mock(AuthorizationCheckService.class),
                mock(UserTestRepository.class), sessionCache);

        var course = new Course();
        course.setId(10L);
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setTitle("Exercise");
        exercise.setCourse(course);
        var competency = new Competency();
        competency.setId(1L);
        competency.setTitle("Competency");
        competency.setCourse(course);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyRepository.findAllById(List.of(1L))).thenReturn(List.of(competency));
        when(linkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());

        var memory = mock(ChatMemory.class);
        when(memory.get(SESSION_ID)).thenReturn(List.of(new AssistantMessage("Latest response")));
        var surface = new AtlasToolSurface(mock(ToolCallbackProvider.class));
        agent = new AtlasAgentService(mock(ChatClient.class), memory, delegation, surface, surface, surface, surface, mock(ExecutionPlanStateManagerService.class), sessionCache,
                mock(AtlasAgentPreviewService.class));
    }

    @AfterEach
    void tearDown() {
        ExerciseMappingToolsService.clearExerciseMappingPreview();
    }

    @ParameterizedTest
    @CsvSource({ "false,false,false", "false,true,false", "true,false,false", "true,true,false", "false,false,true", "false,true,true", "true,false,true", "true,true,true" })
    void rejectedReplacementIsNotReturnedOrStoredUntilSuccessfulRetry(boolean planContinuation, boolean retry, boolean missingMappings) {
        when(delegation.delegateToAgent(anyString(), anyString(), anyLong(), anyString(), anyBoolean(), any())).thenAnswer(_ -> {
            ExerciseMappingToolsService.setCurrentSessionId(SESSION_ID);
            assertThat(tools.previewExerciseCompetencyMapping(10L, 42L, List.of(mapping(1L)), false)).contains("Preview generated successfully");
            var previous = ExerciseMappingToolsService.getExerciseMappingPreview();
            if (missingMappings) {
                assertThat(tools.previewExerciseCompetencyMapping(10L, 42L, null, false)).contains("mappings is required");
            }
            else {
                assertThat(tools.previewExerciseCompetencyMapping(10L, 42L, List.of(mapping(1L), mapping(2L), mapping(3L), mapping(4L)), false)).contains("at most 3");
            }
            assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isSameAs(previous);
            assertThat(ExerciseMappingToolsService.hasRejectedExerciseMappingPreview()).isTrue();
            if (retry) {
                assertThat(tools.previewExerciseCompetencyMapping(10L, 42L, List.of(mapping(1L)), true)).contains("Preview generated successfully");
                assertThat(ExerciseMappingToolsService.hasRejectedExerciseMappingPreview()).isFalse();
            }
            return "Latest response";
        });

        AtlasAgentChatResponseDTO response;
        if (planContinuation) {
            response = ReflectionTestUtils.invokeMethod(agent, "delegateToNextStepAgent", ExecutionPlanStateManagerService.AgentType.EXERCISE_MAPPER, "Preview mappings", 10L,
                    SESSION_ID, "Previous step");
        }
        else {
            response = agent.processChatMessage("Preview mappings", 10L, SESSION_ID);
        }

        assertThat(response).isNotNull();
        if (retry) {
            assertThat(response.exerciseMappingPreview()).isNotNull();
            assertThat(response.exerciseMappingPreview().viewOnly()).isTrue();
            assertThat(response.exerciseMappingPreview().competencies()).extracting(ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO::competencyId).containsExactly(1L);
            verify(sessionCache).storePreviewForMessage(eq(SESSION_ID), eq(0), any());
        }
        else {
            assertThat(response.exerciseMappingPreview()).isNull();
            verify(sessionCache, never()).storePreviewForMessage(anyString(), anyInt(), any());
        }
        verify(sessionCache, never()).getCachedExerciseMappingPreview(SESSION_ID);
        if (!planContinuation) {
            assertThat(ExerciseMappingToolsService.hasRejectedExerciseMappingPreview()).isFalse();
            assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isNull();
        }
    }

    private static ExerciseMappingToolsService.ExerciseCompetencyMappingOperation mapping(long id) {
        return new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(id, 0.5, false, true);
    }
}
