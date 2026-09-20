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

    @ParameterizedTest
    @EnumSource(AssessmentType.class)
    void filterResultsBeforeAssessmentDueDate(AssessmentType assessmentType) {
        var exercise = new ProgrammingExercise();
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(1));
        var participation = new ProgrammingExerciseStudentParticipation();
        var submission = new ProgrammingSubmission();
        participation.setSubmissions(Set.of(submission));
        var result = new Result().assessmentType(assessmentType).completionDate(ZonedDateTime.now().minusMinutes(1));
        submission.setResults(new HashSet<>(Set.of(result)));

        exercise.filterResultsForStudents(participation);

        if (assessmentType == AssessmentType.AUTOMATIC || assessmentType == AssessmentType.AUTOMATIC_ATHENA) {
            assertThat(submission.getResults()).containsExactly(result);
        }
        else {
            assertThat(submission.getResults()).isEmpty();
        }

        exercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(1));
        submission.setResults(new HashSet<>(Set.of(result)));
        exercise.filterResultsForStudents(participation);
        assertThat(submission.getResults()).containsExactly(result);

        result.setCompletionDate(null);
        exercise.filterResultsForStudents(participation);
        assertThat(submission.getResults()).isEmpty();
    }
}
