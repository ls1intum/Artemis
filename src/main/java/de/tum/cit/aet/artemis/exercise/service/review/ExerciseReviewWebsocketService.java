package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ReviewThreadWebsocketDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseReviewWebsocketService {

    private static final Pattern REVIEW_THREAD_TOPIC_PATTERN = Pattern.compile("^/topic/exercises/(\\d+)/review-threads$");

    private final WebsocketMessagingService websocketMessagingService;

    public ExerciseReviewWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Extracts the exercise id from a review thread websocket topic destination.
     *
     * @param destination the websocket destination topic
     * @return an optional containing the exercise id if the destination matches
     */
    public static Optional<Long> getExerciseIdFromReviewThreadDestination(String destination) {
        var matcher = REVIEW_THREAD_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            return Optional.of(Long.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }

    /**
     * Checks whether the destination belongs to the review thread websocket topic.
     *
     * @param destination the websocket destination topic
     * @return true if the destination is a review thread topic, false otherwise
     */
    public static boolean isReviewThreadDestination(String destination) {
        return getExerciseIdFromReviewThreadDestination(destination).isPresent();
    }

    /**
     * Builds the websocket topic for review thread updates of an exercise.
     *
     * @param exerciseId the exercise id
     * @return the websocket destination topic
     */
    private String getTopic(Long exerciseId) {
        return "/topic/exercises/" + exerciseId + "/review-threads";
    }

    /**
     * Broadcasts a thread creation event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param thread     the created thread payload
     */
    public void notifyThreadCreated(Long exerciseId, CommentThreadDTO thread) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.threadCreated(exerciseId, thread));
    }

    /**
     * Broadcasts a thread update event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param thread     the updated thread payload
     */
    public void notifyThreadUpdated(Long exerciseId, CommentThreadDTO thread) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.threadUpdated(exerciseId, thread));
    }

    /**
     * Broadcasts a comment creation event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param comment    the created comment payload
     */
    public void notifyCommentCreated(Long exerciseId, CommentDTO comment) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentCreated(exerciseId, comment));
    }

    /**
     * Broadcasts a comment update event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param comment    the updated comment payload
     */
    public void notifyCommentUpdated(Long exerciseId, CommentDTO comment) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentUpdated(exerciseId, comment));
    }

    /**
     * Broadcasts a comment deletion event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param commentId  the deleted comment id
     */
    public void notifyCommentDeleted(Long exerciseId, Long commentId) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentDeleted(exerciseId, commentId));
    }

    /**
     * Broadcasts a group update event to all subscribers of the exercise.
     *
     * @param exerciseId the exercise id
     * @param threadIds  ids of affected threads
     * @param groupId    the updated group id
     */
    public void notifyGroupUpdated(Long exerciseId, List<Long> threadIds, Long groupId) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.groupUpdated(exerciseId, threadIds, groupId));
    }
}
