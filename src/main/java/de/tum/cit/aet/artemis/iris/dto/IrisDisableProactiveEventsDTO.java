package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

/**
 * DTO for disabling proactive events.
 */
public record IrisDisableProactiveEventsDTO(IrisProactiveEventDisableDuration duration, @Nullable ZonedDateTime endTime) {
}
