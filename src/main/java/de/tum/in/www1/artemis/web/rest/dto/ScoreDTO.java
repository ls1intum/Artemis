package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

/**
 * Wrapper Class to send achieved points and achieved scores of a student to the client for courses / exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScoreDTO {

    public final Long studentId;

    public String studentLogin;

    public Double pointsAchieved;

    public Double scoreAchieved;

    public Double regularPointsAchievable;

    public ScoreDTO(User user) {
        this.studentId = user.getId();
        this.studentLogin = user.getLogin();
        this.pointsAchieved = 0.0;
        this.scoreAchieved = 0.0;
        this.regularPointsAchievable = 0.0;
    }
}
