package de.tum.cit.aet.artemis.jenkins.connector.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for build status responses from the Jenkins connector.
 * Contains only state information, not build results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildStatusResponseDTO(
    String buildId,
    BuildStatus status,
    Instant startTime,
    Instant endTime,
    String errorMessage
) {

    public enum BuildStatus {
        QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT
    }
}