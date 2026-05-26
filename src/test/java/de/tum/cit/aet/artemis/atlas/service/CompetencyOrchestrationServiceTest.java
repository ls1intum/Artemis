package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.FAILED;
import static de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.Status.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService.RunInfo;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/** Unit tests for the fast-paths and lock-release behavior of {@link CompetencyOrchestrationService#run(long)}. */
@ExtendWith(MockitoExtension.class)
class CompetencyOrchestrationServiceTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ContentExtractionService contentExtractionService;

    @Mock
    private OrchestratorToolsService orchestratorToolsService;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private AtlasAgentToolCallbackService toolCallbackFactory;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private IMap<Long, RunInfo> runMap;

    private AtlasOrchestratorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AtlasOrchestratorProperties("gpt-test-orchestrator", 1.0, "");
    }

    @Test
    void run_noChatClient_returnsFailedNoChatClient() {
        when(programmingExerciseRepository.findByIdElseThrow(11L)).thenReturn(courseExercise(11L));

        CompetencyOrchestrationResultDTO result = createService(null).run(11L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(NO_CHAT_CLIENT);
        verify(runMap, never()).putIfAbsent(anyLong(), any());
    }

    @Test
    void run_examExercise_returnsUnsupportedExercise() {
        when(programmingExerciseRepository.findByIdElseThrow(12L)).thenReturn(examExercise(12L));

        CompetencyOrchestrationResultDTO result = createService(null).run(12L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(UNSUPPORTED_EXERCISE);
        verify(runMap, never()).putIfAbsent(anyLong(), any());
    }

    @Test
    void run_alreadyInProgress_returnsInProgress() {
        when(programmingExerciseRepository.findByIdElseThrow(13L)).thenReturn(courseExercise(13L));
        stubRunMap();
        when(runMap.putIfAbsent(eq(COURSE_ID), any(RunInfo.class))).thenReturn(new RunInfo("other-run", 99L, Instant.now()));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(13L);

        assertThat(result.status()).isEqualTo(IN_PROGRESS);
        verify(runMap, never()).remove(anyLong(), any());
    }

    @Test
    void run_releasesLockOnException() {
        ProgrammingExercise exercise = courseExercise(14L);
        when(programmingExerciseRepository.findByIdElseThrow(14L)).thenReturn(exercise);
        stubRunMap();
        when(runMap.putIfAbsent(eq(COURSE_ID), any(RunInfo.class))).thenReturn(null);
        when(contentExtractionService.extractContent(exercise)).thenThrow(new RuntimeException("boom"));

        CompetencyOrchestrationResultDTO result = createServiceWithRunMap(mock(ChatClient.class)).run(14L);

        assertThat(result.status()).isEqualTo(FAILED);
        assertThat(result.failureReason()).isEqualTo(LLM_ERROR);
        verify(runMap).remove(eq(COURSE_ID), any(RunInfo.class));
    }

    private CompetencyOrchestrationService createService(@Nullable ChatClient chatClient) {
        return new CompetencyOrchestrationService(programmingExerciseRepository, contentExtractionService, orchestratorToolsService, templateService, chatClient,
                toolCallbackFactory, hazelcastInstance, properties);
    }

    private CompetencyOrchestrationService createServiceWithRunMap(@Nullable ChatClient chatClient) {
        CompetencyOrchestrationService service = createService(chatClient);
        service.initRunMap();
        return service;
    }

    private void stubRunMap() {
        when(hazelcastInstance.<Long, RunInfo>getMap("atlas-orchestrator-runs")).thenReturn(runMap);
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
}
