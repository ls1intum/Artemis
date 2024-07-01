package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE;
import static de.tum.in.www1.artemis.config.Constants.BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE;
import static de.tum.in.www1.artemis.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.in.www1.artemis.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_TOPIC;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildRunState;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.connectors.pyris.event.PyrisEventService;
import de.tum.in.www1.artemis.service.connectors.pyris.event.SubmissionFailedEvent;
import de.tum.in.www1.artemis.service.connectors.pyris.event.SubmissionSuccessfulEvent;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionDTO;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingMessagingService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingMessagingService.class);

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final ResultWebsocketService resultWebsocketService;

    private final Optional<LtiNewResultService> ltiNewResultService;

    private final TeamRepository teamRepository;

    private final SubmissionRepository submissionRepository;

    private final Optional<PyrisEventService> pyrisEventService;

    public ProgrammingMessagingService(GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService,
            ResultWebsocketService resultWebsocketService, Optional<LtiNewResultService> ltiNewResultService, TeamRepository teamRepository,
            SubmissionRepository submissionRepository, Optional<PyrisEventService> pyrisEventService) {
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.resultWebsocketService = resultWebsocketService;
        this.ltiNewResultService = ltiNewResultService;
        this.teamRepository = teamRepository;
        this.submissionRepository = submissionRepository;
        this.pyrisEventService = pyrisEventService;
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
        var submissionDTO = SubmissionDTO.of(submission);
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
        resultWebsocketService.broadcastNewResult((Participation) participation, result);

        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            // do not try to report results for template or solution participations
            if (ltiNewResultService.isPresent()) {
                ltiNewResultService.get().onNewResult(studentParticipation);
            }
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
     * @param result
     * @param studentParticipation
     */
    private void notifyIrisAboutSubmissionStatus(Result result, ProgrammingExerciseStudentParticipation studentParticipation) {
        log.debug("Checking if Iris should be informed about the submission status for user " + studentParticipation.getParticipant().getName());
        if (studentParticipation.getParticipant() instanceof User) {
            pyrisEventService.ifPresent(eventService -> {
                // Inform Iris so it can send a message to the user
                try {
                    if (result.getScore() < 80.0) {
                        // Check the recent submission and only answer when the last 3 subsequent submissions failed. Failure criteria: Score < 80.0
                        log.info("Checking if the last 3 submissions failed for user " + studentParticipation.getParticipant().getName());
                        var recentSubmissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(studentParticipation.getId());
                        if (recentSubmissions.size() >= 3) {
                            var lastThreeSubmissions = recentSubmissions.subList(recentSubmissions.size() - 3, recentSubmissions.size());
                            var allFailed = lastThreeSubmissions.stream()
                                    .allMatch(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() < 80.0);
                            if (allFailed) {
                                log.info("All last 3 submissions failed for user " + studentParticipation.getParticipant().getName());
                                eventService.trigger(new SubmissionFailedEvent(result));
                            }
                        }
                    }
                    else {
                        log.info("Submission was successful for user " + studentParticipation.getParticipant().getName());
                        // The submission was successful, so we inform Iris about the successful submission,
                        // but before we do that, we check if this is the first successful time out of all submissions out of all submissions for this exercise
                        eventService.trigger(new SubmissionSuccessfulEvent(result));
                        var allSubmissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(studentParticipation.getId());
                        var latestSubmission = allSubmissions.getLast();
                        var allSuccessful = allSubmissions.stream().filter(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() >= 80.0)
                                .count();
                        if (allSuccessful == 1 && latestSubmission.getLatestResult().getScore() >= 80.0) {
                            log.info("First successful submission for user " + studentParticipation.getParticipant().getName());
                        }
                        // else: do nothing, as this is not the first successful submission
                    }

                }
                catch (Exception e) {
                    log.error("Could not trigger submission failed event for result " + result.getId(), e);
                }
            });
        }
    }
}
