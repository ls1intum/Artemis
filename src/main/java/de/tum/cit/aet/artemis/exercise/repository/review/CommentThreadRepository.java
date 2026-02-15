package de.tum.cit.aet.artemis.exercise.repository.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;

/**
 * Spring Data repository for the CommentThread entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CommentThreadRepository extends ArtemisJpaRepository<CommentThread, Long> {

    /**
     * Find all comment threads for a given exercise.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads
     */
    List<CommentThread> findByExerciseId(long exerciseId);

    /**
     * Find all active comment threads for a given exercise that have a concrete line reference.
     * These are the only threads relevant for version-based line remapping.
     *
     * @param exerciseId the exercise id
     * @return list of active line-bound comment threads
     */
    List<CommentThread> findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(long exerciseId);

    /**
     * Find all comment threads for a given exercise with their comments loaded.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads with comments
     */
    @Query("""
            SELECT DISTINCT ct
            FROM CommentThread ct
                LEFT JOIN FETCH ct.comments c
                LEFT JOIN FETCH c.author
            WHERE ct.exercise.id = :exerciseId
            """)
    Set<CommentThread> findWithCommentsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Find a comment thread by id with its comments loaded.
     *
     * @param threadId the thread id
     * @return the comment thread with comments
     */
    @EntityGraph(attributePaths = { "comments", "comments.author" })
    java.util.Optional<CommentThread> findWithCommentsById(long threadId);

    /**
     * Find all comment threads for a given group.
     *
     * @param groupId the group id
     * @return list of comment threads
     */
    List<CommentThread> findByGroupId(long groupId);

    /**
     * Count comment threads for a given group.
     *
     * @param groupId the group id
     * @return number of threads in the group
     */
    long countByGroupId(long groupId);
}
