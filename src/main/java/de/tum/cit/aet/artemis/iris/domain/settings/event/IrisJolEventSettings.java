package de.tum.in.www1.artemis.domain.iris.settings.event;

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
    public IrisEventTarget getDefaultLevel() {
        return IrisEventTarget.COURSE;
    }

    @Override
    public String getDefaultPipelineVariant() {
        return "jol";
    }
}
