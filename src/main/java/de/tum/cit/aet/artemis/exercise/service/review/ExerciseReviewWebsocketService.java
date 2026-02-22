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

    public static Optional<Long> getExerciseIdFromReviewThreadDestination(String destination) {
        var matcher = REVIEW_THREAD_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            return Optional.of(Long.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }

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

    public void notifyThreadCreated(Long exerciseId, CommentThreadDTO thread) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.threadCreated(exerciseId, thread));
    }

    public void notifyThreadUpdated(Long exerciseId, CommentThreadDTO thread) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.threadUpdated(exerciseId, thread));
    }

    public void notifyCommentCreated(Long exerciseId, CommentDTO comment) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentCreated(exerciseId, comment));
    }

    public void notifyCommentUpdated(Long exerciseId, CommentDTO comment) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentUpdated(exerciseId, comment));
    }

    public void notifyCommentDeleted(Long exerciseId, Long commentId) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.commentDeleted(exerciseId, commentId));
    }

    public void notifyGroupUpdated(Long exerciseId, List<Long> threadIds, Long groupId) {
        websocketMessagingService.sendMessage(getTopic(exerciseId), ReviewThreadWebsocketDTO.groupUpdated(exerciseId, threadIds, groupId));
    }
}
