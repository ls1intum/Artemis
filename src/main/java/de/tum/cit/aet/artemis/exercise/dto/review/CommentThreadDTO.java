package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommentThreadDTO(Long id, Long groupId, Long exerciseId, CommentThreadLocationType targetType, Long auxiliaryRepositoryId, Long initialVersionId,
        String initialCommitSha, String filePath, String initialFilePath, Integer lineNumber, Integer initialLineNumber, boolean outdated, boolean resolved,
        List<CommentDTO> comments) {

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
