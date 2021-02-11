package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * This DTO contains the information used for the exercise-scores-chart
 */
public class ExerciseScoresDTO {

    public Exercise exercise;

    public Long scoreOfStudent;

    public Long averageScoreAchieved;

    public Long maxScoreAchieved;

}
