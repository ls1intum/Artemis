package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventSettings;

/**
 * Represents the specific ingestion sub-settings of lectures for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for lecture data ingestion.
 */
@Entity
@DiscriminatorValue("PROACTIVITY")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProactivitySubSettings extends IrisSubSettings {

    @OneToMany(mappedBy = "proactivitySubSettings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<IrisEventSettings> eventSettings = new HashSet<>();

    public Set<IrisEventSettings> getEventSettings() {
        return eventSettings;
    }

    public void setEventSettings(Set<IrisEventSettings> proactivityStatuses) {
        this.eventSettings = proactivityStatuses;
    }
}
