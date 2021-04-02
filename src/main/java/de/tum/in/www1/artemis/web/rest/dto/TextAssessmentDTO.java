package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextAssessmentDTO {

    private Set<TextBlock> textBlocks = new HashSet<>();

    private List<Feedback> feedbacks = new ArrayList<>();

    public TextAssessmentDTO() {
        // needed to make Jackson happy
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
