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
    private IrisChatSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_lecture_ingestion_settings_id")
    private IrisLectureIngestionSubSettings irisLectureIngestionSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_competency_generation_settings_id")
    private IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings;

    @Override
    public IrisLectureIngestionSubSettings getIrisLectureIngestionSettings() {
        return irisLectureIngestionSettings;
    }

    @Override
    public void setIrisLectureIngestionSettings(IrisLectureIngestionSubSettings irisLectureIngestionSettings) {
        this.irisLectureIngestionSettings = irisLectureIngestionSettings;
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
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return irisCompetencyGenerationSettings;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings) {
        this.irisCompetencyGenerationSettings = irisCompetencyGenerationSettings;
    }
}
