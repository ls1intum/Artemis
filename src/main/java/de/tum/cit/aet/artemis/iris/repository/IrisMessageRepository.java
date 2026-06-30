package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
     * Counts the number of LLM responses the user got within the given timeframe.
     *
     * @param userId the id of the user
     * @param start  the start of the timeframe
     * @param end    the end of the timeframe
     * @return the number of chat messages sent by the user within the given timeframe
     */
    @Query("""
            SELECT COUNT(DISTINCT m)
            FROM IrisMessage m
                JOIN TREAT (m.session AS IrisChatSession) s
            WHERE s.userId = :userId
                AND m.sender = de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.LLM
                AND (m.origin IS NULL OR m.origin <> de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin.PROACTIVE_STRUGGLE)
                AND m.sentAt BETWEEN :start AND :end
            """)
    int countLlmResponsesOfUserWithinTimeframe(@Param("userId") long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    /**
     * Deterministic write-target finder: returns the earliest-persisted message tagged with the given episode id.
     * Used by A10 ({@code revealAmbient}) to locate the canonical row to promote.
     *
     * @param proactiveEpisodeId the client-allocated episode UUID
     * @return the earliest IrisMessage with that episode id, or empty if none persisted yet
     */
    Optional<IrisMessage> findFirstByProactiveEpisodeIdOrderBySentAtAsc(String proactiveEpisodeId);

    /**
     * Stable write-target finder for {@code writeEpisodeOutcome}: returns the FIRST-persisted row of the episode,
     * identified by the smallest id. Unlike ordering by {@code sentAt} (which is unstable - a delivery row that
     * persists late can carry an earlier {@code sentAt} but a larger id, shifting the "earliest-sentAt" target after a
     * concurrent insert), the smallest id is monotonic and therefore stable: a row inserted later always gets a larger
     * id, so it can never become the target. Two concurrent outcome writes thus pick the SAME target row, and the
     * row-scoped {@link #setProactiveOutcomeIfNull} guard makes first-terminal-wins atomic without a same-table
     * subquery or a pessimistic lock. The physical target row is immaterial to readers, since outcomes are read
     * episode-wide ({@link #findEpisodeOutcomes}).
     *
     * @param proactiveEpisodeId the client-allocated episode UUID
     * @return the smallest-id (first-persisted) IrisMessage with that episode id, or empty if none persisted yet
     */
    Optional<IrisMessage> findFirstByProactiveEpisodeIdOrderByIdAsc(String proactiveEpisodeId);

    /**
     * Episode-wide outcome read: returns ALL non-null {@code proactive_outcome} values across every row tagged with
     * the given episode id. By first-terminal-wins (A10), at most one such value exists. Reading across ALL episode
     * rows (not just the earliest) makes the result stable under out-of-order persistence: if the delivery row's
     * persist is still pending while a later row already persisted its outcome, this query still finds it.
     * The service helper {@link de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService#isEpisodeTerminal}
     * takes the first element.
     *
     * @param episodeId the client-allocated episode UUID
     * @return list of non-null outcomes for the episode (at most one element by design)
     */
    @Query("SELECT m.proactiveOutcome FROM IrisMessage m WHERE m.proactiveEpisodeId = :episodeId AND m.proactiveOutcome IS NOT NULL")
    List<IrisProactiveOutcome> findEpisodeOutcomes(@Param("episodeId") String episodeId);

    /**
     * Find a message by its client-assigned idempotency key.
     * Used by A10 {@code revealAmbient} to detect duplicate reveal inserts.
     *
     * @param proactiveClientMessageId the client-generated UUID key
     * @return the message row, or empty if none persisted with this key yet
     */
    Optional<IrisMessage> findByProactiveClientMessageId(String proactiveClientMessageId);

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
