package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A message string or message format string rendered in multiple formats.
 *
 * @param text A plain text message string or format string.
 *                 (Required)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultiformatMessageString(String text) {
}
