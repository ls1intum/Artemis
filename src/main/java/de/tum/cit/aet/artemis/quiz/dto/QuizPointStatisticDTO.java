package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Point-bucket statistics calculated on demand from quiz results.
 * Participant counts are per rating bucket: one participation can contribute its latest rated result and its latest unrated result.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizPointStatisticDTO(Set<PointCounterDTO> pointCounters, @JsonUnwrapped QuizStatisticDTO quizStatistic) {

    public static QuizPointStatisticDTO of(Map<Double, QuizStatisticCounterDTO> countersByPoints, long ratedResultCount, long unratedResultCount) {
        Set<PointCounterDTO> pointCounters = countersByPoints.entrySet().stream().map(entry -> new PointCounterDTO(entry.getKey(), entry.getValue())).collect(Collectors.toSet());
        QuizStatisticDTO quizStatistic = new QuizStatisticDTO(Math.toIntExact(ratedResultCount), Math.toIntExact(unratedResultCount));
        return new QuizPointStatisticDTO(pointCounters, quizStatistic);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizStatisticDTO(Integer participantsRated, Integer participantsUnrated) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record PointCounterDTO(Double points, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {
}
