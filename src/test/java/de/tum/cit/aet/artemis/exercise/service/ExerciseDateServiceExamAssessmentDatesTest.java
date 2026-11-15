package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseDateServiceExamAssessmentDatesTest {

    private static final ZonedDateTime END = ZonedDateTime.parse("2030-01-01T10:00:00Z");

    private static Exam examWithGracePeriod(int seconds) {
        var exam = new Exam();
        exam.setGracePeriod(seconds);
        return exam;
    }

    @Test
    void computeExamAssessmentDates_nullEndDate_returnsNull() {
        assertThat(ExerciseDateService.computeExamAssessmentDates(new TextExercise(), new Exam(), null)).isNull();
    }

    @Test
    void computeExamAssessmentDates_addsTheGracePeriod() {
        var dates = ExerciseDateService.computeExamAssessmentDates(new TextExercise(), examWithGracePeriod(60), END);

        assertThat(dates.latestExamEndDate()).isEqualTo(END.plusSeconds(60));
        assertThat(dates.assessmentPossibleFrom()).isEqualTo(END.plusSeconds(60));
    }

    @Test
    void computeExamAssessmentDates_programmingBuildAfterExamEnd_usesBuildDate() {
        var exercise = new ProgrammingExercise();
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(END.plusHours(1));

        var dates = ExerciseDateService.computeExamAssessmentDates(exercise, examWithGracePeriod(0), END);

        assertThat(dates.latestExamEndDate()).isEqualTo(END);
        assertThat(dates.assessmentPossibleFrom()).isEqualTo(END.plusHours(1));
    }

    @Test
    void computeExamAssessmentDates_programmingBuildBeforeExamEnd_keepsExamEnd() {
        var exercise = new ProgrammingExercise();
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(END.minusHours(1));

        var dates = ExerciseDateService.computeExamAssessmentDates(exercise, examWithGracePeriod(0), END);

        assertThat(dates.assessmentPossibleFrom()).isEqualTo(END);
    }
}
