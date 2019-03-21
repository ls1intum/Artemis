package de.tum.in.www1.artemis.domain;

import java.util.Map;
import javax.persistence.*;
import javax.validation.constraints.Size;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

@Entity
@Table(name = "conflict")
public class Conflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Result result;

    @Size(max = 50)
    @Column(name = "jsonElementId")
    private String jsonElementId;

    @ElementCollection
    @CollectionTable(name="conflictingElementsResult")
    @MapKeyColumn(name="jsonElementId")
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


    public String getJsonElementId() {
        return jsonElementId;
    }


    public void setJsonElementId(String jsonElementId) {
        this.jsonElementId = jsonElementId;
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
