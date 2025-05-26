package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSettings} implementation for exercise specific settings.
 * Exercise settings are used to override course settings and currently only allow setting the {@link IrisProgrammingExerciseChatSubSettings}.
 */
@Entity
@DiscriminatorValue("EXERCISE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExerciseSettings extends IrisSettings {

    private long exerciseId;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisProgrammingExerciseChatSubSettings irisProgrammingExerciseChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_text_exercise_chat_settings_id")
    private IrisTextExerciseChatSubSettings irisTextExerciseChatSettings;

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    @Override
    public IrisLectureIngestionSubSettings getIrisLectureIngestionSettings() {
        return null;
    }

    @Override
    public void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings) {
    }

    @Override
    public IrisLectureChatSubSettings getIrisLectureChatSettings() {
        return null;
    }

    @Override
    public void setIrisLectureChatSettings(IrisLectureChatSubSettings irisLectureChatSettings) {
        // Empty because exercises don't have lecture chat settings
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
    public IrisCourseChatSubSettings getIrisCourseChatSettings() {
        // Empty because exercises don't have course chat settings
        return null;
    }

    @Override
    public void setIrisCourseChatSettings(IrisCourseChatSubSettings irisCourseChatSettings) {
        // Empty because exercises don't have course chat settings
    }

    @Override
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return null;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings) {

    }

    @Override
    public IrisFaqIngestionSubSettings getIrisFaqIngestionSettings() {
        // Empty because exercises don't have exercise faq settings
        return null;
    }

    @Override
    public void setIrisFaqIngestionSettings(IrisFaqIngestionSubSettings irisFaqIngestionSubSettings) {
        // Empty because exercises don't have exercise faq settings

    }

    @Override
    public IrisTutorSuggestionSubSettings getIrisTutorSuggestionSettings() {
        // Empty because exercises don't have exercise tutor suggestion settings
        return null;
    }

    @Override
    public void setIrisTutorSuggestionSettings(IrisTutorSuggestionSubSettings irisTutorSuggestionSubSettings) {
        // Empty because exercises don't have exercise tutor suggestion settings
    }
}
