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
public interface IrisProactiveEpisodeRepository extends ArtemisJpaRepository<IrisProactiveEpisode, Long>, IrisProactiveEpisodeWriteRepository {

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
     * every path that decides from the terminal state and then writes takes this lock first and holds it until its
     * transaction commits. Deliberately no join fetch, because that would make this an outer join and PostgreSQL
     * rejects {@code FOR UPDATE} on the nullable side of one.
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
     * Refresh {@code last_triggered_at} on an existing episode, the first half of the registration upsert. No
     * {@code outcome} predicate, because touching an ended episode is harmless and a predicate would make a zero
     * result mean two things. Zero affected rows means "attempt the insert", not "provably absent": some databases
     * report changed rather than matched rows, and the insert's duplicate-key recovery is what makes that safe.
     *
     * @param userId      the student the episode belongs to
     * @param exerciseId  the exercise the episode belongs to
     * @param episodeId   the client-allocated episode id
     * @param triggeredAt the moment of this trigger
     * @return number of rows updated (1 = the episode existed and was touched, 0 = attempt the insert)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE IrisProactiveEpisode e SET e.lastTriggeredAt = :triggeredAt WHERE e.userId = :userId AND e.exerciseId = :exerciseId AND e.episodeId = :episodeId")
    int touchLastTriggeredAt(@Param("userId") long userId, @Param("exerciseId") long exerciseId, @Param("episodeId") String episodeId,
            @Param("triggeredAt") ZonedDateTime triggeredAt);

    /**
     * First-terminal-wins in one statement: sets the outcome only if the row does not already carry one. Callers
     * hold {@link #findForUpdate} while calling this, so the guard matters for the paths that reach the episode
     * without the lock. Clears the offered hint in the same statement, because an episode that ends without being
     * revealed keeps no reader for that text.
     *
     * @param id      the episode row
     * @param outcome the terminal outcome to record
     * @return number of rows updated (1 = recorded, 0 = an outcome already stood)
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE IrisProactiveEpisode e SET e.outcome = :outcome, e.hintText = NULL WHERE e.id = :id AND e.outcome IS NULL")
    int setOutcomeIfNull(@Param("id") long id, @Param("outcome") IrisProactiveOutcome outcome);

    /**
     * Deletes the proactive episodes of a course's own exercises, for the student-data reset. The reset preserves the
     * exercises, so the {@code exercise} foreign key never fires and these rows would otherwise outlive the student
     * data they carry. Scoped to exercises that hold the course directly; an exercise reaching a course indirectly is
     * deliberately out of scope.
     *
     * @param courseId the course whose student data is being reset
     * @return number of rows deleted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            DELETE FROM IrisProactiveEpisode e
            WHERE e.exerciseId IN (SELECT ex.id FROM Exercise ex WHERE ex.course.id = :courseId)
            """)
    int deleteAllByCourseId(@Param("courseId") long courseId);

    /**
     * Retention for episodes that went quiet: a trigger whose callback never arrived leaves an open row behind that
     * nothing on a request path would remove. Rows carrying an {@code outcome} are kept, because deleting one loses
     * the terminal state that suppresses a late message, and rows carrying a consumed offer are kept, because
     * {@code consumed_message_id} is what makes a repeated reveal return the first reveal message. Both go with the
     * course's student-data reset ({@link #deleteAllByCourseId}). The cutoff reads {@code last_triggered_at}, which
     * every trigger refreshes, so an episode still in use is never reaped out from under a run in flight.
     *
     * @param triggeredBefore rows last triggered before this are removed
     * @return number of rows deleted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("DELETE FROM IrisProactiveEpisode e WHERE e.outcome IS NULL AND e.consumedAt IS NULL AND e.lastTriggeredAt < :triggeredBefore")
    int deleteAbandonedEpisodesLastTriggeredBefore(@Param("triggeredBefore") ZonedDateTime triggeredBefore);
}
