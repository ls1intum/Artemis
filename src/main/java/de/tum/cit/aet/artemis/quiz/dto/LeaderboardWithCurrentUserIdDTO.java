package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardWithCurrentUserIdDTO(@NotNull List<LeaderboardEntryDTO> leaderboardEntryDTO, long currentUserId) {
}
