package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests for {@link ExerciseMappingToolsService}.
 * Tests the tools provided to the Exercise Mapper sub-agent for managing exercise-to-competency mappings.
 */
@ExtendWith(MockitoExtension.class)
class ExerciseMappingToolsServiceTest {

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AtlasMLApi atlasMLApi;

    @Mock
    private AtlasAgentSessionCacheService sessionCacheService;

    private ExerciseMappingToolsService service;

    private ObjectMapper objectMapper;

    private Course course;

    private Competency competency1;

    private Competency competency2;

    private Exercise exercise;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ExerciseMappingToolsService(exerciseRepository, courseCompetencyRepository, competencyExerciseLinkRepository, courseRepository, authorizationCheckService,
                userRepository, atlasMLApi, sessionCacheService);

        course = new Course();
        course.setId(10L);

        competency1 = new Competency();
        competency1.setId(1L);
        competency1.setTitle("Sorting Algorithms");
        competency1.setCourse(course);

        competency2 = new Competency();
        competency2.setId(2L);
        competency2.setTitle("Data Structures");
        competency2.setCourse(course);

        exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setTitle("Bubble Sort");
        exercise.setCourse(course);

        ExerciseMappingToolsService.clearExerciseMappingPreview();
        ExerciseMappingToolsService.clearUserSelectedMappings();
    }

    @Test
    void getCourseCompetencies_returnsCompetencies() throws Exception {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findByCourseIdOrderById(10L)).thenReturn(List.of(competency1, competency2));

        JsonNode json = objectMapper.readTree(service.getCourseCompetencies(10L));

        assertThat(json.get("courseId").asLong()).isEqualTo(10L);
        assertThat(json.get("competencies")).hasSize(2);
        assertThat(json.get("competencies").get(0).get("title").asText()).isEqualTo("Sorting Algorithms");
    }

    @Test
    void getCourseCompetencies_courseNotFound() throws Exception {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        JsonNode json = objectMapper.readTree(service.getCourseCompetencies(999L));

        assertThat(json.get("error").asText()).contains("Course not found");
    }

    @Test
    void preview_generatesPreview_withAtlasMLSuggestions() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(atlasMLApi.suggestCompetencies(any(SuggestCompetencyRequestDTO.class)))
                .thenReturn(new SuggestCompetencyResponseDTO(List.of(new AtlasMLCompetencyDTO(1L, "Sorting Algorithms", null, 10L))));
        when(courseCompetencyRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(competency1, competency2));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, false, false),
                new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(2L, 0.5, false, false));

        String result = service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);

        assertThat(result).contains("Preview generated successfully");

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview).isNotNull();
        assertThat(preview.exerciseId()).isEqualTo(42L);
        // competency1 (id=1) was returned by AtlasML -> suggested=true
        assertThat(preview.competencies().get(0).suggested()).isTrue();
        // competency2 (id=2) not in AtlasML response -> suggested=false
        assertThat(preview.competencies().get(1).suggested()).isFalse();
        assertThat(preview.viewOnly()).isFalse();
    }

    @Test
    void preview_usesLlmSuggestedFlags_whenAtlasMLUnavailable() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(atlasMLApi.suggestCompetencies(any(SuggestCompetencyRequestDTO.class))).thenThrow(new RuntimeException("AtlasML down"));
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(competency1, competency2));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, false, true),
                new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(2L, 0.5, false, false));

        service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview).isNotNull();
        assertThat(preview.competencies().get(0).suggested()).isTrue();   // LLM flag preserved
        assertThat(preview.competencies().get(1).suggested()).isFalse();
    }

    @Test
    void preview_marksExistingLinksAsAlreadyMapped() {
        CompetencyExerciseLink existingLink = new CompetencyExerciseLink();
        existingLink.setCompetency(competency1);
        existingLink.setExercise(exercise);
        existingLink.setWeight(0.5);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existingLink));
        when(atlasMLApi.suggestCompetencies(any())).thenReturn(new SuggestCompetencyResponseDTO(List.of()));
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(competency1));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, false, false));
        service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.competencies().get(0).alreadyMapped()).isTrue();
    }

    @Test
    void preview_viewOnly_doesNotSetPreview_whenFlagTrue() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(atlasMLApi.suggestCompetencies(any())).thenReturn(new SuggestCompetencyResponseDTO(List.of()));
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(competency1));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, false, false));
        service.previewExerciseCompetencyMapping(10L, 42L, mappings, true);

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview).isNotNull();
        assertThat(preview.viewOnly()).isTrue();
    }

    @Test
    void preview_exerciseNotFound_returnsError() throws Exception {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(99L)).thenReturn(Optional.empty());

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, false, false));
        JsonNode json = objectMapper.readTree(service.previewExerciseCompetencyMapping(10L, 99L, mappings, false));

        assertThat(json.get("success").asBoolean()).isFalse();
    }

    @Test
    void save_createsNewLink() throws Exception {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(1L))).thenReturn(List.of(competency1));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, false, false));
        JsonNode json = objectMapper.readTree(service.saveExerciseCompetencyMappings(10L, 42L, mappings));

        assertThat(json.get("success").asBoolean()).isTrue();
        verify(competencyExerciseLinkRepository).saveAll(any());
        verify(atlasMLApi).mapCompetencyToExercise(42L, 1L);
    }

    @Test
    void save_deletesRemovedLink() throws Exception {
        CompetencyExerciseLink existingLink = new CompetencyExerciseLink();
        existingLink.setCompetency(competency1);
        existingLink.setExercise(exercise);
        existingLink.setWeight(0.5);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existingLink));
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of());

        JsonNode json = objectMapper.readTree(service.saveExerciseCompetencyMappings(10L, 42L, List.of()));

        assertThat(json.get("success").asBoolean()).isTrue();
        verify(competencyExerciseLinkRepository).deleteAll(any());
        verify(atlasMLApi, never()).mapCompetencyToExercise(any(), any());
    }

    @Test
    void save_updatesWeightOnExistingLink() throws Exception {
        CompetencyExerciseLink existingLink = new CompetencyExerciseLink();
        existingLink.setCompetency(competency1);
        existingLink.setExercise(exercise);
        existingLink.setWeight(0.5);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existingLink));
        when(courseCompetencyRepository.findAllById(List.of())).thenReturn(List.of());

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, true, false));
        service.saveExerciseCompetencyMappings(10L, 42L, mappings);

        assertThat(existingLink.getWeight()).isEqualTo(1.0);
        verify(atlasMLApi, never()).mapCompetencyToExercise(any(), any());
    }

    @Test
    void save_usesUserSelectedMappings_whenSet() throws Exception {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(2L))).thenReturn(List.of(competency2));

        // User selected competency2, but LLM passes competency1
        var userMappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(2L, 0.5, false, false));
        ExerciseMappingToolsService.setUserSelectedMappings(userMappings);

        var llmMappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, false, false));
        service.saveExerciseCompetencyMappings(10L, 42L, llmMappings);

        verify(atlasMLApi).mapCompetencyToExercise(42L, 2L);   // user's competency was used
        verify(atlasMLApi, never()).mapCompetencyToExercise(42L, 1L);
    }

    @Test
    void save_courseNotFound_returnsError() throws Exception {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        JsonNode json = objectMapper.readTree(service.saveExerciseCompetencyMappings(999L, 42L, List.of()));

        assertThat(json.get("success").asBoolean()).isFalse();
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
    }

    @Test
    void clearExerciseMappingPreview_removesStoredPreview() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(atlasMLApi.suggestCompetencies(any())).thenReturn(new SuggestCompetencyResponseDTO(List.of()));
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of());

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, false, false));
        service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isNotNull();

        ExerciseMappingToolsService.clearExerciseMappingPreview();
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isNull();
    }

    @Test
    void setUserSelectedMappings_isConsumedOnSave() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of());

        ExerciseMappingToolsService.setUserSelectedMappings(List.of());
        service.saveExerciseCompetencyMappings(10L, 42L, List.of());

        ExerciseMappingToolsService.clearUserSelectedMappings();
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isNull();
    }

    @Test
    void save_checksInstructorRole() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of());

        service.saveExerciseCompetencyMappings(10L, 42L, List.of());

        verify(authorizationCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    }
}
