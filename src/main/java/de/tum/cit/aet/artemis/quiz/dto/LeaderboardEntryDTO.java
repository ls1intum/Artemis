package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(int rank, int selectedLeague, long userId, @NotNull String userName, @Nullable String imageURL, int score, int answeredCorrectly,
        int answeredWrong, long totalQuestions, @NotNull ZonedDateTime dueDate, int streak) {

    public static LeaderboardEntryDTO of(QuizTrainingLeaderboard leaderboardEntry, int rank, int league, long totalQuestions) {
        return new LeaderboardEntryDTO(rank, league, leaderboardEntry.getUser().getId(), leaderboardEntry.getUser().getName(), leaderboardEntry.getUser().getImageUrl(),
                leaderboardEntry.getScore(), leaderboardEntry.getAnsweredCorrectly(), leaderboardEntry.getAnsweredWrong(), totalQuestions, leaderboardEntry.getDueDate(),
                leaderboardEntry.getStreak());
    }
}
