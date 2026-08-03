package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisQuizTimerDTO(@Nullable ZonedDateTime timerExpiresAt, int timeLimit) {
}
