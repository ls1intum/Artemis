package de.tum.cit.aet.artemis.exercise.web.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewWebsocketService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ExerciseReviewResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseReviewResource.class);

    private final ExerciseReviewService exerciseReviewService;

    private final ExerciseReviewWebsocketService exerciseReviewWebsocketService;

    public ExerciseReviewResource(ExerciseReviewService exerciseReviewService, ExerciseReviewWebsocketService exerciseReviewWebsocketService) {
        this.exerciseReviewService = exerciseReviewService;
        this.exerciseReviewWebsocketService = exerciseReviewWebsocketService;
    }

    /**
     * POST /exercises/:exerciseId/review-threads : Create a new comment thread for an exercise.
     *
     * @param exerciseId             the exercise id
     * @param createCommentThreadDTO the thread data
     * @return the created thread with its initial comment
     */
    @PostMapping("exercises/{exerciseId}/review-threads")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CommentThreadDTO> createThread(@PathVariable long exerciseId, @Valid @NotNull @RequestBody CreateCommentThreadDTO createCommentThreadDTO)
            throws URISyntaxException {
        log.debug("REST request to create exercise review thread for exercise {}", exerciseId);
        ExerciseReviewService.ThreadCreationResult result = exerciseReviewService.createThread(exerciseId, createCommentThreadDTO);
        CommentThread savedThread = result.thread();
        Comment savedComment = result.comment();
        CommentThreadDTO createdThread = new CommentThreadDTO(savedThread, List.of(new CommentDTO(savedComment)));
        exerciseReviewWebsocketService.notifyThreadCreated(exerciseId, createdThread);
        return ResponseEntity.created(new URI("/api/exercise/exercises/" + exerciseId + "/review-threads/" + savedThread.getId())).body(createdThread);
    }

    /**
     * GET /exercises/:exerciseId/review-threads : Get all comment threads for an exercise with their comments.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads with comments
     */
    @GetMapping("exercises/{exerciseId}/review-threads")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<List<CommentThreadDTO>> getThreads(@PathVariable long exerciseId) {
        log.debug("REST request to get exercise review threads for exercise {}", exerciseId);
        List<CommentThreadDTO> threads = exerciseReviewService.findThreadsWithCommentsByExerciseId(exerciseId).stream()
                .sorted(Comparator.comparing(CommentThread::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(thread -> new CommentThreadDTO(thread, mapComments(thread))).toList();
        return ResponseEntity.ok(threads);
    }

    /**
     * POST /exercises/:exerciseId/review-thread-groups : Create a new comment thread group.
     *
     * @param exerciseId                  the exercise id
     * @param createCommentThreadGroupDTO the group data
     * @return the created group
     */
    @PostMapping("exercises/{exerciseId}/review-thread-groups")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CommentThreadGroupDTO> createThreadGroup(@PathVariable long exerciseId,
            @Valid @NotNull @RequestBody CreateCommentThreadGroupDTO createCommentThreadGroupDTO) throws URISyntaxException {
        log.debug("REST request to create exercise review thread group for exercise {}", exerciseId);
        CommentThreadGroup savedGroup = exerciseReviewService.createGroup(exerciseId, createCommentThreadGroupDTO);
        List<Long> savedThreadIds = savedGroup.getThreads().stream().map(CommentThread::getId).sorted().toList();
        exerciseReviewWebsocketService.notifyGroupUpdated(exerciseId, savedThreadIds, savedGroup.getId());
        return ResponseEntity.created(new URI("/api/exercise/exercises/" + exerciseId + "/review-thread-groups/" + savedGroup.getId())).body(new CommentThreadGroupDTO(savedGroup));
    }

    /**
     * DELETE /exercises/:exerciseId/review-thread-groups/:groupId : Delete a comment thread group.
     *
     * @param exerciseId the exercise id
     * @param groupId    the group id
     * @return 204 No Content
     */
    @DeleteMapping("exercises/{exerciseId}/review-thread-groups/{groupId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> deleteThreadGroup(@PathVariable long exerciseId, @PathVariable long groupId) {
        log.debug("REST request to delete exercise review thread group {} for exercise {}", groupId, exerciseId);
        var threadIds = exerciseReviewService.deleteGroup(exerciseId, groupId);
        exerciseReviewWebsocketService.notifyGroupUpdated(exerciseId, threadIds, null);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /exercises/:exerciseId/review-threads/:threadId/comments : Create a new user comment in a thread.
     *
     * @param exerciseId the exercise id
     * @param threadId   the thread id
     * @param content    the comment content
     * @return the created comment
     */
    @PostMapping("exercises/{exerciseId}/review-threads/{threadId}/comments")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CommentDTO> createUserComment(@PathVariable long exerciseId, @PathVariable long threadId, @Valid @NotNull @RequestBody UserCommentContentDTO content)
            throws URISyntaxException {
        log.debug("REST request to create exercise review comment for thread {}", threadId);
        Comment savedComment = exerciseReviewService.createUserComment(exerciseId, threadId, content);
        CommentDTO createdComment = new CommentDTO(savedComment);
        exerciseReviewWebsocketService.notifyCommentCreated(exerciseId, createdComment);
        return ResponseEntity.created(new URI("/api/exercise/exercises/" + exerciseId + "/review-comments/" + savedComment.getId())).body(createdComment);
    }

    /**
     * DELETE /exercises/:exerciseId/review-comments/:commentId : Delete a comment.
     *
     * @param exerciseId the exercise id
     * @param commentId  the comment id
     * @return 204 No Content
     */
    @DeleteMapping("exercises/{exerciseId}/review-comments/{commentId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> deleteComment(@PathVariable long exerciseId, @PathVariable long commentId) {
        log.debug("REST request to delete comment {} for exercise {}", commentId, exerciseId);
        exerciseReviewService.deleteComment(exerciseId, commentId);
        exerciseReviewWebsocketService.notifyCommentDeleted(exerciseId, commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /exercises/:exerciseId/review-threads/:threadId/resolved : Update the resolved state of a thread.
     *
     * @param exerciseId the exercise id
     * @param threadId   the thread id
     * @param dto        the resolved state
     * @return the updated thread
     */
    @PutMapping("exercises/{exerciseId}/review-threads/{threadId}/resolved")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CommentThreadDTO> updateThreadResolvedState(@PathVariable long exerciseId, @PathVariable long threadId,
            @Valid @NotNull @RequestBody UpdateThreadResolvedStateDTO dto) {
        log.debug("REST request to update resolved state of thread {} for exercise {}", threadId, exerciseId);
        CommentThread updated = exerciseReviewService.updateThreadResolvedState(exerciseId, threadId, dto);
        CommentThreadDTO updatedThread = new CommentThreadDTO(updated, mapComments(updated));
        exerciseReviewWebsocketService.notifyThreadUpdated(exerciseId, updatedThread);
        return ResponseEntity.ok(updatedThread);
    }

    /**
     * PUT /exercises/:exerciseId/review-comments/:commentId : Update a user comment's content.
     *
     * @param exerciseId the exercise id
     * @param commentId  the comment id
     * @param dto        the updated content
     * @return the updated comment
     */
    @PutMapping("exercises/{exerciseId}/review-comments/{commentId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CommentDTO> updateUserCommentContent(@PathVariable long exerciseId, @PathVariable long commentId,
            @Valid @NotNull @RequestBody UserCommentContentDTO dto) {
        log.debug("REST request to update content of comment {} for exercise {}", commentId, exerciseId);
        Comment updated = exerciseReviewService.updateUserCommentContent(exerciseId, commentId, dto);
        CommentDTO updatedComment = new CommentDTO(updated);
        exerciseReviewWebsocketService.notifyCommentUpdated(exerciseId, updatedComment);
        return ResponseEntity.ok(updatedComment);
    }

    private List<CommentDTO> mapComments(CommentThread thread) {
        if (thread.getComments() == null || thread.getComments().isEmpty()) {
            return List.of();
        }

        return thread.getComments().stream().sorted(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comment::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))).map(CommentDTO::new).toList();
    }

}
