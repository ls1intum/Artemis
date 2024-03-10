package de.tum.in.www1.artemis.domain;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.dto.ComplaintResponseUpdateDTO;

/**
 * A helper object that aggregates a feedback list and a complaint response which is used to update an assessment after a complaint.
 */
public class AssessmentUpdate {

    /**
     * The updated feedback list
     */
    private List<Feedback> feedbacks = new ArrayList<>();

    /**
     * The corresponding complaint response
     */
    private ComplaintResponseUpdateDTO complaintResponse;

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public AssessmentUpdate feedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
        return this;
    }

    public ComplaintResponseUpdateDTO getComplaintResponse() {
        return complaintResponse;
    }

    public void setComplaintResponse(ComplaintResponseUpdateDTO complaintResponse) {
        this.complaintResponse = complaintResponse;
    }

    public AssessmentUpdate complaintResponse(ComplaintResponseUpdateDTO complaintResponse) {
        this.complaintResponse = complaintResponse;
        return this;
    }
}
