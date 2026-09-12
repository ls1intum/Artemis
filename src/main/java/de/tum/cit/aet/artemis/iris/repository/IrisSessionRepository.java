package de.tum.cit.aet.artemis.iris.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

/**
 * Spring Data repository for the IrisSession entity.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisSessionRepository extends ArtemisJpaRepository<IrisSession, Long>, IrisSessionWriteRepository {

    @Query("""
            SELECT s
            FROM IrisSession s
                LEFT JOIN FETCH s.messages m
            WHERE s.id = :sessionId
            """)
    Optional<IrisSession> findByIdWithMessages(@Param("sessionId") long sessionId);

    @Query("""
            SELECT s
            FROM IrisSession s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content c
            WHERE s.id = :sessionId
            """)
    IrisSession findByIdWithMessagesAndContents(@Param("sessionId") long sessionId);

    @NonNull
    default IrisSession findByIdWithMessagesElseThrow(long sessionId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithMessages(sessionId), sessionId);
    }

    /**
     * Take a write lock on the session row so that appending a message can serialize against a concurrent append to
     * the same session. Callers lock first and then load the messages, inside one transaction. The messages are
     * deliberately not fetch-joined, because PostgreSQL rejects {@code FOR UPDATE} on the nullable side of the outer
     * join {@code LEFT JOIN FETCH s.messages} produces.
     *
     * <p>
     * A cooperative mutex on the parent row, so it only serializes writers that take it. Writers that change a scalar
     * field do not need it and must not merge the aggregate instead; they go through {@link #updateTitle} and
     * {@link #updateLatestSuggestions}, which never mention the collection. The direct message-row writes
     * ({@code deleteSupersededProactiveMessage}, the outcome update) stay outside it: they target one row rather than
     * replacing the list, so they cannot orphan-remove a concurrent append, but they can interleave with one.
     *
     * @param sessionId the session to lock
     * @return the locked session, if it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM IrisSession s
            WHERE s.id = :sessionId
            """)
    Optional<IrisSession> findByIdWithWriteLock(@Param("sessionId") long sessionId);

    /**
     * {@link #findByIdWithWriteLock} or throw if the session does not exist.
     *
     * @param sessionId the session to lock
     * @return the locked session
     * @throws EntityNotFoundException if no session with that id exists
     */
    @NonNull
    default IrisSession findByIdWithWriteLockElseThrow(long sessionId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithWriteLock(sessionId), sessionId);
    }

    /**
     * Write the session title without touching the rest of the aggregate. Deliberately a scalar update rather than
     * {@code save(session)}: the callers hold a session whose messages were loaded earlier, and once that collection
     * is replaced by a plain list it loses its Hibernate snapshot, after which {@code orphanRemoval} can delete a row
     * the caller never knew about. Writing one column cannot reach the collection, so it needs no session lock.
     *
     * @param sessionId the session to rename
     * @param title     the new title
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE IrisSession s
            SET s.title = :title
            WHERE s.id = :sessionId
            """)
    void updateTitle(@Param("sessionId") long sessionId, @Param("title") String title);

    /**
     * Write the serialized latest suggestions without touching the rest of the aggregate, for the same reason as
     * {@link #updateTitle}.
     *
     * @param sessionId         the session to update
     * @param latestSuggestions the serialized suggestions
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE IrisSession s
            SET s.latestSuggestions = :latestSuggestions
            WHERE s.id = :sessionId
            """)
    void updateLatestSuggestions(@Param("sessionId") long sessionId, @Param("latestSuggestions") String latestSuggestions);

    /**
     * Counts all Iris sessions for a given user, regardless of concrete session type.
     *
     * @param userId the ID of the user
     * @return the number of Iris sessions
     */
    long countByUserId(long userId);

    /**
     * Counts the total number of messages across all Iris sessions for a given user.
     *
     * @param userId the ID of the user
     * @return the total number of messages
     */
    @Query("""
            SELECT COUNT(m)
            FROM IrisSession s
                JOIN s.messages m
            WHERE s.userId = :userId
            """)
    long countMessagesByUserId(@Param("userId") long userId);

    /**
     * Finds all Iris session IDs for a user.
     * Used internally to fetch sessions in a two-step process to avoid PostgreSQL
     * JSON equality comparison issues with DISTINCT.
     *
     * @param userId the ID of the user
     * @return a set of session IDs for the user
     */
    @Query("""
            SELECT s.id
            FROM IrisSession s
            WHERE s.userId = :userId
            """)
    Set<Long> findSessionIdsByUserId(@Param("userId") long userId);

    /**
     * Finds all Iris sessions by their IDs with messages and content eagerly loaded.
     * Used internally as the second step of a two-query approach to load sessions
     * with their messages while avoiding PostgreSQL JSON equality comparison issues.
     * Note: This query intentionally does not use DISTINCT because PostgreSQL cannot
     * compare JSON columns for equality. The return type is {@code Set} so duplicate
     * parent rows produced by the LEFT JOIN FETCH are collapsed by entity identity.
     *
     * @param sessionIds the IDs of the sessions to fetch
     * @return a set of Iris sessions with messages
     */
    @Query("""
            SELECT s
            FROM IrisSession s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content
            WHERE s.id IN :sessionIds
            """)
    Set<IrisSession> findAllWithMessagesByIds(@Param("sessionIds") Collection<Long> sessionIds);

    /**
     * Deletes all Iris sessions for a given user.
     * Messages and their content are removed via cascade (CascadeType.ALL + orphanRemoval on IrisSession.messages).
     *
     * @param userId the ID of the user whose sessions should be deleted
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteAllByUserId(long userId);

}
