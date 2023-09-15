package de.tum.in.www1.artemis.exercise.textexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.service.exam.StudentExamService;

class TextComparisonTest {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Test
    void compareSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>());
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, course);

        var submission1 = TextExerciseFactory.generateTextExerciseSubmission(textExercise, "text submission!");
        var submission2 = TextExerciseFactory.generateTextExerciseSubmission(textExercise, "text submission!");  // same as submission1
        var submission3 = TextExerciseFactory.generateTextExerciseSubmission(textExercise, "different submission");
        var submission4 = TextExerciseFactory.generateTextExerciseSubmission(textExercise, null);
        var submission5 = TextExerciseFactory.generateTextExerciseSubmission(textExercise, null);

        assertThat(StudentExamService.isContentEqualTo(submission1, submission2)).isTrue();  // submission with same text
        assertThat(StudentExamService.isContentEqualTo(submission1, submission3)).isFalse(); // submission with different text
        assertThat(StudentExamService.isContentEqualTo(submission3, submission4)).isFalse();
        assertThat(StudentExamService.isContentEqualTo(submission4, submission5)).isTrue();  // both submission with null text

        assertThat(StudentExamService.isContentEqualTo(submission2, null)).isFalse(); // one submission null
        assertThat(StudentExamService.isContentEqualTo(null, submission4)).isFalse();  // one submission null, other null text
        assertThat(StudentExamService.isContentEqualTo((TextSubmission) null, null)).isTrue(); // both submissions null
    }
}
