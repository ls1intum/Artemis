package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
public class ProgrammingMessagingService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingMessagingService.class);

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final LtiNewResultService ltiNewResultService;

    public ProgrammingMessagingService(GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService,
            LtiNewResultService ltiNewResultService) {
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.ltiNewResultService = ltiNewResultService;
    }

    public void notifyInstructorAboutStartedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.RUNNING);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE);
    }

    public void notifyInstructorAboutCompletedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.COMPLETED);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE);
    }

    /**
     * Notify user on a new programming submission.
     *
     * @param submission ProgrammingSubmission
     */
    public void notifyUserAboutSubmission(ProgrammingSubmission submission) {
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            // TODO LOCALVC_CI: Find a way to set the exercise to null (submission.getParticipation().setExercise(null)) as it is not necessary to send all these details here.
            // Just removing it causes issues with the local CI system that calls this method and in some places expects the exercise to be set on the submission's participation
            // afterwards (call by reference).
            // Removing it and immediately setting it back to the original value after sending the message here, is not working either, because some steps in the local CI system
            // happen in parallel to this and the exercise needs to be set at all times.
            // Creating a deep copy of the submission and setting the exercise to null there is also not working, because 'java.time.ZoneRegion' is not open to external libraries
            // (like Jackson) so it cannot be serialized using 'objectMapper.readValue()'.
            // You could look into some kind of ProgrammingSubmissionDTO here that only gets the values set that the client actually needs.
            studentParticipation.getStudents().forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, submission));
        }

        if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null && !(submission.getParticipation() instanceof StudentParticipation)) {
            var topicDestination = getExerciseTopicForTAAndAbove(submission.getParticipation().getExercise().getId());
            websocketMessagingService.sendMessage(topicDestination, submission);
        }
    }

    public void notifyUserAboutSubmissionError(ProgrammingSubmission submission, BuildTriggerWebsocketError error) {
        notifyUserAboutSubmissionError(submission.getParticipation(), error);
    }

    /**
     * Notifies the user (or all users of the team) about a submission error
     *
     * @param participation the participation for which the submission error should be reported
     * @param error         the submission error wrapped in an object
     */
    public void notifyUserAboutSubmissionError(Participation participation, BuildTriggerWebsocketError error) {
        if (participation instanceof StudentParticipation studentParticipation) {
            studentParticipation.getStudents().forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, error));
        }

        if (participation != null && participation.getExercise() != null) {
            websocketMessagingService.sendMessage(getExerciseTopicForTAAndAbove(participation.getExercise().getId()), error);
        }
    }

    /**
     * Notifies editors and instructors about test case changes for the updated programming exercise
     *
     * @param testCasesChanged           whether tests have been changed or not
     * @param updatedProgrammingExercise the programming exercise for which tests have been changed
     */
    public void notifyUserAboutTestCaseChanged(boolean testCasesChanged, ProgrammingExercise updatedProgrammingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseTestCaseChangedTopic(updatedProgrammingExercise.getId()), testCasesChanged);
        // Send a notification to the client to inform the instructor about the test case update.
        if (testCasesChanged) {
            groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        }
        else {
            groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION);
        }
    }

    /**
     * Notify instructor groups about illegal submissions. In case a student has submitted after the individual end date or exam end date,
     * the submission is not valid and therefore marked as illegal. We notify the instructor about this cheating attempt.
     *
     * @param exercise         that has been affected
     * @param notificationText that should be displayed
     */
    public void notifyInstructorGroupAboutIllegalSubmissionsForExercise(ProgrammingExercise exercise, String notificationText) {
        groupNotificationService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(exercise, notificationText);
    }

    private static String getExerciseTopicForTAAndAbove(long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + PROGRAMMING_SUBMISSION_TOPIC;
    }

    public static String getProgrammingExerciseTestCaseChangedTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/test-cases-changed";
    }

    private static String getProgrammingExerciseAllExerciseBuildsTriggeredTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/all-builds-triggered";
    }

    /**
     * Notify user about new result.
     *
     * @param result        the result created from the result returned from the CI system.
     * @param participation the participation for which the result was created.
     */
    public void notifyUserAboutNewResult(Result result, ProgrammingExerciseParticipation participation) {
        log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
        // notify user via websocket
        websocketMessagingService.broadcastNewResult((Participation) participation, result);

        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            // do not try to report results for template or solution participations
            ltiNewResultService.onNewResult(studentParticipation);
        }
    }
}
