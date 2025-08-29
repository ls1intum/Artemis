package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(int rank, long league, long studentLeague, String student, int totalScore, int score, int answeredCorrectly, int answeredWrong,
        int totalQuestions) {
}
