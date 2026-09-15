package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;

/**
 * Spring Data repository for the IrisMessage entity.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisMessageRepository extends ArtemisJpaRepository<IrisMessage, Long> {

    List<IrisMessage> findAllBySessionIdOrderBySentAtAscIdAsc(long sessionId);

    /**
     * Counts the number of final LLM responses the user got within the given timeframe. Proactive responses count
     * like any other, which is what the legacy proactive triggers already do; an origin-based exemption would give
     * one proactive feature a rule none of the others has.
     *
     * @param userId the id of the user
     * @param start  the start of the timeframe
     * @param end    the end of the timeframe
     * @return the number of final LLM responses within the given timeframe
     */
    @Query("""
            SELECT COUNT(DISTINCT m)
            FROM IrisMessage m
                JOIN TREAT (m.session AS IrisChatSession) s
            WHERE s.userId = :userId
                AND m.sender = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.LLM
                AND (m.intermediate IS NULL OR m.intermediate = FALSE)
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countFinalLlmResponsesOfUserWithinTimeframe(@Param("userId") long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    /**
     * Stable write-target finder for the pre-registry outcome write: the smallest id is monotonic, so two concurrent
     * outcome writes pick the same target row and the row-scoped {@link #setProactiveOutcomeIfNull} guard makes
     * first-terminal-wins atomic. Ordering by {@code sentAt} would not be stable.
     * <p>
     * The user scope is a security guard, since {@code episodeId} is client-generated; the exercise scope closes the
     * same reuse inside one user and reads {@code proactiveExerciseId} rather than the session, whose entityId moves
     * on every context switch.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the requesting user; only rows in this user's sessions are returned
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are returned
     * @return the episode's rows owned by this user in this exercise, ordered by id ascending, or empty if none persisted yet
     */
    @Query("""
            SELECT m
            FROM IrisMessage m
            WHERE m.proactiveEpisodeId = :episodeId
              AND m.proactiveExerciseId = :exerciseId
              AND m.session.userId = :userId
            ORDER BY m.id ASC
            """)
    List<IrisMessage> findEpisodeRowsForUserOrderByIdAsc(@Param("episodeId") String episodeId, @Param("userId") long userId, @Param("exerciseId") long exerciseId);

    /**
     * The ids of the same rows {@link #findEpisodeRowsForUserOrderByIdAsc} returns, for the callers that only need the
     * episode's stable smallest-id target. Loading the entities instead would pull each row's EAGER content collection
     * and its session, i.e. the hint text this projection never looks at.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the requesting user; only rows in this user's sessions are returned
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are returned
     * @return the ids of the episode's rows owned by this user in this exercise, ascending, or empty if none persisted yet
     */
    @Query("""
            SELECT m.id
            FROM IrisMessage m
            WHERE m.proactiveEpisodeId = :episodeId
              AND m.proactiveExerciseId = :exerciseId
              AND m.session.userId = :userId
            ORDER BY m.id ASC
            """)
    List<Long> findEpisodeRowIdsForUserOrderByIdAsc(@Param("episodeId") String episodeId, @Param("userId") long userId, @Param("exerciseId") long exerciseId);

    /**
     * Episode-wide outcome read, across every row tagged with the episode id rather than the earliest one, so the
     * result is stable under out-of-order persistence. By first-terminal-wins at most one value exists. Scoped for
     * the reasons given on {@link #findEpisodeRowsForUserOrderByIdAsc}.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the requesting user; only outcomes on rows in this user's sessions are returned
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are considered
     * @return list of non-null outcomes for the episode owned by this user in this exercise (at most one element by design)
     */
    @Query("""
            SELECT m.proactiveOutcome
            FROM IrisMessage m
            WHERE m.proactiveEpisodeId = :episodeId
              AND m.proactiveExerciseId = :exerciseId
              AND m.proactiveOutcome IS NOT NULL
              AND m.session.userId = :userId
            """)
    List<IrisProactiveOutcome> findEpisodeOutcomes(@Param("episodeId") String episodeId, @Param("userId") long userId, @Param("exerciseId") long exerciseId);

    /**
     * The same episode-wide outcome read as {@link #findEpisodeOutcomes}, but as a LOCKING read, so it returns what is
     * committed RIGHT NOW rather than what this transaction's snapshot holds.
     *
     * <p>
     * Only for the caller classifying a guarded outcome write that affected zero rows, where a plain read can miss
     * the outcome it just lost to and the two answers lead to opposite client behaviour. Ordered by id so concurrent
     * callers take the locks in the same order, and user-scoped through a subquery rather than the navigation
     * {@link #findEpisodeOutcomes} uses, which would join {@code iris_session} into the lock.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the requesting user; only outcomes on rows in this user's sessions are returned
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are considered
     * @return list of non-null outcomes for the episode owned by this user in this exercise (at most one by design)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m.proactiveOutcome
            FROM IrisMessage m
            WHERE m.proactiveEpisodeId = :episodeId
              AND m.proactiveExerciseId = :exerciseId
              AND m.proactiveOutcome IS NOT NULL
              AND m.session.id IN (SELECT s.id FROM IrisSession s WHERE s.userId = :userId)
            ORDER BY m.id ASC
            """)
    List<IrisProactiveOutcome> findEpisodeOutcomesForUpdate(@Param("episodeId") String episodeId, @Param("userId") long userId, @Param("exerciseId") long exerciseId);

    /**
     * Row-scoped first-write-wins update: sets {@code proactiveOutcome} on the target row only if that row currently
     * has a null outcome. The guard references only the target row, because a {@code WHERE NOT EXISTS (SELECT ...
     * FROM iris_message ...)} guard would trip MySQL error 1093. The episode-wide first-terminal-wins decision is the
     * caller's ({@link #findEpisodeOutcomes}); this statement only guarantees the chosen row is written at most once.
     *
     * @param messageId the id of the target row (the episode's first-persisted / smallest-id row, chosen by the caller)
     * @param outcome   the outcome to write
     * @return number of rows updated (1 = wrote; 0 = the target row already carried an outcome OR no longer exists)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE IrisMessage m SET m.proactiveOutcome = :outcome WHERE m.id = :messageId AND m.proactiveOutcome IS NULL")
    int setProactiveOutcomeIfNull(@Param("messageId") long messageId, @Param("outcome") IrisProactiveOutcome outcome);

    /**
     * The id of the session a message belongs to, but only when that session is the given user's own. Used to find
     * the row to lock before a delete, and it doubles as the ownership pre-check: a foreign or missing message
     * yields empty, so the caller stops before it locks anything.
     *
     * @param messageId the message to look up
     * @param userId    the requesting user; a message in another user's session is not found
     * @return the owning session's id, or empty when the message does not exist or is not this user's
     */
    @Query("""
            SELECT m.session.id
            FROM IrisMessage m
            WHERE m.id = :messageId
              AND m.session.userId = :userId
            """)
    Optional<Long> findOwnedSessionId(@Param("messageId") long messageId, @Param("userId") long userId);

    /**
     * The message's position in its session's ordered list. Native, because {@code iris_message_order} is an
     * {@link jakarta.persistence.OrderColumn} and therefore not a mapped field JPQL could select.
     *
     * @param messageId the message to look up
     * @return the row's list index, or empty when the row is gone
     */
    @Query(value = "SELECT iris_message_order FROM iris_message WHERE id = :messageId", nativeQuery = true)
    Optional<Integer> findListIndex(@Param("messageId") long messageId);

    /**
     * Close the hole a deleted row leaves in its session's list indices. A plain delete does not go through the
     * collection that owns {@code iris_message_order}, and the gap is not cosmetic: Hibernate materialises an ordered
     * collection by index, so the next load puts a {@code null} on the missing one and fails. The caller holds the
     * session write lock, so this cannot interleave with an append allocating the next index.
     *
     * @param sessionId    the session whose list is being compacted
     * @param removedIndex the index the deleted row occupied
     * @return number of rows shifted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = """
            UPDATE iris_message
            SET iris_message_order = iris_message_order - 1
            WHERE session_id = :sessionId
              AND iris_message_order > :removedIndex
            """, nativeQuery = true)
    int compactMessageOrderAfter(@Param("sessionId") long sessionId, @Param("removedIndex") int removedIndex);

    /**
     * Atomic guarded delete for stale-row suppression: proactive origin, null {@code proactiveOutcome} and the
     * user's own session, all in one statement, which is what removes the check-then-delete race. The ownership
     * guard is a subquery on the session table, so it is MySQL-1093 safe.
     *
     * @param messageId the id of the proactive message row to delete
     * @param userId    the requesting user; the row is only deleted if its session belongs to this user
     * @return number of rows deleted (1 = deleted; 0 = missing, wrong origin, terminal, or not this user's row)
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM IrisMessage m
            WHERE m.id = :messageId
              AND m.origin = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin.PROACTIVE_STRUGGLE
              AND m.proactiveOutcome IS NULL
              AND m.session.id IN (SELECT s.id FROM IrisSession s WHERE s.userId = :userId)
            """)
    int deleteSupersededProactiveMessage(@Param("messageId") long messageId, @Param("userId") long userId);
}
