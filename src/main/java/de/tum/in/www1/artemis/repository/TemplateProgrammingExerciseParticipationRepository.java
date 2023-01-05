package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TemplateProgrammingExerciseParticipationRepository extends JpaRepository<TemplateProgrammingExerciseParticipation, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM TemplateProgrammingExerciseParticipation p
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<TemplateProgrammingExerciseParticipation> findByBuildPlanId(@Param("buildPlanId") String buildPlanId);

    @Query("""
            SELECT DISTINCT p
            FROM TemplateProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results
                LEFT JOIN FETCH p.submissions
            WHERE p.programmingExercise.id = :exerciseId
            """)
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM TemplateProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH p.submissions
            WHERE p.programmingExercise.id = :exerciseId
            """)
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(@Param("exerciseId") Long exerciseId);

    Optional<TemplateProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);

    default TemplateProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(Long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Template Programming Exercise Participation", programmingExerciseId));
    }
}
