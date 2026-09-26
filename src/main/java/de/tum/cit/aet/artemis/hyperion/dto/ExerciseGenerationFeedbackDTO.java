package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A bounded snapshot of one selected review thread, in the same order and wording supplied to the worker.
 *
 * @param targetType the reviewed repository or problem statement
 * @param filePath   the repository-relative file, when present
 * @param lineNumber the reviewed line, when present
 * @param comments   the selected thread's retained comment texts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationFeedbackDTO(@Nullable String targetType, @Nullable String filePath, @Nullable Integer lineNumber, List<String> comments) implements Serializable {
}
