package de.tum.in.www1.artemis.web.rest.dto;

public class TutorLeaderboardDTO {

    private Long userId;

    private String name;

    private Long numberOfAssessments;

    private Long numberOfComplaints;

    private Long numberOfComplaintResponses;

    private Long points;

    public TutorLeaderboardDTO(Long userId, String name, Long numberOfAssessments, Long numberOfComplaints, Long numberOfComplaintResponses, Long points) {
        this.userId = userId;
        this.name = name;
        this.numberOfAssessments = numberOfAssessments;
        this.numberOfComplaints = numberOfComplaints;
        this.numberOfComplaintResponses = numberOfComplaintResponses;
        this.points = points;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getNumberOfAssessments() {
        return numberOfAssessments;
    }

    public void setNumberOfAssessments(Long numberOfAssessments) {
        this.numberOfAssessments = numberOfAssessments;
    }

    public Long getNumberOfComplaints() {
        return numberOfComplaints;
    }

    public void setNumberOfComplaints(Long numberOfComplaints) {
        this.numberOfComplaints = numberOfComplaints;
    }

    public Long getNumberOfComplaintResponses() {
        return numberOfComplaintResponses;
    }

    public void setNumberOfComplaintResponses(Long numberOfComplaintResponses) {
        this.numberOfComplaintResponses = numberOfComplaintResponses;
    }

    public Long getPoints() {
        return points;
    }

    public void setPoints(Long points) {
        this.points = points;
    }
}
