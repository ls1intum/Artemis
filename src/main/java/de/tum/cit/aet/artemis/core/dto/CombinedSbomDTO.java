package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing both server (Java/Gradle) and client (npm) SBOMs.
 *
 * @param server the server-side SBOM containing Java dependencies
 * @param client the client-side SBOM containing npm dependencies
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CombinedSbomDTO(SbomDTO server, SbomDTO client) {
}
