package de.tum.cit.aet.artemis.iris.domain.settings.event;

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

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisProactivitySubSettings;

@Entity
@Table(name = "iris_event_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisProgressStalledEventSettings.class, name = "progress_stalled"),
    @JsonSubTypes.Type(value = IrisBuildFailedEventSettings.class, name = "build_failed"),
    @JsonSubTypes.Type(value = IrisJolEventSettings.class, name = "jol")
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisEventSettings extends DomainObject {
    // Is event active
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // The variant of the pipeline the event is associated with
    @Column(name = "pipeline_variant", nullable = false)
    private String pipelineVariant;

    // The level of the event which type of session the event will be triggered in
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "target")
    private IrisEventTarget target;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "iris_proactivity_settings_id")
    private IrisProactivitySubSettings proactivitySubSettings;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        if (target == null) {
            target = getDefaultLevel();
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

    public String getPipelineVariant() {
        return pipelineVariant;
    }

    public void setPipelineVariant(String pipelineVariant) {
        this.pipelineVariant = pipelineVariant;
    }

    public IrisEventTarget getTarget() {
        return target;
    }

    @JsonIgnore
    protected abstract IrisEventTarget getDefaultLevel();

    @JsonIgnore
    protected abstract String getDefaultPipelineVariant();
}
