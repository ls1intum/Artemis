package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;

/**
 * DTO carrying how many pool items of a course are in one state.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState state, long count) {
}
