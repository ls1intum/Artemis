package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(int rank, int league, int studentLeague, User student, String leaderboardName, int score, int answeredCorrectly, int answeredWrong,
        long totalQuestions, ZonedDateTime dueDate, int streak) {
}
