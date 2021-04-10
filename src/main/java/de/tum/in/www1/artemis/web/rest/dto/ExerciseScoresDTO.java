package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This DTO contains the information used for the exercise-scores-chart.component.ts
 * For every exercise we send the score of the requesting student, the average score achieved and the max score
 * achieved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseScoresDTO {

    public ExerciseScoresDTO() {
        // empty constructor for jackson
    }

    public Long exerciseId;

    public String exerciseTitle;

    public String exerciseType;

    public ZonedDateTime releaseDate;

    public Double scoreOfStudent;

    public Double averageScoreAchieved;

    public Double maxScoreAchieved;

}
