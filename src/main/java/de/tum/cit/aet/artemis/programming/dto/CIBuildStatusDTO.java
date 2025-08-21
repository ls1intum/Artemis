package de.tum.cit.aet.artemis.programming.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for CI build status information from external CI connectors.
 * This only contains the state of the build, not the results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CIBuildStatusDTO(String buildId, BuildStatus status, Instant startTime, Instant endTime, String commitHash, String branch, String errorMessage) {

    public enum BuildStatus {
        QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT
    }

    /**
     * Creates a simple status DTO with minimal information.
     */
    public static CIBuildStatusDTO simple(String buildId, BuildStatus status) {
        return new CIBuildStatusDTO(buildId, status, null, null, null, null, null);
    }

    /**
     * Creates a completed build status DTO.
     */
    public static CIBuildStatusDTO completed(String buildId, BuildStatus status, Instant startTime, Instant endTime, String commitHash) {
        return new CIBuildStatusDTO(buildId, status, startTime, endTime, commitHash, null, null);
    }

    /**
     * Creates a failed build status DTO.
     */
    public static CIBuildStatusDTO failed(String buildId, Instant startTime, String errorMessage) {
        return new CIBuildStatusDTO(buildId, BuildStatus.FAILED, startTime, Instant.now(), null, null, errorMessage);
    }
}
