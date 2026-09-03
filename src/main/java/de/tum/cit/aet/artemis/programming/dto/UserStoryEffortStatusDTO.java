package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One user story the requesting participant has started, with whatever effort they have reported for it.
 * <p>
 * Returned per course so the exercise overview can mark the stories still missing an estimate in one request, rather
 * than the effort riding along on every serialized participation - which cost a query per participation and broke the
 * dashboard payload when the participation was already detached.
 *
 * @param exerciseId      the user story exercise
 * @param estimatedEffort hours the participant expected it to take, {@code null} when not reported
 * @param actualEffort    hours it actually took, {@code null} when not reported
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryEffortStatusDTO(Long exerciseId, @Nullable Double estimatedEffort, @Nullable Double actualEffort) {
}
