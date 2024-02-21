package de.tum.in.www1.artemis.service.connectors.iris.dto;

public abstract class PyrisPipelineExecutionDTO {

    protected final PyrisPipelineExecutionSettingsDTO settings;

    public PyrisPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings) {
        this.settings = settings;
    }

    public PyrisPipelineExecutionSettingsDTO getSettings() {
        return settings;
    }
}
