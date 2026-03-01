package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadSyncAction;

/**
 * DTO that is included as payload for review thread related synchronization messages.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewThreadSyncDTO(ReviewThreadSyncAction action, Long exerciseId, CommentThreadDTO thread, CommentDTO comment, Long commentId, List<Long> threadIds, Long groupId) {

    public static ReviewThreadSyncDTO threadCreated(Long exerciseId, CommentThreadDTO thread) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.THREAD_CREATED, exerciseId, thread, null, null, null, null);
    }

    public static ReviewThreadSyncDTO threadUpdated(Long exerciseId, CommentThreadDTO thread) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.THREAD_UPDATED, exerciseId, thread, null, null, null, null);
    }

    public static ReviewThreadSyncDTO commentCreated(Long exerciseId, CommentDTO comment) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_CREATED, exerciseId, null, comment, null, null, null);
    }

    public static ReviewThreadSyncDTO commentUpdated(Long exerciseId, CommentDTO comment) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_UPDATED, exerciseId, null, comment, null, null, null);
    }

    public static ReviewThreadSyncDTO commentDeleted(Long exerciseId, Long commentId) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.COMMENT_DELETED, exerciseId, null, null, commentId, null, null);
    }

    public static ReviewThreadSyncDTO groupUpdated(Long exerciseId, List<Long> threadIds, Long groupId) {
        return new ReviewThreadSyncDTO(ReviewThreadSyncAction.GROUP_UPDATED, exerciseId, null, null, null, threadIds, groupId);
    }
}
