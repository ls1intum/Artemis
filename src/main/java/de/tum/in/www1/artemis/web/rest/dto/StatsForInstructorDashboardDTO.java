package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class StatsForInstructorDashboardDTO {

    private Long numberOfStudents;

    private Long numberOfSubmissions;

    private Long numberOfAssessments;

    private Long numberOfComplaints;

    private Long numberOfOpenComplaints;

    private Long numberOfMoreFeedbackRequests;

    private Long numberOfOpenMoreFeedbackRequests;

    private List<TutorLeaderboardDTO> tutorLeaderboardEntries;

    public StatsForInstructorDashboardDTO() {
    }

    public Long getNumberOfStudents() {
        return numberOfStudents;
    }

    public void setNumberOfStudents(Long numberOfStudents) {
        this.numberOfStudents = numberOfStudents;
    }

    public Long getNumberOfSubmissions() {
        return numberOfSubmissions;
    }

    public void setNumberOfSubmissions(Long numberOfSubmissions) {
        this.numberOfSubmissions = numberOfSubmissions;
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

    public Long getNumberOfOpenComplaints() {
        return numberOfOpenComplaints;
    }

    public void setNumberOfOpenComplaints(Long numberOfOpenComplaints) {
        this.numberOfOpenComplaints = numberOfOpenComplaints;
    }

    public Long getNumberOfMoreFeedbackRequests() {
        return numberOfMoreFeedbackRequests;
    }

    public void setNumberOfMoreFeedbackRequests(Long numberOfMoreFeedbackRequests) {
        this.numberOfMoreFeedbackRequests = numberOfMoreFeedbackRequests;
    }

    public Long getNumberOfOpenMoreFeedbackRequests() {
        return numberOfOpenMoreFeedbackRequests;
    }

    public void setNumberOfOpenMoreFeedbackRequests(Long numberOfOpenMoreFeedbackRequests) {
        this.numberOfOpenMoreFeedbackRequests = numberOfOpenMoreFeedbackRequests;
    }

    public List<TutorLeaderboardDTO> getTutorLeaderboardEntries() {
        return tutorLeaderboardEntries;
    }

    public void setTutorLeaderboardEntries(List<TutorLeaderboardDTO> tutorLeaderboardEntries) {
        this.tutorLeaderboardEntries = tutorLeaderboardEntries;
    }
}
