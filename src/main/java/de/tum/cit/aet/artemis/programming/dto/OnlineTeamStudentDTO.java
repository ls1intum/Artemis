package de.tum.cit.aet.artemis.programming.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineTeamStudentDTO(String login, Instant lastTypingDate, Instant lastActionDate) {
}
