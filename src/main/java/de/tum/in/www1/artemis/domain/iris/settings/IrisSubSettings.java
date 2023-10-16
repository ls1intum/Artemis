package de.tum.in.www1.artemis.domain.iris.settings;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisSubSettings object represents the settings for a specific feature of Iris.
 * {@link IrisSettings} is the parent of this class.
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
    private Set<String> allowedModels = new HashSet<>();

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
