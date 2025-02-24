package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The analysis tool that was run.
 *
 * @param driver A component, such as a plug-in or the driver, of the analysis tool that was run.
 *                   (Required)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tool(ToolComponent driver) {
}
