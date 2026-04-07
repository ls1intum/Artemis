package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
     * Uses {@code FOR UPDATE SKIP LOCKED} to prevent multiple Artemis nodes from claiming
     * the same retry-eligible job simultaneously. Each row is locked by the first node that
     * reads it; concurrent nodes silently skip already-locked rows.
     *
     * @param phase the processing phase to check (enum name as string, e.g. "FAILED")
     * @param now   the current time to compare against retryEligibleAt
     * @return list of states ready for retry, locked for the duration of the calling transaction
     */
    @Transactional
    @Query(value = """
            SELECT *
            FROM lecture_unit_processing_state
            WHERE phase = :phase
            AND retry_eligible_at IS NOT NULL
            AND retry_eligible_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findStatesReadyForRetry(@Param("phase") String phase, @Param("now") ZonedDateTime now);

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
     * Atomically claim IDLE jobs that are ready for dispatch.
     * <p>
     * Uses {@code FOR UPDATE SKIP LOCKED} to prevent double-dispatch in clustered Artemis:
     * if two scheduler instances race for the same row, one gets the lock and the other skips it.
     * <p>
     * Only returns jobs where:
     * <ul>
     * <li>{@code phase = 'IDLE'} — waiting in the queue</li>
     * <li>{@code started_at IS NULL} — not yet dispatched to Iris</li>
     * <li>{@code retry_eligible_at IS NULL OR retry_eligible_at <= now} — not in backoff period</li>
     * </ul>
     *
     * @param now   the current time for backoff comparison
     * @param limit maximum number of jobs to claim
     * @return list of IDLE states ready for dispatch, locked for this transaction
     */
    @Query(value = """
            SELECT * FROM lecture_unit_processing_state
            WHERE phase = 'IDLE'
            AND started_at IS NULL
            AND (retry_eligible_at IS NULL OR retry_eligible_at <= :now)
            ORDER BY id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<LectureUnitProcessingState> findIdleForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);
}
