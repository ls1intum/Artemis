package de.tum.in.www1.artemis.service.connectors.iris.dto;

public record IrisStatusDTO(String model, ModelStatus status) {

    public enum ModelStatus {
        UP, DOWN, NOT_AVAILABLE
    }
}
