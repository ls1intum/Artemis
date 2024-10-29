package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum PyrisStageState {

    NOT_STARTED, IN_PROGRESS, DONE, SKIPPED, ERROR;

    public boolean isTerminal() {
        return this == DONE || this == SKIPPED || this == ERROR;
    }
}
