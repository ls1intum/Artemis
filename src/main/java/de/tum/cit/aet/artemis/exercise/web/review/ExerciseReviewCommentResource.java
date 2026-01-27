package de.tum.cit.aet.artemis.exercise.web.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;

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

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewCommentService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
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

        validateThreadPayload(createCommentThreadDTO);
        ExerciseVersion initialVersion = loadInitialVersion(createCommentThreadDTO.targetType(), exerciseId);
        String initialCommitSha = exerciseReviewCommentService.resolveLatestCommitSha(createCommentThreadDTO.targetType(), createCommentThreadDTO.auxiliaryRepositoryId(),
                exerciseId);
        CreateCommentDTO initialCommentDTO = createCommentThreadDTO.initialComment();
        validateUserComment(initialCommentDTO);
        CommentThread thread = toEntity(createCommentThreadDTO, initialVersion, initialCommitSha);
        CommentThread savedThread = exerciseReviewCommentService.createThread(exerciseId, thread);
        Comment savedComment = exerciseReviewCommentService.createComment(savedThread.getId(), toEntity(initialCommentDTO));
        return ResponseEntity.created(new URI("/api/exercise/exercises/" + exerciseId + "/review-threads/" + savedThread.getId()))
                .body(new CommentThreadDTO(savedThread, List.of(new CommentDTO(savedComment))));
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
     * POST /exercises/:exerciseId/review-threads/:threadId/comments : Create a new user comment in a thread.
     *
     * @param exerciseId       the exercise id
     * @param threadId         the thread id
     * @param createCommentDTO the comment data
     * @return the created comment
     */
    @PostMapping("exercises/{exerciseId}/review-threads/{threadId}/comments")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentDTO> createUserComment(@PathVariable long exerciseId, @PathVariable long threadId, @Valid @RequestBody CreateCommentDTO createCommentDTO)
            throws URISyntaxException {
        log.debug("REST request to create exercise review comment for thread {}", threadId);
        validateUserComment(createCommentDTO);
        Comment comment = toEntity(createCommentDTO);
        Comment savedComment = exerciseReviewCommentService.createComment(threadId, comment);
        return ResponseEntity.created(new URI("/api/exercise/exercises/" + exerciseId + "/review-comments/" + savedComment.getId())).body(new CommentDTO(savedComment));
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
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentThreadDTO> updateThreadResolvedState(@PathVariable long exerciseId, @PathVariable long threadId,
            @Valid @RequestBody UpdateThreadResolvedStateDTO dto) {
        log.debug("REST request to update resolved state of thread {} for exercise {}", threadId, exerciseId);
        CommentThread updated = exerciseReviewCommentService.updateThreadResolvedState(threadId, dto.resolved());
        return ResponseEntity.ok(new CommentThreadDTO(updated, mapComments(updated)));
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
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<CommentDTO> updateUserCommentContent(@PathVariable long exerciseId, @PathVariable long commentId, @Valid @RequestBody UpdateCommentContentDTO dto) {
        log.debug("REST request to update content of comment {} for exercise {}", commentId, exerciseId);
        if (!(dto.content() instanceof UserCommentContentDTO)) {
            throw new BadRequestAlertException("Only user comment content can be updated via this endpoint", THREAD_ENTITY_NAME, "commentContentNotSupported");
        }
        Comment updated = exerciseReviewCommentService.updateCommentContent(commentId, dto.content());
        return ResponseEntity.ok(new CommentDTO(updated));
    }

    /**
     * DELETE /exercises/:exerciseId/review-comments/:commentId : Delete a comment.
     *
     * @param exerciseId the exercise id
     * @param commentId  the comment id
     * @return 200 OK
     */
    @DeleteMapping("exercises/{exerciseId}/review-comments/{commentId}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> deleteComment(@PathVariable long exerciseId, @PathVariable long commentId) {
        log.debug("REST request to delete comment {} for exercise {}", commentId, exerciseId);
        exerciseReviewCommentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }

    private ExerciseVersion loadInitialVersion(CommentThreadLocationType targetType, long exerciseId) {
        if (targetType != CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId)
                .orElseThrow(() -> new BadRequestAlertException("No exercise version available for problem statement thread", THREAD_ENTITY_NAME, "initialVersionMissing"));
    }

    private CommentThread toEntity(CreateCommentThreadDTO dto, ExerciseVersion initialVersion, String initialCommitSha) {
        CommentThread thread = new CommentThread();
        thread.setGroupId(dto.groupId());
        thread.setTargetType(dto.targetType());
        thread.setAuxiliaryRepositoryId(dto.auxiliaryRepositoryId());
        thread.setInitialVersion(initialVersion);
        thread.setInitialCommitSha(initialCommitSha);
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
        return comment;
    }

    private void validateUserComment(CreateCommentDTO dto) {
        if (dto.type() != CommentType.USER) {
            throw new BadRequestAlertException("Only user comments can be created via this endpoint", THREAD_ENTITY_NAME, "commentTypeNotSupported");
        }
        if (!(dto.content() instanceof UserCommentContentDTO)) {
            throw new BadRequestAlertException("Only user comment content can be created via this endpoint", THREAD_ENTITY_NAME, "commentContentNotSupported");
        }
    }

    private void validateThreadPayload(CreateCommentThreadDTO dto) {
        if (dto.initialFilePath() == null || dto.initialLineNumber() == null) {
            throw new BadRequestAlertException("Initial file path and line number are required", THREAD_ENTITY_NAME, "initialLocationMissing");
        }
        if (dto.targetType() != CommentThreadLocationType.PROBLEM_STATEMENT) {
            if (dto.filePath() == null || dto.lineNumber() == null) {
                throw new BadRequestAlertException("File path and line number are required for repository threads", THREAD_ENTITY_NAME, "locationMissing");
            }
        }
    }

    private List<CommentDTO> mapComments(CommentThread thread) {
        if (thread.getComments() == null || thread.getComments().isEmpty()) {
            return List.of();
        }

        return thread.getComments().stream().sorted(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comment::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))).map(CommentDTO::new).toList();
    }

}
