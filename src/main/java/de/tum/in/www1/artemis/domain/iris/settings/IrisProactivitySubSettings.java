package de.tum.in.www1.artemis.domain.iris.settings;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisEventType;
import de.tum.in.www1.artemis.domain.iris.IrisProactiveEventStatus;

/**
 * Represents the specific ingestion sub-settings of lectures for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for lecture data ingestion.
 */
@Entity
@DiscriminatorValue("PROACTIVITY")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProactivitySubSettings extends IrisSubSettings {

    @OneToMany(mappedBy = "proactivitySubSettings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<IrisProactiveEventStatus> proactivityStatuses = new HashSet<>();

    public Set<IrisProactiveEventStatus> getProactivityStatuses() {
        return proactivityStatuses;
    }

    public void setProactivityStatuses(HashSet<IrisProactiveEventStatus> proactivityStatuses) {
        this.proactivityStatuses = proactivityStatuses;
    }

    public void setActiveFor(IrisEventType eventType) {
        proactivityStatuses.stream().findFirst().filter(e -> e.getType() == eventType).ifPresent(selectedEvent -> selectedEvent.setActive(true));
    }

    public void setInactiveFor(IrisEventType eventType) {
        proactivityStatuses.stream().findFirst().filter(e -> e.getType() == eventType).ifPresent(selectedEvent -> selectedEvent.setActive(false));
    }

    public void addProactiveEventStatus(IrisProactiveEventStatus event) {
        var eventMatch = proactivityStatuses.stream().anyMatch(e -> e.getType().equals(event.getType()));
        if (eventMatch || proactivityStatuses.contains(event)) {
            return;
        }
        proactivityStatuses.add(event);
    }
}
