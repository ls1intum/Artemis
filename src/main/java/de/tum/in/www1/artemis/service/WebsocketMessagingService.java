package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_TOPIC;

import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

/**
 * This service sends out websocket messages.
 */
@Service
public class WebsocketMessagingService {

    SimpMessageSendingOperations messagingTemplate;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
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
     * Broadcast a new result to the client.
     *
     * @param participation the id is used in the destination (so that only clients who have subscribed the specific participation will receive the result)
     * @param result the new result that should be send to the client. It typically includes feedback, its participation will be cut off here to reduce the payload size.
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
        if (participation instanceof StudentParticipation) {
            StudentParticipation studentParticipation = (StudentParticipation) participation;

            result.filterSensitiveInformation();
            result.filterSensitiveFeedbacks(studentParticipation.getExercise().isBeforeDueDate());

            studentParticipation.getStudents().forEach(user -> messagingTemplate.convertAndSendToUser(user.getLogin(), NEW_RESULT_TOPIC, result));
        }

        // Restore information that should not go to students but tutors, instructors, and admins should still see
        result.setAssessor(originalAssessor);
        result.setFeedbacks(originalFeedback);

        // Send to tutors, instructors and admins
        messagingTemplate.convertAndSend(getResultDestination(participation.getExercise().getId()), result);

        // recover the participation because we might want to use it again after this method
        result.setParticipation(originalParticipation);
    }

    /**
     * Returns true if the given destination should be handled by this service.
     * This is the case if this is a 'non-personal' subscription (a result subscription for a whole exercise).
     * Only teaching assistants, instructors and admins should be allowed to subscribe to this topic.
     *
     * @param destination Websocket destination topic which to check
     * @return flag whether the destination belongs to this controller (is a 'non-personal' result subscription)
     */
    public static boolean isResultNonPersonalDestination(String destination) {
        return Optional.ofNullable(getExerciseIdFromResultDestination(destination)).isPresent();
    }

    /**
     * Returns the exercise id from the destination route
     *
     * @param destination Websocket destination topic from which to extract the exercise id
     * @return exercise id
     */
    public static Long getExerciseIdFromResultDestination(String destination) {
        Pattern pattern = Pattern.compile("^" + getResultDestination("(\\d*)"));
        Matcher matcher = pattern.matcher(destination);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String getResultDestination(long exerciseId) {
        return getResultDestination(String.valueOf(exerciseId));
    }

    private static String getResultDestination(String exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + "/newResults";
    }
}
