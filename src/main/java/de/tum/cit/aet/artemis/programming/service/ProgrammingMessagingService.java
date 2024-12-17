package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE;
import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE;
import static de.tum.cit.aet.artemis.core.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.cit.aet.artemis.core.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_SUBMISSION_TOPIC;
import static de.tum.cit.aet.artemis.core.config.Constants.SUBMISSION_PROCESSING;
import static de.tum.cit.aet.artemis.core.config.Constants.SUBMISSION_PROCESSING_TOPIC;
import static de.tum.cit.aet.artemis.core.config.Constants.TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventPublisherService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.lti.service.LtiNewResultService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildRunState;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingMessagingService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingMessagingService.class);

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final ResultWebsocketService resultWebsocketService;

    private final Optional<LtiNewResultService> ltiNewResultService;

    private final TeamRepository teamRepository;

    private final Optional<PyrisEventPublisherService> pyrisEventPublisher;

    private final ParticipationRepository participationRepository;

    public ProgrammingMessagingService(GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService,
            ResultWebsocketService resultWebsocketService, Optional<LtiNewResultService> ltiNewResultService, TeamRepository teamRepository,
            Optional<PyrisEventPublisherService> pyrisEventPublisher, ParticipationRepository participationRepository) {
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.resultWebsocketService = resultWebsocketService;
        this.ltiNewResultService = ltiNewResultService;
        this.teamRepository = teamRepository;
        this.pyrisEventPublisher = pyrisEventPublisher;
        this.participationRepository = participationRepository;
    }

    private static String getExerciseTopicForTAAndAbove(long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + PROGRAMMING_SUBMISSION_TOPIC;
    }

    private static String getSubmissionProcessingTopicForTAAndAbove(Long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + SUBMISSION_PROCESSING;
    }

    public static String getProgrammingExerciseTestCaseChangedTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/test-cases-changed";
    }

    private static String getProgrammingExerciseAllExerciseBuildsTriggeredTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/all-builds-triggered";
    }

    public void notifyInstructorAboutStartedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.RUNNING);
        // Send a notification to the client to inform the instructor about started builds.
        groupNotificationService.notifyEditorAndInstructorGroupsAboutBuildRunUpdate(programmingExercise, BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE);
    }

    public void notifyInstructorAboutCompletedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.COMPLETED);
        // Send a notification to the client to inform the instructor about the completed builds.
        groupNotificationService.notifyEditorAndInstructorGroupsAboutBuildRunUpdate(programmingExercise, BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE);
    }

    /**
     * Notify user on a new programming submission.
     *
     * @param submission ProgrammingSubmission
     * @param exerciseId used to build the correct topic
     */
    public void notifyUserAboutSubmission(ProgrammingSubmission submission, Long exerciseId) {
        var submissionDTO = SubmissionDTO.of(submission, false, null, null);
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            if (studentParticipation.getParticipant() instanceof Team team) {
                // eager load the team with students so their information can be used for the messages below
                studentParticipation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
            }
            studentParticipation.getStudents().forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, submissionDTO));
        }

        // send an update to tutors, editors and instructors about submissions for template and solution participations
        if (!(submission.getParticipation() instanceof StudentParticipation)) {
            var topicDestination = getExerciseTopicForTAAndAbove(exerciseId);
            websocketMessagingService.sendMessage(topicDestination, submissionDTO);
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
            if (studentParticipation.getParticipant() instanceof Team team) {
                // eager load the team with students so their information can be used for the messages below
                studentParticipation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
            }
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

    /**
     * Notify user about new result.
     *
     * @param result        the result created from the result returned from the CI system.
     * @param participation the participation for which the result was created.
     */
    public void notifyUserAboutNewResult(Result result, ProgrammingExerciseParticipation participation) {
        var submission = result.getSubmission();
        log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, submission, submission.getParticipation());
        // notify user via websocket
        resultWebsocketService.broadcastNewResult((Participation) participation, result);

        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            // do not try to report results for template or solution participations
            ltiNewResultService.ifPresent(newResultService -> newResultService.onNewResult(studentParticipation));
            // Inform Iris about the submission status
            notifyIrisAboutSubmissionStatus(result, studentParticipation);
        }
    }

    /**
     * Notify Iris about the submission status for the given result and student participation.
     * If the submission was successful, Iris will be informed about the successful submission.
     * If the submission failed, Iris will be informed about the submission failure.
     * Iris will only be informed about the submission status if the participant is a user.
     *
     * @param result               the result for which Iris should be informed about the submission status
     * @param studentParticipation the student participation for which Iris should be informed about the submission status
     */
    private void notifyIrisAboutSubmissionStatus(Result result, ProgrammingExerciseStudentParticipation studentParticipation) {
        if (studentParticipation.getParticipant() instanceof User) {
            pyrisEventPublisher.ifPresent(service -> {
                try {
                    service.publishEvent(new NewResultEvent(this, result));
                }
                catch (Exception e) {
                    log.error("Could not publish event for result {}", result.getId(), e);
                }
            });
        }
    }

    /**
     * Notifies the user about the processing of a submission.
     * This method sends a notification to the user that their submission is processing
     * It handles both student participations and template/solution participations.
     *
     * @param submission      the submission processing data transfer object containing the submission details
     * @param exerciseId      the ID of the exercise associated with the submission
     * @param participationId the ID of the participation associated with the submission
     */
    public void notifyUserAboutSubmissionProcessing(SubmissionProcessingDTO submission, long exerciseId, long participationId) {
        Participation participation = participationRepository.findWithProgrammingExerciseWithBuildConfigById(participationId).orElseThrow();
        if (participation instanceof StudentParticipation studentParticipation) {
            if (studentParticipation.getParticipant() instanceof Team team) {
                // Eagerly load the team with students so their information can be used for the messages below
                studentParticipation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
            }
            studentParticipation.getStudents().forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), SUBMISSION_PROCESSING_TOPIC, submission));
        }

        // send an update to tutors, editors and instructors about submissions for template and solution participations
        if (!(participation instanceof StudentParticipation)) {
            String topicDestination = getSubmissionProcessingTopicForTAAndAbove(exerciseId);
            websocketMessagingService.sendMessage(topicDestination, submission);
        }
    }
}
