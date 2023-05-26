package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(type = LOAD, attributePaths = { "results", "programmingExercise" })
    @Query("select p from TemplateProgrammingExerciseParticipation p where p.buildPlanId = :#{#buildPlanId}")
    Optional<TemplateProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    Optional<TemplateProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);

    default TemplateProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(Long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Template Programming Exercise Participation", programmingExerciseId));
    }
}
