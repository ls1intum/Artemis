package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadWebsocketAction;

/**
 * DTO that is included as payload for review thread related websocket messages.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction action, Long exerciseId, CommentThreadDTO thread, CommentDTO comment, Long commentId, List<Long> threadIds,
        Long groupId) {

    public static ReviewThreadWebsocketDTO threadCreated(Long exerciseId, CommentThreadDTO thread) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.THREAD_CREATED, exerciseId, thread, null, null, null, null);
    }

    public static ReviewThreadWebsocketDTO threadUpdated(Long exerciseId, CommentThreadDTO thread) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.THREAD_UPDATED, exerciseId, thread, null, null, null, null);
    }

    public static ReviewThreadWebsocketDTO commentCreated(Long exerciseId, CommentDTO comment) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.COMMENT_CREATED, exerciseId, null, comment, null, null, null);
    }

    public static ReviewThreadWebsocketDTO commentUpdated(Long exerciseId, CommentDTO comment) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.COMMENT_UPDATED, exerciseId, null, comment, null, null, null);
    }

    public static ReviewThreadWebsocketDTO commentDeleted(Long exerciseId, Long commentId) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.COMMENT_DELETED, exerciseId, null, null, commentId, null, null);
    }

    public static ReviewThreadWebsocketDTO groupUpdated(Long exerciseId, List<Long> threadIds, Long groupId) {
        return new ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction.GROUP_UPDATED, exerciseId, null, null, null, threadIds, groupId);
    }
}
