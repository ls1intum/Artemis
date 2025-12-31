package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for updating a participation's presentation score.
 *
 * @param id                 the ID of the participation to update
 * @param exerciseId         the ID of the exercise the participation belongs to
 * @param presentationScore  the new presentation score (can be null to remove score)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationUpdateDTO(@NotNull Long id, @NotNull Long exerciseId, Double presentationScore) {
}
