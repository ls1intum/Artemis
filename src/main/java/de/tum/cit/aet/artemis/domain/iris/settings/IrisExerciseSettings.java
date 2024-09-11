package de.tum.cit.aet.artemis.domain.iris.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;

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
    public IrisHestiaSubSettings getIrisHestiaSettings() {
        return null;
    }

    @Override
    public void setIrisHestiaSettings(IrisHestiaSubSettings irisHestiaSettings) {

    }

    @Override
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return null;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSubSettings) {

    }
}
