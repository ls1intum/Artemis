package de.tum.cit.aet.artemis.math.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A MathSubmission.
 *
 * <p>
 * The scaffold persists the student's work as an opaque {@code content} payload. The structured
 * derivation model (steps, expression trees) is layered on in a later change.
 */
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "math_submission_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathSubmission extends Submission {

    @Column(table = "math_submission_details", name = "content", columnDefinition = "longtext")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String getSubmissionExerciseType() {
        return "math";
    }

    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return content == null || content.isBlank();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + "}";
    }
}
