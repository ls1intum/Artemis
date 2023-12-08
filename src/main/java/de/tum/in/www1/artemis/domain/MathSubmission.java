package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A MathSubmission.
 */
@Entity
@DiscriminatorValue(value = "MATH")
@SecondaryTable(name = "math_submission_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathSubmission extends Submission {

    @Override
    public String getSubmissionExerciseType() {
        return "math";
    }

    @Column(name = "text", table = "math_submission_details", columnDefinition = "LONGTEXT")
    @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The text submission is too large.")
    private String text;

    public MathSubmission() {
    }

    public String getText() {
        return text;
    }

    public MathSubmission text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + "}";
    }

}
