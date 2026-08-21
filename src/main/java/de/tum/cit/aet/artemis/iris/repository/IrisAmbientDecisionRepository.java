package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
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
import de.tum.cit.aet.artemis.iris.domain.message.IrisAmbientDecision;

/**
 * Spring Data JPA repository for the {@link IrisAmbientDecision} entity.
 */
@Conditional(IrisEnabled.class)
@Lazy
@Repository
public interface IrisAmbientDecisionRepository extends ArtemisJpaRepository<IrisAmbientDecision, Long> {

    /**
     * Find the ambient decision a reveal addresses. Scoped by user and exercise as well as episode, so a
     * replayed or guessed episode id can only ever reach the caller's own decision.
     *
     * @param userId     the revealing student
     * @param exerciseId the exercise the reveal targets
     * @param episodeId  the client-allocated episode id
     * @return the decision, if Artemis recorded one for this triple
     */
    Optional<IrisAmbientDecision> findByUserIdAndExerciseIdAndEpisodeId(long userId, long exerciseId, String episodeId);

    /**
     * Row-scoped single-use claim: marks the decision consumed ONLY IF it is still unconsumed. Mirrors
     * {@link IrisMessageRepository#setProactiveOutcomeIfNull}: the guard references only the target row, so it stays
     * portable across H2, MySQL and PostgreSQL.
     *
     * <p>
     * A return value of 1 means this caller won the claim and owns the reveal; 0 means another request consumed it
     * first, and the caller must return that request's message rather than inserting a second one.
     *
     * @param id         the decision to claim
     * @param consumedAt the claim timestamp
     * @param messageId  the message this reveal created
     * @return number of rows updated (1 = claimed, 0 = already consumed or gone)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE IrisAmbientDecision d
            SET d.consumedAt = :consumedAt, d.consumedMessageId = :messageId
            WHERE d.id = :id AND d.consumedAt IS NULL
            """)
    int claimIfUnconsumed(@Param("id") long id, @Param("consumedAt") ZonedDateTime consumedAt, @Param("messageId") Long messageId);

    /**
     * Delete decisions that were recorded before the given cut-off, so offers the student never revealed do not
     * accumulate. Consumed rows are removed by the same sweep: once claimed, the message row is the record that matters.
     *
     * @param cutoff decisions created before this instant are removed
     * @return number of rows deleted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("DELETE FROM IrisAmbientDecision d WHERE d.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") ZonedDateTime cutoff);
}
