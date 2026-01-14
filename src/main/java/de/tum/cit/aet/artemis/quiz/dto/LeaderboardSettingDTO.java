package de.tum.cit.aet.artemis.quiz.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardSettingDTO(@Nullable Boolean showInLeaderboard) {
}
