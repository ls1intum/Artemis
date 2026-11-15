package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;

/**
 * DTO carrying how many pool items of a course share one state and, for judged items, one filter outcome.
 *
 * @param state    the lifecycle state
 * @param accepted the filter's decision, {@code null} for items the filter has not judged
 * @param count    the number of items
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState state, @Nullable Boolean accepted, long count) {
}
