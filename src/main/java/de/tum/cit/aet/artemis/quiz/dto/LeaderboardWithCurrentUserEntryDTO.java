package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardWithCurrentUserEntryDTO(@NotNull List<LeaderboardEntryDTO> leaderboardEntries, boolean hasUserSetSettings, @NotNull LeaderboardEntryDTO currentUserEntry,
        @NotNull ZonedDateTime currentTime) {
}
