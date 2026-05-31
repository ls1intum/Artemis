package de.tum.cit.aet.artemis.math.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import lombok.Getter;

/**
 * A MathSubmission.
 */
@Getter
@Entity
@DiscriminatorValue(value = "R")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathSubmission extends Submission {

    @JsonIgnore
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepIndex ASC")
    private List<DerivationStep> steps = new ArrayList<>();

    public void setSteps(List<DerivationStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    @Override
    public String getSubmissionExerciseType() {
        return "math";
    }

    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return steps == null || steps.isEmpty();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + "}";
    }
}
