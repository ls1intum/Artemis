package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;

import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

public record ModelingExerciseSnapshot(DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation) implements Serializable {

    public static ModelingExerciseSnapshot of(ModelingExercise exercise) {
        return new ModelingExerciseSnapshot(exercise.getDiagramType(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation());
    }
}
