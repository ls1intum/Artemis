package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Focused unit test for the backfill contract of {@link ExerciseImportService#copyExerciseBasis}. This is the exact
 * logic that silently dropped content during bulk import (exam / course-material) before it was fixed: the merge rule
 * must keep the caller's value on {@code newExercise} where present and otherwise take it from {@code sourceExercise}.
 */
class ExerciseImportServiceTest {

    /**
     * Minimal concrete subclass exposing the protected copy helpers. {@code copyExerciseBasis} and
     * {@code prepareNewExerciseForImport} do not use any injected dependency, so passing {@code null} is safe here.
     */
    private static final class TestableExerciseImport extends ExerciseImportService {

        private TestableExerciseImport() {
            super(null, null, null, null);
        }

        private void copyBasis(Exercise newExercise, Exercise sourceExercise) {
            copyExerciseBasis(newExercise, sourceExercise, new HashMap<>());
        }
    }

    private final TestableExerciseImport service = new TestableExerciseImport();

    private TextExercise sourceWithContent() {
        Course course = new Course();
        TextExercise source = new TextExercise();
        source.setCourse(course);
        source.setTitle("Source title");
        source.setProblemStatement("Source problem statement");
        source.setDifficulty(DifficultyLevel.HARD);
        source.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        source.setGradingInstructions("Source grading instructions");
        source.setMaxPoints(7.0);
        source.setBonusPoints(1.0);
        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Source criterion");
        source.setGradingCriteria(new HashSet<>(Set.of(criterion)));
        return source;
    }

    @Test
    void backfillsEveryContentFieldFromSourceWhenNewExerciseIsAnEmptySkeleton() {
        TextExercise source = sourceWithContent();
        // A bulk-import skeleton: only the destination is set, everything else is a fresh default.
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getProblemStatement()).isEqualTo("Source problem statement");
        assertThat(newExercise.getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        assertThat(newExercise.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(newExercise.getGradingInstructions()).isEqualTo("Source grading instructions");
        // Grading criteria must be deep-copied from the source, not left as the skeleton's empty set (regression guard).
        assertThat(newExercise.getGradingCriteria()).hasSize(1);
        assertThat(newExercise.getGradingCriteria().iterator().next().getTitle()).isEqualTo("Source criterion");
        assertThat(newExercise.getGradingCriteria().iterator().next()).isNotSameAs(source.getGradingCriteria().iterator().next());
    }

    @Test
    void keepsTheCallersOwnValuesAndBackfillsOnlyTheGaps() {
        TextExercise source = sourceWithContent();
        // A standalone import: the caller (edited body) already carries its own values, which must win over the source.
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setProblemStatement("Edited problem statement");
        newExercise.setDifficulty(DifficultyLevel.EASY);
        // assessmentType and gradingInstructions are left unset, so they must be backfilled from the source.

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getProblemStatement()).isEqualTo("Edited problem statement");
        assertThat(newExercise.getDifficulty()).isEqualTo(DifficultyLevel.EASY);
        assertThat(newExercise.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(newExercise.getGradingInstructions()).isEqualTo("Source grading instructions");
    }

    @Test
    void prepareNewExerciseForImportClearsIdAndParticipationState() {
        TextExercise newExercise = new TextExercise();
        newExercise.setId(42L);

        ExerciseImportService.prepareNewExerciseForImport(newExercise);

        assertThat(newExercise.getId()).isNull();
        assertThat(newExercise.getStudentParticipations()).isEmpty();
        assertThat(newExercise.getExampleSubmissions()).isEmpty();
    }
}
