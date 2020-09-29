package de.tum.in.www1.artemis.service.dto;

import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentConflictType;

/**
 * A DTO representing a text assessment conflicts to be retrieved from the remote Athene service.
 */
public class TextAssessmentConflictResponseDTO {

    private long firstFeedbackId;

    private long secondFeedbackId;

    private TextAssessmentConflictType type;

    public long getFirstFeedbackId() {
        return firstFeedbackId;
    }

    public void setFirstFeedbackId(long firstFeedbackId) {
        this.firstFeedbackId = firstFeedbackId;
    }

    public long getSecondFeedbackId() {
        return secondFeedbackId;
    }

    public void setSecondFeedbackId(long secondFeedbackId) {
        this.secondFeedbackId = secondFeedbackId;
    }

    public TextAssessmentConflictType getType() {
        return type;
    }

    public void setType(TextAssessmentConflictType type) {
        this.type = type;
    }
}
