package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper Class to send achieved points and achieved scores of a student to the client for courses / exam
 */
// TODO: convert this into a record
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScoreDTO {

    public final Long studentId;

    public final String studentLogin;

    public Double pointsAchieved;

    public Double scoreAchieved;

    public Double regularPointsAchievable;

    @JsonCreator
    public ScoreDTO(Long studentId, String studentLogin, Double pointsAchieved, Double scoreAchieved, Double regularPointsAchievable) {
        this.studentId = studentId;
        this.studentLogin = studentLogin;
        this.pointsAchieved = pointsAchieved;
        this.scoreAchieved = scoreAchieved;
        this.regularPointsAchievable = regularPointsAchievable;
    }
}
