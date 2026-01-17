package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to create a new comment thread.")
public record CreateCommentThreadDTO(@Schema(description = "Grouping identifier for related threads.") Long groupId,
        @Schema(description = "Location type of the thread.") @NotNull CommentThreadLocationType targetType,
        @Schema(description = "Identifier of the auxiliary repository, if applicable.") Long auxiliaryRepositoryId,
        @Schema(description = "Current file path for repository-based threads.") String filePath,
        @Schema(description = "Initial file path captured at thread creation.") String initialFilePath,
        @Schema(description = "Current line number for repository-based threads.") Integer lineNumber,
        @Schema(description = "Initial line number captured at thread creation.") Integer initialLineNumber) {
}
