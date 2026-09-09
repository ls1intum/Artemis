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
     * Refresh {@code last_triggered_at} on an existing episode, keyed on the natural key. This is the first half of
     * the registration upsert: it replaces a read followed by a write, which could interleave with the retention
     * delete and then update a row that no longer exists.
     *
     * <p>
     * Deliberately no {@code outcome} predicate. Touching an episode that already ended is harmless, because rows
     * carrying an outcome are never reaped, and a predicate here would make a zero result mean two different things.
     *
     * <p>
     * Zero affected rows means "attempt the insert", not "provably absent": some databases report changed rather
     * than matched rows, so a refresh landing on the same timestamp can report zero. The insert's duplicate-key
     * recovery is what makes that safe.
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
     * First-terminal-wins in one statement: sets the outcome only if the row does not already carry one. The guard
     * references only the target row, so it is portable and needs no same-table subquery.
     *
     * <p>
     * Callers hold {@link #findForUpdate} while calling this, so the guard is belt and braces rather than the
     * primary defence. It still matters for the paths that reach the episode without the lock.
     *
     * <p>
     * Clears the offered hint in the same statement. An episode that ends without ever being revealed keeps no
     * reader for that text: the reveal refuses a terminal episode outright, so the column would only carry the
     * largest payload of a row that survives the episode. Nulling it here covers the ending-before-reveal case the
     * way {@code consumeOfferInCurrentTransaction} covers the revealed one.
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
     * Deletes the proactive episodes of a course's own exercises, for the student-data reset.
     *
     * <p>
     * The reset preserves the course's exercises, so the {@code exercise} foreign key never fires and these rows
     * would otherwise outlive the student data they belong to: an episode carries {@code user_id} and the shape of
     * one student's struggle. Joining through {@code exercise} rather than storing a course id keeps the row narrow
     * and the binding single-sourced.
     *
     * <p>
     * Scoped to exercises that hold the course directly. An exercise can also reach a course indirectly, and such an
     * exercise's episodes are deliberately out of scope here.
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
     * Retention for episodes that went quiet: a trigger whose callback never arrived leaves an open row behind, and
     * nothing on a request path would ever remove it. Two kinds of row are kept:
     *
     * <ul>
     * <li>rows carrying an {@code outcome}, since deleting one would lose the terminal state that suppresses a late
     * message;</li>
     * <li>rows carrying a consumed offer, since {@code consumed_message_id} is what makes a repeated reveal return
     * the first reveal message instead of writing a second one, and the row itself is what stops a spent offer from
     * being revealed again.</li>
     * </ul>
     *
     * <p>
     * The cutoff is measured from {@code last_triggered_at}, which every trigger refreshes, so an episode that is
     * still in use is never reaped out from under a run in flight.
     *
     * <p>
     * Neither kind is kept indefinitely. Both go with the course's student-data reset (see
     * {@link #deleteAllByCourseId}, whose own scope note applies), which is the horizon Artemis already sets for
     * student data; there is no episode-specific expiry on top of it. What a retained row costs is bounded in the meantime: the offered hint
     * is cleared as soon as the episode is revealed or ends, so a kept row is the identifiers plus its terminal
     * state, never the hint text.
     *
     * @param triggeredBefore rows last triggered before this are removed
     * @return number of rows deleted
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("DELETE FROM IrisProactiveEpisode e WHERE e.outcome IS NULL AND e.consumedAt IS NULL AND e.lastTriggeredAt < :triggeredBefore")
    int deleteAbandonedEpisodesLastTriggeredBefore(@Param("triggeredBefore") ZonedDateTime triggeredBefore);
}
