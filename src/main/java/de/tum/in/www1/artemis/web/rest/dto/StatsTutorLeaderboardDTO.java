package de.tum.in.www1.artemis.web.rest.dto;

public class StatsTutorLeaderboardDTO {

    public String name;

    public String login;

    public int numberOfAssessments;

    public int numberOfComplaints;

    public StatsTutorLeaderboardDTO(String name, String login, int numberOfAssessments, int numberOfComplaints) {
        this.name = name;
        this.login = login;
        this.numberOfAssessments = numberOfAssessments;
        this.numberOfComplaints = numberOfComplaints;
    }
}
