package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;

/**
 * DTO for the response of disabling proactive events.
 */
public record IrisDisableProactiveEventsResponseDTO(Instant disabledUntil) {
}
