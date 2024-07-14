package de.tum.in.www1.artemis.domain.iris.settings.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("SUBMISSION_SUCCESSFUL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSubmissionSuccessfulEventSettings extends IrisEventSettings {

    @Nullable
    @Column(name = "success_threshold")
    private Integer successThreshold;

    public Integer getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(Integer successThreshold) {
        this.successThreshold = successThreshold;
    }

    @Override
    public IrisEventLevel getDefaultLevel() {
        return IrisEventLevel.COURSE;
    }
}
