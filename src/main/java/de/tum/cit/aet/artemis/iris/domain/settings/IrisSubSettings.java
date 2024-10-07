package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * IrisSubSettings is an abstract super class for the specific sub settings types.
 * Sub Settings are settings for a specific feature of Iris.
 * {@link IrisChatSubSettings} are used to specify settings for the chat feature.
 * {@link IrisHestiaSubSettings} are used to specify settings for the Hestia integration.
 * {@link IrisCompetencyGenerationSubSettings} are used to specify settings for the competency generation feature.
 * <p>
 * Also see {@link de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService} for more information.
 */
@Entity
@Table(name = "iris_sub_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisChatSubSettings.class, name = "chat"),
    @JsonSubTypes.Type(value = IrisLectureIngestionSubSettings.class, name = "lecture-ingestion"),
    @JsonSubTypes.Type(value = IrisHestiaSubSettings.class, name = "hestia"),
    @JsonSubTypes.Type(value = IrisCompetencyGenerationSubSettings.class, name = "competency-generation"),
    @JsonSubTypes.Type(value = IrisProactivitySubSettings.class, name = "proactivity")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSubSettings extends DomainObject {

    @Column(name = "enabled")
    private boolean enabled = false;

    @Column(name = "allowed_models")
    @Convert(converter = IrisModelListConverter.class)
    private SortedSet<String> allowedModels = new TreeSet<>();

    @Nullable
    @Column(name = "preferred_model")
    private String preferredModel;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SortedSet<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(SortedSet<String> allowedModels) {
        this.allowedModels = allowedModels;
    }

    @Nullable
    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(@Nullable String preferredModel) {
        this.preferredModel = preferredModel;
    }
}
