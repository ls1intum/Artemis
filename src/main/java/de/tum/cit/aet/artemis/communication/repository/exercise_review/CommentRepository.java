package de.tum.cit.aet.artemis.communication.repository.exercise_review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.exercise_review.Comment;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

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
     * Count comments for a thread.
     *
     * @param threadId the thread id
     * @return comment count
     */
    long countByThreadId(long threadId);

    /**
     * Find a comment by id with its thread and exercise loaded.
     *
     * @param commentId the comment id
     * @return the comment with thread and exercise
     */
    @EntityGraph(attributePaths = { "thread", "thread.exercise" })
    Optional<Comment> findWithThreadById(long commentId);
}
