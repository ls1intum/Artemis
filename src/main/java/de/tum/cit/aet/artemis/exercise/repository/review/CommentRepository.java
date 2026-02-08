package de.tum.cit.aet.artemis.exercise.repository.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;

/**
 * Spring Data repository for the Comment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CommentRepository extends ArtemisJpaRepository<Comment, Long> {

    /**
     * Find all comments for a thread.
     *
     * @param threadId the thread id
     * @return list of comments
     */
    List<Comment> findByThreadId(long threadId);

    /**
     * Find all comments for a thread ordered by creation date ascending.
     *
     * @param threadId the thread id
     * @return ordered list of comments
     */
    List<Comment> findByThreadIdOrderByCreatedDateAsc(long threadId);

    /**
     * Find a comment by id with its thread and exercise loaded.
     *
     * @param commentId the comment id
     * @return the comment with thread and exercise
     */
    @EntityGraph(attributePaths = { "thread", "thread.exercise", "author" })
    Optional<Comment> findWithThreadById(long commentId);

    /**
     * Delete a comment and, if that was the last comment, remove its thread.
     * If the removed thread was the last one in its group, remove the group as well.
     * Executes in a single transaction so the count checks and deletes stay consistent.
     *
     * @param commentId the comment id
     */
    @Transactional
    default void deleteCommentWithCascade(long commentId) {
        Comment comment = findWithThreadById(commentId).orElse(null);
        if (comment == null) {
            return;
        }

        Long threadId = comment.getThread().getId();
        Long groupId = comment.getThread().getGroup() != null ? comment.getThread().getGroup().getId() : null;
        deleteCommentById(commentId);

        if (countByThreadId(threadId) == 0) {
            deleteThreadById(threadId);
            if (groupId != null && countThreadsByGroupId(groupId) == 0) {
                deleteGroupById(groupId);
            }
        }
    }

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.id = :commentId")
    void deleteCommentById(@Param("commentId") long commentId);

    @Modifying
    @Query("DELETE FROM CommentThread ct WHERE ct.id = :threadId")
    void deleteThreadById(@Param("threadId") long threadId);

    @Modifying
    @Query("DELETE FROM CommentThreadGroup g WHERE g.id = :groupId")
    void deleteGroupById(@Param("groupId") long groupId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.thread.id = :threadId")
    long countByThreadId(@Param("threadId") long threadId);

    @Query("SELECT COUNT(ct) FROM CommentThread ct WHERE ct.group.id = :groupId")
    long countThreadsByGroupId(@Param("groupId") long groupId);
}
