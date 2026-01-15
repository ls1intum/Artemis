package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCommentThreadDTO(Long groupId, @NotNull CommentThreadLocationType targetType, Long auxiliaryRepositoryId, Long initialVersionId, String initialCommitSha,
        String filePath, String initialFilePath, Integer lineNumber, Integer initialLineNumber) {
}
