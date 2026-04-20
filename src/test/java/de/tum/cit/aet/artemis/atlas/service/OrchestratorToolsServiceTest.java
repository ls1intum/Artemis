package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@ExtendWith(MockitoExtension.class)
class OrchestratorToolsServiceTest {

    private static final long COURSE_ID = 42L;

    private static final long OTHER_COURSE_ID = 99L;

    private static final String JUSTIFICATION = "Evidence test passed: solution demonstrates the competency stand-alone (band 1.0).";

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    @Mock
    private ContentExtractionService contentExtractionService;

    @Mock
    private CompetencyService competencyService;

    @Mock
    private CourseCompetencyService courseCompetencyService;

    @Mock
    private CompetencyProgressApi competencyProgressApi;

    private OrchestratorToolsService service;

    private ToolContext toolContext;

    private List<AppliedActionDTO> appliedActions;

    @BeforeEach
    void setUp() {
        service = new OrchestratorToolsService(new ObjectMapper(), courseRepository, courseCompetencyRepository, exerciseRepository, competencyExerciseLinkRepository,
                contentExtractionService, competencyService, courseCompetencyService, Optional.of(competencyProgressApi));
        appliedActions = new ArrayList<>();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolsService.COURSE_ID_KEY, COURSE_ID);
        ctx.put(OrchestratorToolsService.APPLIED_ACTIONS_KEY, appliedActions);
        toolContext = new ToolContext(ctx);
    }

    @Test
    void createCompetency_validInput_persistsAndLogsActionWithJustification() {
        Course course = new Course();
        course.setId(COURSE_ID);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        Competency persisted = new Competency("Sorting Algorithms", "Understand sorting basics.", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND,
                false);
        persisted.setId(101L);
        when(competencyService.createCompetencies(any(), eq(course))).thenReturn(List.of(persisted));

        String result = service.createCompetency("Sorting Algorithms", "Understand sorting basics.", "UNDERSTAND", JUSTIFICATION, toolContext);

        assertThat(result).contains("\"id\":101").contains("Sorting Algorithms").contains("UNDERSTAND");
        assertThat(appliedActions).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AppliedActionDTO.ActionType.CREATE);
            assertThat(action.competencyId()).isEqualTo(101L);
            assertThat(action.competencyTitle()).isEqualTo("Sorting Algorithms");
            assertThat(action.justification()).isEqualTo(JUSTIFICATION);
        });
    }

    @Test
    void createCompetency_missingJustification_returnsError() {
        String result = service.createCompetency("Title", "Desc", "APPLY", "   ", toolContext);

        assertThat(result).contains("justification is required");
        verify(competencyService, never()).createCompetencies(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void createCompetency_invalidTaxonomy_returnsError() {
        String result = service.createCompetency("Title", "Description", "WIZARD", JUSTIFICATION, toolContext);

        assertThat(result).contains("error").contains("taxonomy");
        assertThat(appliedActions).isEmpty();
        verify(competencyService, never()).createCompetencies(any(), any());
    }

    @Test
    void createCompetency_missingCourseContext_returnsError() {
        String result = service.createCompetency("Title", "Desc", "APPLY", JUSTIFICATION, new ToolContext(Map.of()));

        assertThat(result).contains("No course context");
        verify(competencyService, never()).createCompetencies(any(), any());
    }

    @Test
    void editCompetency_titleChange_updatesAndLogsActionWithJustification() {
        CourseCompetency existing = newCompetency(10L, "Old Title", "Desc", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(courseCompetencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.editCompetency(10L, "New Title", null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("New Title").contains("\"changed\"");
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.EDIT);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
    }

    @Test
    void editCompetency_missingJustification_returnsError() {
        String result = service.editCompetency(10L, "New Title", null, null, null, toolContext);

        assertThat(result).contains("justification is required");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_wrongCourse_returnsErrorAndDoesNotSave() {
        CourseCompetency existing = newCompetency(10L, "Old Title", "Desc", CompetencyTaxonomy.APPLY, courseWithId(OTHER_COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));

        String result = service.editCompetency(10L, "New", null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_noFieldsChanged_returnsNoopWithoutSaving() {
        CourseCompetency existing = newCompetency(10L, "Keep", "Desc", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));

        String result = service.editCompetency(10L, null, null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("noop");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_newLink_createsAndTriggersProgressRecalcAndLogsWeight() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Implement Quicksort", course);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(exercise);
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(20L)).thenReturn(List.of());

        String result = service.assignExerciseToCompetency(5L, 20L, 1.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("\"status\":\"ok\"").contains("\"weight\":1.0");
        verify(competencyExerciseLinkRepository).save(any(CompetencyExerciseLink.class));
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(exercise);
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.ASSIGN);
            assertThat(a.exerciseId()).isEqualTo(20L);
            assertThat(a.weight()).isEqualTo(1.0);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
    }

    @Test
    void assignExerciseToCompetency_missingWeight_returnsError() {
        String result = service.assignExerciseToCompetency(5L, 20L, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight is required");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_zeroWeight_returnsError() {
        String result = service.assignExerciseToCompetency(5L, 20L, 0.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight must be greater than 0.0");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_weightAboveRange_returnsError() {
        String result = service.assignExerciseToCompetency(5L, 20L, 1.5, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight must be greater than 0.0");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_missingJustification_returnsError() {
        String result = service.assignExerciseToCompetency(5L, 20L, 0.5, " ", toolContext);

        assertThat(result).contains("justification is required");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_sameWeightAsExisting_isNoop() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Implement Quicksort", course);
        CompetencyExerciseLink existing = new CompetencyExerciseLink(competency, exercise, 1.0);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(exercise);
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(20L)).thenReturn(List.of(existing));

        String result = service.assignExerciseToCompetency(5L, 20L, 1.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("noop");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        verify(competencyProgressApi, never()).updateProgressByLearningObjectAsync(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_differentCourse_returnsError() {
        Course course = courseWithId(OTHER_COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));

        String result = service.assignExerciseToCompetency(5L, 20L, 1.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
    }

    @Test
    void unassignExerciseFromCompetency_existingLink_deletesAndLogs() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Implement Quicksort", course);
        CompetencyExerciseLink existing = new CompetencyExerciseLink(competency, exercise, 1.0);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(20L)).thenReturn(List.of(existing));

        String result = service.unassignExerciseFromCompetency(5L, 20L, JUSTIFICATION, toolContext);

        assertThat(result).contains("\"status\":\"ok\"");
        verify(competencyExerciseLinkRepository).delete(existing);
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(exercise);
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.UNASSIGN);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
    }

    @Test
    void unassignExerciseFromCompetency_missingLink_returnsNoop() {
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(20L)).thenReturn(List.of());

        String result = service.unassignExerciseFromCompetency(5L, 20L, JUSTIFICATION, toolContext);

        assertThat(result).contains("noop");
        verify(competencyExerciseLinkRepository, never()).delete(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void unassignExerciseFromCompetency_missingJustification_returnsError() {
        String result = service.unassignExerciseFromCompetency(5L, 20L, "", toolContext);

        assertThat(result).contains("justification is required");
        verify(competencyExerciseLinkRepository, never()).delete(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void deleteCompetency_validCompetency_delegatesToServiceWithJustification() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(7L, "Remove Me", "Desc", CompetencyTaxonomy.UNDERSTAND, course);
        when(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(7L)).thenReturn(Optional.of(competency));

        String result = service.deleteCompetency(7L, JUSTIFICATION, toolContext);

        assertThat(result).contains("\"deletedId\":7");
        verify(courseCompetencyService).deleteCourseCompetency(competency, course);
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.DELETE);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
    }

    @Test
    void deleteCompetency_wrongCourse_returnsErrorWithoutDelete() {
        Course course = courseWithId(OTHER_COURSE_ID);
        CourseCompetency competency = newCompetency(7L, "Remove Me", "Desc", CompetencyTaxonomy.UNDERSTAND, course);
        when(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(7L)).thenReturn(Optional.of(competency));

        String result = service.deleteCompetency(7L, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(courseCompetencyService, never()).deleteCourseCompetency(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void deleteCompetency_missingJustification_returnsError() {
        String result = service.deleteCompetency(7L, null, toolContext);

        assertThat(result).contains("justification is required");
        verify(courseCompetencyService, never()).deleteCourseCompetency(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void listCompetencyIndex_includesCurrentLinkWeights() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Algorithms and Complexity", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise partial = exerciseInCourse(20L, "Hash Maps in Practice", course);
        ProgrammingExercise standalone = exerciseInCourse(21L, "Sorting Fundamentals", course);
        Set<CompetencyExerciseLink> links = new LinkedHashSet<>();
        links.add(new CompetencyExerciseLink(competency, partial, 0.5));
        links.add(new CompetencyExerciseLink(competency, standalone, 1.0));
        competency.setExerciseLinks(links);
        when(courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(COURSE_ID)).thenReturn(Set.of(competency));
        when(exerciseRepository.findAllExercisesByCourseId(COURSE_ID)).thenReturn(Set.of(partial, standalone));

        CompetencyIndexResponseDTO index = service.listCompetencyIndex(COURSE_ID);

        assertThat(index.competencies()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo(5L);
            assertThat(entry.exercises()).extracting(CompetencyIndexDTO.ExerciseLinkRef::title, CompetencyIndexDTO.ExerciseLinkRef::weight)
                    .containsExactlyInAnyOrder(tuple("Hash Maps in Practice", 0.5), tuple("Sorting Fundamentals", 1.0));
        });
        assertThat(index.unassignedExercises()).isEmpty();
    }

    @Test
    void listCompetencyIndex_includesUnassignedExercises() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Algorithms and Complexity", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise linked = exerciseInCourse(20L, "Hash Maps in Practice", course);
        ProgrammingExercise unlinked = exerciseInCourse(21L, "Dynamic Programming", course);
        Set<CompetencyExerciseLink> links = new LinkedHashSet<>();
        links.add(new CompetencyExerciseLink(competency, linked, 0.5));
        competency.setExerciseLinks(links);
        when(courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(COURSE_ID)).thenReturn(Set.of(competency));
        when(exerciseRepository.findAllExercisesByCourseId(COURSE_ID)).thenReturn(Set.of(linked, unlinked));

        CompetencyIndexResponseDTO index = service.listCompetencyIndex(COURSE_ID);

        assertThat(index.unassignedExercises()).singleElement().satisfies(ref -> {
            assertThat(ref.id()).isEqualTo(21L);
            assertThat(ref.title()).isEqualTo("Dynamic Programming");
            assertThat(ref.type()).isEqualTo(unlinked.getType());
        });
    }

    @Test
    void listCompetencyIndex_noCompetencies_listsAllExercisesAsUnassigned() {
        Course course = courseWithId(COURSE_ID);
        ProgrammingExercise first = exerciseInCourse(30L, "Exercise A", course);
        ProgrammingExercise second = exerciseInCourse(31L, "Exercise B", course);
        when(courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(COURSE_ID)).thenReturn(Set.of());
        when(exerciseRepository.findAllExercisesByCourseId(COURSE_ID)).thenReturn(Set.of(first, second));

        CompetencyIndexResponseDTO index = service.listCompetencyIndex(COURSE_ID);

        assertThat(index.competencies()).isEmpty();
        assertThat(index.unassignedExercises()).extracting(CompetencyIndexResponseDTO.UnassignedExerciseRef::id).containsExactly(30L, 31L);
    }

    @Test
    void getCompetencyDetails_includesCurrentLinkWeights() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Algorithms and Complexity", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Hash Maps in Practice", course);
        Set<CompetencyExerciseLink> links = new LinkedHashSet<>();
        links.add(new CompetencyExerciseLink(competency, exercise, 0.5));
        competency.setExerciseLinks(links);
        when(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(5L)).thenReturn(Optional.of(competency));

        String result = service.getCompetencyDetails(5L, toolContext);

        assertThat(result).contains("\"title\":\"Hash Maps in Practice\"").contains("\"weight\":0.5");
    }

    // Helpers --------------------------------------------------------------------------------------

    private static Course courseWithId(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private static CourseCompetency newCompetency(long id, String title, String description, CompetencyTaxonomy taxonomy, Course course) {
        Competency competency = new Competency(title, description, null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, taxonomy, false);
        competency.setId(id);
        competency.setCourse(course);
        return competency;
    }

    private static ProgrammingExercise exerciseInCourse(long id, String title, Course course) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setCourse(course);
        return exercise;
    }
}
