package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "DTO representing a comment thread group.")
public record CommentThreadGroupDTO(@Schema(description = "Group identifier.") @NotNull Long id,
        @Schema(description = "Identifier of the owning exercise.") @NotNull Long exerciseId,
        @Schema(description = "Thread identifiers associated with the group.") @NotNull List<@NotNull Long> threadIds) {

    public CommentThreadGroupDTO(CommentThreadGroup group) {
        this(group.getId(), group.getExercise().getId(), mapThreadIds(group));
    }

    private static List<Long> mapThreadIds(CommentThreadGroup group) {
        if (group.getThreads() == null || group.getThreads().isEmpty()) {
            return List.of();
        }
        return group.getThreads().stream().map(CommentThread::getId).filter(Objects::nonNull).toList();
    }
}
