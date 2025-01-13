package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;

import jakarta.annotation.Nullable;

/**
 * DTO for disabling proactive events.
 */
public record IrisProactiveEventDisableDTO(IrisProactiveEventDisableDuration duration, @Nullable Instant endTime) {
}
