package de.tum.cit.aet.artemis.assessment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Impact of an AI programming feedback issue, independent of credits. */
public enum FeedbackSeverity {
    @JsonProperty("high")
    HIGH, @JsonProperty("medium")
    MEDIUM, @JsonProperty("low")
    LOW
}
