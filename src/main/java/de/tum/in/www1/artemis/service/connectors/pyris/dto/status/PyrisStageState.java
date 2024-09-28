package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum PyrisStageState {

    NOT_STARTED, IN_PROGRESS, DONE, SKIPPED, ERROR;

    public boolean isTerminal() {
        return this == DONE || this == SKIPPED || this == ERROR;
    }
}
