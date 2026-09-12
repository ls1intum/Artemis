package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;

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
     * Find processing states that are stuck (no callback received recently) or past the absolute deadline.
     * Uses {@code lastUpdated} instead of {@code startedAt} so that heartbeat callbacks
     * from Iris keep resetting the clock — a healthy job is never considered stuck.
     * <p>
     * A leased run ({@code lastHeartbeatAt} set) that has reported a stage ({@code lastProgressAt} set) is
     * excluded from the no-callback arm: its liveness is judged by the stall detector and the much tighter
     * lease expiry in {@link #findRunsWithLapsedLease}. A leased run that has NOT reported a stage — the
     * whole transcription phase, which sends no stage name — stays in the no-callback arm, because
     * {@code lastUpdated} (bumped by every raw transcription checkpoint) is its only liveness signal, just
     * as in push mode; otherwise a silently wedged transcription would hide behind its fresh lease until
     * the absolute deadline. The absolute deadline on {@code startedAt} is the backstop for jobs that keep
     * sending heartbeats without ever terminating: no single ingestion run may exceed it.
     * <p>
     * Only finds states that are NOT already scheduled for retry (retryEligibleAt IS NULL).
     * This prevents stuck detection from interfering with states waiting for their backoff period.
     *
     * @param phases             the phases to check
     * @param cutoffTime         the time before which states are considered stuck (no callback since)
     * @param absoluteCutoffTime the time before which a started job is considered stuck regardless of heartbeats
     * @return list of stuck processing states
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            AND (((ps.lastHeartbeatAt IS NULL OR ps.lastProgressAt IS NULL) AND ps.lastUpdated < :cutoffTime) OR ps.startedAt < :absoluteCutoffTime)
            AND ps.retryEligibleAt IS NULL
            """)
    List<LectureUnitProcessingState> findStuckStates(@Param("phases") List<ProcessingPhase> phases, @Param("cutoffTime") ZonedDateTime cutoffTime,
            @Param("absoluteCutoffTime") ZonedDateTime absoluteCutoffTime);

    /**
     * Find in-flight runs whose worker lease has lapsed: the run was claimed by a Pyris worker
     * (proven by at least one recorded heartbeat) and that worker has not renewed the lease since
     * the cutoff. A lapsed lease is a strong infrastructure signal — the fixed-interval heartbeat is
     * a timer, not the pipeline's work, so its silence means the worker process is gone, not that a
     * stage is slow. Recovery therefore preserves the retry budget, unlike {@link #findStuckStates}.
     * <p>
     * Runs without any recorded heartbeat (legacy push dispatch, or an Iris without worker support)
     * never match here and stay under the timeout-based stuck detection.
     *
     * @param phases      the in-flight phases to check
     * @param leaseCutoff the time before which an unrenewed lease counts as lapsed
     * @return runs whose lease has lapsed
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            AND ps.lastHeartbeatAt IS NOT NULL
            AND ps.lastHeartbeatAt < :leaseCutoff
            AND ps.retryEligibleAt IS NULL
            """)
    List<LectureUnitProcessingState> findRunsWithLapsedLease(@Param("phases") List<ProcessingPhase> phases, @Param("leaseCutoff") ZonedDateTime leaseCutoff);

    /**
     * Find the processing state currently carrying the given ingestion job token. Backs worker lease
     * renewal: each heartbeat lists the tokens of the runs the worker is executing.
     *
     * @param token the ingestion job token
     * @return the state currently associated with this token, if any
     */
    Optional<LectureUnitProcessingState> findByIngestionJobToken(String token);

    /**
     * Resolve the identity of the ingestion job currently associated with the given token.
     * <p>
     * Backs the database fallback for authenticating Iris ingestion callbacks: the distributed job map
     * entry expires after a TTL, but the token stays valid in the processing state row for as long as
     * the job is in flight, so a late terminal callback is never rejected for a job Artemis still tracks.
     *
     * @param token the ingestion job token from the callback's Authorization header
     * @return the job identity if a processing state currently carries this token
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO(l.course.id, l.id, lu.id)
            FROM LectureUnitProcessingState ps
            JOIN ps.lectureUnit lu
            JOIN lu.lecture l
            WHERE ps.ingestionJobToken = :token
            """)
    Optional<IngestionJobIdentityDTO> findIngestionJobIdentityByToken(@Param("token") String token);

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
     * Atomically clear the ingestion job token of a state, but only when it still carries the expected token.
     * <p>
     * This is the claim step for terminal callbacks: exactly one caller wins the update, so two concurrent
     * callbacks for the same run (for example a success and a failure racing each other) cannot both write
     * a terminal state. A return value of 0 means another callback already claimed the token.
     *
     * @param id    the id of the processing state row
     * @param token the job token the callback carried
     * @return the number of updated rows: 1 if this call claimed the token, 0 if it was already claimed or changed
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.ingestionJobToken = NULL
            WHERE ps.id = :id AND ps.ingestionJobToken = :token
            """)
    int clearIngestionJobTokenIfMatches(@Param("id") long id, @Param("token") String token);

    /**
     * Find all processing states for a course with their lecture units fetched.
     * Used by the ingestion reconciler, which touches every unit of the course and
     * would otherwise lazy-load them one by one.
     *
     * @param courseId the ID of the course
     * @return list of processing states with initialized lecture units
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            JOIN FETCH ps.lectureUnit lu
            JOIN lu.lecture l
            WHERE l.course.id = :courseId
            """)
    List<LectureUnitProcessingState> findWithLectureUnitByCourseId(@Param("courseId") long courseId);

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
     * Select claimable IDLE jobs of the fresh priority class (user- and content-triggered work).
     * Only used inside {@link #claimJobsForDispatch}; {@code FOR UPDATE SKIP LOCKED} prevents two
     * nodes from claiming the same row while the claim transaction runs.
     *
     * @param now   the current time for backoff comparison
     * @param limit maximum number of jobs to select
     * @return fresh IDLE states ready for dispatch, locked for the claiming transaction
     */
    @Query(value = """
            SELECT * FROM lecture_unit_processing_state
            WHERE phase = 'IDLE'
            AND started_at IS NULL
            AND (retry_eligible_at IS NULL OR retry_eligible_at <= :now)
            AND COALESCE(dispatch_priority, 0) = 0
            ORDER BY id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findFreshIdleForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);

    /**
     * Select claimable IDLE jobs of the backlog priority classes (backfill and reconcile requeues).
     * Only used inside {@link #claimJobsForDispatch}.
     *
     * @param now   the current time for backoff comparison
     * @param limit maximum number of jobs to select
     * @return backlog IDLE states ready for dispatch, locked for the claiming transaction
     */
    @Query(value = """
            SELECT * FROM lecture_unit_processing_state
            WHERE phase = 'IDLE'
            AND started_at IS NULL
            AND (retry_eligible_at IS NULL OR retry_eligible_at <= :now)
            AND COALESCE(dispatch_priority, 0) > 0
            ORDER BY COALESCE(dispatch_priority, 0) ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findBacklogIdleForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);

    /**
     * Atomically claim jobs for dispatch: fresh work first, expired retries second, backlog last.
     * <p>
     * Claiming marks {@code startedAt} (and moves retry rows back to IDLE) inside ONE committed
     * transaction, so by the time the dispatcher sends the first HTTP request to Pyris the claim is
     * durable: a crash mid-dispatch can never lead a re-dispatching node to spawn a duplicate
     * pipeline, and no row locks are held across external calls. A claim whose send never happened
     * (crash, node death) is released by {@link #releaseExpiredDispatchClaims}.
     *
     * @param now   the current time
     * @param limit maximum number of jobs to claim (free capacity slots)
     * @return the claimed states, in dispatch order
     */
    @Transactional // ok: the claim must commit before dispatch sends HTTP requests
    default List<LectureUnitProcessingState> claimJobsForDispatch(ZonedDateTime now, int limit) {
        ArrayList<LectureUnitProcessingState> claimed = new ArrayList<>(findFreshIdleForDispatch(now, limit));
        int remaining = limit - claimed.size();
        if (remaining > 0) {
            List<LectureUnitProcessingState> retries = findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, remaining);
            for (LectureUnitProcessingState retry : retries) {
                // Back to IDLE while claimed: an abandoned claim is then released by expiry
                // instead of being misread as a terminal failure.
                retry.clearRetryEligibility();
                retry.setPhase(ProcessingPhase.IDLE);
            }
            claimed.addAll(retries);
            remaining = limit - claimed.size();
        }
        if (remaining > 0) {
            claimed.addAll(findBacklogIdleForDispatch(now, remaining));
        }
        for (LectureUnitProcessingState state : claimed) {
            state.setStartedAt(now);
        }
        saveAll(claimed);
        return claimed;
    }

    /**
     * Release dispatch claims whose send never completed: IDLE rows whose {@code startedAt} was set by
     * a claim but never advanced to an in-flight phase within the given cutoff. Clearing
     * {@code startedAt} puts them back into the claimable queue.
     *
     * @param cutoff claims older than this are considered abandoned
     * @return the number of released claims
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.startedAt = NULL
            WHERE ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE
            AND ps.startedAt IS NOT NULL
            AND ps.startedAt < :cutoff
            """)
    int releaseExpiredDispatchClaims(@Param("cutoff") ZonedDateTime cutoff);
}
