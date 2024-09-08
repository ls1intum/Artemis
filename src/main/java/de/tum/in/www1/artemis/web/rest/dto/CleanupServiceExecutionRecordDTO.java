package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;

/**
 * todo
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CleanupServiceExecutionRecordDTO(ZonedDateTime executionDate, String jobType) {

    public static CleanupServiceExecutionRecordDTO of(CleanupJobExecution cleanupJobExecution) {
        return new CleanupServiceExecutionRecordDTO(cleanupJobExecution.getDeletionTimestamp(), cleanupJobExecution.getCleanupJobType().getName());
    }

}
