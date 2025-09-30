package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(int rank, int selectedLeague, long userId, @NotNull String userName, @Nullable String imageURL, int score, int answeredCorrectly,
        int answeredWrong, long totalQuestions, @NotNull ZonedDateTime dueDate, int streak) {
}
