package de.tum.in.www1.artemis.web.rest.iris.dto;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public abstract class IrisStatusUpdateDTO {

    protected final List<PyrisStageDTO> stages;

    protected IrisStatusUpdateDTO(List<PyrisStageDTO> stages) {
        this.stages = stages;
    }

    public List<PyrisStageDTO> getStages() {
        return stages;
    }
}
