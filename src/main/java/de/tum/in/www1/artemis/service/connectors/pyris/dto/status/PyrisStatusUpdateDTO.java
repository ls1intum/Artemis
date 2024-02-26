package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import java.util.List;

public abstract class PyrisStatusUpdateDTO {

    private final List<PyrisStageDTO> stages;

    public PyrisStatusUpdateDTO(List<PyrisStageDTO> stages) {
        this.stages = stages;
    }

    public List<PyrisStageDTO> getStages() {
        return stages;
    }
}
