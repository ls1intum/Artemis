package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Static Analysis Results Format (SARIF) Version 2.1.0 JSON Schema
 * <p>
 * Static Analysis Results Format (SARIF) Version 2.1.0 JSON Schema: a standard format for the output of static analysis tools.
 *
 * @param runs The set of runs contained in this log file.
 *                 (Required)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SarifLog(List<Run> runs) {
}
