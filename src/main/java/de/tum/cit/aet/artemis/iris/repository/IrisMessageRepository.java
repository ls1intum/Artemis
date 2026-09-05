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
     * Counts the number of final LLM responses the user got within the given timeframe.
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
                AND (m.origin IS NULL OR m.origin <> de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin.PROACTIVE_STRUGGLE)
                AND (m.intermediate IS NULL OR m.intermediate = FALSE)
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countFinalLlmResponsesOfUserWithinTimeframe(@Param("userId") long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    /**
     * Stable write-target finder for the pre-registry outcome write, SCOPED to the requesting user's own sessions.
     * Returns the episode's rows ordered by id ascending; the caller takes the first (smallest-id / first-persisted)
     * element as the target. JPQL has no {@code LIMIT}, so the ordered list is returned rather than a single row.
     * Unlike ordering by {@code sentAt} (which is unstable - a delivery row that persists late can carry an earlier
     * {@code sentAt} but a larger id, shifting the "earliest-sentAt" target after a concurrent insert), the smallest
     * id is monotonic and therefore stable: a row inserted later always gets a larger id, so it can never become the
     * target. Two concurrent outcome writes thus pick the SAME target row, and the row-scoped
     * {@link #setProactiveOutcomeIfNull} guard makes first-terminal-wins atomic without a same-table subquery or a
     * pessimistic lock. The physical target row is immaterial to readers, since outcomes are read episode-wide
     * ({@link #findEpisodeOutcomes}).
     * <p>
     * The user scope is a security guard: {@code episodeId} is a client-generated UUID, so an unscoped lookup would
     * let any student write an outcome onto another student's episode by guessing/replaying the id (IDOR). Scoping by
     * the owning session's {@code userId} closes that hole - a foreign episode id returns an empty list, never a
     * foreign row.
     * <p>
     * The exercise scope closes the remaining hole INSIDE one user: the same client-generated id can be sent for two
     * exercises, and without this predicate an outcome written for one of them would make the episode terminal for
     * the other. It matches on {@code proactiveExerciseId} rather than the session's {@code entityId} because a
     * session's mode/entityId change on every context switch, so only the row's own stamp is a durable binding.
     * The match is strict: a row that carries no exercise (written before this column existed) is never returned.
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
     * Episode-wide outcome read, SCOPED to the requesting user's own sessions: returns ALL non-null
     * {@code proactive_outcome} values across every row tagged with the given episode id that belongs to this user.
     * By first-terminal-wins (A10), at most one such value exists. Reading across ALL episode rows (not just the
     * earliest) makes the result stable under out-of-order persistence: if the delivery row's persist is still
     * pending while a later row already persisted its outcome, this query still finds it.
     * Callers that only need to know whether the episode is terminal check the result for emptiness; the one that
     * needs the value takes the first element.
     * <p>
     * The user scope is a security guard: an unscoped
     * read would let any student probe or read the outcome of another student's episode by guessing/replaying the
     * client-generated episode id (IDOR). Scoping by the owning session's {@code userId} closes that hole.
     * The exercise scope keeps one user's two exercises apart when the client reuses an episode id across them; see
     * {@link #findEpisodeRowsForUserOrderByIdAsc} for why the binding is read from the row and not from the session.
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
     * Only for the caller that has to classify a guarded outcome write which came back with zero affected rows. Under
     * REPEATABLE READ a plain read there can still miss the outcome the write just lost to, and the two answers lead
     * to opposite client behaviour: an outcome that stands means there is nothing left to back-fill, while a target
     * row that merely vanished (superseded-row suppression) leaves the episode open and the client has to write the
     * outcome again onto a later row. Guessing that from a stale snapshot silently drops a student's dismiss.
     *
     * <p>
     * Ordered by id so concurrent callers take the row locks in the same order. The user scope is a SUBQUERY on the
     * session table rather than the navigation {@code m.session.userId} that {@link #findEpisodeOutcomes} uses: that
     * navigation joins {@code iris_session} into the FROM list, and a {@code FOR UPDATE} over a join locks the joined
     * session row too on dialects that cannot restrict the lock to one table. This query has no business locking a
     * session, and the append path holds that very row's write lock.
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
     * Row-scoped first-write-wins update: sets {@code proactiveOutcome} on the target row ONLY IF that row currently
     * has a null outcome. The guard references only the target row (no same-table subquery), so it is portable across
     * H2, MySQL, and PostgreSQL (a {@code WHERE NOT EXISTS (SELECT ... FROM iris_message ...)} guard would trip MySQL
     * error 1093, "can't specify target table for update in FROM clause"). The episode-wide first-terminal-wins
     * decision is made by the caller via an episode-wide existence pre-check ({@link #findEpisodeOutcomes}); this
     * statement only guarantees that the chosen target row is written at most once.
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
     * Atomic guarded delete for stale-row suppression (A10 {@code deleteSupersededProactiveMessage}). Deletes the row
     * ONLY IF all three guards hold in one statement: it is {@code PROACTIVE_STRUGGLE} origin, it carries a null
     * {@code proactiveOutcome} (never delete a canonical outcome row), and it belongs to one of the given user's
     * sessions. Doing the guard + delete in a single statement removes the check-then-delete (TOCTOU) race: a
     * concurrent outcome write that lands between a load and a delete can no longer cause a terminal row to be deleted.
     * The user-ownership guard uses a subquery on the (different) session table, so it is MySQL-1093 safe.
     *
     * @param messageId the id of the proactive message row to delete
     * @param userId    the requesting user; the row is only deleted if its session belongs to this user
     * @return number of rows deleted (1 = deleted; 0 = missing, wrong origin, terminal, or not this user's row)
     */
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
     * {@link jakarta.persistence.OrderColumn} maintained by Hibernate from the owning side and therefore not a
     * mapped field this repository could select through JPQL.
     *
     * @param messageId the message to look up
     * @return the row's list index, or empty when the row is gone
     */
    @Query(value = "SELECT iris_message_order FROM iris_message WHERE id = :messageId", nativeQuery = true)
    Optional<Integer> findListIndex(@Param("messageId") long messageId);

    /**
     * Close the hole a deleted row leaves in its session's list indices by shifting every later row down one.
     *
     * <p>
     * Deleting a message with a plain statement does not go through the collection that owns
     * {@code iris_message_order}, so nothing renumbers the rows after it. The gap is not cosmetic: Hibernate
     * materialises an ordered collection by index, so the next load of the session puts a {@code null} where the
     * missing index is and the read fails on it, which is the same failure mode {@code IrisMessageService#saveMessage}
     * documents for an insert that bypasses the owner. The caller must hold the session's write lock, so this cannot
     * interleave with an append allocating the next index.
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
