package de.tum.in.www1.artemis.domain.math;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Submission;

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

    /**
     * Stores the content of the submission, which is a versioned JSON object.
     * The JSON contents depend on the input type of the exercise.
     */
    @Column(name = "content", columnDefinition = "JSON", table = "math_submission_details")
    private String content;

    public MathSubmission() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + "}";
    }

}
