package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum PyrisStageState {

    NOT_STARTED(false), IN_PROGRESS(false), DONE(true), SKIPPED(true), ERROR(true);

    private final boolean isTerminal;

    PyrisStageState(boolean isTerminal) {
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }
}
