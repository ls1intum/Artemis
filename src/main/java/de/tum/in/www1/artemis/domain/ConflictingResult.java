package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

@Entity
@Table(name = "conflicting-result")
public class ConflictingResult {

    @Size(max = 50)
    @Column(name = "modelElementId")
    private String modelElementId;

    @ManyToOne
    private Result result;

    private Feedback updatedFeedback;

    public String getModelElementId() {
        return modelElementId;
    }

    public Result getResult() {
        return result;
    }

    public Feedback getUpdatedFeedback() {
        return updatedFeedback;
    }
}
