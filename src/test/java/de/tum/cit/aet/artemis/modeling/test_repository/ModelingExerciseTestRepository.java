package de.tum.cit.aet.artemis.modeling.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

@Repository
@Lazy
@Primary
public interface ModelingExerciseTestRepository extends ModelingExerciseRepository {

    @NotNull
    default ModelingExercise findByIdWithStudentParticipationsSubmissionsResultsElseThrow(long exerciseId) {
        return getValueElseThrow(findWithStudentParticipationsSubmissionsResultsById(exerciseId), exerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<ModelingExercise> findWithStudentParticipationsSubmissionsResultsById(Long exerciseId);
}
