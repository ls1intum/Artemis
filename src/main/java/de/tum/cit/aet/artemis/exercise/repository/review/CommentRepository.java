package de.tum.cit.aet.artemis.exercise.repository.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
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
public interface CommentRepository extends ArtemisJpaRepository<Comment, Long>, CommentRepositoryCustom {

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
    @EntityGraph(attributePaths = { "thread", "thread.exercise", "thread.group", "author" })
    Optional<Comment> findWithThreadById(long commentId);

    /**
     * Delete a comment and, if that was the last comment, remove its thread.
     * If the removed thread was the last one in its group, remove the group as well.
     * Executes in a single transaction so the count checks and deletes stay consistent.
     *
     * @param comment the loaded comment entity including thread and optional group
     */
    @Transactional
    void deleteCommentWithCascade(Comment comment);
}
