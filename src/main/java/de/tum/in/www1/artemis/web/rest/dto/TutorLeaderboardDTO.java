package de.tum.in.www1.artemis.web.rest.dto;

public class TutorLeaderboardDTO {

    private Long userId;

    private String name;

    private Long numberOfAssessments;

    private Long numberOfAcceptedComplaints;

    private Long numberOfNotAnsweredMoreFeedbackRequests;

    private Long numberOfComplaintResponses;

    private Long points;

    public TutorLeaderboardDTO(Long userId, String name, Long numberOfAssessments, Long numberOfAcceptedComplaints, Long numberOfNotAnsweredMoreFeedbackRequests,
            Long numberOfComplaintResponses, Long points) {
        this.userId = userId;
        this.name = name;
        this.numberOfAssessments = numberOfAssessments;
        this.numberOfAcceptedComplaints = numberOfAcceptedComplaints;
        this.numberOfNotAnsweredMoreFeedbackRequests = numberOfNotAnsweredMoreFeedbackRequests;
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

    public Long getNumberOfNotAnsweredMoreFeedbackRequests() {
        return numberOfNotAnsweredMoreFeedbackRequests;
    }

    public void setNumberOfNotAnsweredMoreFeedbackRequests(Long numberOfNotAnsweredMoreFeedbackRequests) {
        this.numberOfNotAnsweredMoreFeedbackRequests = numberOfNotAnsweredMoreFeedbackRequests;
    }

    public Long getNumberOfAcceptedComplaints() {
        return numberOfAcceptedComplaints;
    }

    public void setNumberOfAcceptedComplaints(Long numberOfAcceptedComplaints) {
        this.numberOfAcceptedComplaints = numberOfAcceptedComplaints;
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
