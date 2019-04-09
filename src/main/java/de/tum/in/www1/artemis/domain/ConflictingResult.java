package de.tum.in.www1.artemis.domain;

import javax.persistence.*;
import javax.validation.constraints.Size;

/**
 * Representing a model element and the corresponding result that is in conflict
 */
@Entity
@Table(name = "conflicting-result")
public class ConflictingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id of model element in result that is in conflict
     */
    @Size(max = 50)
    @Column(name = "modelElementId")
    private String modelElementId;

    /**
     * Result that is in conflict
     */
    @ManyToOne
    private Result result;

    /**
     * Changed feedback for model element with modelElementId updated by the assessor during conflict resolution. Used to track the decision made by tutors during the
     * ESCALATED_TO_TUTORS_IN_CONFLICT EscalationState.
     */
    @OneToOne
    private Feedback updatedFeedback;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelElementId() {
        return modelElementId;
    }

    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Feedback getUpdatedFeedback() {
        return updatedFeedback;
    }

    public void setUpdatedFeedback(Feedback updatedFeedback) {
        this.updatedFeedback = updatedFeedback;
    }
}
