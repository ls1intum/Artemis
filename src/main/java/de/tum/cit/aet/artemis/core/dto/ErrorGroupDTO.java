package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;

/**
 * Errors that are alike, grouped by the error analysis.
 *
 * @param location  where the errors happened
 * @param severity  how serious they are
 * @param message   what they have in common: the message with the numbers, identifiers and personal data replaced by placeholders
 * @param count     how often they happened in the selected time span
 * @param firstSeen when the first of them happened since the server started, which can be before the selected time span
 * @param lastSeen  when the last of them happened
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorGroupDTO(ErrorLocation location, ErrorSeverity severity, String message, long count, Instant firstSeen, Instant lastSeen) {
}
