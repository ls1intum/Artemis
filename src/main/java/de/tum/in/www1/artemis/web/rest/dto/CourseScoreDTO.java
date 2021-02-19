package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseScoreDTO {

    public Long studentId;

    public String studentLogin;

    public Double pointsAchieved;

    public Double scoreAchieved;

    public Double regularPointsAchievable;

    public CourseScoreDTO(User user) {
        this.studentId = user.getId();
        this.studentLogin = user.getLogin();
        this.pointsAchieved = 0.0;
        this.scoreAchieved = 0.0;
        this.regularPointsAchievable = 0.0;
    }

    public CourseScoreDTO() {
        // for jackson
    }
}
