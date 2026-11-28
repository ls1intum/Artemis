package de.tum.cit.aet.artemis.assessment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class ResultListenerAthenaTest {

    @Test
    void pendingAndFailedAthenaResultsDoNotScheduleScoreOrCompetencyChanges() {
        var messages = mock(InstanceMessageSendService.class);
        var listener = new ResultListener(messages);
        for (Boolean successful : Arrays.asList(null, false)) {
            Result result = result(AssessmentType.AUTOMATIC_ATHENA, successful);
            listener.createOrUpdateResult(result);
            listener.removeResult(result);
        }
        verifyNoInteractions(messages);
    }

    @Test
    void successfulAthenaStillSchedulesTheGrade() {
        var messages = mock(InstanceMessageSendService.class);
        new ResultListener(messages).createOrUpdateResult(result(AssessmentType.AUTOMATIC_ATHENA, true));
        verify(messages).sendParticipantScoreSchedule(7L, 3L, null);
    }

    @Test
    void aFailedOrdinaryAutomaticResultStillSchedulesItsZeroGrade() {
        var messages = mock(InstanceMessageSendService.class);
        new ResultListener(messages).createOrUpdateResult(result(AssessmentType.AUTOMATIC, false));
        verify(messages).sendParticipantScoreSchedule(7L, 3L, null);
    }

    private Result result(AssessmentType type, Boolean successful) {
        var student = new User();
        student.setId(3L);
        var exercise = new TextExercise();
        exercise.setId(7L);
        var participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student);
        var submission = new TextSubmission();
        submission.setParticipation(participation);
        var result = new Result();
        result.setId(11L);
        result.setSubmission(submission);
        result.setAssessmentType(type);
        result.setSuccessful(successful);
        return result;
    }
}
