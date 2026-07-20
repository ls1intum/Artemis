package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.conduction.StudentExamForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.detail.StudentExamForDetailDTO;
import de.tum.cit.aet.artemis.exam.dto.summary.StudentExamForSummaryDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests pinning that the student exam projections drop {@code null} exercises instead of carrying them onto the
 * wire. {@code StudentExam.exercises} is an {@code @OrderColumn} list, so Hibernate materializes a {@code null} for
 * every gap in {@code exercise_order} (the same hazard {@code ExamService} already filters with
 * {@code .filter(Objects::nonNull)}). Unfiltered, such a gap serializes a literal {@code null} into the exercises JSON
 * array, which the client model cannot deserialize.
 */
class StudentExamProjectionNullExerciseTest {

    /**
     * Builds a student exam whose exercise list has a gap, as an {@code @OrderColumn} list with a hole would load.
     * The exam is left null and the test-run flag set so the factories short-circuit their publish check: this test is
     * only about the exercise list.
     */
    private StudentExam studentExamWithNullExerciseGap() {
        TextExercise exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setTitle("Text exercise");
        StudentExam studentExam = new StudentExam();
        studentExam.setId(1L);
        studentExam.setTestRun(true);
        studentExam.setExercises(Arrays.asList(exercise, null));
        return studentExam;
    }

    @Test
    void shouldDropNullExercisesFromConductionProjection() {
        var dto = StudentExamForConductionDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
    }

    @Test
    void shouldDropNullExercisesFromSummaryProjection() {
        var dto = StudentExamForSummaryDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
    }

    @Test
    void shouldDropNullExercisesFromDetailProjection() {
        var dto = StudentExamForDetailDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
    }
}
