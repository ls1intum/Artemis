package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;

public class TextAssessmentDTO {

    private Set<TextBlock> textBlocks;

    private List<Feedback> feedbacks;

    public TextAssessmentDTO() {
    }

    public TextAssessmentDTO(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public Set<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public void setTextBlocks(Set<TextBlock> textBlocks) {
        this.textBlocks = textBlocks;
    }

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }
}
