package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
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

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@ExtendWith(MockitoExtension.class)
class OrchestratorToolsServiceTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private ContentExtractionService contentExtractionService;

    private OrchestratorToolsService service;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        service = new OrchestratorToolsService(new ObjectMapper(), courseCompetencyRepository, exerciseRepository, contentExtractionService);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolsService.COURSE_ID_KEY, COURSE_ID);
        toolContext = new ToolContext(ctx);
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

    @Test
    void getExerciseContent_examExercise_isRejectedDefenseInDepth() {
        // Defense in depth: even if an exam exercise reaches the tool layer (the orchestrator's
        // run() should reject it earlier), no read tool may walk the lazy
        // exerciseGroup.exam.course chain to expose course-wide data.
        ProgrammingExercise examExercise = examExercise(20L, "Exam Exercise");
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(examExercise);

        String result = service.getExerciseContent(20L, toolContext);

        assertThat(result).contains("does not belong to the current course");
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

    private static ProgrammingExercise examExercise(long id, String title) {
        // Course is intentionally null on the exercise itself; it lives behind exerciseGroup→exam→course.
        // exerciseBelongsToCourse must reject without walking that lazy chain.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setExerciseGroup(new ExerciseGroup());
        return exercise;
    }
}
