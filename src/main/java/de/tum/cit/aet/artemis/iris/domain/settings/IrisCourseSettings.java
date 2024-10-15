package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * An {@link IrisSettings} implementation for course specific settings.
 * Course settings are used to override global settings and allows all sub setting types.
 */
@Entity
@DiscriminatorValue("COURSE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseSettings extends IrisSettings {

    @OneToOne(optional = false)
    @JoinColumn(name = "course_id", unique = true, nullable = false)
    private Course course;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisChatSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_text_exercise_chat_settings_id")
    private IrisTextExerciseChatSubSettings irisTextExerciseChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_proactivity_settings_id")
    private IrisProactivitySubSettings irisProactivitySettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_lecture_ingestion_settings_id")
    private IrisLectureIngestionSubSettings irisLectureIngestionSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_competency_generation_settings_id")
    private IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public IrisLectureIngestionSubSettings getIrisLectureIngestionSettings() {
        return irisLectureIngestionSettings;
    }

    @Override
    public void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings) {
        this.irisLectureIngestionSettings = irisLectureIngestionSettings;
    }

    @Override
    public IrisProactivitySubSettings getIrisProactivitySettings() {
        return irisProactivitySettings;
    }

    @Override
    public void setIrisProactivitySettings(IrisProactivitySubSettings irisProactivitySettings) {
        this.irisProactivitySettings = irisProactivitySettings;
    }

    @Override
    public IrisChatSubSettings getIrisChatSettings() {
        return irisChatSettings;
    }

    @Override
    public void setIrisChatSettings(IrisChatSubSettings irisChatSettings) {
        this.irisChatSettings = irisChatSettings;
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
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return irisCompetencyGenerationSettings;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings) {
        this.irisCompetencyGenerationSettings = irisCompetencyGenerationSubSettings;
    }
}
