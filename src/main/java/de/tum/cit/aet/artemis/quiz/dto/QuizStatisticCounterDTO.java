package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizStatisticCounterDTO(Long id, Integer ratedCounter, Integer unRatedCounter) {

    public static QuizStatisticCounterDTO of(long[] counters) {
        return new QuizStatisticCounterDTO(null, Math.toIntExact(counters[0]), Math.toIntExact(counters[1]));
    }
}
