package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

public record PyrisStageDTO(String name, int weight, PyrisStageStateDTO state, String message) {
}
