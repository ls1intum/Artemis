package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;

/**
 * A TransformationModelingExercise.
 */
@Entity
// @Polymorphism(type = PolymorphismType.EXPLICIT)
@DiscriminatorValue(value = "TM")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TransformationModelingExercise extends ModelingExercise {

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_diagram_type")
    private DiagramType problemDiagramType;

    @Column(name = "problem_model")
    @Lob
    private String problemModel;

    @Column(name = "correction_scheme")
    @Lob
    private String correctionScheme;

    public DiagramType getProblemDiagramType() {
        return problemDiagramType;
    }

    public void setProblemDiagramType(DiagramType problemDiagramType) {
        this.problemDiagramType = problemDiagramType;
    }

    public String getProblemModel() {
        return problemModel;
    }

    public void setProblemModel(String problemModel) {
        this.problemModel = problemModel;
    }

    public String getCorrectionScheme() {
        return correctionScheme;
    }

    public double getCorrectionSchemePointsFor(String key) {
        try {
            return (new ObjectMapper()).readTree(correctionScheme).get(key).doubleValue();
        }
        catch (JsonProcessingException e) {
            return 0;
        }
    }

    public void setCorrectionScheme(String correctionScheme) {
        this.correctionScheme = correctionScheme;
    }

    @Override
    public String toString() {
        return "TransformationModelingExercise{" + "problemDiagramType=" + problemDiagramType + ", problemModel='" + problemModel + '\'' + ", correctionScheme='" + correctionScheme
                + '\'' + ", super='" + super.toString() + '\'' + '}';
    }
}
