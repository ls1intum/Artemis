package de.tum.in.www1.artemis.domain.iris.settings.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("SUBMISSION_FAILED")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSubmissionFailedEventSettings extends IrisEventSettings {

    // The number of failed attempts for the event to be triggered
    @Column(name = "number_of_failed_attempts")
    private Integer numberOfFailedAttempts;

    @Nullable
    @Column(name = "success_threshold")
    private Double successThreshold;

    public Integer getNumberOfFailedAttempts() {
        return numberOfFailedAttempts;
    }

    public void setNumberOfFailedAttempts(Integer numberOfFailedAttempts) {
        this.numberOfFailedAttempts = numberOfFailedAttempts;
    }

    public Double getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(Double successThreshold) {
        this.successThreshold = successThreshold;
    }

    @Override
    public IrisEventLevel getDefaultLevel() {
        return IrisEventLevel.EXERCISE;
    }

    @Override
    public String getDefaultPipelineVariant() {
        return "submission_failed";
    }
}
