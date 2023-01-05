package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SolutionProgrammingExerciseParticipationRepository extends JpaRepository<SolutionProgrammingExerciseParticipation, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM SolutionProgrammingExerciseParticipation p
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanId(@Param("buildPlanId") String buildPlanId);

    @Query("""
            SELECT DISTINCT p
            FROM SolutionProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.programmingExercise.id = :exerciseId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM SolutionProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH r.feedbacks
            WHERE p.programmingExercise.id = :exerciseId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(@Param("exerciseId") Long exerciseId);

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);

    default SolutionProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(Long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Solution Programming Exercise Participation", programmingExerciseId));
    }
}
