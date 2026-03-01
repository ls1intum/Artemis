package de.tum.cit.aet.artemis.exercise.dto.synchronization;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadWebsocketAction;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ReviewThreadWebsocketDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Websocket payload relaying review-thread updates over the shared exercise synchronization topic.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Review-thread update relayed over the exercise synchronization topic.")
public record ExerciseReviewThreadUpdateDTO(@Schema(description = "Event type discriminator.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncEventType eventType,
        @Schema(description = "Synchronization target.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncTarget target,
        @Schema(description = "Review update action.", requiredMode = Schema.RequiredMode.REQUIRED) ReviewThreadWebsocketAction action,
        @Schema(description = "Identifier of the exercise.", requiredMode = Schema.RequiredMode.REQUIRED) Long exerciseId,
        @Schema(description = "Thread payload for thread-related actions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) CommentThreadDTO thread,
        @Schema(description = "Comment payload for comment-related actions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) CommentDTO comment,
        @Schema(description = "Deleted comment id for delete actions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long commentId,
        @Schema(description = "Affected thread ids for group actions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<Long> threadIds,
        @Schema(description = "Updated group id for group actions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long groupId,
        @Schema(description = "The client session id of the sender.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String sessionId,
        @Schema(description = "Event timestamp in milliseconds since epoch.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long timestamp) {

    /**
     * Creates a synchronization payload from a review update payload.
     *
     * @param reviewUpdate the review update payload
     * @param sessionId    the sender session id
     * @param timestamp    the event timestamp in milliseconds since epoch
     * @return the mapped synchronization payload
     */
    public static ExerciseReviewThreadUpdateDTO fromReviewThreadUpdate(ReviewThreadWebsocketDTO reviewUpdate, @Nullable String sessionId, @Nullable Long timestamp) {
        return new ExerciseReviewThreadUpdateDTO(ExerciseEditorSyncEventType.REVIEW_THREAD_UPDATE, ExerciseEditorSyncTarget.REVIEW_COMMENTS, reviewUpdate.action(),
                reviewUpdate.exerciseId(), reviewUpdate.thread(), reviewUpdate.comment(), reviewUpdate.commentId(), reviewUpdate.threadIds(), reviewUpdate.groupId(), sessionId,
                timestamp);
    }
}
