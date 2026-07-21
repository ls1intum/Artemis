package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight preview payload carrying only an exercise's id and its problem statement. Returned in batch for the
 * members of an {@link de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup} so the student group-detail page can
 * render member previews with a single request instead of one heavyweight {@code /exercises/{id}/details} call each.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record ExerciseProblemStatementDTO(long exerciseId, @Nullable String problemStatement) {
}
