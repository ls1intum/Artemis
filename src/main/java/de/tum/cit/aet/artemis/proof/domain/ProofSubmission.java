package de.tum.cit.aet.artemis.proof.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A ProofSubmission.
 */
@Entity
@DiscriminatorValue(value = "R")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProofSubmission extends Submission {

    @Lob
    @Column(name = "text")
    private String text;

    @Column(name = "student_checkbox_state")
    private Boolean studentCheckboxState = false;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean isStudentCheckboxState() {
        return studentCheckboxState;
    }

    public void setStudentCheckboxState(Boolean studentCheckboxState) {
        this.studentCheckboxState = studentCheckboxState;
    }

    @Override
    public String getSubmissionExerciseType() {
        return "proof";
    }

    @Override
    public boolean isEmpty() {
        return (text == null || text.isBlank()) && (studentCheckboxState == null || !studentCheckboxState);
    }

    @Override
    public String toString() {
        return "ProofSubmission{" + "id=" + getId() + "}";
    }
}
