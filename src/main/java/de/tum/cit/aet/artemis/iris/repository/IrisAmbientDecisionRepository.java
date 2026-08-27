package de.tum.cit.aet.artemis.iris.repository;

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
     * <p>
     * {@code createdAt} is deliberately left alone. It records when the offer was first made, and a later callback
     * refining the wording does not make it a new offer. Moving it would also let repeated callbacks postpone the
     * expiry of any age-based retention added later.
     *
     * @param userId     the student the hint is offered to
     * @param exerciseId the exercise the hint belongs to
     * @param episodeId  the client-allocated episode id
     * @param hintText   the newest hint as authored by Pyris
     * @return number of rows updated (1 = refreshed, 0 = no unconsumed offer for this triple)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE IrisAmbientDecision d
            SET d.hintText = :hintText
            WHERE d.userId = :userId AND d.exerciseId = :exerciseId AND d.episodeId = :episodeId
              AND d.consumedAt IS NULL
            """)
    int refreshIfUnconsumed(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId, @Param("hintText") String hintText);
}
