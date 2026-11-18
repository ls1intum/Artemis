package de.tum.cit.aet.artemis.exercise.repository.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;

/**
 * Spring Data repository for the Comment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CommentRepository extends ArtemisJpaRepository<Comment, Long> {

    /**
     * Find a comment by id with its thread and exercise loaded.
     *
     * @param commentId the comment id
     * @return the comment with thread and exercise
     */
    @EntityGraph(attributePaths = { "thread", "thread.exercise", "thread.group", "author" })
    Optional<Comment> findWithThreadById(long commentId);

    /**
     * Find comments by thread ids and type with thread loaded.
     *
     * @param threadIds the thread ids
     * @param type      the comment type
     * @return matching comments
     */
    @Query("""
            SELECT c
            FROM Comment c
                JOIN FETCH c.thread t
            WHERE t.id IN :threadIds
                AND c.type = :type
            """)
    List<Comment> findByThreadIdsAndType(@Param("threadIds") Collection<Long> threadIds, @Param("type") CommentType type);

    /**
     * Count the number of comments belonging to a given thread.
     *
     * @param threadId the thread id
     * @return the number of comments in the thread
     */
    long countByThreadId(long threadId);
}
