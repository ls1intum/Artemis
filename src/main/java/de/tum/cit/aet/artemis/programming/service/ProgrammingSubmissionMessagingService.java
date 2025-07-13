package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.cit.aet.artemis.core.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_SUBMISSION_TOPIC;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class ProgrammingSubmissionMessagingService {

    private final TeamRepository teamRepository;

    private final WebsocketMessagingService websocketMessagingService;

    public ProgrammingSubmissionMessagingService(TeamRepository teamRepository, WebsocketMessagingService websocketMessagingService) {
        this.teamRepository = teamRepository;
        this.websocketMessagingService = websocketMessagingService;
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

    private static String getExerciseTopicForTAAndAbove(long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + PROGRAMMING_SUBMISSION_TOPIC;
    }

    public void notifyUserAboutSubmissionError(ProgrammingSubmission submission, BuildTriggerWebsocketError error) {
        notifyUserAboutSubmissionError(submission.getParticipation(), error);
    }
}
