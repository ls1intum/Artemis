package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;

public class TutorScore {

    @EmbeddedId
    private long tutorScoreId;

    @Column(name = "tutor_id")
    private long tutorId;

    @Column(name = "exercise_id")
    private long exerciseId;

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

    @Column(name = "all_feedback_requests")
    private long allFeedbackRequests;

    @Column(name = "not_answered_feedback_requests")
    private long notAnsweredFeedbackRequests;

    @Column(name = "feedback_requests_points")
    private double feedbackRequestsPoints;

    @Column(name = "answered_feedback_requests")
    private long answered_feedback_requests;

    @Column(name = "answered_feedback_requests_points")
    private double answeredFeedbackRequestsPoints;

    public long getTutorScoreId() {
        return tutorScoreId;
    }

    public long getTutorId() {
        return tutorId;
    }

    public long getExerciseId() {
        return exerciseId;
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

    public long getAllFeedbackRequests() {
        return allFeedbackRequests;
    }

    public long getNotAnsweredFeedbackRequests() {
        return notAnsweredFeedbackRequests;
    }

    public double getFeedbackRequestsPoints() {
        return feedbackRequestsPoints;
    }

    public long getAnswered_feedback_requests() {
        return answered_feedback_requests;
    }

    public double getAnsweredFeedbackRequestsPoints() {
        return answeredFeedbackRequestsPoints;
    }

    public TutorScore(long tutorId, long exerciseId) {
        this.tutorId = tutorId;
        this.exerciseId = exerciseId;
    }

    public TutorScore(long tutorId, long exerciseId, long assessments, double assessmentsPoints, long allComplaints, long acceptedComplaints, double complaintsPoints, long allFeedbackRequests, long notAnsweredFeedbackRequests, double feedbackRequestsPoints, long answered_feedback_requests, double answeredFeedbackRequestsPoints) {
        this.tutorId = tutorId;
        this.exerciseId = exerciseId;
        this.assessments = assessments;
        this.assessmentsPoints = assessmentsPoints;
        this.allComplaints = allComplaints;
        this.acceptedComplaints = acceptedComplaints;
        this.complaintsPoints = complaintsPoints;
        this.allFeedbackRequests = allFeedbackRequests;
        this.notAnsweredFeedbackRequests = notAnsweredFeedbackRequests;
        this.feedbackRequestsPoints = feedbackRequestsPoints;
        this.answered_feedback_requests = answered_feedback_requests;
        this.answeredFeedbackRequestsPoints = answeredFeedbackRequestsPoints;
    }
}
