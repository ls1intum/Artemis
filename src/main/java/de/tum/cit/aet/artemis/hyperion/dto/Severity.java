package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enum for consistency issue severity levels.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum Severity {

    LOW, MEDIUM, HIGH;
}
