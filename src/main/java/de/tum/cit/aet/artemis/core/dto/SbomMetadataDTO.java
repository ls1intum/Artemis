package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing metadata from a CycloneDX SBOM.
 *
 * @param timestamp     when the SBOM was generated
 * @param componentName the name of the main component
 * @param version       the version of the main component
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SbomMetadataDTO(Instant timestamp, String componentName, String version) {
}
