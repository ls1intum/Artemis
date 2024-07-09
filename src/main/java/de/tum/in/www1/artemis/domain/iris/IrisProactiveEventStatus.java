package de.tum.in.www1.artemis.domain.iris;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.settings.IrisProactivitySubSettings;

@Entity
@Table(name = "iris_proactive_event_status")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProactiveEventStatus extends DomainObject {

    @Enumerated(EnumType.STRING)
    private IrisEventType type;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @ManyToOne
    private IrisProactivitySubSettings proactivitySubSettings;

    public IrisProactivitySubSettings getProactivitySubSettings() {
        return proactivitySubSettings;
    }

    public void setProactivitySubSettings(IrisProactivitySubSettings proactivitySubSettings) {
        this.proactivitySubSettings = proactivitySubSettings;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public IrisEventType getType() {
        return type;
    }

    public void setType(IrisEventType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        IrisProactiveEventStatus eventStatus = (IrisProactiveEventStatus) other;
        return type == eventStatus.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }
}
