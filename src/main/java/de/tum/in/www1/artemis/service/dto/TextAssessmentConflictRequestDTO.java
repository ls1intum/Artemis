package de.tum.in.www1.artemis.service.dto;

/**
 * A DTO representing a text assessment to be sent to remote Athene service the check the feedback conflicts.
 */
public class TextAssessmentConflictRequestDTO {

    private final String textBlockId;

    private final String text;

    private final Long clusterId;

    private final Long feedbackId;

    private final String feedbackText;

    private final Double credits;

    public TextAssessmentConflictRequestDTO(String textBlockId, String text, Long clusterId, Long feedbackId, String feedbackText, Double credits) {
        this.textBlockId = textBlockId;
        this.text = text;
        this.clusterId = clusterId;
        this.feedbackId = feedbackId;
        this.feedbackText = feedbackText;
        this.credits = credits;
    }

    public String getTextBlockId() {
        return textBlockId;
    }

    public String getText() {
        return text;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public Double getCredits() {
        return credits;
    }
}
