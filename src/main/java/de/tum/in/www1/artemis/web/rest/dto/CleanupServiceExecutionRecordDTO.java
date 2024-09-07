package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * todo
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CleanupServiceExecutionRecordDTO(ZonedDateTime executionDate) {
}
