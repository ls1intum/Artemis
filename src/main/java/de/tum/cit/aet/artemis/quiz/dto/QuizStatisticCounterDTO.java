package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizStatisticCounterDTO(Integer ratedCounter, Integer unRatedCounter) {

    /**
     * Creates a counter DTO from the two normalized rating buckets.
     *
     * @param ratedCounter   the rated count
     * @param unratedCounter the unrated count
     * @return the counter DTO
     */
    public static QuizStatisticCounterDTO of(long ratedCounter, long unratedCounter) {
        return new QuizStatisticCounterDTO(Math.toIntExact(ratedCounter), Math.toIntExact(unratedCounter));
    }
}
