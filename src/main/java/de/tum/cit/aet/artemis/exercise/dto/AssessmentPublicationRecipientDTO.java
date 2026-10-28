package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A notification recipient and the score of their completed assessment. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentPublicationRecipientDTO(Long userId, Double score) {
}
