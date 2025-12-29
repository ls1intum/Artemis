package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * Spring Data JPA repository for the LectureUnitProcessingState entity.
 * Tracks the automated processing state of lecture units through transcription and ingestion.
 */
@Profile(PROFILE_CORE)
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
     * Find processing states that are stuck (in active phases for too long).
     * Used for recovery after node restart or detecting hung processes.
     * <p>
     * Only finds states that are NOT already scheduled for retry (retryEligibleAt IS NULL).
     * This prevents stuck detection from interfering with states waiting for their backoff period.
     *
     * @param phases     the phases to check
     * @param cutoffTime the time before which states are considered stuck
     * @return list of stuck processing states
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            AND ps.startedAt < :cutoffTime
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
     *
     * @param phase the processing phase to check
     * @param now   the current time to compare against retryEligibleAt
     * @return list of states ready for retry
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase = :phase
            AND ps.retryEligibleAt IS NOT NULL
            AND ps.retryEligibleAt <= :now
            """)
    List<LectureUnitProcessingState> findStatesReadyForRetry(@Param("phase") ProcessingPhase phase, @Param("now") ZonedDateTime now);

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
}
