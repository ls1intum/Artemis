package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.PointCounter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizStatisticCounterDTO(Long id, Integer ratedCounter, Integer unRatedCounter) {

    public static QuizStatisticCounterDTO of(PointCounter pointCounter) {
        return new QuizStatisticCounterDTO(pointCounter.getId(), pointCounter.getRatedCounter(), pointCounter.getUnRatedCounter());
    }
}
