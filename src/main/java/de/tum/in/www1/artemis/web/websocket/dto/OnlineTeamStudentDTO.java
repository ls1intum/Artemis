package de.tum.in.www1.artemis.web.websocket.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineTeamStudentDTO(String login, Instant lastTypingDate, Instant lastActionDate) {
}
