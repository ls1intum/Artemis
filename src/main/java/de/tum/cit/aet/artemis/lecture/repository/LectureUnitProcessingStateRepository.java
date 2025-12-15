package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * Find all processing states in a specific phase.
     *
     * @param phase the processing phase to search for
     * @return list of processing states in the given phase
     */
    List<LectureUnitProcessingState> findByPhase(ProcessingPhase phase);

    /**
     * Find processing states that are stuck (in active phases for too long).
     * Used for recovery after node restart or detecting hung processes.
     * Applies to any retry count - a retry can also get stuck if callback is lost.
     *
     * @param phases     the phases to check
     * @param cutoffTime the time before which states are considered stuck
     * @return list of stuck processing states
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN :phases
            AND ps.startedAt < :cutoffTime
            """)
    List<LectureUnitProcessingState> findStuckStates(@Param("phases") List<ProcessingPhase> phases, @Param("cutoffTime") ZonedDateTime cutoffTime);

    /**
     * Find processing states that failed and are ready for retry after backoff.
     * Used by the scheduler to retry failed states with exponential backoff.
     *
     * @param phase      the processing phase to check
     * @param cutoffTime the time before which states are ready for retry
     * @return list of states ready for retry
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase = :phase
            AND ps.retryCount > 0
            AND ps.lastUpdated < :cutoffTime
            """)
    List<LectureUnitProcessingState> findStatesReadyForRetry(@Param("phase") ProcessingPhase phase, @Param("cutoffTime") ZonedDateTime cutoffTime);

    /**
     * Find all processing states for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return list of processing states for all units in the lecture
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            JOIN ps.lectureUnit lu
            WHERE lu.lecture.id = :lectureId
            """)
    List<LectureUnitProcessingState> findByLectureId(@Param("lectureId") Long lectureId);

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
     * Delete the processing state for a lecture unit.
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    @Modifying
    @Transactional
    void deleteByLectureUnit_Id(Long lectureUnitId);

    /**
     * Count processing states in a specific phase for a course.
     *
     * @param courseId the ID of the course
     * @param phase    the processing phase
     * @return count of states in the given phase
     */
    @Query("""
            SELECT COUNT(ps) FROM LectureUnitProcessingState ps
            JOIN ps.lectureUnit lu
            JOIN lu.lecture l
            WHERE l.course.id = :courseId
            AND ps.phase = :phase
            """)
    long countByCourseIdAndPhase(@Param("courseId") Long courseId, @Param("phase") ProcessingPhase phase);
}
