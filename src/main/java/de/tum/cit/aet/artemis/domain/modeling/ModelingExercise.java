package de.tum.cit.aet.artemis.domain.modeling;

import static de.tum.cit.aet.artemis.domain.enumeration.ExerciseType.MODELING;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.enumeration.DiagramType;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseType;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    @Column(name = "example_solution_model")
    private String exampleSolutionModel;

    @Column(name = "example_solution_explanation")
    private String exampleSolutionExplanation;

    @Transient
    private ZonedDateTime clusterBuildDate;

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

    @JsonIgnore
    public ZonedDateTime getClusterBuildDate() {
        return clusterBuildDate;
    }

    public void setClusterBuildDate(ZonedDateTime examEndDate) {
        this.clusterBuildDate = examEndDate;
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
