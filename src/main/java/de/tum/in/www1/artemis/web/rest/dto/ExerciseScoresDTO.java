package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This DTO contains the information used for the exercise-scores-chart
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseScoresDTO {

    public Long exerciseId;

    public String exerciseTitle;

    public String exerciseType;

    public ZonedDateTime releaseDate;

    public Long scoreOfStudent;

    public Long averageScoreAchieved;

    public Long maxScoreAchieved;

}
