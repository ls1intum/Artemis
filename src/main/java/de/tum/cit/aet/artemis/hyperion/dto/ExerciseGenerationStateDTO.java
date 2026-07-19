package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Exercise-scoped generation state shared with authorized editors. It intentionally contains no prompt, progress, file-change, or owner information.
 *
 * @param exerciseId the exercise whose mutation slot changed
 * @param jobId      the generation job that changed the slot
 * @param running    whether that job currently owns the slot
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationStateDTO(long exerciseId, String jobId, boolean running) {
}
