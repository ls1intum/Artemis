package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorToolContextKeys.AppliedActionsBuffer;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Unit tests for {@link AssignerToolsService}; relocated from the former monolithic OrchestratorToolsServiceTest. */
@ExtendWith(MockitoExtension.class)
class AssignerToolsServiceTest {

    private static final long COURSE_ID = 42L;

    private static final long OTHER_COURSE_ID = 99L;

    private static final String JUSTIFICATION = "Evidence test passed: solution demonstrates the competency stand-alone (band 1.0).";

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkRepository;

    @Mock
    private CompetencyProgressApi competencyProgressApi;

    private AssignerToolsService service;

    private ToolContext toolContext;

    private List<AppliedActionDTO> appliedActions;

    private AppliedActionsBuffer appliedActionsBuffer;

    @BeforeEach
    void setUp() {
        service = new AssignerToolsService(new ObjectMapper(), courseCompetencyRepository, exerciseRepository, competencyExerciseLinkRepository,
                Optional.of(competencyProgressApi));
        appliedActions = Collections.synchronizedList(new ArrayList<>());
        appliedActionsBuffer = new AppliedActionsBuffer(appliedActions);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolContextKeys.COURSE_ID_KEY, COURSE_ID);
        ctx.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, appliedActionsBuffer);
        toolContext = new ToolContext(ctx);
    }

    @Test
    void assignExerciseToCompetency_newLink_createsAndTriggersProgressRecalcAndLogsWeight() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Implement Quicksort", course);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(exercise);
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.empty());

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
    void assignExerciseToCompetency_zeroWeight_returnsBandError() {
        String result = service.assignExerciseToCompetency(5L, 20L, 0.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight must be one of 1.0").contains("0.5").contains("0.3");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_weightAboveRange_returnsBandError() {
        String result = service.assignExerciseToCompetency(5L, 20L, 1.5, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight must be one of 1.0").contains("0.5").contains("0.3");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_weightBetweenBands_returnsBandError() {
        // 0.7 is in (0, 1.0] but matches no band — the prompt forbids it and the code enforces it.
        String result = service.assignExerciseToCompetency(5L, 20L, 0.7, JUSTIFICATION, toolContext);

        assertThat(result).contains("weight must be one of 1.0");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_weightAtBandWithFloatTolerance_succeedsWithCanonicalBand() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Comp", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Exercise", course);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(exercise);
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.empty());

        // LLMs sometimes return 0.30000001 due to JSON float formatting; the canonical band is 0.3.
        String result = service.assignExerciseToCompetency(5L, 20L, 0.30000001, JUSTIFICATION, toolContext);

        assertThat(result).contains("\"status\":\"ok\"").contains("\"weight\":0.3");
        assertThat(appliedActions).singleElement().satisfies(a -> assertThat(a.weight()).isEqualTo(0.3));
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
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.of(existing));

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
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.of(existing));

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
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.empty());

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
    void unassignExerciseFromCompetency_exerciseInDifferentCourse_returnsErrorWithoutDelete() {
        // Defense-in-depth: even if a stale cross-course link existed, the tool must refuse to
        // delete it because the linked exercise does not belong to the run's course context.
        Course currentCourse = courseWithId(COURSE_ID);
        Course otherCourse = courseWithId(OTHER_COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, currentCourse);
        ProgrammingExercise exercise = exerciseInCourse(20L, "Foreign Exercise", otherCourse);
        CompetencyExerciseLink staleLink = new CompetencyExerciseLink(competency, exercise, 1.0);
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(20L, 5L)).thenReturn(Optional.of(staleLink));

        String result = service.unassignExerciseFromCompetency(5L, 20L, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(competencyExerciseLinkRepository, never()).delete(any(CompetencyExerciseLink.class));
        verify(competencyProgressApi, never()).updateProgressByLearningObjectAsync(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void assignExerciseToCompetency_examExercise_isRejectedDefenseInDepth() {
        // Defense in depth: even if an exam exercise reaches the tool layer, the assign tool must
        // not silently mutate course-wide competencies via the lazy exerciseGroup.exam.course chain.
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(5L, "Target", "Desc", CompetencyTaxonomy.APPLY, course);
        ProgrammingExercise examExercise = examExercise(20L, "Exam Exercise");
        when(courseCompetencyRepository.findById(5L)).thenReturn(Optional.of(competency));
        when(exerciseRepository.findByIdElseThrow(20L)).thenReturn(examExercise);

        String result = service.assignExerciseToCompetency(5L, 20L, 1.0, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
    }

    @Test
    void writeQuotaReached_furtherWriteToolCallsReturnError() {
        for (int i = 0; i < OrchestratorToolContextKeys.MAX_WRITE_CALLS; i++) {
            assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isTrue();
        }
        assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isFalse();

        String assignResult = service.assignExerciseToCompetency(10L, 20L, 1.0, JUSTIFICATION, toolContext);
        String unassignResult = service.unassignExerciseFromCompetency(10L, 20L, JUSTIFICATION, toolContext);

        assertThat(assignResult).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
        assertThat(unassignResult).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
        verify(competencyExerciseLinkRepository, never()).save(any(CompetencyExerciseLink.class));
        verify(competencyExerciseLinkRepository, never()).delete(any());
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
        // Course is intentionally null on the exercise itself; it lives behind exerciseGroup→exam→course.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setExerciseGroup(new ExerciseGroup());
        return exercise;
    }
}
