package de.tum.cit.aet.artemis.proof.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;

/**
 * Spring Data JPA repository for the ProofSubmission entity.
 */
@Conditional(ProofEnabled.class)
@Lazy
@Repository
public interface ProofSubmissionRepository extends JpaRepository<ProofSubmission, Long> {

    @Query("SELECT s FROM ProofSubmission s LEFT JOIN FETCH s.steps LEFT JOIN FETCH s.results WHERE s.id = :id")
    Optional<ProofSubmission> findByIdWithStepsAndResults(@Param("id") Long id);

    @Query("SELECT s FROM ProofSubmission s LEFT JOIN FETCH s.steps LEFT JOIN FETCH s.results LEFT JOIN FETCH s.participation p LEFT JOIN FETCH p.exercise WHERE s.id = :id")
    Optional<ProofSubmission> findByIdWithStepsResultsAndParticipation(@Param("id") Long id);

    @Query("""
            SELECT s FROM ProofSubmission s
            LEFT JOIN FETCH s.results
            LEFT JOIN FETCH s.steps
            LEFT JOIN FETCH s.participation p
            LEFT JOIN FETCH p.student
            WHERE p.exercise.id = :exerciseId AND s.submitted = true
            ORDER BY s.submissionDate DESC
            """)
    java.util.List<ProofSubmission> findSubmittedByExerciseId(@Param("exerciseId") Long exerciseId);
}
