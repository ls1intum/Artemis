package de.tum.cit.aet.artemis.iris.domain.settings;

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
 * IrisSettings is an abstract super class for the specific settings types.
 * Settings bundle {@link IrisSubSettings} together.
 * {@link IrisGlobalSettings} are used to specify settings on a global level.
 * {@link IrisCourseSettings} are used to specify settings on a course level.
 * {@link IrisExerciseSettings} are used to specify settings on an exercise level.
 * <p>
 * Also see {@link de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService} for more information.
 */
@Entity
@Table(name = "iris_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisGlobalSettings.class, name = "global"),
    @JsonSubTypes.Type(value = IrisCourseSettings.class, name = "course"),
    @JsonSubTypes.Type(value = IrisExerciseSettings.class, name = "exercise")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSettings extends DomainObject {

    public abstract IrisChatSubSettings getIrisChatSettings();

    public abstract void setIrisChatSettings(IrisChatSubSettings irisChatSettings);

    public abstract IrisTextExerciseChatSubSettings getIrisTextExerciseChatSettings();

    public abstract void setIrisTextExerciseChatSettings(IrisTextExerciseChatSubSettings irisTextExerciseChatSettings);

    public abstract IrisLectureIngestionSubSettings getIrisLectureIngestionSettings();

    public abstract void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings);

    public abstract IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings();

    public abstract void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings);
}
