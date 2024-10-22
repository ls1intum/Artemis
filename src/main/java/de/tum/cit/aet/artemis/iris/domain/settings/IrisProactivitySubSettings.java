package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventSettings;

/**
 * Represents the proactivity sub settings for Iris.
 */
@Entity
@Table(name = "iris_proactivity_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProactivitySubSettings extends DomainObject implements IrisToggleableSetting {

    @Column(name = "enabled")
    private boolean enabled = false;

    @OneToMany(mappedBy = "proactivitySubSettings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<IrisEventSettings> eventSettings = new HashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<IrisEventSettings> getEventSettings() {
        return eventSettings;
    }

    public void setEventSettings(Set<IrisEventSettings> proactivityStatuses) {
        this.eventSettings = proactivityStatuses;
    }
}
