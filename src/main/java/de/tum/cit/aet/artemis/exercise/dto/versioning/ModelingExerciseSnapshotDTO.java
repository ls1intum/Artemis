package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseSnapshotDTO(DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation) implements Serializable {

    public static ModelingExerciseSnapshotDTO of(ModelingExercise exercise) {
        return new ModelingExerciseSnapshotDTO(exercise.getDiagramType(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation());
    }
}
