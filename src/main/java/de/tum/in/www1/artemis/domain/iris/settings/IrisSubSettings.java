package de.tum.in.www1.artemis.domain.iris.settings;

import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * IrisSubSettings is an abstract super class for the specific sub settings types.
 * Sub Settings are settings for a specific feature of Iris.
 * {@link IrisChatSubSettings} are used to specify settings for the chat feature.
 * {@link IrisHestiaSubSettings} are used to specify settings for the Hestia integration.
 * {@link IrisCodeEditorSubSettings} are used to specify settings for the code editor feature.
 * <p>
 * Also see {@link de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService} for more information.
 */
@Entity
@Table(name = "iris_sub_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisChatSubSettings.class, name = "chat"), @JsonSubTypes.Type(value = IrisHestiaSubSettings.class, name = "hestia"),
        @JsonSubTypes.Type(value = IrisCodeEditorSubSettings.class, name = "code-editor") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSubSettings extends DomainObject {

    @Column(name = "enabled")
    private boolean enabled = false;

    @Column(name = "allowed_models")
    @Convert(converter = IrisModelListConverter.class)
    private Set<String> allowedModels = new TreeSet<>();

    @Nullable
    @Column(name = "preferred_model")
    private String preferredModel;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(Set<String> allowedModels) {
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
