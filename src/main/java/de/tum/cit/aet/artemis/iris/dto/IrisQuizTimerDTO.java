package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Timer information for an Iris ask-user-mode quiz question sent to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisQuizTimerDTO(@Nullable ZonedDateTime timerExpiresAt, int timeLimit) {
}
