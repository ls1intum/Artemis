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
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisCourseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisFaqIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisLectureChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisProgrammingExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisTutorSuggestionSubSettings;

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
    @JsonSubTypes.Type(value = IrisExerciseSettings.class, name = "exercise"),
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSettings extends DomainObject {

    public abstract IrisProgrammingExerciseChatSubSettings getIrisProgrammingExerciseChatSettings();

    public abstract void setIrisProgrammingExerciseChatSettings(IrisProgrammingExerciseChatSubSettings irisProgrammingExerciseChatSettings);

    public abstract IrisTextExerciseChatSubSettings getIrisTextExerciseChatSettings();

    public abstract void setIrisTextExerciseChatSettings(IrisTextExerciseChatSubSettings irisTextExerciseChatSettings);

    public abstract IrisCourseChatSubSettings getIrisCourseChatSettings();

    public abstract void setIrisCourseChatSettings(IrisCourseChatSubSettings irisCourseChatSettings);

    public abstract IrisLectureIngestionSubSettings getIrisLectureIngestionSettings();

    public abstract void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings);

    public abstract IrisLectureChatSubSettings getIrisLectureChatSettings();

    public abstract void setIrisLectureChatSettings(IrisLectureChatSubSettings irisLectureChatSettings);

    public abstract IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings();

    public abstract void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings);

    public abstract IrisFaqIngestionSubSettings getIrisFaqIngestionSettings();

    public abstract void setIrisFaqIngestionSettings(IrisFaqIngestionSubSettings irisFaqIngestionSubSettings);

    public abstract IrisTutorSuggestionSubSettings getIrisTutorSuggestionSettings();

    public abstract void setIrisTutorSuggestionSettings(IrisTutorSuggestionSubSettings irisTutorSuggestionSubSettings);

}
