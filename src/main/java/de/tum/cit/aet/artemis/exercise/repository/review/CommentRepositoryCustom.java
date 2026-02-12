package de.tum.cit.aet.artemis.exercise.repository.review;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;

/**
 * Custom repository contract for comment lifecycle operations spanning multiple review entities.
 */
public interface CommentRepositoryCustom {

    /**
     * Delete a comment and, if that was the last comment, remove its thread.
     * If the removed thread was the last one in its group, remove the group as well.
     *
     * @param comment the loaded comment entity including thread and optional group
     */
    void deleteCommentWithCascade(Comment comment);
}
