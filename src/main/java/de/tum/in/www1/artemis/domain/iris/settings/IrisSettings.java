package de.tum.in.www1.artemis.domain.iris.settings;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisSettings object represents the settings for Iris for a part of Artemis.
 * These settings can be either global, course or exercise specific.
 * {@link de.tum.in.www1.artemis.service.iris.IrisSettingsService} for more details how IrisSettings are used.
 */
@Entity
@Table(name = "iris_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisGlobalSettings.class, name = "global"), @JsonSubTypes.Type(value = IrisCourseSettings.class, name = "course"),
        @JsonSubTypes.Type(value = IrisExerciseSettings.class, name = "exercise") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSettings extends DomainObject {

    public abstract IrisChatSubSettings getIrisChatSettings();

    public abstract void setIrisChatSettings(IrisChatSubSettings irisChatSettings);

    public abstract IrisHestiaSubSettings getIrisHestiaSettings();

    public abstract void setIrisHestiaSettings(IrisHestiaSubSettings irisHestiaSettings);

    public abstract IrisCodeEditorSubSettings getIrisCodeEditorSettings();

    public abstract void setIrisCodeEditorSettings(IrisCodeEditorSubSettings irisCodeEditorSettings);

    public abstract boolean isValid();
}
