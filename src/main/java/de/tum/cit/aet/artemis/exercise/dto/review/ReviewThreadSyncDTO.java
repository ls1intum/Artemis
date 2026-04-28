package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadSyncAction;

/**
 * DTO that is included as payload for review thread related synchronization messages.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewThreadSyncDTO(ReviewThreadSyncAction action, CommentThreadDTO thread, CommentDTO comment, Long commentId, List<Long> threadIds, Long groupId) {

    public static ReviewThreadSyncDTO threadCreated(CommentThreadDTO thread) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.THREAD_CREATED, thread, null, null, null, null);
    }

    public static ReviewThreadSyncDTO threadUpdated(CommentThreadDTO thread) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.THREAD_UPDATED, thread, null, null, null, null);
    }

    public static ReviewThreadSyncDTO commentCreated(CommentDTO comment) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_CREATED, null, comment, null, null, null);
    }

    public static ReviewThreadSyncDTO commentUpdated(CommentDTO comment) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_UPDATED, null, comment, null, null, null);
    }

    public static ReviewThreadSyncDTO commentDeleted(Long commentId) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_DELETED, null, null, commentId, null, null);
    }

    public static ReviewThreadSyncDTO groupUpdated(List<Long> threadIds, Long groupId) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.GROUP_UPDATED, null, null, null, threadIds, groupId);
    }
}
