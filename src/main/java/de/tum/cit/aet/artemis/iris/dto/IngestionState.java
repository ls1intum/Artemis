package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum IngestionState {
    NOT_STARTED, IN_PROGRESS, PARTIALLY_INGESTED, DONE, ERROR
}
