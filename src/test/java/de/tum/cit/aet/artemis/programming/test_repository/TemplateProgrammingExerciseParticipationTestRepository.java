package de.tum.cit.aet.artemis.programming.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

@Repository
@Primary
public interface TemplateProgrammingExerciseParticipationTestRepository extends TemplateProgrammingExerciseParticipationRepository {

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default TemplateProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId));
    }
}
