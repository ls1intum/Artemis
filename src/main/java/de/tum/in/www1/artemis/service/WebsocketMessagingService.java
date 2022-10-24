package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

/**
 * This service sends out websocket messages.
 */
@Service
public class WebsocketMessagingService {

    private final SimpMessageSendingOperations messagingTemplate;

    private final ExamDateService examDateService;

    private final ExerciseDateService exerciseDateService;

    private final AuthorizationCheckService authCheckService;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate, ExamDateService examDateService, ExerciseDateService exerciseDateService,
            AuthorizationCheckService authCheckService) {
        this.messagingTemplate = messagingTemplate;
        this.examDateService = examDateService;
        this.exerciseDateService = exerciseDateService;
        this.authCheckService = authCheckService;
    }

    /**
     * Wrapper method to send a message over websocket to the given topic
     * @param topic the destination to which subscription the message should be sent
     * @param message any object that should be sent to the destination (topic), this will typically get transformed into json
     */
    public void sendMessage(String topic, Object message) {
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * Wrapper method to send a message over websocket to the given topic to a specific user
     * @param user the user that should receive the message.
     * @param destination the destination to send the message to
     * @param payload the payload to send
     */
    public void sendMessageToUser(String user, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(user, destination, payload);
    }

    /**
     * Broadcast a new result to the client.
     *
     * @param participation the id is used in the destination (so that only clients who have subscribed the specific participation will receive the result)
     * @param result the new result that should be sent to the client. It typically includes feedback, its participation will be cut off here to reduce the payload size.
     *               As the participation is already known to the client, we do not need to send it. This also cuts of the exercise (including the potentially huge
     *               problem statement and the course with all potential attributes
     */
    public void broadcastNewResult(Participation participation, Result result) {
        // remove unnecessary properties to reduce the data sent to the client (we should not send the exercise and its potentially huge problem statement)
        var originalParticipation = result.getParticipation();
        result.setParticipation(originalParticipation.copyParticipationId());

        final var originalAssessor = result.getAssessor();
        final var originalFeedback = new ArrayList<>(result.getFeedbacks());

        // TODO: Are there other cases that must be handled here?
        if (participation instanceof StudentParticipation studentParticipation) {
            final Exercise exercise = studentParticipation.getExercise();
            final boolean isWorkingPeriodOver;
            if (exercise.isExamExercise()) {
                isWorkingPeriodOver = examDateService.isExerciseWorkingPeriodOver(exercise);
            }
            else {
                isWorkingPeriodOver = exerciseDateService.isAfterLatestDueDate(exercise);
            }
            // Don't send students results after the exam ended
            boolean isAfterExamEnd = isWorkingPeriodOver && exercise.isExamExercise();
            // If the assessment due date is not over yet, do not send manual feedback to students!
            boolean isReadyForRelease = AssessmentType.AUTOMATIC == result.getAssessmentType() || exercise.getAssessmentDueDate() == null
                    || ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate());

            if (isReadyForRelease && !isAfterExamEnd) {
                result.filterSensitiveInformation();
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                    result.filterSensitiveFeedbacks(!isWorkingPeriodOver);
                }

                studentParticipation.getStudents().forEach(user -> messagingTemplate.convertAndSendToUser(user.getLogin(), NEW_RESULT_TOPIC, result));
            }
        }

        // Restore information that should not go to students but tutors, instructors, and admins should still see
        result.setAssessor(originalAssessor);
        result.setFeedbacks(originalFeedback);

        // Send to tutors, instructors and admins
        messagingTemplate.convertAndSend(getNonPersonalExerciseResultDestination(participation.getExercise().getId()), result);

        // recover the participation because we might want to use it again after this method
        result.setParticipation(originalParticipation);
    }

    /**
     * Returns true if the given destination is a 'non-personal' exercise result subscription.
     * Only teaching assistants, instructors and admins should be allowed to subscribe to this topic.
     *
     * @param destination Websocket destination topic which to check
     * @return flag whether the destination is a 'non-personal' exercise result subscription
     */
    public static boolean isNonPersonalExerciseResultDestination(String destination) {
        return Optional.ofNullable(getExerciseIdFromNonPersonalExerciseResultDestination(destination)).isPresent();
    }

    /**
     * Returns the exercise id from the destination route
     *
     * @param destination Websocket destination topic from which to extract the exercise id
     * @return exercise id
     */
    public static Long getExerciseIdFromNonPersonalExerciseResultDestination(String destination) {
        Pattern pattern = Pattern.compile("^" + getNonPersonalExerciseResultDestination("(\\d*)"));
        Matcher matcher = pattern.matcher(destination);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String getNonPersonalExerciseResultDestination(long exerciseId) {
        return getNonPersonalExerciseResultDestination(String.valueOf(exerciseId));
    }

    private static String getNonPersonalExerciseResultDestination(String exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + "/newResults";
    }
}
