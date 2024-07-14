package de.tum.in.www1.artemis.domain.iris.settings.event;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("JOL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisJolEventSettings extends IrisEventSettings {

    @Override
    public IrisEventLevel getDefaultLevel() {
        return IrisEventLevel.COURSE;
    }
}
