package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentThreadLocationType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCommentThreadDTO(Long groupId, @NotNull CommentThreadLocationType targetType, Long auxiliaryRepositoryId, Long initialVersionId, String initialCommitSha,
        String filePath, String initialFilePath, Integer lineNumber, Integer initialLineNumber) {
}
