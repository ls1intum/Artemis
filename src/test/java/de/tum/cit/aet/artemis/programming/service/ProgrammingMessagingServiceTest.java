package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.iris.api.PyrisEventApi;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class ProgrammingMessagingServiceTest {

    private final LtiApi ltiApi = mock(LtiApi.class);

    private final PyrisEventApi pyrisEventApi = mock(PyrisEventApi.class);

    private final ResultWebsocketService resultWebsocketService = mock(ResultWebsocketService.class);

    private final ProgrammingMessagingService service = new ProgrammingMessagingService(mock(GroupNotificationService.class), mock(WebsocketMessagingService.class),
            resultWebsocketService, Optional.of(ltiApi), mock(TeamRepository.class), Optional.of(pyrisEventApi), mock(ParticipationRepository.class));

    private Result result(AssessmentType type) {
        var participation = new ProgrammingExerciseStudentParticipation();
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        var result = new Result();
        result.setAssessmentType(type);
        result.setSubmission(submission);
        return result;
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { true, false })
    void athenaResultsOnlyNotifyTheClient(Boolean successful) {
        var result = result(AssessmentType.AUTOMATIC_ATHENA);
        result.setScore(Boolean.TRUE.equals(successful) ? 60.0 : null);
        result.setSuccessful(successful);
        var participation = (ProgrammingExerciseStudentParticipation) result.getSubmission().getParticipation();

        service.notifyUserAboutNewResult(result, participation);

        verify(resultWebsocketService).broadcastNewResult(participation, result);
        verify(ltiApi, never()).onNewResult(participation);
        verifyNoInteractions(pyrisEventApi);
    }

    @ParameterizedTest
    @EnumSource(value = AssessmentType.class, names = { "AUTOMATIC", "SEMI_AUTOMATIC", "MANUAL" })
    void officialResultsStillNotifyLtiAndIris(AssessmentType type) {
        var result = result(type);
        var participation = (ProgrammingExerciseStudentParticipation) result.getSubmission().getParticipation();

        service.notifyUserAboutNewResult(result, participation);

        verify(resultWebsocketService).broadcastNewResult(participation, result);
        verify(ltiApi).onNewResult(participation);
        verify(pyrisEventApi).trigger(org.mockito.ArgumentMatchers.any());
    }
}
