package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to create a new comment thread.")
public record CreateCommentThreadDTO(@Schema(description = "Location type of the thread.") @NotNull CommentThreadLocationType targetType,
        @Schema(description = "Identifier of the auxiliary repository, if applicable.") Long auxiliaryRepositoryId,
        @Schema(description = "Initial file path captured at thread creation.") @Size(max = 1024) String initialFilePath,
        @Schema(description = "Initial line number captured at thread creation.") @NotNull @Min(1) Integer initialLineNumber,
        @Schema(description = "Initial user comment that populates the thread.") @NotNull @Valid UserCommentContentDTO initialComment) {

    /**
     * Convert this DTO into a new comment thread entity using resolved versioning metadata.
     *
     * @param initialVersion   the initial exercise version (problem statement only)
     * @param initialCommitSha the initial commit SHA (repository targets only)
     * @return the initialized comment thread entity
     */
    public CommentThread toEntity(ExerciseVersion initialVersion, String initialCommitSha) {
        CommentThread thread = new CommentThread();
        thread.setTargetType(targetType);
        thread.setAuxiliaryRepositoryId(auxiliaryRepositoryId);
        thread.setInitialVersion(initialVersion);
        thread.setInitialCommitSha(initialCommitSha);
        thread.setInitialFilePath(initialFilePath);
        thread.setInitialLineNumber(initialLineNumber);
        thread.setFilePath(initialFilePath);
        thread.setLineNumber(initialLineNumber);
        return thread;
    }
}
