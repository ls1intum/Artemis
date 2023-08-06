package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
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

    private final Logger log = LoggerFactory.getLogger(WebsocketMessagingService.class);

    private final SimpMessageSendingOperations messagingTemplate;

    private final ExamDateService examDateService;

    private final ExerciseDateService exerciseDateService;

    private final AuthorizationCheckService authCheckService;

    private final Executor asyncExecutor;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate, ExamDateService examDateService, ExerciseDateService exerciseDateService,
            AuthorizationCheckService authCheckService, @Qualifier("taskExecutor") Executor asyncExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.examDateService = examDateService;
        this.exerciseDateService = exerciseDateService;
        this.authCheckService = authCheckService;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Sends a message over websocket to the given topic.
     * The message will be sent asynchronously.
     *
     * @param topic   the destination to which subscription the message should be sent
     * @param message a prebuild message that should be sent to the destination (topic)
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(String topic, Message<?> message) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.send(topic, message), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} to topic {}", message, topic, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a message over websocket to the given topic.
     * The message will be sent asynchronously.
     *
     * @param topic   the destination to which subscription the message should be sent
     * @param message any object that should be sent to the destination (topic), this will typically get transformed into json
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(String topic, Object message) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSend(topic, message), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} to topic {}", message, topic, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a message over websocket to the given topic to a specific user.
     * The message will be sent asynchronously.
     *
     * @param user    the user that should receive the message.
     * @param topic   the destination to send the message to
     * @param payload the payload to send
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessageToUser(String user, String topic, Object payload) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSendToUser(user, topic, payload), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} on topic {} to user {}", payload, topic, user, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Broadcast a new result to the client.
     * Waits until all notifications are sent and the result properties are restored.
     * This allows the caller to reuse the passed result object again after calling.
     *
     * @param participation used to find the receivers of the notification
     * @param result        the result object to publish
     */
    public void awaitBroadcastNewResult(Participation participation, Result result) {
        try {
            broadcastNewResult(participation, result).get();
            // Wait until all notifications got send and the objects were reconnected.
        }
        catch (InterruptedException | ExecutionException e) {
            log.warn("timed out while sending a result notification", e);
        }
    }

    /**
     * Broadcast a new result to the client.
     *
     * @param participation the id is used in the destination (so that only clients who have subscribed the specific participation will receive the result)
     * @param result        the new result that should be sent to the client. It typically includes feedback, its participation will be cut off here to reduce the payload size.
     *                          As the participation is already known to the client, we do not need to send it. This also cuts of the exercise (including the potentially huge
     *                          problem statement and the course with all potential attributes
     * @return a CompletableFuture allowing to wait until all messages got send.
     */
    public CompletableFuture<Void> broadcastNewResult(Participation participation, Result result) {
        // remove unnecessary properties to reduce the data sent to the client (we should not send the exercise and its potentially huge problem statement)
        var originalParticipation = result.getParticipation();
        result.setParticipation(originalParticipation.copyParticipationId());
        var originalSubmission = result.getSubmission();
        result.setSubmission(null);

        final var originalAssessor = result.getAssessor();
        final var originalFeedback = new ArrayList<>(result.getFeedbacks());

        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        // TODO: Are there other cases that must be handled here?
        if (participation instanceof StudentParticipation studentParticipation) {
            final Exercise exercise = studentParticipation.getExercise();
            boolean isWorkingPeriodOver;
            if (exercise.isExamExercise()) {
                isWorkingPeriodOver = examDateService.isExerciseWorkingPeriodOver(exercise, studentParticipation);
            }
            else {
                isWorkingPeriodOver = exerciseDateService.isAfterLatestDueDate(exercise);
            }
            // Don't send students results after the exam ended
            boolean isAfterExamEnd = isWorkingPeriodOver && exercise.isExamExercise() && !exercise.getExamViaExerciseGroupOrCourseMember().isTestExam();
            // If the assessment due date is not over yet, do not send manual feedback to students!
            boolean isAutomaticAssessmentOrDueDateOver = AssessmentType.AUTOMATIC == result.getAssessmentType() || exercise.getAssessmentDueDate() == null
                    || ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate());

            if (isAutomaticAssessmentOrDueDateOver && !isAfterExamEnd) {
                var students = studentParticipation.getStudents();

                result.filterSensitiveInformation();

                students.stream().filter(student -> authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student))
                        .forEach(user -> allFutures.add(sendMessageToUser(user.getLogin(), NEW_RESULT_TOPIC, result)));

                result.filterSensitiveFeedbacks(!isWorkingPeriodOver);

                students.stream().filter(student -> !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student))
                        .forEach(user -> allFutures.add(sendMessageToUser(user.getLogin(), NEW_RESULT_TOPIC, result)));
            }
        }

        return CompletableFuture.allOf(allFutures.toArray(CompletableFuture[]::new)).thenCompose(v -> {
            // Restore information that should not go to students but tutors, instructors, and admins should still see
            // only add these values after the async broadcast is done to not publish it mistakenly
            result.setAssessor(originalAssessor);
            result.setFeedbacks(originalFeedback);

            // Send to tutors, instructors and admins
            return sendMessage(getNonPersonalExerciseResultDestination(participation.getExercise().getId()), result).thenAccept(v2 -> {
                // recover the participation because we might want to use it again after this method
                result.setParticipation(originalParticipation);
                result.setSubmission(originalSubmission);
            });
        });
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
