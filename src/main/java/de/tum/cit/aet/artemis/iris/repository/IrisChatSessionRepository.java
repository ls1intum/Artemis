package de.tum.cit.aet.artemis.iris.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisChatSessionRepository extends ArtemisJpaRepository<IrisChatSession, Long> {

    // -------------------------------------------------------------------------
    // Sidebar overview query (course-scoped, all modes)
    // -------------------------------------------------------------------------

    /**
     * Finds a list of {@link IrisChatSession} based on the course and user ID. Filters sessions without messages and sorts them by last activity in descending order.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @return A list of chat sessions sorted by last activity (most recent message) in descending order.
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.iris.dao.IrisChatSessionDAO(
                      s,
                      s.entityId,
                      CASE s.chatMode
                          WHEN de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.LECTURE_CHAT THEN l.title
                          WHEN de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT THEN e.shortName
                          WHEN de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.TEXT_EXERCISE_CHAT THEN e.shortName
                          ELSE NULL END,
                      MAX(m.sentAt)
                  )
                FROM IrisChatSession s
                    LEFT JOIN Exercise e ON e.id = s.entityId AND s.chatMode IN (
                        de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT,
                        de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.TEXT_EXERCISE_CHAT)
                    LEFT JOIN Lecture l ON l.id = s.entityId AND s.chatMode = de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.LECTURE_CHAT
                    LEFT JOIN s.messages m
                WHERE s.userId = :userId
                    AND s.courseId = :courseId
                    AND m.sender = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.USER
                GROUP BY s, s.entityId, s.chatMode, e.shortName, l.title
                HAVING COUNT(m) > 0
                ORDER BY MAX(m.sentAt) DESC
            """)
    List<IrisChatSessionDAO> findByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId);

    // -------------------------------------------------------------------------
    // Session lookup by entity (exercise or lecture)
    // -------------------------------------------------------------------------

    List<IrisChatSession> findByEntityIdAndUserIdOrderByCreationDateDesc(Long entityId, Long userId, Pageable pageable);

    /**
     * Finds the latest chat sessions for the given entity (exercise or lecture) and user, with messages eagerly loaded.
     *
     * @param entityId the entity ID (exerciseId or lectureId depending on chatMode)
     * @param userId   the user ID
     * @param pageable pagination info
     * @return list of sessions with messages
     */
    default List<IrisChatSession> findLatestByEntityIdAndUserIdWithMessages(Long entityId, Long userId, Pageable pageable) {
        List<Long> ids = findByEntityIdAndUserIdOrderByCreationDateDesc(entityId, userId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return findSessionsWithMessagesByIdIn(ids);
    }

    // -------------------------------------------------------------------------
    // Session lookup by course (COURSE_CHAT mode only)
    // -------------------------------------------------------------------------

    List<IrisChatSession> findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(long courseId, IrisChatMode chatMode, long userId, Pageable pageable);

    /**
     * Finds the latest course-only chat sessions for the given course and user, with messages eagerly loaded.
     *
     * @param courseId the course ID
     * @param userId   the user ID
     * @param pageable pagination info
     * @return list of sessions with messages
     */
    default List<IrisChatSession> findLatestCourseChatSessionsByUserIdWithMessages(long courseId, long userId, Pageable pageable) {
        List<Long> ids = findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(courseId, IrisChatMode.COURSE_CHAT, userId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return findSessionsWithMessagesByIdIn(ids);
    }

    // -------------------------------------------------------------------------
    // Shared helper: eager message loading by IDs
    // -------------------------------------------------------------------------

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    // -------------------------------------------------------------------------
    // Course-level admin queries (used by IrisSettingsApi)
    // -------------------------------------------------------------------------

    /**
     * Count the number of chat sessions for a given course.
     *
     * @param courseId the id of the course
     * @return the number of chat sessions in the course
     */
    long countByCourseId(long courseId);

    /**
     * Find all chat sessions with messages for a given course.
     * <p>
     * Note: This query intentionally does not use DISTINCT because IrisMessage contains json columns
     * (accessed_memories, created_memories) and PostgreSQL's json type does not support equality operators.
     * The return type is {@code Set} to deduplicate results from the LEFT JOIN FETCH.
     * Use {@link #findAllWithMessagesByCourseIdSortedByCreationDate(long)} for sorted results.
     *
     * @param courseId the id of the course
     * @return set of chat sessions with their messages
     */
    @Query("""
            SELECT s
            FROM IrisChatSession s
                LEFT JOIN FETCH s.messages
            WHERE s.courseId = :courseId
            """)
    Set<IrisChatSession> findAllWithMessagesByCourseId(@Param("courseId") long courseId);

    /**
     * Find all chat sessions with messages for a given course, sorted by creation date ascending.
     * Sorting is done in Java because the query returns a {@code Set} to avoid issues with
     * DISTINCT and PostgreSQL's json columns.
     *
     * @param courseId the id of the course
     * @return list of chat sessions with their messages, sorted by creation date ascending
     */
    default List<IrisChatSession> findAllWithMessagesByCourseIdSortedByCreationDate(long courseId) {
        return findAllWithMessagesByCourseId(courseId).stream().sorted(Comparator.comparing(IrisChatSession::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Deletes all chat sessions for a given course.
     *
     * @param courseId The ID of the course.
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteAllByCourseId(long courseId);

    // -------------------------------------------------------------------------
    // User-level queries (used by IrisChatSessionResource and IrisDataExportApi)
    // -------------------------------------------------------------------------

    /**
     * Counts the number of chat sessions for a given user.
     *
     * @param userId the ID of the user
     * @return the number of chat sessions
     */
    long countByUserId(long userId);

    /**
     * Counts the total number of messages across all chat sessions for a given user.
     *
     * @param userId the ID of the user
     * @return the total number of messages
     */
    @Query("""
            SELECT COUNT(m)
            FROM IrisChatSession s
                JOIN s.messages m
            WHERE s.userId = :userId
            """)
    long countMessagesByUserId(@Param("userId") long userId);

    /**
     * Finds all chat session IDs for a user.
     * Used internally to fetch sessions in a two-step process to avoid PostgreSQL
     * JSON equality comparison issues with DISTINCT.
     *
     * @param userId the ID of the user
     * @return a set of session IDs for the user
     */
    @Query("""
            SELECT s.id
            FROM IrisChatSession s
            WHERE s.userId = :userId
            """)
    Set<Long> findSessionIdsByUserId(@Param("userId") long userId);

    /**
     * Finds all chat sessions by their IDs with messages and content eagerly loaded.
     * Used internally as the second step of a two-query approach to load sessions
     * with their messages while avoiding PostgreSQL JSON equality comparison issues.
     * Note: This query intentionally does not use DISTINCT because PostgreSQL cannot
     * compare JSON columns for equality. Deduplication must be done in the service layer.
     *
     * @param sessionIds the IDs of the sessions to fetch
     * @return a set of chat sessions with messages (may contain duplicates due to LEFT JOIN FETCH)
     */
    @Query("""
            SELECT s
            FROM IrisChatSession s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content
            WHERE s.id IN :sessionIds
            """)
    Set<IrisChatSession> findAllWithMessagesByIds(@Param("sessionIds") Collection<Long> sessionIds);

    /**
     * Deletes all chat sessions for a given user.
     * Messages and their content are removed via cascade (CascadeType.ALL + orphanRemoval on IrisSession.messages).
     *
     * @param userId the ID of the user whose sessions should be deleted
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteAllByUserId(long userId);
}
