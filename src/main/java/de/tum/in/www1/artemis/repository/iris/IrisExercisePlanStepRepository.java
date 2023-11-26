package de.tum.in.www1.artemis.repository.iris;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanStep;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface IrisExercisePlanStepRepository extends JpaRepository<IrisExercisePlanStep, Long> {

    default IrisExercisePlanStep findByIdElseThrow(long exercisePlanComponentId) {
        return findById(exercisePlanComponentId).orElseThrow(() -> new EntityNotFoundException("Exercise Plan Step"));
    }

    default void setInProgress(IrisExercisePlanStep exercisePlanComponent) {
        exercisePlanComponent.setExecutionStage(IrisExercisePlanStep.ExecutionStage.IN_PROGRESS);
        save(exercisePlanComponent);
    }

    default void setCompleted(IrisExercisePlanStep exercisePlanComponent) {
        exercisePlanComponent.setExecutionStage(IrisExercisePlanStep.ExecutionStage.COMPLETE);
        save(exercisePlanComponent);
    }

    default void setFailed(IrisExercisePlanStep exercisePlanComponent) {
        exercisePlanComponent.setExecutionStage(IrisExercisePlanStep.ExecutionStage.FAILED);
        save(exercisePlanComponent);
    }

}
