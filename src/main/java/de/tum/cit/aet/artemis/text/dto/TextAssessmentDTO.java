package de.tum.cit.aet.artemis.text.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextAssessmentDTO {

    private Set<TextBlock> textBlocks = new HashSet<>();

    private List<Feedback> feedbacks = new ArrayList<>();

    private String assessmentNote;

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

    public String getAssessmentNote() {
        return assessmentNote;
    }

    public void setAssessmentNote(String assessmentNote) {
        this.assessmentNote = assessmentNote;
    }
}
