package de.tum.in.www1.artemis.domain.modeling;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.MODELING;

import java.time.ZonedDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A ModelingExercise.
 */
@Entity
@DiscriminatorValue(value = "M")
@SecondaryTable(name = "model_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingExercise extends Exercise {

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    @Column(name = "example_solution_model")
    // @Lob
    private String exampleSolutionModel;

    @Column(name = "example_solution_explanation")
    // @Lob
    private String exampleSolutionExplanation;

    @ManyToOne
    @JoinColumn(table = "model_exercise_details")
    @JsonIgnore
    private ModelAssessmentKnowledge knowledge;

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

    public ZonedDateTime getClusterBuildDate() {
        return clusterBuildDate;
    }

    public void setClusterBuildDate(ZonedDateTime examEndDate) {
        this.clusterBuildDate = examEndDate;
    }

    public ModelAssessmentKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(ModelAssessmentKnowledge knowledge) {
        this.knowledge = knowledge;
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
