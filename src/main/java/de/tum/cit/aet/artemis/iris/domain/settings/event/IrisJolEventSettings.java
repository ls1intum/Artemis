package de.tum.cit.aet.artemis.iris.domain.settings.event;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The settings for the Iris event of type JOL.
 */
@Entity
@DiscriminatorValue("JOL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisJolEventSettings extends IrisEventSettings {

    @Override
    public IrisEventSessionType getDefaultSessionType() {
        return IrisEventSessionType.COURSE;
    }

    @Override
    public String getDefaultSelectedEventVariant() {
        return "jol";
    }
}
