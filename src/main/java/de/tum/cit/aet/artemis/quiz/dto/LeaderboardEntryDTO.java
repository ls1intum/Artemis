package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(@Nullable Integer rank, @Nullable Integer league, @Nullable Integer studentLeague, @Nullable Long userId, @Nullable String userName,
        @Nullable String imageURL, @Nullable String leaderboardName, @Nullable Integer score, @Nullable Integer answeredCorrectly, @Nullable Integer answeredWrong,
        @Nullable Long totalQuestions, @Nullable ZonedDateTime dueDate, @Nullable Integer streak) {
}
