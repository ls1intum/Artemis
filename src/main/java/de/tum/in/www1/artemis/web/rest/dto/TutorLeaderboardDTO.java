package de.tum.in.www1.artemis.web.rest.dto;

public class TutorLeaderboardDTO {

    private long userId;

    private String name;

    private long numberOfAssessments;

    private long numberOfAcceptedComplaints;

    private long numberOfTutorComplaints;

    private long numberOfNotAnsweredMoreFeedbackRequests;

    private long numberOfComplaintResponses;

    private long numberOfAnsweredMoreFeedbackRequests;

    private long numberOfTutorMoreFeedbackRequests;

    private double points;

    public TutorLeaderboardDTO(long userId, String name, long numberOfAssessments, long numberOfAcceptedComplaints, long numberOfTutorComplaints,
            long numberOfNotAnsweredMoreFeedbackRequests, long numberOfComplaintResponses, long numberOfAnsweredMoreFeedbackRequests, long numberOfTutorMoreFeedbackRequests,
            double points) {
        this.userId = userId;
        this.name = name;
        this.numberOfAssessments = numberOfAssessments;
        this.numberOfAcceptedComplaints = numberOfAcceptedComplaints;
        this.numberOfTutorComplaints = numberOfTutorComplaints;
        this.numberOfNotAnsweredMoreFeedbackRequests = numberOfNotAnsweredMoreFeedbackRequests;
        this.numberOfComplaintResponses = numberOfComplaintResponses;
        this.numberOfAnsweredMoreFeedbackRequests = numberOfAnsweredMoreFeedbackRequests;
        this.numberOfTutorMoreFeedbackRequests = numberOfTutorMoreFeedbackRequests;
        this.points = points;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getNumberOfAssessments() {
        return numberOfAssessments;
    }

    public void setNumberOfAssessments(long numberOfAssessments) {
        this.numberOfAssessments = numberOfAssessments;
    }

    public long getNumberOfNotAnsweredMoreFeedbackRequests() {
        return numberOfNotAnsweredMoreFeedbackRequests;
    }

    public void setNumberOfNotAnsweredMoreFeedbackRequests(long numberOfNotAnsweredMoreFeedbackRequests) {
        this.numberOfNotAnsweredMoreFeedbackRequests = numberOfNotAnsweredMoreFeedbackRequests;
    }

    public long getNumberOfAcceptedComplaints() {
        return numberOfAcceptedComplaints;
    }

    public void setNumberOfAcceptedComplaints(long numberOfAcceptedComplaints) {
        this.numberOfAcceptedComplaints = numberOfAcceptedComplaints;
    }

    public long getNumberOfTutorComplaints() {
        return numberOfTutorComplaints;
    }

    public void setNumberOfTutorComplaints(long numberOfTutorComplaints) {
        this.numberOfTutorComplaints = numberOfTutorComplaints;
    }

    public long getNumberOfComplaintResponses() {
        return numberOfComplaintResponses;
    }

    public void setNumberOfComplaintResponses(long numberOfComplaintResponses) {
        this.numberOfComplaintResponses = numberOfComplaintResponses;
    }

    public long getNumberOfAnsweredMoreFeedbackRequests() {
        return numberOfAnsweredMoreFeedbackRequests;
    }

    public void setNumberOfAnsweredMoreFeedbackRequests(long numberOfAnsweredMoreFeedbackRequests) {
        this.numberOfAnsweredMoreFeedbackRequests = numberOfAnsweredMoreFeedbackRequests;
    }

    public long getNumberOfTutorMoreFeedbackRequests() {
        return numberOfTutorMoreFeedbackRequests;
    }

    public void setNumberOfTutorMoreFeedbackRequests(long numberOfTutorMoreFeedbackRequests) {
        this.numberOfTutorMoreFeedbackRequests = numberOfTutorMoreFeedbackRequests;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
    }
}
