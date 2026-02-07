package de.tum.cit.aet.artemis.exercise.repository.review;

import org.springframework.transaction.annotation.Transactional;

/**
 * Custom repository fragment for comment operations.
 */
public interface CommentRepositoryCustom {

    /**
     * Delete a comment and, if that was the last comment, remove its thread.
     * If the removed thread was the last one in its group, remove the group as well.
     * Executes in a single transaction so the count checks and deletes stay consistent.
     *
     * @param commentId the comment id
     */
    @Transactional
    void deleteCommentWithCascade(long commentId);
}
