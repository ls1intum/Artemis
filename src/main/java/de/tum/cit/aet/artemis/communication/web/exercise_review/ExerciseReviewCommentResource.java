package de.tum.cit.aet.artemis.communication.web.exercise_review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;

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

import de.tum.cit.aet.artemis.communication.domain.exercise_review.Comment;
import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentThread;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.CommentDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.CommentThreadDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.CreateCommentDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.UpdateCommentContentDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.UpdateThreadOutdatedStateDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.communication.service.exercise_review.ExerciseReviewCommentService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/communication/")
public class ExerciseReviewCommentResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseReviewCommentResource.class);

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    private final ExerciseReviewCommentService exerciseReviewCommentService;

    private final ExerciseVersionRepository exerciseVersionRepository;

    public ExerciseReviewCommentResource(ExerciseReviewCommentService exerciseReviewCommentService, ExerciseVersionRepository exerciseVersionRepository) {
        this.exerciseReviewCommentService = exerciseReviewCommentService;
        this.exerciseVersionRepository = exerciseVersionRepository;
    }

    /**
     * POST /exercises/:exerciseId/review-threads : Create a new comment thread for an exercise.
     *
     * @param exerciseId             the exercise id
     * @param createCommentThreadDTO the thread data
     * @return the created thread
     */
    @PostMapping("exercises/{exerciseId}/review-threads")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentThreadDTO> createThread(@PathVariable long exerciseId, @Valid @RequestBody CreateCommentThreadDTO createCommentThreadDTO)
            throws URISyntaxException {
        log.debug("REST request to create exercise review thread for exercise {}", exerciseId);

        ExerciseVersion initialVersion = loadInitialVersion(createCommentThreadDTO.initialVersionId(), exerciseId);
        CommentThread thread = toEntity(createCommentThreadDTO, initialVersion);
        CommentThread savedThread = exerciseReviewCommentService.createThread(exerciseId, thread);
        return ResponseEntity.created(new URI("/api/communication/exercises/" + exerciseId + "/review-threads/" + savedThread.getId()))
                .body(new CommentThreadDTO(savedThread, List.of()));
    }

    /**
     * GET /exercises/:exerciseId/review-threads : Get all comment threads for an exercise with their comments.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads with comments
     */
    @GetMapping("exercises/{exerciseId}/review-threads")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<List<CommentThreadDTO>> getThreads(@PathVariable long exerciseId) {
        log.debug("REST request to get exercise review threads for exercise {}", exerciseId);
        List<CommentThreadDTO> threads = exerciseReviewCommentService.findThreadsWithCommentsByExerciseId(exerciseId).stream()
                .map(thread -> new CommentThreadDTO(thread, mapComments(thread))).toList();
        return ResponseEntity.ok(threads);
    }

    /**
     * POST /exercises/:exerciseId/review-threads/:threadId/comments : Create a new comment in a thread.
     *
     * @param exerciseId       the exercise id
     * @param threadId         the thread id
     * @param createCommentDTO the comment data
     * @return the created comment
     */
    @PostMapping("exercises/{exerciseId}/review-threads/{threadId}/comments")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentDTO> createComment(@PathVariable long exerciseId, @PathVariable long threadId, @Valid @RequestBody CreateCommentDTO createCommentDTO)
            throws URISyntaxException {
        log.debug("REST request to create exercise review comment for thread {}", threadId);
        Comment comment = toEntity(createCommentDTO);
        Comment savedComment = exerciseReviewCommentService.createComment(threadId, comment);
        return ResponseEntity.created(new URI("/api/communication/exercises/" + exerciseId + "/review-comments/" + savedComment.getId())).body(new CommentDTO(savedComment));
    }

    /**
     * PUT /exercises/:exerciseId/review-threads/:threadId/resolved : Update the resolved state of a thread.
     *
     * @param threadId the thread id
     * @param dto      the resolved state
     * @return the updated thread
     */
    @PutMapping("exercises/{exerciseId}/review-threads/{threadId}/resolved")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentThreadDTO> updateThreadResolvedState(@PathVariable long exerciseId, @PathVariable long threadId,
            @Valid @RequestBody UpdateThreadResolvedStateDTO dto) {
        log.debug("REST request to update resolved state of thread {} for exercise {}", threadId, exerciseId);
        CommentThread updated = exerciseReviewCommentService.updateThreadResolvedState(threadId, dto.resolved());
        return ResponseEntity.ok(new CommentThreadDTO(updated, mapComments(updated)));
    }

    /**
     * PUT /exercises/:exerciseId/review-threads/:threadId/outdated : Update the outdated state of a thread.
     *
     * @param threadId the thread id
     * @param dto      the outdated state
     * @return the updated thread
     */
    @PutMapping("exercises/{exerciseId}/review-threads/{threadId}/outdated")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentThreadDTO> updateThreadOutdatedState(@PathVariable long exerciseId, @PathVariable long threadId,
            @Valid @RequestBody UpdateThreadOutdatedStateDTO dto) {
        log.debug("REST request to update outdated state of thread {} for exercise {}", threadId, exerciseId);
        CommentThread updated = exerciseReviewCommentService.updateThreadOutdatedState(threadId, dto.outdated());
        return ResponseEntity.ok(new CommentThreadDTO(updated, mapComments(updated)));
    }

    /**
     * PUT /exercises/:exerciseId/review-comments/:commentId : Update a comment's content.
     *
     * @param commentId the comment id
     * @param dto       the updated content
     * @return the updated comment
     */
    @PutMapping("exercises/{exerciseId}/review-comments/{commentId}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentDTO> updateCommentContent(@PathVariable long exerciseId, @PathVariable long commentId, @Valid @RequestBody UpdateCommentContentDTO dto) {
        log.debug("REST request to update content of comment {} for exercise {}", commentId, exerciseId);
        Comment updated = exerciseReviewCommentService.updateCommentContent(commentId, dto.content());
        return ResponseEntity.ok(new CommentDTO(updated));
    }

    /**
     * DELETE /exercises/:exerciseId/review-comments/:commentId : Delete a comment.
     *
     * @param commentId the comment id
     * @return 200 OK
     */
    @DeleteMapping("exercises/{exerciseId}/review-comments/{commentId}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> deleteComment(@PathVariable long exerciseId, @PathVariable long commentId) {
        log.debug("REST request to delete comment {} for exercise {}", commentId, exerciseId);
        exerciseReviewCommentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }

    private ExerciseVersion loadInitialVersion(Long initialVersionId, long exerciseId) {
        if (initialVersionId == null) {
            return null;
        }

        ExerciseVersion initialVersion = exerciseVersionRepository.findById(initialVersionId).orElseThrow(() -> new EntityNotFoundException("ExerciseVersion", initialVersionId));
        if (!Objects.equals(initialVersion.getExerciseId(), exerciseId)) {
            throw new BadRequestAlertException("Initial version does not belong to exercise", THREAD_ENTITY_NAME, "initialVersionMismatch");
        }
        return initialVersion;
    }

    private CommentThread toEntity(CreateCommentThreadDTO dto, ExerciseVersion initialVersion) {
        CommentThread thread = new CommentThread();
        thread.setGroupId(dto.groupId());
        thread.setTargetType(dto.targetType());
        thread.setAuxiliaryRepositoryId(dto.auxiliaryRepositoryId());
        thread.setInitialVersion(initialVersion);
        thread.setInitialCommitSha(dto.initialCommitSha());
        thread.setFilePath(dto.filePath());
        thread.setInitialFilePath(dto.initialFilePath());
        thread.setLineNumber(dto.lineNumber());
        thread.setInitialLineNumber(dto.initialLineNumber());
        return thread;
    }

    private Comment toEntity(CreateCommentDTO dto) {
        Comment comment = new Comment();
        comment.setType(dto.type());
        comment.setContent(dto.content());
        if (dto.inReplyToId() != null) {
            Comment replyTarget = new Comment();
            replyTarget.setId(dto.inReplyToId());
            comment.setInReplyTo(replyTarget);
        }
        return comment;
    }

    private List<CommentDTO> mapComments(CommentThread thread) {
        if (thread.getComments() == null || thread.getComments().isEmpty()) {
            return List.of();
        }

        return thread.getComments().stream().sorted(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comment::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))).map(CommentDTO::new).toList();
    }
}
