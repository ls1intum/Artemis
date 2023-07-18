package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.Map;

public record IrisStatusDTO(Map<String, ModelStatus> modelStatuses) {

    public enum ModelStatus {
        UP, DOWN, NOT_AVAILABLE
    }
}
