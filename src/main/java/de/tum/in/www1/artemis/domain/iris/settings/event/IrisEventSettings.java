package de.tum.in.www1.artemis.domain.iris.settings.event;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import javax.annotation.Nullable;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.settings.IrisProactivitySubSettings;

@Entity
@Table(name = "iris_event_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisSubmissionSuccessfulEventSettings.class, name = "submission_successful"),
    @JsonSubTypes.Type(value = IrisSubmissionFailedEventSettings.class, name = "submission_failed"),
    @JsonSubTypes.Type(value = IrisJolEventSettings.class, name = "jol")
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisEventSettings extends DomainObject {
    // Is event active
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // The date until which the event is deferred
    @Nullable
    @Column(name = "deferred_until")
    private ZonedDateTime deferredUntil;

    @Nullable
    @Column(name="last_triggered")
    private ZonedDateTime lastTriggered;

    // The variant of the pipeline the event is associated with
    @Column(name = "pipeline_variant", nullable = false)
    private String pipelineVariant;

    // The priority of the event
    @Column(name = "priority", nullable = false)
    private int priority;

    // The level of the event which type of session the event will be triggered in
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private IrisEventLevel level;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "iris_proactivity_settings_id")
    private IrisProactivitySubSettings proactivitySubSettings;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        if (level == null) {
            level = getDefaultLevel();
        }
        if (pipelineVariant == null) {
            pipelineVariant = getDefaultPipelineVariant();
        }
    }

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

    public ZonedDateTime getDeferredUntil() {
        return deferredUntil;
    }

    public void setDeferredUntil(ZonedDateTime deferredUntil) {
        this.deferredUntil = deferredUntil;
    }

    public String getPipelineVariant() {
        return pipelineVariant;
    }

    public void setPipelineVariant(String pipelineVariant) {
        this.pipelineVariant = pipelineVariant;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public IrisEventLevel getLevel() {
        return level;
    }

    public void setLevel(IrisEventLevel level) {
        this.level = level;
    }

    // Check if the event can be triggered
    public boolean canBeTriggered() {
        return isActive && (deferredUntil == null || deferredUntil.isBefore(ZonedDateTime.now()));
    }

    public abstract IrisEventLevel getDefaultLevel();

    public abstract String getDefaultPipelineVariant();
}
