package de.tum.in.www1.artemis.domain.iris.settings;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An {@link IrisSettings} implementation for exercise specific settings.
 * Exercise settings are used to override course settings and currently only allow setting the {@link IrisChatSubSettings}.
 */
@Entity
@DiscriminatorValue("EXERCISE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExerciseSettings extends IrisSettings {

    @OneToOne(optional = false)
    @JoinColumn(name = "exercise_id", unique = true, nullable = false)
    private Exercise exercise;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisChatSubSettings irisChatSettings;

    @Override
    public boolean isValid() {
        return exercise != null;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public IrisChatSubSettings getIrisChatSettings() {
        return irisChatSettings;
    }

    public void setIrisChatSettings(IrisChatSubSettings irisChatSettings) {
        this.irisChatSettings = irisChatSettings;
    }

    @Override
    public IrisHestiaSubSettings getIrisHestiaSettings() {
        return null;
    }

    @Override
    public void setIrisHestiaSettings(IrisHestiaSubSettings irisHestiaSettings) {

    }

    @Override
    public IrisCodeEditorSubSettings getIrisCodeEditorSettings() {
        return null;
    }

    @Override
    public void setIrisCodeEditorSettings(IrisCodeEditorSubSettings irisCodeEditorSettings) {

    }
}
