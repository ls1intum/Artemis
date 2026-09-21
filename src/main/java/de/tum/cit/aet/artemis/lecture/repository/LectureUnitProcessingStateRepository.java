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
     * Atomically complete a run as DONE, but only while it still carries the expected token in an
     * in-flight phase: combines the terminal-callback claim with the terminal state write in one
     * statement, rather than clearing the token first and writing the rest in a later whole-entity
     * {@code save}.
     * <p>
     * That two-step version left two gaps a single statement closes. A crash between the clear and the
     * save stranded the row in an active phase with a null token, which {@link #failIfStillLive} could
     * never reclaim: its predicate compares {@code ingestionJobToken = :token}, and SQL equality against
     * NULL is never true, so a stuck-detection pass that read the null token as {@code tokenAtRead} could
     * not match it either. And a content-triggered requeue or a newer activation landing in that same
     * window (both of which need the token already cleared to proceed) could be overwritten by the stale
     * whole-entity save, which would blindly confirm the old fingerprint as current even though a newer
     * generation had already started.
     * <p>
     * Exactly one caller wins the update, so two concurrent callbacks for the same run (a success and a
     * failure racing each other) cannot both write a terminal state; a return value of 0 means either the
     * run already moved on or another callback already claimed it. Mirrors {@link
     * LectureUnitProcessingState#transitionTo} for the DONE phase, plus the token clear and fingerprint
     * confirmation applied on a successful {@code handleIngestionComplete}.
     *
     * @param id    the id of the processing state row
     * @param token the job token the callback carried
     * @param now   recorded as the new {@code startedAt} and {@code lastUpdated}
     * @return the number of updated rows: 1 if this call completed the run, 0 if it was no longer in flight under this token
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE, ps.startedAt = :now, ps.lastUpdated = :now,
                ps.errorKey = NULL, ps.retryEligibleAt = NULL, ps.revivalCount = 0,
                ps.currentStage = NULL, ps.stageStartedAt = NULL, ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL,
                ps.ingestionJobToken = NULL, ps.confirmedFingerprint = ps.contentFingerprint, ps.forceReingest = NULL
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int completeIngestionIfLive(@Param("id") long id, @Param("token") String token, @Param("now") ZonedDateTime now);

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
     *                  Fresh work (priority 0: user- and content-triggered) lists before backlog work (backfill and
     *                  reconcile requeues, priority > 0), so a large backfill can never starve a fresh upload.
     *
     * @return list of candidate IDLE states; a caller must claim one before dispatching it
     */
    @Query(value = """
            SELECT * FROM lecture_unit_processing_state
            WHERE phase = 'IDLE'
            AND started_at IS NULL
            AND (retry_eligible_at IS NULL OR retry_eligible_at <= :now)
            ORDER BY COALESCE(dispatch_priority, 0) ASC, id ASC
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

    /**
     * Invalidate an in-flight run's token, atomically, only while it still matches the token a
     * content-change detection pass just observed.
     * <p>
     * Called as the first step of content-triggered reprocessing, before the transcript and
     * attachment cleanup that follows it deletes stored content: see
     * {@code LectureContentProcessingService#handleContentChanges}. A checkpoint still holding this
     * token then fails its own token-match check immediately afterward, rather than succeeding on a
     * stale snapshot and persisting content the cleanup is about to invalidate, in the window before
     * the caller's own later full requeue (which resets this and other fields together) is itself
     * persisted.
     *
     * @param id            the processing state to invalidate
     * @param expectedToken the token last observed for this run
     * @param now           recorded as the new {@code lastUpdated}
     * @return 1 when invalidated, 0 when the token had already changed
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.ingestionJobToken = NULL, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :expectedToken
            """)
    int invalidateTokenIfMatches(@Param("id") long id, @Param("expectedToken") String expectedToken, @Param("now") ZonedDateTime now);

    /**
     * Apply a heartbeat's stage/progress fields, but only while the run is still in flight under the
     * token that reported them. A terminal callback (success or failure) clears the token before this
     * runs; matching on it here is what stops a heartbeat whose read raced ahead of that terminal write
     * from reviving a row the terminal callback already finished, since the predicate then matches no
     * row and the write is silently dropped instead of overwriting the DONE/FAILED state.
     *
     * @param id             the processing state to update
     * @param token          the job token the heartbeat carried
     * @param now            recorded as the new {@code lastUpdated}
     * @param currentStage   the stage name to store
     * @param stageStartedAt when the current stage began
     * @param stageProgress  the stage's progress counter, may be null
     * @param stageTotal     the stage's total work items, may be null
     * @param lastProgressAt when the progress clock was last advanced
     * @return 1 when applied, 0 when the run is no longer in flight under this token
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.lastUpdated = :now, ps.currentStage = :currentStage, ps.stageStartedAt = :stageStartedAt,
                ps.stageProgress = :stageProgress, ps.stageTotal = :stageTotal, ps.lastProgressAt = :lastProgressAt
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int applyHeartbeat(@Param("id") long id, @Param("token") String token, @Param("now") ZonedDateTime now, @Param("currentStage") String currentStage,
            @Param("stageStartedAt") ZonedDateTime stageStartedAt, @Param("stageProgress") Integer stageProgress, @Param("stageTotal") Integer stageTotal,
            @Param("lastProgressAt") ZonedDateTime lastProgressAt);

    /**
     * Refresh liveness only, for a raw (non-enriched) transcription checkpoint that reports no stage
     * progress of its own. Same token-and-phase guard as {@link #applyHeartbeat}, for the same reason.
     *
     * @param id    the processing state to update
     * @param token the job token the checkpoint carried
     * @param now   recorded as the new {@code lastUpdated}
     * @return 1 when applied, 0 when the run is no longer in flight under this token
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int touchLastUpdated(@Param("id") long id, @Param("token") String token, @Param("now") ZonedDateTime now);

    /**
     * Renew a run's worker lease atomically, for a heartbeat batch. Same token-and-phase guard as
     * {@link #applyHeartbeat}: a terminal callback that finishes the run in the window between the
     * heartbeat's read and this write clears the token first, so this predicate then matches no row and
     * the stale lease-renewal is silently dropped instead of overwriting the DONE/FAILED state back to
     * the in-flight phase and token it read.
     *
     * @param id           the processing state to update
     * @param token        the job token the heartbeat carried
     * @param now          recorded as the new {@code lastHeartbeatAt}
     * @param workerBootId boot id of the worker renewing the lease
     * @return 1 when applied, 0 when the run is no longer in flight under this token
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.lastHeartbeatAt = :now, ps.lockedBy = :workerBootId
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int renewLease(@Param("id") long id, @Param("token") String token, @Param("now") ZonedDateTime now, @Param("workerBootId") String workerBootId);

    /**
     * Transition TRANSCRIBING to INGESTING for an enriched transcription checkpoint, atomically: same
     * token-and-phase guard as {@link #applyHeartbeat}, and the same field set {@link
     * de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState#transitionTo} applies, so a
     * checkpoint racing a terminal callback cannot revive a run the terminal callback already finished.
     *
     * @param id    the processing state to update
     * @param token the job token the checkpoint carried
     * @param now   recorded as the new {@code startedAt} and {@code lastUpdated}
     * @return 1 when applied, 0 when the run is no longer in flight under this token
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING, ps.startedAt = :now, ps.lastUpdated = :now,
                ps.errorKey = NULL, ps.retryEligibleAt = NULL, ps.retryCount = 0,
                ps.currentStage = NULL, ps.stageStartedAt = NULL, ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING
            """)
    int transitionToIngestingIfTranscribing(@Param("id") long id, @Param("token") String token, @Param("now") ZonedDateTime now);

    /**
     * Reclaim one lapsed-lease run atomically: reset it to IDLE, but only while it is still exactly the
     * row the batch read found lapsed. A heartbeat can renew the lease, or a terminal callback can finish
     * the run, in the window between {@link #findRunsWithLapsedLease} and this write; matching on the
     * token and re-checking every field {@code stillLapsed} would have recomputed (phase, no pending retry,
     * heartbeat still older than the cutoff) is what stops that write from overwriting either one. Fields
     * mirror {@link de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState#requeue}.
     *
     * @param id     the processing state to reclaim
     * @param token  the job token observed at batch-read time
     * @param phases the in-flight phases eligible for reclaim
     * @param cutoff the lease cutoff: a heartbeat at or after this time cancels the reclaim
     * @param now    recorded as the new {@code lastUpdated}
     * @return 1 when reclaimed, 0 when the run is no longer lapsed under this token
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.ingestionJobToken = NULL, ps.startedAt = NULL,
                ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.lastUpdated = :now,
                ps.currentStage = NULL, ps.stageStartedAt = NULL, ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase IN :phases
            AND ps.retryEligibleAt IS NULL
            AND ps.lastHeartbeatAt IS NOT NULL
            AND ps.lastHeartbeatAt < :cutoff
            """)
    int reclaimLapsedLease(@Param("id") long id, @Param("token") String token, @Param("phases") List<ProcessingPhase> phases, @Param("cutoff") ZonedDateTime cutoff,
            @Param("now") ZonedDateTime now);

    /**
     * Requeue a stuck INGESTING run without charging its retry budget, atomically: only while it is still
     * exactly the run whose census evidence justified skipping the failure penalty, AND it still matches the
     * same no-callback-or-absolute-timeout predicate {@link #findStuckStates} used to find it. The census
     * lookup that precedes this call is a slow external round-trip; a terminal callback finishing (or
     * otherwise changing) this run in that window clears its token first, so the id/token guard alone already
     * catches that. But a heartbeat or status callback can also land in that window without touching phase or
     * token -- proving the run is not actually stuck -- and without re-checking the stuck predicate this write
     * would still wipe that live run back to IDLE and schedule a duplicate dispatch. Same field set as
     * {@link #reclaimLapsedLease}, since both put the run back to a fresh IDLE state.
     *
     * @param id                 the processing state to requeue
     * @param token              the job token observed when the census evidence was decided
     * @param cutoffTime         the same no-callback cutoff {@link #findStuckStates} used to find this candidate
     * @param absoluteCutoffTime the same absolute-timeout cutoff {@link #findStuckStates} used to find this candidate
     * @param now                recorded as the new {@code lastUpdated}
     * @return 1 when requeued, 0 when the run is no longer in flight under this token or is no longer stuck
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.ingestionJobToken = NULL, ps.startedAt = NULL,
                ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.lastUpdated = :now,
                ps.currentStage = NULL, ps.stageStartedAt = NULL, ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL
            WHERE ps.id = :id
            AND ps.ingestionJobToken = :token
            AND ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING
            AND (((ps.lastHeartbeatAt IS NULL OR ps.lastProgressAt IS NULL) AND ps.lastUpdated < :cutoffTime) OR ps.startedAt < :absoluteCutoffTime)
            AND ps.retryEligibleAt IS NULL
            """)
    int requeueStuckIngestionWithoutPenalty(@Param("id") long id, @Param("token") String token, @Param("cutoffTime") ZonedDateTime cutoffTime,
            @Param("absoluteCutoffTime") ZonedDateTime absoluteCutoffTime, @Param("now") ZonedDateTime now);

    /**
     * Fail a stalled or stuck run atomically, but only while it is still exactly the run that was judged
     * stalled/stuck: same id, phase, and job token observed at that decision. A terminal callback
     * finishing (or otherwise changing) this run in the window between that observation and this write
     * clears its token first, so this predicate then matches no row and the failure write is silently
     * dropped instead of overwriting whatever the callback wrote. Same guard shape as
     * {@link #requeueStuckIngestionWithoutPenalty}.
     * <p>
     * {@code expectedLastProgressAt}/{@code expectedLastUpdated} additionally pin the liveness signal
     * that justified the failure, for callers where phase and token alone are not enough: a heartbeat
     * advances one of these fields without touching phase or token, so a heartbeat landing between the
     * caller's re-fetch and this write would otherwise let a run that just became live again still be
     * failed. Pass {@code null} for whichever (or both) the caller does not need to pin — the plain
     * {@code IS NULL OR =} guard then imposes no constraint on that field, exactly as before this was
     * added, which is what the ordinary terminal-callback failure path (no liveness signal to pin) relies
     * on.
     *
     * @param id                     the processing state to fail
     * @param phase                  the phase observed when the run was judged stalled/stuck
     * @param token                  the job token observed at the same time
     * @param expectedLastProgressAt the stall detector's observed {@code lastProgressAt}, or {@code null} not to pin it
     * @param expectedLastUpdated    the stuck detector's observed {@code lastUpdated}, or {@code null} not to pin it
     * @param retryCount             the new retry count to persist
     * @param errorKey               the i18n error key to persist
     * @param retryEligibleAt        when the retry becomes eligible, or {@code null} for a permanent failure
     * @param now                    recorded as the new {@code lastUpdated}
     * @return 1 when the failure was applied, 0 when the run is no longer the one that was judged stalled/stuck
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED, ps.errorKey = :errorKey,
                ps.ingestionJobToken = NULL, ps.startedAt = NULL, ps.retryCount = :retryCount,
                ps.retryEligibleAt = :retryEligibleAt, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.phase = :phase
            AND ps.ingestionJobToken = :token
            AND (:expectedLastProgressAt IS NULL OR ps.lastProgressAt = :expectedLastProgressAt)
            AND (:expectedLastUpdated IS NULL OR ps.lastUpdated = :expectedLastUpdated)
            """)
    int failIfStillLive(@Param("id") long id, @Param("phase") ProcessingPhase phase, @Param("token") String token,
            @Param("expectedLastProgressAt") ZonedDateTime expectedLastProgressAt, @Param("expectedLastUpdated") ZonedDateTime expectedLastUpdated,
            @Param("retryCount") int retryCount, @Param("errorKey") String errorKey, @Param("retryEligibleAt") ZonedDateTime retryEligibleAt, @Param("now") ZonedDateTime now);

    /**
     * Activate a claim exactly once: turn a claimed row into an in-flight run, but only while it still holds exactly
     * the claim that produced the activation. A claimed IDLE row carries {@code startedAt}; a claimed FAILED retry
     * carries its lease in {@code retryEligibleAt}. Neither has a job token yet. Matching {@code claimedAt} against
     * that exact marker (not just its presence) is what stops a late activation from a lapsed, re-claimed claim from
     * activating a newer, still-unactivated claim for the same unit with the wrong job token: two different claims
     * can pass through this same generic shape one after another, so presence alone cannot tell them apart. Same
     * guard as {@link #markSkippedIfStillClaimed}. Applies {@link LectureUnitProcessingState#transitionTo}, the
     * token, the fingerprint and {@link LectureUnitProcessingState#renewLease} in one statement.
     *
     * @param lectureUnitId      the claimed unit
     * @param phase              the in-flight phase to enter
     * @param token              the registered Pyris job token
     * @param contentFingerprint the fingerprint computed at claim time
     * @param workerBootId       boot id of the worker that owns the lease
     * @param claimedAt          the claim marker observed at claim time, from
     *                               {@link de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO#claimedAt()}
     * @param now                the activation time, recorded as start, last update and first heartbeat
     * @return 1 when the claim was activated, 0 when the row no longer holds this exact claim
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = :phase, ps.startedAt = :now, ps.lastUpdated = :now, ps.errorKey = NULL, ps.retryEligibleAt = NULL,
                ps.currentStage = NULL, ps.stageStartedAt = NULL, ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL,
                ps.ingestionJobToken = :token, ps.contentFingerprint = :contentFingerprint, ps.lastHeartbeatAt = :now, ps.lockedBy = :workerBootId
            WHERE ps.lectureUnit.id = :lectureUnitId
            AND ps.ingestionJobToken IS NULL
            AND ((ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE AND ps.startedAt = :claimedAt)
                OR (ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED AND ps.retryEligibleAt = :claimedAt))
            """)
    int activateClaimedJob(@Param("lectureUnitId") long lectureUnitId, @Param("phase") ProcessingPhase phase, @Param("token") String token,
            @Param("contentFingerprint") String contentFingerprint, @Param("workerBootId") String workerBootId, @Param("claimedAt") ZonedDateTime claimedAt,
            @Param("now") ZonedDateTime now);

    /**
     * Mark a claimed unit SKIPPED, but only while it still holds exactly the claim that decided it was not
     * processable: same claim-shape check as {@link #activateClaimedJob}, plus the specific claim marker
     * ({@code claimedAt}, echoed back from {@link de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO})
     * so that two different claims passing through the same generic unactivated shape one after another are
     * not conflated the way a shape-only check would allow. Without this, a superseded claim's stale result
     * could cancel a newer claim that has since been legitimately activated.
     *
     * @param lectureUnitId the claimed unit
     * @param claimedAt     the claim marker observed at claim time (startedAt for an IDLE claim, retryEligibleAt
     *                          for a retry claim)
     * @param now           recorded as the new {@code lastUpdated}
     * @return 1 when marked SKIPPED, 0 when the row no longer holds this exact claim
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.SKIPPED, ps.startedAt = NULL, ps.retryEligibleAt = NULL, ps.lastUpdated = :now
            WHERE ps.lectureUnit.id = :lectureUnitId
            AND ps.ingestionJobToken IS NULL
            AND ((ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE AND ps.startedAt = :claimedAt)
                OR (ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED AND ps.retryEligibleAt = :claimedAt))
            """)
    int markSkippedIfStillClaimed(@Param("lectureUnitId") long lectureUnitId, @Param("claimedAt") ZonedDateTime claimedAt, @Param("now") ZonedDateTime now);

}
