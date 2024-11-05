package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * An {@link IrisSettings} implementation for lecture specific settings.
 * Lecture settings are used to override course settings and currently only allow setting the {@link IrisChatSubSettings}.
 */
@Entity
@DiscriminatorValue("LECTURE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureSettings extends IrisSettings {

    @OneToOne(optional = false)
    @JoinColumn(name = "lecture_id", unique = true, nullable = false)
    private Lecture lecture;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisChatSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_lecture_chat_settings_id")
    private IrisLectureChatSubSettings irisLectureChatSettings;

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    @Override
    public IrisLectureIngestionSubSettings getIrisLectureIngestionSettings() {
        return null;
    }

    @Override
    public void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings) {
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
        return null;
    }

    @Override
    public void setIrisTextExerciseChatSettings(IrisTextExerciseChatSubSettings irisTextExerciseChatSettings) {
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
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return null;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings) {
    }
}
