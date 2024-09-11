package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StatsForDashboardDTO {

    private Long numberOfStudents;

    private DueDateStat numberOfSubmissions;

    private DueDateStat totalNumberOfAssessments;

    private Long totalNumberOfAssessmentLocks;

    private Boolean complaintsEnabled;

    private Boolean feedbackRequestEnabled;

    private DueDateStat[] numberOfAssessmentsOfCorrectionRounds;

    private DueDateStat[] numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;

    private DueDateStat numberOfAutomaticAssistedAssessments;

    private Long numberOfComplaints;

    private Long numberOfOpenComplaints;

    private Long numberOfMoreFeedbackRequests;

    private Long numberOfOpenMoreFeedbackRequests;

    private Long numberOfAssessmentLocks;

    private List<TutorLeaderboardDTO> tutorLeaderboardEntries = new ArrayList<>();

    private Long numberOfRatings;

    /**
     * Empty constructor is needed by Jackson
     */
    public StatsForDashboardDTO() {
    }

    public Long getNumberOfStudents() {
        return numberOfStudents;
    }

    public void setNumberOfStudents(Long numberOfStudents) {
        this.numberOfStudents = numberOfStudents;
    }

    public DueDateStat getNumberOfSubmissions() {
        return numberOfSubmissions;
    }

    public void setNumberOfSubmissions(DueDateStat numberOfSubmissions) {
        this.numberOfSubmissions = numberOfSubmissions;
    }

    public DueDateStat getTotalNumberOfAssessments() {
        return totalNumberOfAssessments;
    }

    public void setTotalNumberOfAssessments(DueDateStat totalNumberOfAssessments) {
        this.totalNumberOfAssessments = totalNumberOfAssessments;
    }

    public DueDateStat[] getNumberOfAssessmentsOfCorrectionRounds() {
        return numberOfAssessmentsOfCorrectionRounds;
    }

    public void setNumberOfAssessmentsOfCorrectionRounds(DueDateStat[] numberOfAssessmentsOfCorrectionRounds) {
        this.numberOfAssessmentsOfCorrectionRounds = numberOfAssessmentsOfCorrectionRounds;
    }

    public DueDateStat getNumberOfAutomaticAssistedAssessments() {
        return numberOfAutomaticAssistedAssessments;
    }

    public void setNumberOfAutomaticAssistedAssessments(DueDateStat numberOfAutomaticAssistedAssessments) {
        this.numberOfAutomaticAssistedAssessments = numberOfAutomaticAssistedAssessments;
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

    public Long getNumberOfAssessmentLocks() {
        return numberOfAssessmentLocks;
    }

    public void setNumberOfAssessmentLocks(long numberOfAssessmentLocks) {
        this.numberOfAssessmentLocks = numberOfAssessmentLocks;
    }

    public Long getTotalNumberOfAssessmentLocks() {
        return totalNumberOfAssessmentLocks;
    }

    public void setTotalNumberOfAssessmentLocks(long totalNumberOfAssessmentLocks) {
        this.totalNumberOfAssessmentLocks = totalNumberOfAssessmentLocks;
    }

    public List<TutorLeaderboardDTO> getTutorLeaderboardEntries() {
        return tutorLeaderboardEntries;
    }

    public void setTutorLeaderboardEntries(List<TutorLeaderboardDTO> tutorLeaderboardEntries) {
        this.tutorLeaderboardEntries = tutorLeaderboardEntries;
    }

    public DueDateStat[] getNumberOfLockedAssessmentByOtherTutorsOfCorrectionRound() {
        return numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;
    }

    public void setNumberOfLockedAssessmentByOtherTutorsOfCorrectionRound(DueDateStat[] numberOfLockedAssessmentByOtherTutorsOfCorrectionRound) {
        this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;
    }

    public Boolean getComplaintsEnabled() {
        return complaintsEnabled;
    }

    public void setComplaintsEnabled(Boolean complaintsEnabled) {
        this.complaintsEnabled = complaintsEnabled;
    }

    public Boolean getFeedbackRequestEnabled() {
        return feedbackRequestEnabled;
    }

    public void setFeedbackRequestEnabled(Boolean feedbackRequestEnabled) {
        this.feedbackRequestEnabled = feedbackRequestEnabled;
    }

    public long getNumberOfRatings() {
        return numberOfRatings == null ? 0L : numberOfRatings;
    }

    public void setNumberOfRatings(long numberOfRatings) {
        this.numberOfRatings = numberOfRatings;
    }
}
