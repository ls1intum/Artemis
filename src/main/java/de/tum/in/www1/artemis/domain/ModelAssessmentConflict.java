package de.tum.in.www1.artemis.domain;

import java.util.Map;
import javax.persistence.*;
import javax.validation.constraints.Size;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

@Entity
@Table(name = "model_assessment_conflict")
public class ModelAssessmentConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Result result;

    @Size(max = 50)
    @Column(name = "modelElementId")
    private String modelElementId;

    @ElementCollection
    @CollectionTable(name="conflictingElementsResult")
    @MapKeyColumn(name="modelElementId")
    private Map<String, Result> conflictingElementsResultMap;

    @Column(name = "state")
    private EscalationState state;


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public Result getResult() {
        return result;
    }


    public void setResult(Result result) {
        this.result = result;
    }


    public String getModelElementId() {
        return modelElementId;
    }


    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }


    public Map<String, Result> getConflictingElementsResultMap() {
        return conflictingElementsResultMap;
    }


    public void setConflictingElementsResultMap(Map<String, Result> conflictingElementsResultMap) {
        this.conflictingElementsResultMap = conflictingElementsResultMap;
    }


    public EscalationState getState() {
        return state;
    }


    public void setState(EscalationState state) {
        this.state = state;
    }
}
