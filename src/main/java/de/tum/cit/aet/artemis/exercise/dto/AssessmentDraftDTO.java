package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

/** An unfinished assessment owned by the current user on an older submission. */
public record AssessmentDraftDTO(long participationId, long submissionId, long resultId, @Nullable Integer correctionRound) {
}
