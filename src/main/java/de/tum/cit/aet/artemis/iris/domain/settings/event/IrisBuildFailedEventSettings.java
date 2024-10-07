package de.tum.cit.aet.artemis.iris.domain.settings.event;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The settings for the Iris event of type BUILD_FAILED.
 */
@Entity
@DiscriminatorValue("BUILD_FAILED")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisBuildFailedEventSettings extends IrisEventSettings {

    @Override
    public IrisEventTarget getDefaultLevel() {
        return IrisEventTarget.EXERCISE;
    }

    @Override
    public String getDefaultPipelineVariant() {
        return "build_failed";
    }
}
