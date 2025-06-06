package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSettings} implementation for global settings.
 * Global settings provide default values for all of Artemis for all sub setting types.
 * It also includes functionality to automatically update the sub settings in the future.
 */
@Entity
@DiscriminatorValue("GLOBAL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisGlobalSettings extends IrisSettings {

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisProgrammingExerciseChatSubSettings irisProgrammingExerciseChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_text_exercise_chat_settings_id")
    private IrisTextExerciseChatSubSettings irisTextExerciseChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_course_chat_settings_id")
    private IrisCourseChatSubSettings irisCourseChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_lecture_ingestion_settings_id")
    private IrisLectureIngestionSubSettings irisLectureIngestionSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_lecture_chat_settings_id")
    private IrisLectureChatSubSettings irisLectureChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_faq_ingestion_settings_id")
    private IrisFaqIngestionSubSettings irisFaqIngestionSubSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_competency_generation_settings_id")
    private IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_tutor_suggestion_settings_id")
    private IrisTutorSuggestionSubSettings irisTutorSuggestionSubSettings;

    @Override
    public IrisLectureIngestionSubSettings getIrisLectureIngestionSettings() {
        return irisLectureIngestionSettings;
    }

    @Override
    public void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings) {
        this.irisLectureIngestionSettings = irisLectureIngestionSettings;
    }

    @Override
    public IrisProgrammingExerciseChatSubSettings getIrisProgrammingExerciseChatSettings() {
        return irisProgrammingExerciseChatSettings;
    }

    @Override
    public void setIrisProgrammingExerciseChatSettings(IrisProgrammingExerciseChatSubSettings irisProgrammingExerciseChatSettings) {
        this.irisProgrammingExerciseChatSettings = irisProgrammingExerciseChatSettings;
    }

    @Override
    public IrisTextExerciseChatSubSettings getIrisTextExerciseChatSettings() {
        return irisTextExerciseChatSettings;
    }

    @Override
    public void setIrisTextExerciseChatSettings(IrisTextExerciseChatSubSettings irisTextExerciseChatSettings) {
        this.irisTextExerciseChatSettings = irisTextExerciseChatSettings;
    }

    @Override
    public IrisLectureChatSubSettings getIrisLectureChatSettings() {
        return irisLectureChatSettings;
    }

    @Override
    public void setIrisLectureChatSettings(IrisLectureChatSubSettings irisLectureChatSettings) {
        this.irisLectureChatSettings = irisLectureChatSettings;
    }

    @Override
    public IrisCourseChatSubSettings getIrisCourseChatSettings() {
        return irisCourseChatSettings;
    }

    @Override
    public void setIrisCourseChatSettings(IrisCourseChatSubSettings irisCourseChatSettings) {
        this.irisCourseChatSettings = irisCourseChatSettings;
    }

    @Override
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return irisCompetencyGenerationSettings;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings) {
        this.irisCompetencyGenerationSettings = irisCompetencyGenerationSettings;
    }

    @Override
    public IrisFaqIngestionSubSettings getIrisFaqIngestionSettings() {
        return irisFaqIngestionSubSettings;
    }

    @Override
    public void setIrisFaqIngestionSettings(IrisFaqIngestionSubSettings irisFaqIngestionSubSettings) {
        this.irisFaqIngestionSubSettings = irisFaqIngestionSubSettings;

    }

    @Override
    public IrisTutorSuggestionSubSettings getIrisTutorSuggestionSettings() {
        return irisTutorSuggestionSubSettings;
    }

    @Override
    public void setIrisTutorSuggestionSettings(IrisTutorSuggestionSubSettings irisTutorSuggestionSubSettings) {
        this.irisTutorSuggestionSubSettings = irisTutorSuggestionSubSettings;
    }

}
