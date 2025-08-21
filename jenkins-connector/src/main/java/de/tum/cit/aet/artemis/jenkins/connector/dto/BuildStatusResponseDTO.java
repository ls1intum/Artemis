package de.tum.cit.aet.artemis.jenkins.connector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the status of a build job.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildStatusResponseDTO(String buildId, BuildStatus status, String message) {

    public enum BuildStatus {
        QUEUED,
        RUNNING, 
        COMPLETED,
        FAILED
    }
}