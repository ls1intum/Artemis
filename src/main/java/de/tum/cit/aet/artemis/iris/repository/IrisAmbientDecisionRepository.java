package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
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
import de.tum.cit.aet.artemis.iris.domain.message.IrisAmbientDecision;

/**
 * Spring Data JPA repository for the {@link IrisAmbientDecision} entity.
 */
@Conditional(IrisEnabled.class)
@Lazy
@Repository
public interface IrisAmbientDecisionRepository extends ArtemisJpaRepository<IrisAmbientDecision, Long> {

    /**
     * The same lookup, but taking a write lock on the decision row. Concurrent reveals of one decision serialize on
     * this lock, so the "is it still unconsumed" check and the claim that follows cannot interleave and let two
     * requests each insert a message for the same offer.
     *
     * @param userId     the revealing student
     * @param exerciseId the exercise the reveal targets
     * @param episodeId  the client-allocated episode id
     * @return the locked decision, if Artemis recorded one for this triple
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM IrisAmbientDecision d WHERE d.userId = :userId AND d.exerciseId = :exerciseId AND d.episodeId = :episodeId")
    Optional<IrisAmbientDecision> findForReveal(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId);

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
     * Refresh the hint of an offer that is still unconsumed, keyed by the natural key. Deliberately takes no
     * previously-loaded entity: the callback runs outside a transaction, so anything it read would be detached, and
     * saving a detached aggregate merges EVERY column. A reveal committing between that read and the save would be
     * overwritten, resetting {@code consumedAt} and {@code consumedMessageId} to NULL and making an already-revealed
     * offer revealable a second time.
     *
     * <p>
     * A return value of 0 means either that no offer exists for this episode yet, or that the student already
     * revealed the previous one. Both are normal, neither is an error: the caller falls through to an insert and
     * lets the unique constraint on (user, exercise, episode) decide.
     *
     * @param userId     the student the hint is offered to
     * @param exerciseId the exercise the hint belongs to
     * @param episodeId  the client-allocated episode id
     * @param hintText   the newest hint as authored by Pyris
     * @param now        the refresh timestamp
     * @return number of rows updated (1 = refreshed, 0 = no unconsumed offer for this triple)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE IrisAmbientDecision d
            SET d.hintText = :hintText, d.createdAt = :now
            WHERE d.userId = :userId AND d.exerciseId = :exerciseId AND d.episodeId = :episodeId
              AND d.consumedAt IS NULL
            """)
    int refreshIfUnconsumed(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId, @Param("hintText") String hintText,
            @Param("now") ZonedDateTime now);

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
