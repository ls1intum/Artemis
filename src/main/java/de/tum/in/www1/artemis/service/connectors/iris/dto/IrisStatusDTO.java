package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.Map;

import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;

public record IrisStatusDTO(Map<IrisModel, ModelStatus> modelStatuses) {

    public enum ModelStatus {
        UP, DOWN, NOT_AVAILABLE
    }
}
