package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
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
     * <p>
     * The exam, its course and the user are populated rather than left null. With them null every nested factory
     * ({@code ExamForConductionDTO.of}, {@code ExamForSummaryDTO.of}, {@code UserNameDTO.of},
     * {@code UserForDetailDTO.of}) returns on its own null guard and {@code StudentExam.isEnded} bails at its
     * {@code getExam() == null} check, so the projections would run against a graph far thinner than any real one.
     * The test-run flag stays set: it is what exempts the fixture from the quiz publish check, which needs exam dates
     * this test has no reason to model.
     */
    private StudentExam studentExamWithNullExerciseGap() {
        TextExercise exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setTitle("Text exercise");

        Course course = new Course();
        course.setId(3L);
        course.setInstructorGroupName("instructors");
        course.setAccuracyOfScores(1);

        Exam exam = new Exam();
        exam.setId(2L);
        exam.setTitle("Endterm");
        exam.setCourse(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));

        User user = new User();
        user.setId(4L);
        user.setLogin("student1");

        StudentExam studentExam = new StudentExam();
        studentExam.setId(1L);
        studentExam.setTestRun(true);
        studentExam.setExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime(3600);
        studentExam.setExercises(Arrays.asList(exercise, null));
        return studentExam;
    }

    @Test
    void shouldDropNullExercisesFromConductionProjection() {
        var dto = StudentExamForConductionDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
        assertThat(dto.exam()).as("the exam factory must have run, not returned on its null guard").isNotNull();
        assertThat(dto.exam().course().id()).isEqualTo(3L);
        assertThat(dto.user()).as("the user factory must have run, not returned on its null guard").isNotNull();
        assertThat(dto.user().login()).isEqualTo("student1");
    }

    @Test
    void shouldDropNullExercisesFromSummaryProjection() {
        var dto = StudentExamForSummaryDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
        assertThat(dto.exam()).as("the exam factory must have run, not returned on its null guard").isNotNull();
        assertThat(dto.exam().exam().title()).isEqualTo("Endterm");
    }

    @Test
    void shouldDropNullExercisesFromDetailProjection() {
        var dto = StudentExamForDetailDTO.of(studentExamWithNullExerciseGap());

        assertThat(dto.exercises()).doesNotContainNull().hasSize(1);
        assertThat(dto.exercises().getFirst().base().id()).isEqualTo(42L);
        assertThat(dto.exam()).as("the exam factory must have run, not returned on its null guard").isNotNull();
        assertThat(dto.user()).as("the user factory must have run, not returned on its null guard").isNotNull();
        assertThat(dto.user().login()).isEqualTo("student1");
    }
}
