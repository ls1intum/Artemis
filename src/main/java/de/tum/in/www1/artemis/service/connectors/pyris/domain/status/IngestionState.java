package de.tum.in.www1.artemis.service.connectors.pyris.domain.status;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum IngestionState {
    NOT_STARTED, IN_PROGRESS, PARTIALLY_INGESTED, DONE, ERROR
}
