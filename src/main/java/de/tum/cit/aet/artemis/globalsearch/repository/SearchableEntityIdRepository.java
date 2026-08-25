package de.tum.cit.aet.artemis.globalsearch.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;

/**
 * Walks the ids the database expects to be indexed, one page at a time, for the entity types whose modules are
 * always present.
 * <p>
 * Every query takes the id the last page stopped at and returns the next ones in ascending order, so a pass can
 * stop after a bounded slice and resume exactly where it left off. Only ids are selected: deciding whether an
 * entity is indexed at all never needs the entity itself.
 * <p>
 * These queries live here rather than in each entity's own repository because they exist only for the reconcile
 * passes, following the same consumer-owns-its-queries pattern as {@code StatisticsRepository}. The bound
 * {@link Course} type is just the subject the methods share; the inherited CRUD operations are unused. Entities
 * from optional modules (lecture, lecture unit, exam) are deliberately absent: those go through their module's
 * {@code api} package so a pass degrades gracefully when the module is disabled.
 * <p>
 * The indexability conditions below must match the write path exactly, or a pass will queue work forever for
 * entities that are then resolved as not indexable. Note that a channel and its posts do not share a condition:
 * a course-wide but non-public channel is indexed while its posts are not.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntityIdRepository extends ArtemisJpaRepository<Course, Long> {

    /**
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next course ids in ascending order
     */
    @Query("""
            SELECT course.id
            FROM Course course
            WHERE course.id > :afterId
            ORDER BY course.id ASC
            """)
    List<Long> findCourseIdsAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * Every exercise is indexed, including exam exercises, which resolve their course through the exercise group.
     *
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next exercise ids in ascending order
     */
    @Query("""
            SELECT exercise.id
            FROM Exercise exercise
            WHERE exercise.id > :afterId
            ORDER BY exercise.id ASC
            """)
    List<Long> findExerciseIdsAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next FAQ ids in ascending order
     */
    @Query("""
            SELECT faq.id
            FROM Faq faq
            WHERE faq.id > :afterId
            ORDER BY faq.id ASC
            """)
    List<Long> findFaqIdsAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * A channel is indexed when it is not archived and is either course-wide or public, matching
     * {@code ChannelSearchableEntityDTO.isIndexable}.
     *
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next indexable channel ids in ascending order
     */
    @Query("""
            SELECT channel.id
            FROM Channel channel
            WHERE channel.id > :afterId
                AND channel.isArchived = FALSE
                AND (channel.isCourseWide = TRUE OR channel.isPublic = TRUE)
            ORDER BY channel.id ASC
            """)
    List<Long> findIndexableChannelIdsAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * A post is indexed when it lives in a channel that is not archived and is public, matching
     * {@code PostSearchableEntityDTO.isIndexable}. Note this is stricter than the channel condition above: posts in
     * a course-wide but non-public channel are not indexed. Conversations that are not channels are never indexed.
     *
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next indexable post ids in ascending order
     */
    @Query("""
            SELECT post.id
            FROM Post post
            WHERE post.id > :afterId
                AND post.conversation.id IN (
                    SELECT channel.id
                    FROM Channel channel
                    WHERE channel.isArchived = FALSE AND channel.isPublic = TRUE
                )
            ORDER BY post.id ASC
            """)
    List<Long> findIndexablePostIdsAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * An answer post follows the indexability of its parent post's channel.
     *
     * @param afterId  the id the previous page stopped at
     * @param pageable the page size
     * @return the next indexable answer post ids in ascending order
     */
    @Query("""
            SELECT answerPost.id
            FROM AnswerPost answerPost
            WHERE answerPost.id > :afterId
                AND answerPost.post.conversation.id IN (
                    SELECT channel.id
                    FROM Channel channel
                    WHERE channel.isArchived = FALSE AND channel.isPublic = TRUE
                )
            ORDER BY answerPost.id ASC
            """)
    List<Long> findIndexableAnswerPostIdsAfter(@Param("afterId") long afterId, Pageable pageable);
}
