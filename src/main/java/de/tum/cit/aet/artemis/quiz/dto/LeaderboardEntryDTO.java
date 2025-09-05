package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(int rank, int league, int studentLeague, long userId, String userName, String imageURL, String leaderboardName, int score, int answeredCorrectly,
        int answeredWrong, long totalQuestions, ZonedDateTime dueDate, int streak) {
}
