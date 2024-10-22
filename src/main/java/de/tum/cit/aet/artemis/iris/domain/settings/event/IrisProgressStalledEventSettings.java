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
    public IrisEventSessionType getDefaultSessionType() {
        return IrisEventSessionType.EXERCISE;
    }

    @Override
    public String getDefaultSelectedEventVariant() {
        return "progress_stalled";
    }
}
