package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a component and its associated vulnerabilities.
 * Uses a simple structure compatible with distributed caching.
 *
 * @param componentKey    unique identifier for the component (typically the purl)
 * @param vulnerabilities list of vulnerabilities affecting this component
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComponentWithVulnerabilitiesDTO(String componentKey, List<VulnerabilityDTO> vulnerabilities) implements Serializable {
}
