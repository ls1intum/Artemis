package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Modeling-exercise-specific fields carried in the conduction payload (unwrapped into the exercise object). The example
 * solution model / explanation are already stripped from the entity before this factory runs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseForConductionDTO(DiagramType diagramType) {

    /**
     * Extracts the modeling-specific fields.
     *
     * @param modelingExercise the modeling exercise to convert
     * @return the modeling-specific fields
     */
    public static ModelingExerciseForConductionDTO of(ModelingExercise modelingExercise) {
        return new ModelingExerciseForConductionDTO(modelingExercise.getDiagramType());
    }
}
