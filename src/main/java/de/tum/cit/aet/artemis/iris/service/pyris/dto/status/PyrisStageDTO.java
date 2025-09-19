package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStageDTO(String name, int weight, PyrisStageState state, String message, @JsonProperty(defaultValue = "false") boolean internal) {

    public PyrisStageDTO notStarted() {
        return new PyrisStageDTO(name, weight, PyrisStageState.NOT_STARTED, message, internal);
    }

    public PyrisStageDTO inProgress() {
        return new PyrisStageDTO(name, weight, PyrisStageState.IN_PROGRESS, message, internal);
    }

    public PyrisStageDTO error(String message) {
        return new PyrisStageDTO(name, weight, PyrisStageState.ERROR, message, internal);
    }

    public PyrisStageDTO done() {
        return new PyrisStageDTO(name, weight, PyrisStageState.DONE, message, internal);
    }

    public PyrisStageDTO with(PyrisStageState state, String message) {
        return new PyrisStageDTO(name, weight, state, message, internal);
    }

}
