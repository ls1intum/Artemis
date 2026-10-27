package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An unfinished assessment owned by the current user on an older submission. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentDraftDTO(long participationId, long submissionId, long resultId, @Nullable Integer correctionRound) {
}
