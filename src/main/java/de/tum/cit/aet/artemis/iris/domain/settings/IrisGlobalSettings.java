package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisCourseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisFaqIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisLectureChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisProgrammingExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.subsettings.IrisTutorSuggestionSubSettings;

/**
 * An {@link IrisSettings} implementation for global settings.
 * Global settings provide default values for all of Artemis for all sub setting types.
 * It also includes functionality to automatically update the sub settings in the future.
 */
@Entity
@DiscriminatorValue("GLOBAL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisGlobalSettings extends IrisSettings {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_programming_exercise_chat_settings", columnDefinition = "json")
    private IrisProgrammingExerciseChatSubSettings irisProgrammingExerciseChatSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_text_exercise_chat_settings", columnDefinition = "json")
    private IrisTextExerciseChatSubSettings irisTextExerciseChatSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_course_chat_settings", columnDefinition = "json")
    private IrisCourseChatSubSettings irisCourseChatSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_lecture_ingestion_settings", columnDefinition = "json")
    private IrisLectureIngestionSubSettings irisLectureIngestionSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_lecture_chat_settings", columnDefinition = "json")
    private IrisLectureChatSubSettings irisLectureChatSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_faq_ingestion_settings", columnDefinition = "json")
    private IrisFaqIngestionSubSettings irisFaqIngestionSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_competency_generation_settings", columnDefinition = "json")
    private IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "iris_tutor_suggestion_settings", columnDefinition = "json")
    private IrisTutorSuggestionSubSettings irisTutorSuggestionSettings;

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
        return irisFaqIngestionSettings;
    }

    @Override
    public void setIrisFaqIngestionSettings(IrisFaqIngestionSubSettings irisFaqIngestionSubSettings) {
        this.irisFaqIngestionSettings = irisFaqIngestionSubSettings;

    }

    @Override
    public IrisTutorSuggestionSubSettings getIrisTutorSuggestionSettings() {
        return irisTutorSuggestionSettings;
    }

    @Override
    public void setIrisTutorSuggestionSettings(IrisTutorSuggestionSubSettings irisTutorSuggestionSubSettings) {
        this.irisTutorSuggestionSettings = irisTutorSuggestionSubSettings;
    }

}
