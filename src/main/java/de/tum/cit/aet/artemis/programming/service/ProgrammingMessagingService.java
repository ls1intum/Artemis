package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.SUBMISSION_PROCESSING;
import static de.tum.cit.aet.artemis.core.config.Constants.SUBMISSION_PROCESSING_TOPIC;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.iris.api.PyrisEventApi;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildRunState;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingMessagingService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingMessagingService.class);

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final ResultWebsocketService resultWebsocketService;

    private final Optional<LtiApi> ltiApi;

    private final TeamRepository teamRepository;

    private final Optional<PyrisEventApi> pyrisEventApi;

    private final ParticipationRepository participationRepository;

    // The GroupNotificationService has many dependencies. We cannot refactor it to avoid that. Therefore, we lazily inject it here, so it's only instantiated when needed, or our
    // DeferredEagerInitialization kicks, but not on startup.
    public ProgrammingMessagingService(@Lazy GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService,
            ResultWebsocketService resultWebsocketService, Optional<LtiApi> ltiApi, TeamRepository teamRepository, Optional<PyrisEventApi> pyrisEventApi,
            ParticipationRepository participationRepository) {
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.resultWebsocketService = resultWebsocketService;
        this.ltiApi = ltiApi;
        this.teamRepository = teamRepository;
        this.pyrisEventApi = pyrisEventApi;
        this.participationRepository = participationRepository;
    }

    private static String getSubmissionProcessingTopicForTAAndAbove(Long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + SUBMISSION_PROCESSING;
    }

    private static String getProgrammingExerciseAllExerciseBuildsTriggeredTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/all-builds-triggered";
    }

    public void notifyInstructorAboutStartedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.RUNNING);
        // Send a notification to the client to inform the instructor about started builds.
        groupNotificationService.notifyEditorAndInstructorGroupsAboutBuildRunUpdate(programmingExercise);
    }

    public void notifyInstructorAboutCompletedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.COMPLETED);
        // Send a notification to the client to inform the instructor about the completed builds.
        groupNotificationService.notifyEditorAndInstructorGroupsAboutBuildRunUpdate(programmingExercise);
    }

    /**
     * Notify user about new result.
     *
     * @param result        the result created from the result returned from the CI system.
     * @param participation the participation for which the result was created.
     */
    public void notifyUserAboutNewResult(Result result, ProgrammingExerciseParticipation participation) {
        log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getSubmission().getParticipation());
        // notify user via websocket
        resultWebsocketService.broadcastNewResult((Participation) participation, result);

        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            // do not try to report results for template or solution participations
            ltiApi.ifPresent(api -> api.onNewResult(studentParticipation));
            // Inform Iris about the submission status (when certain conditions are met)
            notifyIrisAboutSubmissionStatus(result);
        }
    }

    /**
     * Notify Iris about the submission status for the given result and student participation.
     * <p>
     * If the submission was successful, Iris will be informed about the successful submission.
     * If the submission failed, Iris will be informed about the submission failure.
     * Iris will only be informed about the submission status if the participant is a user.
     *
     * @param result the result for which Iris should be informed about the submission status
     */
    private void notifyIrisAboutSubmissionStatus(Result result) {
        pyrisEventApi.ifPresent(eventApi -> {
            // Inform event service about the new result
            try {
                eventApi.trigger(new NewResultEvent(result));
            }
            catch (Exception e) {
                log.error("Could not trigger service for result {}", result.getId(), e);
            }
        });
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
