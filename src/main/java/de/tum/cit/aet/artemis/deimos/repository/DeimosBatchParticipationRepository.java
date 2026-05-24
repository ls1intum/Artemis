package de.tum.cit.aet.artemis.deimos.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Deimos-specific queries for selecting student programming participations in a submission date range.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface DeimosBatchParticipationRepository extends ArtemisJpaRepository<ProgrammingSubmission, Long> {

    @Query("""
            SELECT DISTINCT p.id
            FROM ProgrammingSubmission s
                JOIN StudentParticipation p ON s.participation.id = p.id
                JOIN p.exercise e
                LEFT JOIN e.exerciseGroup eg
                LEFT JOIN eg.exam ex
            WHERE s.submissionDate IS NOT NULL
                AND s.submissionDate >= :from
                AND s.submissionDate <= :to
                AND COALESCE(e.course.id, ex.course.id) = :courseId
            ORDER BY p.id ASC
            """)
    Slice<Long> findParticipationIdsForCourseInRange(@Param("courseId") long courseId, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM ProgrammingSubmission s
                JOIN StudentParticipation p ON s.participation.id = p.id
                JOIN p.exercise e
                LEFT JOIN e.exerciseGroup eg
                LEFT JOIN eg.exam ex
            WHERE s.submissionDate IS NOT NULL
                AND s.submissionDate >= :from
                AND s.submissionDate <= :to
                AND COALESCE(e.course.id, ex.course.id) = :courseId
            """)
    long countDistinctParticipationIdsForCourseInRange(@Param("courseId") long courseId, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    @Query("""
            SELECT DISTINCT p.id
            FROM ProgrammingSubmission s
                JOIN StudentParticipation p ON s.participation.id = p.id
                JOIN p.exercise e
            WHERE s.submissionDate IS NOT NULL
                AND s.submissionDate >= :from
                AND s.submissionDate <= :to
                AND e.id = :exerciseId
            ORDER BY p.id ASC
            """)
    Slice<Long> findParticipationIdsForExerciseInRange(@Param("exerciseId") long exerciseId, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM ProgrammingSubmission s
                JOIN StudentParticipation p ON s.participation.id = p.id
                JOIN p.exercise e
            WHERE s.submissionDate IS NOT NULL
                AND s.submissionDate >= :from
                AND s.submissionDate <= :to
                AND e.id = :exerciseId
            """)
    long countDistinctParticipationIdsForExerciseInRange(@Param("exerciseId") long exerciseId, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);
}
