package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a Software Bill of Materials (SBOM) response.
 *
 * @param bomFormat    the format of the SBOM (e.g., CycloneDX)
 * @param specVersion  the specification version of the SBOM format
 * @param serialNumber the unique serial number of this SBOM
 * @param version      the version of this SBOM document
 * @param metadata     metadata about the SBOM generation
 * @param components   the list of components in the SBOM
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SbomDTO(String bomFormat, String specVersion, String serialNumber, int version, SbomMetadataDTO metadata, List<SbomComponentDTO> components) {
}
