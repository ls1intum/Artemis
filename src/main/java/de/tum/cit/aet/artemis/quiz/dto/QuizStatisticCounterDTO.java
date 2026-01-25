package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizStatisticCounter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizStatisticCounterDTO(Long id, Integer ratedCounter, Integer unRatedCounter) {

    public static QuizStatisticCounterDTO of(QuizStatisticCounter quizStatisticCounter) {
        return new QuizStatisticCounterDTO(quizStatisticCounter.getId(), quizStatisticCounter.getRatedCounter(), quizStatisticCounter.getUnRatedCounter());
    }
}
