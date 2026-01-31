package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "DTO representing a review comment thread.")
public record CommentThreadDTO(@Schema(description = "Thread identifier.") @NotNull Long id, @Schema(description = "Grouping identifier for related threads.") Long groupId,
        @Schema(description = "Identifier of the owning exercise.") @NotNull Long exerciseId,
        @Schema(description = "Location type of the thread.") @NotNull CommentThreadLocationType targetType,
        @Schema(description = "Identifier of the auxiliary repository, if applicable.") Long auxiliaryRepositoryId,
        @Schema(description = "Identifier of the initial exercise version, if available.") Long initialVersionId,
        @Schema(description = "Initial commit SHA for repository-based threads, if available.") String initialCommitSha,
        @Schema(description = "Current file path for repository-based threads.") String filePath,
        @Schema(description = "Initial file path captured at thread creation.") String initialFilePath,
        @Schema(description = "Current line number for repository-based threads.") Integer lineNumber,
        @Schema(description = "Initial line number captured at thread creation.") @NotNull Integer initialLineNumber,
        @Schema(description = "Whether the thread is outdated.") @NotNull Boolean outdated, @Schema(description = "Whether the thread is resolved.") @NotNull Boolean resolved,
        @Schema(description = "Comments belonging to the thread.") @NotNull List<@NotNull CommentDTO> comments) {

    /**
     * Maps a CommentThread entity to a DTO.
     *
     * @param thread   the comment thread entity
     * @param comments the mapped comments
     */
    public CommentThreadDTO(CommentThread thread, List<CommentDTO> comments) {
        this(thread.getId(), thread.getGroup() != null ? thread.getGroup().getId() : null, thread.getExercise().getId(), thread.getTargetType(), thread.getAuxiliaryRepositoryId(),
                thread.getInitialVersion() != null ? thread.getInitialVersion().getId() : null, thread.getInitialCommitSha(), thread.getFilePath(), thread.getInitialFilePath(),
                thread.getLineNumber(), thread.getInitialLineNumber(), thread.isOutdated(), thread.isResolved(), comments);
    }
}
