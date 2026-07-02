package de.tum.cit.aet.artemis.programming.service.hades.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Hades build response.
 * Represents the response from Hades when a build is successfully triggered.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesBuildResponseDTO(@JsonProperty("job_id") String jobId, String message) {
}
