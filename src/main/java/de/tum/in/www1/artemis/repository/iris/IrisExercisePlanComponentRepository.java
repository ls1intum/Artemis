package de.tum.in.www1.artemis.repository.iris;

import de.tum.in.www1.artemis.domain.iris.message.ExercisePlanComponent;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrisExercisePlanComponentRepository extends JpaRepository<ExercisePlanComponent, Long> {

    default ExercisePlanComponent findByIdElseThrow(long exercisePlanComponentId) {
        return findById(exercisePlanComponentId).orElseThrow(() -> new EntityNotFoundException("Exercise Plan Component"));
    }

}
