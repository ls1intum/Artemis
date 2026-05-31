package de.tum.cit.aet.artemis.math.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;

/**
 * Spring Data JPA repository for the MathSubmission entity.
 */
@Conditional(MathEnabled.class)
@Lazy
@Repository
public interface MathSubmissionRepository extends JpaRepository<MathSubmission, Long> {

    @Query("SELECT s FROM MathSubmission s LEFT JOIN FETCH s.steps LEFT JOIN FETCH s.results WHERE s.id = :id")
    Optional<MathSubmission> findByIdWithStepsAndResults(@Param("id") Long id);

    @Query("SELECT s FROM MathSubmission s LEFT JOIN FETCH s.steps LEFT JOIN FETCH s.results LEFT JOIN FETCH s.participation p LEFT JOIN FETCH p.exercise WHERE s.id = :id")
    Optional<MathSubmission> findByIdWithStepsResultsAndParticipation(@Param("id") Long id);

    @Query("""
            SELECT s FROM MathSubmission s
            LEFT JOIN FETCH s.results
            LEFT JOIN FETCH s.steps
            LEFT JOIN FETCH s.participation p
            LEFT JOIN FETCH p.student
            WHERE p.exercise.id = :exerciseId AND s.submitted = true
            ORDER BY s.submissionDate DESC
            """)
    java.util.List<MathSubmission> findSubmittedByExerciseId(@Param("exerciseId") Long exerciseId);
}
