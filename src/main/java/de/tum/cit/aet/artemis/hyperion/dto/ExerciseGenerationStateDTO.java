package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Which job currently owns an exercise's mutation slot. Broadcast to every authorized editor of the exercise, so it carries no prompt, progress, file-change, or owner
 * information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationStateDTO(long exerciseId, String jobId, boolean running) {
}
