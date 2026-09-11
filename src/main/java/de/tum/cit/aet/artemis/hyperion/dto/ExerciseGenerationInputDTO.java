package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Owner-only snapshot of the instructor's input, retained with the run rather than read from mutable review threads.
 *
 * @param prompt         the original brief or adaptation instructions
 * @param reviewFeedback the selected review comments as supplied to the worker
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationInputDTO(@Nullable String prompt, List<ExerciseGenerationFeedbackDTO> reviewFeedback) implements Serializable {
}
