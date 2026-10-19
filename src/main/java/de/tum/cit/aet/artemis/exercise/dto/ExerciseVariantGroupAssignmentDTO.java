package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for assigning an exercise to (or removing it from) an exercise variant group. Membership is edited from the
 * exercise side: a {@code null} {@code groupId} removes the exercise from its current group, any other value moves it
 * into the referenced group.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupAssignmentDTO(@Nullable Long groupId) {
}
