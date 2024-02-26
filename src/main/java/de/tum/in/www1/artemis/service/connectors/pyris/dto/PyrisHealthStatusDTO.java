package de.tum.in.www1.artemis.service.connectors.pyris.dto;

public record PyrisHealthStatusDTO(String model, ModelStatus status) {

    public enum ModelStatus {
        UP, DOWN, NOT_AVAILABLE
    }
}
