package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a build log entry for the local CI system. Not implemented as of now.
 *
 * @param date the date of the log entry.
 * @param log  the log entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCIBuildLogDTO(ZonedDateTime date, String log) {
}
