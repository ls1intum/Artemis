package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An unfinished assessment owned by the current user on an older submission. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentDraftDTO(@JsonProperty(required = true) long participationId, @JsonProperty(required = true) long submissionId,
        @JsonProperty(required = true) long resultId, @Nullable Integer correctionRound) {
}
