package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
