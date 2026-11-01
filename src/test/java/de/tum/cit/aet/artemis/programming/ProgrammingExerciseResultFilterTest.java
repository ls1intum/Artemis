package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class ProgrammingExerciseResultFilterTest {

    private static final ZonedDateTime COMPLETED_RESULT_DATE = ZonedDateTime.parse("2020-01-01T12:00:00Z");

    private static final ZonedDateTime PAST_ASSESSMENT_DUE_DATE = ZonedDateTime.parse("2019-12-31T12:00:00Z");

    private static final ZonedDateTime FUTURE_ASSESSMENT_DUE_DATE = ZonedDateTime.parse("2200-01-01T12:00:00Z");

    @ParameterizedTest
    @EnumSource(AssessmentType.class)
    void filterResultsBeforeAssessmentDueDate(AssessmentType assessmentType) {
        var exercise = new ProgrammingExercise();
        exercise.setAssessmentDueDate(FUTURE_ASSESSMENT_DUE_DATE);
        var participation = new ProgrammingExerciseStudentParticipation();
        var submission = new ProgrammingSubmission();
        participation.setSubmissions(Set.of(submission));
        var result = new Result().assessmentType(assessmentType).completionDate(COMPLETED_RESULT_DATE);
        submission.setResults(new HashSet<>(Set.of(result)));

        exercise.filterResultsForStudents(participation);

        if (assessmentType == AssessmentType.AUTOMATIC || assessmentType == AssessmentType.AUTOMATIC_ATHENA) {
            assertThat(submission.getResults()).containsExactly(result);
        }
        else {
            assertThat(submission.getResults()).isEmpty();
        }

        exercise.setAssessmentDueDate(PAST_ASSESSMENT_DUE_DATE);
        submission.setResults(new HashSet<>(Set.of(result)));
        exercise.filterResultsForStudents(participation);
        assertThat(submission.getResults()).containsExactly(result);

        result.setCompletionDate(null);
        exercise.filterResultsForStudents(participation);
        assertThat(submission.getResults()).isEmpty();
    }
}
