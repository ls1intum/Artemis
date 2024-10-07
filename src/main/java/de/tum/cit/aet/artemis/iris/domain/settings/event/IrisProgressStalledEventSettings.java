package de.tum.cit.aet.artemis.iris.domain.settings.event;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The settings for the Iris event of type PROGRESS_STALLED.
 */
@Entity
@DiscriminatorValue("PROGRESS_STALLED")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProgressStalledEventSettings extends IrisEventSettings {

    @Override
    public IrisEventTarget getDefaultLevel() {
        return IrisEventTarget.EXERCISE;
    }

    @Override
    public String getDefaultPipelineVariant() {
        return "progress_stalled";
    }
}
