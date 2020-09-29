package de.tum.in.www1.artemis.service.dto;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentConflictType;

/**
 * A DTO representing assessment conflicts (e.g. retrieved from Athene)
 */
public class AssessmentConflictResponseDTO {

    private long firstFeedbackId;

    private long secondFeedbackId;

    private AssessmentConflictType type;

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

    public AssessmentConflictType getType() {
        return type;
    }

    public void setType(AssessmentConflictType type) {
        this.type = type;
    }
}
