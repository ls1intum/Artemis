package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO that is included as payload for review thread related websocket messages.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewThreadWebsocketDTO(ReviewThreadWebsocketAction action, Long exerciseId, CommentThreadDTO thread, CommentDTO comment, Long commentId, List<Long> threadIds,
        Long groupId) {
}
