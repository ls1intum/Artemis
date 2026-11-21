package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisExerciseWithStudentSubmissionsDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class PyrisExerciseWithStudentSubmissionsDTOTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { false, true })
    void programmingAiFeedbackDoesNotReplaceOfficialScore(Boolean successful) {
        var submission = new ProgrammingSubmission();
        submission.addResult(result(1L, AssessmentType.AUTOMATIC, 80D, true));
        submission.addResult(result(2L, AssessmentType.AUTOMATIC_ATHENA, successful == null ? null : 20D, successful));
        var exercise = exerciseWithSubmission(new ProgrammingExercise(), submission);

        var dto = PyrisExerciseWithStudentSubmissionsDTO.of(exercise);

        assertThat(dto.submissions()).singleElement().satisfies(mapped -> {
            assertThat(mapped.score()).isEqualTo(80D);
            assertThat(mapped.timestamp()).isEqualTo(submission.getSubmissionDate().toInstant());
        });
    }

    @Test
    void programmingAiFeedbackWithoutOfficialResultDoesNotInventProgress() {
        var submission = new ProgrammingSubmission();
        submission.addResult(result(1L, AssessmentType.AUTOMATIC_ATHENA, 20D, true));
        var exercise = exerciseWithSubmission(new ProgrammingExercise(), submission);

        assertThat(PyrisExerciseWithStudentSubmissionsDTO.of(exercise).submissions()).singleElement().satisfies(mapped -> assertThat(mapped.score()).isZero());
    }

    @Test
    void successfulTextAiAssessmentRemainsGradeRelevant() {
        var submission = new TextSubmission();
        submission.addResult(result(1L, AssessmentType.MANUAL, 80D, true));
        submission.addResult(result(2L, AssessmentType.AUTOMATIC_ATHENA, 20D, true));
        var exercise = exerciseWithSubmission(new TextExercise(), submission);

        assertThat(PyrisExerciseWithStudentSubmissionsDTO.of(exercise).submissions()).singleElement().satisfies(mapped -> assertThat(mapped.score()).isEqualTo(20D));
    }

    private static Exercise exerciseWithSubmission(Exercise exercise, Submission submission) {
        exercise.setId(1L);
        exercise.setMaxPoints(10D);
        exercise.setBonusPoints(0D);
        var participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.addSubmission(submission);
        exercise.setStudentParticipations(Set.of(participation));
        submission.setSubmissionDate(ZonedDateTime.parse("2026-11-19T10:00:00Z"));
        return exercise;
    }

    private static Result result(long id, AssessmentType type, Double score, Boolean successful) {
        var result = new Result();
        result.setId(id);
        result.setAssessmentType(type);
        result.setScore(score);
        result.setSuccessful(successful);
        return result;
    }
}
