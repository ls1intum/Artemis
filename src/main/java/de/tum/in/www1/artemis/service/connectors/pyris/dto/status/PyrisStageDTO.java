package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStageDTO(String name, int weight, PyrisStageState state, String message) {

    public PyrisStageDTO notStarted() {
        return new PyrisStageDTO(name, weight, PyrisStageState.NOT_STARTED, message);
    }

    public PyrisStageDTO inProgress() {
        return new PyrisStageDTO(name, weight, PyrisStageState.IN_PROGRESS, message);
    }

    public PyrisStageDTO error(String message) {
        return new PyrisStageDTO(name, weight, PyrisStageState.ERROR, message);
    }

    public PyrisStageDTO done() {
        return new PyrisStageDTO(name, weight, PyrisStageState.DONE, message);
    }

    public PyrisStageDTO with(PyrisStageState state, String message) {
        return new PyrisStageDTO(name, weight, state, message);
    }

}
