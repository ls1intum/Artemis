package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * This DTO contains the information used for the exercise-scores-chart.component.ts
 * For every exercise we send the score of the requesting student, the average score achieved and the max score
 * achieved
 * <p>
 * Important: we need the release date information to sort the exercises in the chart by their release date
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseScoresDTO(long exerciseId, String exerciseTitle, ExerciseType exerciseType, ZonedDateTime releaseDate, double scoreOfStudent, double averageScoreAchieved,
        double maxScoreAchieved) {

    public static ExerciseScoresDTO of(Exercise exercise, double scoreOfStudent, double averageScoreAchieved, double maxScoreAchieved) {
        return new ExerciseScoresDTO(exercise.getId(), exercise.getTitle(), exercise.getExerciseType(), exercise.getReleaseDate(), scoreOfStudent, averageScoreAchieved,
                maxScoreAchieved);
    }
}
