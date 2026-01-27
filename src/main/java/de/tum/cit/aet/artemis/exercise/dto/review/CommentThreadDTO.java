package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "DTO representing a review comment thread.")
public record CommentThreadDTO(@Schema(description = "Thread identifier.") Long id, @Schema(description = "Grouping identifier for related threads.") Long groupId,
        @Schema(description = "Identifier of the owning exercise.") Long exerciseId, @Schema(description = "Location type of the thread.") CommentThreadLocationType targetType,
        @Schema(description = "Identifier of the auxiliary repository, if applicable.") Long auxiliaryRepositoryId,
        @Schema(description = "Identifier of the initial exercise version, if available.") Long initialVersionId,
        @Schema(description = "Initial commit SHA for repository-based threads, if available.") String initialCommitSha,
        @Schema(description = "Current file path for repository-based threads.") String filePath,
        @Schema(description = "Initial file path captured at thread creation.") String initialFilePath,
        @Schema(description = "Current line number for repository-based threads.") Integer lineNumber,
        @Schema(description = "Initial line number captured at thread creation.") Integer initialLineNumber,
        @Schema(description = "Whether the thread is outdated.") boolean outdated, @Schema(description = "Whether the thread is resolved.") boolean resolved,
        @Schema(description = "Comments belonging to the thread.") List<CommentDTO> comments) {

    /**
     * Maps a CommentThread entity to a DTO.
     *
     * @param thread   the comment thread entity
     * @param comments the mapped comments
     */
    public CommentThreadDTO(CommentThread thread, List<CommentDTO> comments) {
        this(thread.getId(), thread.getGroupId(), thread.getExercise() != null ? thread.getExercise().getId() : null, thread.getTargetType(), thread.getAuxiliaryRepositoryId(),
                thread.getInitialVersion() != null ? thread.getInitialVersion().getId() : null, thread.getInitialCommitSha(), thread.getFilePath(), thread.getInitialFilePath(),
                thread.getLineNumber(), thread.getInitialLineNumber(), thread.isOutdated(), thread.isResolved(), comments);
    }
}
