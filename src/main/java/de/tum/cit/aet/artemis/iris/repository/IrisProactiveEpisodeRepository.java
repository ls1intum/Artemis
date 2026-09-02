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
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;

/**
 * Spring Data JPA repository for the {@link IrisProactiveEpisode} entity.
 */
@Conditional(IrisEnabled.class)
@Lazy
@Repository
public interface IrisProactiveEpisodeRepository extends ArtemisJpaRepository<IrisProactiveEpisode, Long> {

    /**
     * The episode, without locking it. For read-only checks that do not go on to write anything.
     *
     * @param userId     the student the episode belongs to
     * @param exerciseId the exercise the episode belongs to
     * @param episodeId  the client-allocated episode id
     * @return the episode, if one was registered for this triple
     */
    @Query("SELECT e FROM IrisProactiveEpisode e WHERE e.userId = :userId AND e.exerciseId = :exerciseId AND e.episodeId = :episodeId")
    Optional<IrisProactiveEpisode> find(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId);

    /**
     * The same lookup, taking a write lock on the episode row. This is the mutex the whole feature serializes on:
     * every path that decides something from the terminal state and then writes (the active append, the ambient
     * offer, the reveal, the confirm-close row, and the outcome write itself) takes this lock first and holds it
     * until its transaction commits, so no two of them can interleave between their check and their write.
     *
     * <p>
     * Deliberately no join fetch. A fetch join would make this an outer join, and PostgreSQL rejects
     * {@code FOR UPDATE} on the nullable side of one.
     *
     * @param userId     the student the episode belongs to
     * @param exerciseId the exercise the episode belongs to
     * @param episodeId  the client-allocated episode id
     * @return the locked episode, if one was registered for this triple
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM IrisProactiveEpisode e WHERE e.userId = :userId AND e.exerciseId = :exerciseId AND e.episodeId = :episodeId")
    Optional<IrisProactiveEpisode> findForUpdate(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId);

    /**
     * First-terminal-wins in one statement: sets the outcome only if the row does not already carry one. The guard
     * references only the target row, so it is portable and needs no same-table subquery.
     *
     * <p>
     * Callers hold {@link #findForUpdate} while calling this, so the guard is belt and braces rather than the
     * primary defence. It still matters for the paths that reach the episode without the lock.
     *
     * @param id      the episode row
     * @param outcome the terminal outcome to record
     * @return number of rows updated (1 = recorded, 0 = an outcome already stood)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE IrisProactiveEpisode e SET e.outcome = :outcome WHERE e.id = :id AND e.outcome IS NULL")
    int setOutcomeIfNull(@Param("id") long id, @Param("outcome") IrisProactiveOutcome outcome);

    /**
     * Retention for episodes that never reached a terminal outcome: a trigger whose callback never arrived leaves an
     * open row behind, and nothing else would ever remove it. Rows that carry an outcome are kept, since deleting
     * one would lose the terminal state that suppresses a late message.
     *
     * @param createdBefore rows older than this are removed
     * @return number of rows deleted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("DELETE FROM IrisProactiveEpisode e WHERE e.outcome IS NULL AND e.createdAt < :createdBefore")
    int deleteOpenEpisodesOlderThan(@Param("createdBefore") ZonedDateTime createdBefore);
}
