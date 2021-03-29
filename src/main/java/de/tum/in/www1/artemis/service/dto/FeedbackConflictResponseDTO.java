package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackConflictType;

/**
 * A DTO representing feedback conflicts (e.g. retrieved from Athene)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FeedbackConflictResponseDTO {

    private long firstFeedbackId;

    private long secondFeedbackId;

    private FeedbackConflictType type;

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

    public FeedbackConflictType getType() {
        return type;
    }

    public void setType(FeedbackConflictType type) {
        this.type = type;
    }
}
