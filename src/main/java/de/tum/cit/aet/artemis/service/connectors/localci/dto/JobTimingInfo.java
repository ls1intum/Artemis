package de.tum.cit.aet.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JobTimingInfo(ZonedDateTime submissionDate, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate) implements Serializable {
}
