package de.tum.in.www1.artemis.domain;

import java.util.List;

/**
 * A helper object that aggregates a feedback list and a complaint response which is used to update an assessment after a complaint.
 */
public class AssessmentUpdate {

    /**
     * The updated feedback list
     */
    private List<Feedback> feedbacks;

    /**
     * The corresponding complaint response
     */
    private ComplaintResponse complaintResponse;

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public ComplaintResponse getComplaintResponse() {
        return complaintResponse;
    }

    public void setComplaintResponse(ComplaintResponse complaintResponse) {
        this.complaintResponse = complaintResponse;
    }
}
