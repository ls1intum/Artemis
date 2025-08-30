package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntryDTO(Integer rank, Long league, Long studentLeague, String student, Integer totalScore, Integer score, Integer answeredCorrectly,
        Integer answeredWrong, Integer totalQuestions) {
}
