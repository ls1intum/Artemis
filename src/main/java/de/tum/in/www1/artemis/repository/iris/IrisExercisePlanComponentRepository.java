package de.tum.in.www1.artemis.repository.iris;

import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanComponent;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrisExercisePlanComponentRepository extends JpaRepository<IrisExercisePlanComponent, Long> {

    default IrisExercisePlanComponent findByIdElseThrow(long exercisePlanComponentId) {
        return findById(exercisePlanComponentId).orElseThrow(() -> new EntityNotFoundException("Exercise Plan Component"));
    }

}
