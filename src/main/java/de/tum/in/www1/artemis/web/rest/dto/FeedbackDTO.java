package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Feedback;

import java.util.List;

public class FeedbackDTO {

    private List<Feedback> assessments;

    public List<Feedback> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<Feedback> assessments) {
        this.assessments = assessments;
    }
}
