package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
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
    private AtlasAgentSessionCacheService sessionCacheService;

    private ExerciseMappingToolsService service;

    private JsonMapper objectMapper;

    private Course course;

    private Competency competency1;

    private Competency competency2;

    private Exercise exercise;

    @BeforeEach
    void setUp() {
        objectMapper = JsonObjectMapper.get();
        service = new ExerciseMappingToolsService(exerciseRepository, courseCompetencyRepository, competencyExerciseLinkRepository, courseRepository, authorizationCheckService,
                userRepository, sessionCacheService);

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
        assertThat(json.get("competencies").get(0).get("title").asString()).isEqualTo("Sorting Algorithms");
    }

    @Test
    void getCourseCompetencies_courseNotFound() throws Exception {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        JsonNode json = objectMapper.readTree(service.getCourseCompetencies(999L));

        assertThat(json.get("error").asString()).contains("Course not found");
    }

    @Test
    void preview_usesLlmSuggestedFlags() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(competency1, competency2));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, false, false),
                new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(2L, 0.5, false, true));

        String result = service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);

        assertThat(result).contains("Preview generated successfully");

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview).isNotNull();
        assertThat(preview.exerciseId()).isEqualTo(42L);
        assertThat(preview.competencies()).filteredOn(option -> option.competencyId().equals(1L)).singleElement().satisfies(option -> assertThat(option.suggested()).isFalse());
        assertThat(preview.competencies()).filteredOn(option -> option.competencyId().equals(2L)).singleElement().satisfies(option -> assertThat(option.suggested()).isTrue());
        assertThat(preview.viewOnly()).isFalse();
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
        when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(competency1));

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, false, false));
        service.previewExerciseCompetencyMapping(10L, 42L, mappings, false);

        ExerciseCompetencyMappingDTO preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.competencies().get(0).alreadyMapped()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preview_acceptsThreeSuggestionsAlongsideExistingMappingsAndMissingFlags(boolean viewOnly) {
        var competencies = LongStream.rangeClosed(1, 5).<CourseCompetency>mapToObj(id -> {
            var competency = new Competency();
            competency.setId(id);
            competency.setTitle("Competency " + id);
            competency.setCourse(course);
            return competency;
        }).toList();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(courseCompetencyRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(competencies);
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L))
                .thenReturn(List.of(new CompetencyExerciseLink(competencies.get(3), exercise, 0.5), new CompetencyExerciseLink(competencies.get(4), exercise, 0.5)));
        var mappings = List.of(previewMapping(1L, true), previewMapping(2L, true), previewMapping(3L, true), previewMapping(4L, false), previewMapping(5L, null));

        assertThat(service.previewExerciseCompetencyMapping(10L, 42L, mappings, viewOnly)).contains("Preview generated successfully");

        var preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.viewOnly()).isEqualTo(viewOnly);
        assertThat(preview.competencies()).hasSize(5).allSatisfy(option -> assertThat(option.weight()).isEqualTo(0.5));
        assertThat(preview.competencies()).filteredOn(option -> Boolean.TRUE.equals(option.suggested()))
                .extracting(ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO::competencyId).containsExactly(1L, 2L, 3L);
        assertThat(preview.competencies()).filteredOn(option -> Boolean.TRUE.equals(option.alreadyMapped())).allSatisfy(option -> assertThat(option.suggested()).isFalse())
                .extracting(ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO::competencyId).containsExactly(4L, 5L);
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
        verify(competencyExerciseLinkRepository, never()).deleteAll(any(Iterable.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preview_rejectsFourSuggestionsWithoutReplacingPreviousPreviewOrCache(boolean viewOnly) {
        var existingLink = new CompetencyExerciseLink(competency1, exercise, 0.5);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existingLink));
        when(courseCompetencyRepository.findAllById(List.of(1L))).thenReturn(List.of(competency1));
        ExerciseMappingToolsService.setCurrentSessionId("mapping-session");
        assertThat(service.previewExerciseCompetencyMapping(10L, 42L, List.of(previewMapping(1L, false)), false)).contains("Preview generated successfully");
        var previousPreview = ExerciseMappingToolsService.getExerciseMappingPreview();
        var mappings = List.of(previewMapping(1L, true), previewMapping(2L, true), previewMapping(3L, true), previewMapping(4L, true));

        JsonNode result = objectMapper.readTree(service.previewExerciseCompetencyMapping(10L, 42L, mappings, viewOnly));

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asString()).contains("at most 3", "suggested=false", "retry the preview");
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isSameAs(previousPreview);
        assertThat(previousPreview.competencies()).singleElement().satisfies(option -> {
            assertThat(option.competencyId()).isEqualTo(1L);
            assertThat(option.alreadyMapped()).isTrue();
            assertThat(option.suggested()).isFalse();
        });
        verify(sessionCacheService).cacheExerciseMappingPreview("mapping-session", previousPreview);
        verifyNoMoreInteractions(sessionCacheService);
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
        verify(competencyExerciseLinkRepository, never()).deleteAll(any(Iterable.class));
        assertThat(existingLink.getWeight()).isEqualTo(0.5);
        assertThat(existingLink.getCompetency()).isSameAs(competency1);
    }

    @Test
    void preview_countsDistinctSuggestedCompetenciesWithoutChangingDuplicateHandling() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(1L, 2L, 3L, 1L))).thenReturn(List.of(competency1, competency2));
        var mappings = List.of(previewMapping(1L, true), previewMapping(2L, true), previewMapping(3L, true), previewMapping(1L, true));

        assertThat(service.previewExerciseCompetencyMapping(10L, 42L, mappings, false)).contains("Preview generated successfully");

        var preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.competencies()).hasSize(4).allSatisfy(option -> assertThat(option.suggested()).isTrue())
                .extracting(ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO::competencyId).containsExactly(1L, 2L, 3L, 1L);
    }

    @Test
    void preview_missingMappingsIdentifiesTheRequiredArgumentWithoutCreatingAPreview() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));

        JsonNode result = objectMapper.readTree(service.previewExerciseCompetencyMapping(10L, 42L, null, false));

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asString()).startsWith("mappings is required").contains("retry the preview");
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isNull();
        verifyNoMoreInteractions(sessionCacheService);
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
        verify(competencyExerciseLinkRepository, never()).deleteAll(any(Iterable.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preview_missingMappingsPreservesPreviousPreviewAndCache(boolean viewOnly) {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(1L))).thenReturn(List.of(competency1));
        ExerciseMappingToolsService.setCurrentSessionId("mapping-session");
        service.previewExerciseCompetencyMapping(10L, 42L, List.of(previewMapping(1L, true)), false);
        var previousPreview = ExerciseMappingToolsService.getExerciseMappingPreview();

        JsonNode result = objectMapper.readTree(service.previewExerciseCompetencyMapping(10L, 42L, null, viewOnly));

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asString()).startsWith("mappings is required").contains("retry the preview");
        assertThat(ExerciseMappingToolsService.getExerciseMappingPreview()).isSameAs(previousPreview);
        verify(sessionCacheService).cacheExerciseMappingPreview("mapping-session", previousPreview);
        verifyNoMoreInteractions(sessionCacheService);
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
        verify(competencyExerciseLinkRepository, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void preview_acceptsAnExplicitlyEmptyMappingsList() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of())).thenReturn(List.of());

        assertThat(service.previewExerciseCompetencyMapping(10L, 42L, List.of(), false)).contains("Preview generated successfully");

        var preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.exerciseId()).isEqualTo(42L);
        assertThat(preview.competencies()).isEmpty();
    }

    private static ExerciseMappingToolsService.ExerciseCompetencyMappingOperation previewMapping(long competencyId, Boolean suggested) {
        return new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(competencyId, 0.5, false, suggested);
    }

    @Test
    void preview_viewOnly_doesNotSetPreview_whenFlagTrue() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CompetencyExerciseLink>> linkCaptor = ArgumentCaptor.forClass(List.class);
        verify(competencyExerciseLinkRepository).saveAll(linkCaptor.capture());
        assertThat(linkCaptor.getValue()).singleElement().satisfies(link -> assertThat(link.isGeneratedByAi()).isTrue());

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
    }

    @Test
    void save_updatesWeightOnExistingLink() throws Exception {
        CompetencyExerciseLink existingLink = new CompetencyExerciseLink();
        existingLink.setCompetency(competency1);
        existingLink.setExercise(exercise);
        existingLink.setWeight(0.5);
        existingLink.setGeneratedByAi(true);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existingLink));
        when(courseCompetencyRepository.findAllById(List.of())).thenReturn(List.of());

        var mappings = List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 1.0, true, false));
        service.saveExerciseCompetencyMappings(10L, 42L, mappings);

        assertThat(existingLink.getWeight()).isEqualTo(1.0);
        assertThat(existingLink.isGeneratedByAi()).isTrue();

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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CompetencyExerciseLink>> savedLinks = ArgumentCaptor.forClass(Iterable.class);
        verify(competencyExerciseLinkRepository).saveAll(savedLinks.capture());
        // the user's competency was used, not the LLM's
        assertThat(savedLinks.getValue()).extracting(link -> link.getCompetency().getId()).containsExactly(2L);
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

    @AfterEach
    void clearThreadLocalState() {
        ExerciseMappingToolsService.clearExerciseMappingPreview();
        ExerciseMappingToolsService.clearUserSelectedMappings();
        ExerciseMappingToolsService.clearCurrentSessionId();
    }

    @Test
    void previewCachesUnknownCompetencyAndDefaultsMissingFlags() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of());
        when(courseCompetencyRepository.findAllById(List.of(99L))).thenReturn(List.of());
        ExerciseMappingToolsService.setCurrentSessionId("mapping-session");
        var operation = new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(99L, 0.5, false, null);

        assertThat(service.previewExerciseCompetencyMapping(10L, 42L, List.of(operation), null)).contains("Preview generated successfully");

        var preview = ExerciseMappingToolsService.getExerciseMappingPreview();
        assertThat(preview.competencies().getFirst().competencyTitle()).isEqualTo("Unknown Competency");
        assertThat(preview.competencies().getFirst().suggested()).isFalse();
        assertThat(preview.viewOnly()).isFalse();
        verify(sessionCacheService).cacheExerciseMappingPreview("mapping-session", preview);
    }

    @Test
    void saveSkipsUnknownAndForeignCompetenciesAndLeavesUnchangedLinkUntouched() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(exerciseRepository.findWithCompetenciesById(42L)).thenReturn(Optional.of(exercise));
        var existing = new CompetencyExerciseLink();
        existing.setCompetency(competency1);
        existing.setExercise(exercise);
        existing.setWeight(0.5);
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(42L)).thenReturn(List.of(existing));
        var foreignCourse = new Course();
        foreignCourse.setId(11L);
        competency2.setCourse(foreignCourse);
        when(courseCompetencyRepository.findAllById(List.of(2L, 99L))).thenReturn(List.of(competency2));
        var result = service.saveExerciseCompetencyMappings(10L, 42L,
                List.of(new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(1L, 0.5, true, false),
                        new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(2L, 1.0, false, false),
                        new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(99L, 1.0, false, false)));
        assertThat(result).contains("Created 0, updated 0, deleted 0");
        assertThat(existing.getWeight()).isEqualTo(0.5);
        verify(competencyExerciseLinkRepository, never()).saveAll(any());
        verify(competencyExerciseLinkRepository, never()).deleteAll(any(Iterable.class));

    }

}
