package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "tutor_scores")
public class TutorScore {

    @Id
    @Column(name = "id")
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
    private long answeredFeedbackRequests;

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

    public long getAnsweredFeedbackRequests() {
        return answeredFeedbackRequests;
    }

    public double getAnsweredFeedbackRequestsPoints() {
        return answeredFeedbackRequestsPoints;
    }

    public TutorScore() {
        // Empty constructor because of @Entity
    }

    public TutorScore(long tutorScoreId, long tutorId, long exerciseId) {
        this.tutorScoreId = tutorScoreId;
        this.tutorId = tutorId;
        this.exerciseId = exerciseId;
    }

    /*public TutorScore(long tutorScoreId, long tutorId, long exerciseId, long assessments, double assessmentsPoints, long allComplaints, long acceptedComplaints,
            double complaintsPoints, long allFeedbackRequests, long notAnsweredFeedbackRequests, double feedbackRequestsPoints, long answeredFeedbackRequests,
            double answeredFeedbackRequestsPoints) {
        this.tutorScoreId = tutorScoreId;
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
        this.answeredFeedbackRequests = answeredFeedbackRequests;
        this.answeredFeedbackRequestsPoints = answeredFeedbackRequestsPoints;
    }*/
}
