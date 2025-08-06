package de.tum.cit.aet.artemis.quiz.dto;

public record LeaderboardEntryDTO(int rank, long league, long studentLeague, String student, int score, int answeredCorrectly, int answeredWrong, int totalQuestions) {
}
