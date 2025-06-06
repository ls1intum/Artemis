package de.tum.cit.aet.artemis.modeling.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.MODELING;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A ModelingExercise.
 */
@Entity
@DiscriminatorValue(value = "M")
@SecondaryTable(name = "model_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingExercise extends Exercise {

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    @Override
    public String getType() {
        return "modeling";
    }

    // TODO: move to secondary table
    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    // TODO: move to secondary table
    @Column(name = "example_solution_model")
    private String exampleSolutionModel;

    // TODO: move to secondary table
    @Column(name = "example_solution_explanation")
    private String exampleSolutionExplanation;

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    public String getExampleSolutionModel() {
        return exampleSolutionModel;
    }

    public void setExampleSolutionModel(String exampleSolutionModel) {
        this.exampleSolutionModel = exampleSolutionModel;
    }

    public String getExampleSolutionExplanation() {
        return exampleSolutionExplanation;
    }

    public void setExampleSolutionExplanation(String exampleSolutionExplanation) {
        this.exampleSolutionExplanation = exampleSolutionExplanation;
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            setExampleSolutionModel(null);
            setExampleSolutionExplanation(null);
        }
        super.filterSensitiveInformation();
    }

    @Override
    public ExerciseType getExerciseType() {
        return MODELING;
    }

    @Override
    public String toString() {
        return "ModelingExercise{" + "id=" + getId() + ", maxPoints='" + getMaxPoints() + "'" + ", diagramType='" + getDiagramType() + "'" + ", exampleSolutionModel='"
                + getExampleSolutionModel() + "'" + ", exampleSolutionExplanation='" + getExampleSolutionExplanation() + "'" + "}";
    }

}
