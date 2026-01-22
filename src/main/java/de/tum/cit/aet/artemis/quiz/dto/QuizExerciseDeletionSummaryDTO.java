package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfSubmissions, long numberOfQuizQuestions, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
