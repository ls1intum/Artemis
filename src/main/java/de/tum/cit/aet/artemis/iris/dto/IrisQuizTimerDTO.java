package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisQuizTimerDTO(ZonedDateTime timerExpiresAt, int timeLimit) {
}
