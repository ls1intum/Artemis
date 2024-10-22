package de.tum.cit.aet.artemis.iris.domain.settings.event;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import de.tum.cit.aet.artemis.iris.domain.settings.IrisListConverter;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisProactivitySubSettings;

/**
 * IrisEventSettings is an abstract super class for the specific sub event settings types.
 * Sub Event Settings are settings for a proactive event of Iris.
 * {@link IrisProgressStalledEventSettings} are used to specify settings for the progress stalled event.
 * {@link IrisBuildFailedEventSettings} are used to specify settings for the build failed event.
 * {@link IrisJolEventSettings} are used to specify settings for the JOL event.
 * <p>
 * Also see {@link de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService} for more information.
 */
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
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "allowed_event_variants", nullable = false)
    @Convert(converter = IrisListConverter.class)
    private SortedSet<String> allowedEventVariants = new TreeSet<>();

    // The selected event variant of the pipeline the event is associated with
    @Column(name = "selected_event_variant", nullable = false)
    private String selectedEventVariant;

    // The session type of the event which type of session the event will be triggered in
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private IrisEventSessionType sessionType;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "iris_proactivity_settings_id")
    private IrisProactivitySubSettings proactivitySubSettings;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        if (sessionType == null) {
            sessionType = getDefaultSessionType();
        }
        if (selectedEventVariant == null) {
            selectedEventVariant = getDefaultSelectedEventVariant();
        }
    }

    public IrisProactivitySubSettings getProactivitySubSettings() {
        return proactivitySubSettings;
    }

    public void setProactivitySubSettings(IrisProactivitySubSettings proactivitySubSettings) {
        this.proactivitySubSettings = proactivitySubSettings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean active) {
        enabled = active;
    }

    public IrisEventSessionType getSessionType() {
        return sessionType;
    }

    public SortedSet<String> getAllowedEventVariants() {
        return allowedEventVariants;
    }

    public void setAllowedEventVariants(SortedSet<String> allowedEventVariants) {
        this.allowedEventVariants = allowedEventVariants;
    }

    @Nullable
    public String getSelectedEventVariant() {
        return selectedEventVariant;
    }

    public void setSelectedEventVariant(@Nullable String selectedVariant) {
        this.selectedEventVariant = selectedVariant;
    }

    @JsonIgnore
    protected abstract IrisEventSessionType getDefaultSessionType();

    @JsonIgnore
    protected abstract String getDefaultSelectedEventVariant();
}
