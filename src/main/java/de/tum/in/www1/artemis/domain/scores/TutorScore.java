package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "tutor_score")
public class TutorScore extends DomainObject {

    @ManyToOne
    private User tutor;

    @ManyToOne
    private Exercise exercise;

    @Column(name = "assessments")
    private long assessments;

    @Column(name = "assessments_points")
    private double assessmentsPoints;

    @Column(name = "all_complaints")
    private long allComplaints;

    @Column(name = "accepted_complaints")
    private long acceptedComplaints;

    @Column(name = "complaints_points")
    private double complaintsPoints;

    @Column(name = "complaint_responses")
    private long complaintResponses;

    @Column(name = "complaint_responses_points")
    private double complaintResponsesPoints;

    @Column(name = "all_feedback_requests")
    private long allFeedbackRequests;

    @Column(name = "not_answered_feedback_requests")
    private long notAnsweredFeedbackRequests;

    @Column(name = "feedback_requests_points")
    private double feedbackRequestsPoints;

    @Column(name = "answered_feedback_requests")
    private long answeredFeedbackRequests;

    @Column(name = "answered_feedback_requests_points")
    private double answeredFeedbackRequestsPoints;

    public User getTutor() {
        return tutor;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public long getAssessments() {
        return assessments;
    }

    public double getAssessmentsPoints() {
        return assessmentsPoints;
    }

    public long getAllComplaints() {
        return allComplaints;
    }

    public long getAcceptedComplaints() {
        return acceptedComplaints;
    }

    public double getComplaintsPoints() {
        return complaintsPoints;
    }

    public long getComplaintResponses() {
        return complaintResponses;
    }

    public double getComplaintResponsesPoints() {
        return complaintResponsesPoints;
    }

    public long getAllFeedbackRequests() {
        return allFeedbackRequests;
    }

    public long getNotAnsweredFeedbackRequests() {
        return notAnsweredFeedbackRequests;
    }

    public double getFeedbackRequestsPoints() {
        return feedbackRequestsPoints;
    }

    public long getAnsweredFeedbackRequests() {
        return answeredFeedbackRequests;
    }

    public double getAnsweredFeedbackRequestsPoints() {
        return answeredFeedbackRequestsPoints;
    }

    public void setTutor(User tutor) {
        this.tutor = tutor;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public void setAssessments(long assessments) {
        this.assessments = assessments;
    }

    public void setAssessmentsPoints(double assessmentsPoints) {
        this.assessmentsPoints = assessmentsPoints;
    }

    public void setAllComplaints(long allComplaints) {
        this.allComplaints = allComplaints;
    }

    public void setAcceptedComplaints(long acceptedComplaints) {
        this.acceptedComplaints = acceptedComplaints;
    }

    public void setComplaintsPoints(double complaintsPoints) {
        this.complaintsPoints = complaintsPoints;
    }

    public void setComplaintResponses(long complaintResponses) {
        this.complaintResponses = complaintResponses;
    }

    public void setComplaintResponsesPoints(double complaintResponsesPoints) {
        this.complaintResponsesPoints = complaintResponsesPoints;
    }

    public void setAllFeedbackRequests(long allFeedbackRequests) {
        this.allFeedbackRequests = allFeedbackRequests;
    }

    public void setNotAnsweredFeedbackRequests(long notAnsweredFeedbackRequests) {
        this.notAnsweredFeedbackRequests = notAnsweredFeedbackRequests;
    }

    public void setFeedbackRequestsPoints(double feedbackRequestsPoints) {
        this.feedbackRequestsPoints = feedbackRequestsPoints;
    }

    public void setAnsweredFeedbackRequests(long answeredFeedbackRequests) {
        this.answeredFeedbackRequests = answeredFeedbackRequests;
    }

    public void setAnsweredFeedbackRequestsPoints(double answeredFeedbackRequestsPoints) {
        this.answeredFeedbackRequestsPoints = answeredFeedbackRequestsPoints;
    }

    public TutorScore() {
        // Empty constructor because of @Entity
    }

    public TutorScore(User tutor, Exercise exercise, long assessments, double assessmentsPoints) {
        this.tutor = tutor;
        this.exercise = exercise;
        this.assessments = assessments;
        this.assessmentsPoints = assessmentsPoints;
    }
}
