package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Unit tests for {@link OrchestratorReadToolsService}; relocated from the former monolithic OrchestratorToolsServiceTest. */
@ExtendWith(MockitoExtension.class)
class OrchestratorReadToolsServiceTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private ContentExtractionService contentExtractionService;

    private OrchestratorReadToolsService service;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        service = new OrchestratorReadToolsService(new ObjectMapper(), courseCompetencyRepository, exerciseRepository, contentExtractionService);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolContextKeys.COURSE_ID_KEY, COURSE_ID);
        toolContext = new ToolContext(ctx);
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
    void getCompetencyDetails_missingCourseContext_returnsError() {
        String result = service.getCompetencyDetails(5L, new ToolContext(Map.of()));

        assertThat(result).contains("No course context");
    }

    @Test
    void getExerciseContent_examExercise_isRejectedDefenseInDepth() {
        // Defense in depth: even if an exam exercise reaches the tool layer, no read tool may walk
        // the lazy exerciseGroup.exam.course chain to expose course-wide data.
        ProgrammingExercise examExercise = examExercise(20L, "Exam Exercise");
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(examExercise);

        String result = service.getExerciseContent(20L, toolContext);

        assertThat(result).contains("does not belong to the current course");
    }

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
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setExerciseGroup(new ExerciseGroup());
        return exercise;
    }
}
