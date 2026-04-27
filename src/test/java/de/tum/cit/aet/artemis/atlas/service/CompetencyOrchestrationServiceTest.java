package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/** Unit tests for the UNSUPPORTED_EXERCISE / NO_CHAT_CLIENT fast-paths of {@link CompetencyOrchestrationService#run(long)}. */
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
    private IMap<Long, CompetencyOrchestrationService.RunInfo> runMap;

    private AtlasOrchestratorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AtlasOrchestratorProperties("gpt-test-chat", 0.8, "gpt-test-orchestrator", 1.0, "");
        when(hazelcastInstance.<Long, CompetencyOrchestrationService.RunInfo>getMap("atlas-orchestrator-runs")).thenReturn(runMap);
    }

    @Test
    void run_noChatClient_returnsFailedNoChatClient() {
        ProgrammingExercise exercise = courseExercise(11L);
        when(programmingExerciseRepository.findByIdElseThrow(11L)).thenReturn(exercise);
        CompetencyOrchestrationService service = new CompetencyOrchestrationService(programmingExerciseRepository, contentExtractionService, orchestratorToolsService,
                templateService, null, toolCallbackFactory, hazelcastInstance, properties);

        CompetencyOrchestrationResultDTO result = service.run(11L);

        assertThat(result.status()).isEqualTo(CompetencyOrchestrationResultDTO.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo(CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT);
        verify(runMap, never()).putIfAbsent(anyLong(), any());
    }

    @Test
    void run_examExercise_returnsUnsupportedExercise() {
        ProgrammingExercise exam = examExercise(12L);
        when(programmingExerciseRepository.findByIdElseThrow(12L)).thenReturn(exam);
        CompetencyOrchestrationService service = new CompetencyOrchestrationService(programmingExerciseRepository, contentExtractionService, orchestratorToolsService,
                templateService, null, toolCallbackFactory, hazelcastInstance, properties);

        CompetencyOrchestrationResultDTO result = service.run(12L);

        assertThat(result.status()).isEqualTo(CompetencyOrchestrationResultDTO.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo(CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE);
        verify(runMap, never()).putIfAbsent(anyLong(), any());
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
