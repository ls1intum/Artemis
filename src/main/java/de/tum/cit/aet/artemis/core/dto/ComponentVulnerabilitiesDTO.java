package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing vulnerability information for all components.
 * Uses a simple List structure compatible with distributed caching (Hazelcast).
 *
 * @param vulnerabilities      list of components with their vulnerabilities
 * @param totalVulnerabilities total number of vulnerabilities found
 * @param criticalCount        number of critical severity vulnerabilities
 * @param highCount            number of high severity vulnerabilities
 * @param mediumCount          number of medium severity vulnerabilities
 * @param lowCount             number of low severity vulnerabilities
 * @param lastChecked          timestamp when vulnerabilities were last checked (ISO 8601)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComponentVulnerabilitiesDTO(List<ComponentWithVulnerabilitiesDTO> vulnerabilities, int totalVulnerabilities, int criticalCount, int highCount, int mediumCount,
        int lowCount, String lastChecked) implements Serializable {
}
