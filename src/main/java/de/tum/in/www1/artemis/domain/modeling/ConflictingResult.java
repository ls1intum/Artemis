package de.tum.in.www1.artemis.domain.modeling;

import javax.persistence.*;
import javax.validation.constraints.Size;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;

/**
 * Representing a model element and the corresponding result that is in conflict
 */
@Entity
@Table(name = "conflicting_result")
public class ConflictingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id of the model element (in the submitted UML model) that is assessed differently in multiple assessment and therefore in conflict
     */
    @Size(max = 50)
    @Column(name = "modelElementId")
    private String modelElementId;

    /**
     * Result (i.e. model assessment) that includes a conflict (note: not all assessments of the result are in conflict)
     */
    @ManyToOne
    private Result result;

    @ManyToOne
    private ModelAssessmentConflict conflict;

    /**
     * Changed feedback (i.e. model assessment) for the model element with modelElementId. This is updated by the assessor during conflict resolution. It is used to track the
     * decision made by tutors during the ESCALATED_TO_TUTORS_IN_CONFLICT EscalationState.
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
