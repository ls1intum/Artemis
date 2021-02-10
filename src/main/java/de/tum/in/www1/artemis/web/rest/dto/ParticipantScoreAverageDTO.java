package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;

public class ParticipantScoreAverageDTO {

    public String userName;

    public String teamName;

    public Double averageScore;

    public Double averageRatedScore;

    public ParticipantScoreAverageDTO(User user, Double averageScore, Double averageRatedScore) {
        this.userName = user.getLogin();
        this.teamName = null;
        this.averageScore = averageScore;
        this.averageRatedScore = averageRatedScore;
    }

    public ParticipantScoreAverageDTO(Team team, Double averageScore, Double averageRatedScore) {
        this.userName = null;
        this.teamName = team.getName();
        this.averageScore = averageScore;
        this.averageRatedScore = averageRatedScore;
    }

    public ParticipantScoreAverageDTO() {
    }
}
