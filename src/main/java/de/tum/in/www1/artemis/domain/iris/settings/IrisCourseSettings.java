package de.tum.in.www1.artemis.domain.iris.settings;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 * An IrisSettings object represents the settings for Iris for a part of Artemis.
 * These settings can be either global, course or exercise specific.
 * {@link de.tum.in.www1.artemis.service.iris.IrisSettingsService} for more details how IrisSettings are used.
 */
@Entity
@DiscriminatorValue("COURSE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseSettings extends IrisSettings {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", unique = true, nullable = false)
    private Course course;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisChatSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_hestia_settings_id")
    private IrisHestiaSubSettings irisHestiaSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_code_editor_settings_id")
    private IrisCodeEditorSubSettings irisCodeEditorSettings;

    @Override
    public boolean isValid() {
        return course != null;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public IrisChatSubSettings getIrisChatSettings() {
        return irisChatSettings;
    }

    public void setIrisChatSettings(IrisChatSubSettings irisChatSettings) {
        this.irisChatSettings = irisChatSettings;
    }

    public IrisHestiaSubSettings getIrisHestiaSettings() {
        return irisHestiaSettings;
    }

    public void setIrisHestiaSettings(IrisHestiaSubSettings irisHestiaSettings) {
        this.irisHestiaSettings = irisHestiaSettings;
    }

    public IrisCodeEditorSubSettings getIrisCodeEditorSettings() {
        return irisCodeEditorSettings;
    }

    public void setIrisCodeEditorSettings(IrisCodeEditorSubSettings irisCodeEditorSettings) {
        this.irisCodeEditorSettings = irisCodeEditorSettings;
    }
}
