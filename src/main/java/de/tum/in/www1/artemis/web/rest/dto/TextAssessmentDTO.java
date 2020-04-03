package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;

public class TextAssessmentDTO {

    private List<TextBlock> textBlocks;

    private List<Feedback> feedbacks;

    public TextAssessmentDTO() {
    }

    public TextAssessmentDTO(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public List<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public void setTextBlocks(List<TextBlock> textBlocks) {
        this.textBlocks = textBlocks;
    }

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }
}
