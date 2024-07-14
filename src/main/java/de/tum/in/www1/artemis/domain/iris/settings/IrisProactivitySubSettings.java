package de.tum.in.www1.artemis.domain.iris.settings;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.settings.event.IrisEventSettings;

/**
 * Represents the specific ingestion sub-settings of lectures for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for lecture data ingestion.
 */
@Entity
@DiscriminatorValue("PROACTIVITY")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProactivitySubSettings extends IrisSubSettings {

    @OneToMany(mappedBy = "proactivitySubSettings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<IrisEventSettings> eventSettingsList = new HashSet<>();

    public Set<IrisEventSettings> getEventSettingsList() {
        return eventSettingsList;
    }

    public void setEventSettingsList(HashSet<IrisEventSettings> proactivityStatuses) {
        this.eventSettingsList = proactivityStatuses;
    }
}
