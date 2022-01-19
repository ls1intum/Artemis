package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * This DTO contains the information used for the exercise-scores-chart.component.ts
 * For every exercise we send the score of the requesting student, the average score achieved and the max score
 * achieved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseScoresDTO {

    public Long exerciseId;

    public String exerciseTitle;

    public ExerciseType exerciseType;

    // we need the release date information to sort the exercises in the chart by their release date
    public ZonedDateTime releaseDate;

    public Double scoreOfStudent;

    public Double averageScoreAchieved;

    public Double maxScoreAchieved;

    public ExerciseScoresDTO() {
        // empty constructor for jackson
    }

    public ExerciseScoresDTO(Exercise exercise) {
        this.exerciseId = exercise.getId();
        this.exerciseTitle = exercise.getTitle();
        this.releaseDate = exercise.getReleaseDate();
        this.exerciseType = exercise.getExerciseType();
    }

}
