package de.tum.cit.aet.artemis.lecture.repository;

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
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * Spring Data JPA repository for the LectureUnitProcessingState entity.
 * Tracks the automated processing state of lecture units through transcription and ingestion.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitProcessingStateRepository extends ArtemisJpaRepository<LectureUnitProcessingState, Long> {

    /**
     * Find the processing state for a specific lecture unit.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @return the processing state if it exists
     */
    Optional<LectureUnitProcessingState> findByLectureUnit_Id(Long lectureUnitId);

    /**
     * Find processing states that are stuck (no callback received recently).
     * Uses {@code lastUpdated} instead of {@code startedAt} so that heartbeat callbacks
     * from Iris keep resetting the clock — a healthy job is never considered stuck.
     * <p>
     * Only finds states that are NOT already scheduled for retry (retryEligibleAt IS NULL).
     * This prevents stuck detection from interfering with states waiting for their backoff period.
     *
     * @param phases     the phases to check
     * @param cutoffTime the time before which states are considered stuck (no callback since)
     * @return list of stuck processing states
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            AND ps.lastUpdated < :cutoffTime
            AND ps.retryEligibleAt IS NULL
            """)
    List<LectureUnitProcessingState> findStuckStates(@Param("phases") List<ProcessingPhase> phases, @Param("cutoffTime") ZonedDateTime cutoffTime);

    /**
     * Find processing states that are ready for retry (backoff period has passed).
     * <p>
     * Only finds states where:
     * - retryEligibleAt is not null (explicitly scheduled for retry)
     * - retryEligibleAt has passed (backoff period complete)
     * <p>
     * This query is mutually exclusive with findStuckStates (which requires retryEligibleAt IS NULL).
     * <p>
     * This is a plain read that takes no row locks: it lists candidates, and the caller then competes for each one
     * through {@link #claimRetryEligible}. Locking here would only be meaningful while a transaction spans the read
     * and the later write, and declaring that boundary in a service is not allowed.
     *
     * @param phase the processing phase to check (enum name as string, e.g. "FAILED")
     * @param now   the current time to compare against retryEligibleAt
     * @param limit maximum number of rows to return
     * @return list of candidate states ready for retry; a caller must claim one before acting on it
     */
    @Query(value = """
            SELECT *
            FROM lecture_unit_processing_state
            WHERE phase = :phase
            AND retry_eligible_at IS NOT NULL
            AND retry_eligible_at <= :now
            ORDER BY retry_eligible_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findStatesReadyForRetry(@Param("phase") String phase, @Param("now") ZonedDateTime now, @Param("limit") int limit);

    /**
     * Find all processing states for a course.
     *
     * @param courseId the ID of the course
     * @return list of processing states for all units in the course
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            JOIN ps.lectureUnit lu
            JOIN lu.lecture l
            WHERE l.course.id = :courseId
            """)
    List<LectureUnitProcessingState> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find all processing states for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return list of processing states for all units in the lecture
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            JOIN FETCH ps.lectureUnit lu
            WHERE lu.lecture.id = :lectureId
            """)
    List<LectureUnitProcessingState> findByLectureId(@Param("lectureId") Long lectureId);

    /**
     * Find all processing states currently in active processing phases.
     * Used by Iris reset to mark all in-flight jobs as failed regardless of
     * retry state or last-updated time.
     *
     * @param phases the active phases to find (e.g. TRANSCRIBING, INGESTING)
     * @return all states currently in the given phases
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            """)
    List<LectureUnitProcessingState> findByPhaseIn(@Param("phases") List<ProcessingPhase> phases);

    /**
     * Count processing states currently in active processing phases (TRANSCRIBING or INGESTING).
     * Used to limit the number of concurrent processing jobs.
     *
     * @param phases the phases to count
     * @return count of states in the given phases
     */
    @Query("""
            SELECT COUNT(ps) FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            """)
    long countByPhaseIn(@Param("phases") List<ProcessingPhase> phases);

    /**
     * List IDLE jobs that are ready for dispatch.
     * <p>
     * A plain read that takes no row locks; the caller competes for each candidate through
     * {@link #claimIdleForDispatch}, which is what actually prevents double-dispatch in a cluster.
     * <p>
     * Only returns jobs where:
     * <ul>
     * <li>{@code phase = 'IDLE'} — waiting in the queue</li>
     * <li>{@code started_at IS NULL} — not yet dispatched to Iris</li>
     * <li>{@code retry_eligible_at IS NULL OR retry_eligible_at <= now} — not in backoff period</li>
     * </ul>
     *
     * @param now   the current time for backoff comparison
     * @param limit maximum number of candidates to list
     * @return list of candidate IDLE states; a caller must claim one before dispatching it
     */
    @Query(value = """
            SELECT * FROM lecture_unit_processing_state
            WHERE phase = 'IDLE'
            AND started_at IS NULL
            AND (retry_eligible_at IS NULL OR retry_eligible_at <= :now)
            ORDER BY id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findIdleForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);

    /**
     * Claim one IDLE job for dispatch, so that exactly one node acts on it.
     * <p>
     * The claim is the {@code started_at} write itself: the predicate requires it to still be null, and
     * {@link #findIdleForDispatch} only lists rows where it is null, so the winner's update immediately removes the row
     * from every other node's candidate list. A single conditional statement replaces the previous
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} followed by a much later save, which only excluded other nodes while a
     * transaction spanned both — a boundary that had to be declared in a service, and which was silently absent
     * whenever the dispatch was reached by a self-invoking call.
     *
     * @param id  the id of the state to claim
     * @param now the timestamp to record as the dispatch start
     * @return 1 if this caller claimed the job, 0 if another caller already had it
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.startedAt = :now, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE
            AND ps.startedAt IS NULL
            """)
    int claimIdleForDispatch(@Param("id") long id, @Param("now") ZonedDateTime now);

    /**
     * Claim one retry-eligible job, so that exactly one node retries it.
     * <p>
     * The claim is a lease rather than a clear: it pushes {@code retryEligibleAt} into the future, and
     * {@link #findStatesReadyForRetry} only lists rows whose value has passed, so the row is invisible to every other
     * caller for the length of the lease. Two callers racing on the same row both pass the {@code <= :now} guard, but
     * the update is one statement, so the second one matches nothing and is told it lost.
     * <p>
     * Leasing rather than clearing is what makes an abandoned claim recover itself. A node killed between the claim
     * and the phase write leaves the row exactly as the lease left it, and once the lease expires the row is eligible
     * again with no recovery query involved. That matters because a cleared {@code retryEligibleAt} is
     * indistinguishable from the deliberate representation of a permanently failed unit — {@code handleProcessingFailure}
     * leaves a YOUTUBE_PRIVATE failure FAILED with no scheduled retry and attempts still on the clock — so any
     * sweep that resurrected such rows would keep sending private videos back to Iris. With a lease there is
     * nothing to sweep: a permanent failure is null forever and is never eligible.
     * <p>
     * A successful dispatch clears the lease on its own, because the phase transition out of FAILED sets
     * {@code retryEligibleAt} to null.
     *
     * @param id          the id of the state to claim
     * @param now         the current time, which the backoff must already have passed
     * @param leaseExpiry when the claim lapses and the row becomes eligible again
     * @return 1 if this caller claimed the retry, 0 if another caller already had it
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.retryEligibleAt = :leaseExpiry, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.retryEligibleAt IS NOT NULL
            AND ps.retryEligibleAt <= :now
            """)
    int claimRetryEligible(@Param("id") long id, @Param("now") ZonedDateTime now, @Param("leaseExpiry") ZonedDateTime leaseExpiry);

    /**
     * Release IDLE claims whose owner never got as far as dispatching them.
     * <p>
     * {@link #claimIdleForDispatch} commits {@code startedAt} before the dispatch itself commits a phase, so a node
     * killed in between — a rolling deploy landing during the call to Iris — leaves a row that is IDLE with
     * {@code startedAt} set. No query selects that: {@link #findIdleForDispatch} wants a null {@code startedAt},
     * {@link #findStuckStates} is only asked about the active phases, and {@link #findStatesReadyForRetry} wants
     * FAILED. The unit would wait forever. Clearing the timestamp puts it back in the queue.
     * <p>
     * The previous {@code SELECT ... FOR UPDATE SKIP LOCKED} could not produce this state, because the claim was not
     * visible to anyone until the dispatch committed alongside it. Trading that for a released row is the cost of
     * keeping the boundary out of the service, and this is what pays it.
     * <p>
     * {@link #claimRetryEligible} needs no counterpart to this, because it leases rather than clears and therefore
     * recovers on its own.
     *
     * @param cutoffTime rows claimed before this are considered abandoned
     * @param now        the timestamp to record as the last update
     * @return how many claims were released
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.startedAt = NULL, ps.lastUpdated = :now
            WHERE ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE
            AND ps.startedAt IS NOT NULL
            AND ps.startedAt < :cutoffTime
            """)
    int releaseAbandonedIdleClaims(@Param("cutoffTime") ZonedDateTime cutoffTime, @Param("now") ZonedDateTime now);

}
