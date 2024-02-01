package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingAssessmentDTO {

    private List<Feedback> feedbacks = new ArrayList<>();

    private String assessmentNote;

    public ModelingAssessmentDTO() {

    }

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public String getAssessmentNote() {
        return assessmentNote;
    }

    public void setAssessmentNote(String assessmentNote) {
        this.assessmentNote = assessmentNote;
    }
}
