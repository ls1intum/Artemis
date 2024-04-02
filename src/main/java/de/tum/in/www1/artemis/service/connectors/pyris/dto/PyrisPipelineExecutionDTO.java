package de.tum.in.www1.artemis.service.connectors.pyris.dto;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public abstract class PyrisPipelineExecutionDTO {

    protected final PyrisPipelineExecutionSettingsDTO settings;

    protected final List<PyrisStageDTO> initialStages;

    public PyrisPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
        this.settings = settings;
        this.initialStages = initialStages;
    }

    public PyrisPipelineExecutionSettingsDTO getSettings() {
        return settings;
    }

    public List<PyrisStageDTO> getInitialStages() {
        return initialStages;
    }
}
