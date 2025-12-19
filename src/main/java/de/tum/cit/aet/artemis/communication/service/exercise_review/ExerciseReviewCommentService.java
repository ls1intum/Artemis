package de.tum.cit.aet.artemis.communication.service.exercise_review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.exercise_review.Comment;
import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentThread;
import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentType;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.CommentContentDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.communication.dto.exercise_review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.communication.repository.exercise_review.CommentRepository;
import de.tum.cit.aet.artemis.communication.repository.exercise_review.CommentThreadRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseReviewCommentService {

    private static final String COMMENT_ENTITY_NAME = "exerciseReviewComment";

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    private final CommentThreadRepository commentThreadRepository;

    private final CommentRepository commentRepository;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ExerciseReviewCommentService(CommentThreadRepository commentThreadRepository, CommentRepository commentRepository, ExerciseRepository exerciseRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.commentThreadRepository = commentThreadRepository;
        this.commentRepository = commentRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Retrieve all comment threads for an exercise.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads
     */
    public List<CommentThread> findThreadsByExerciseId(long exerciseId) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        return commentThreadRepository.findByExerciseId(exerciseId);
    }

    /**
     * Retrieve all comment threads for an exercise with their comments loaded.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads with comments
     */
    public List<CommentThread> findThreadsWithCommentsByExerciseId(long exerciseId) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        return commentThreadRepository.findWithCommentsByExerciseId(exerciseId);
    }

    /**
     * Retrieve all comments for a thread, ordered by creation date.
     *
     * @param threadId the thread id
     * @return list of comments
     */
    public List<Comment> findCommentsByThreadId(long threadId) {
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());
        return commentRepository.findByThreadIdOrderByCreatedDateAsc(threadId);
    }

    /**
     * Create a new comment thread for an exercise.
     *
     * @param exerciseId the exercise id
     * @param thread     the thread to create
     * @return the persisted thread
     */
    public CommentThread createThread(long exerciseId, CommentThread thread) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        if (thread.getId() != null) {
            throw new BadRequestAlertException("A new thread cannot already have an ID", THREAD_ENTITY_NAME, "idexists");
        }

        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        if (thread.getExercise() != null && !Objects.equals(thread.getExercise().getId(), exerciseId)) {
            throw new BadRequestAlertException("Thread exercise does not match request", THREAD_ENTITY_NAME, "exerciseMismatch");
        }

        thread.setExercise(exercise);
        return commentThreadRepository.save(thread);
    }

    /**
     * Create a new comment within a thread.
     *
     * @param threadId the thread id
     * @param comment  the comment to create
     * @return the persisted comment
     */
    public Comment createComment(long threadId, Comment comment) {
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());

        if (comment.getId() != null) {
            throw new BadRequestAlertException("A new comment cannot already have an ID", COMMENT_ENTITY_NAME, "idexists");
        }
        if (comment.getType() == null) {
            throw new BadRequestAlertException("Comment type must be set", COMMENT_ENTITY_NAME, "typeMissing");
        }
        validateContentMatchesType(comment.getType(), comment.getContent());

        if (comment.getInReplyTo() != null) {
            Comment replyTarget = commentRepository.findById(comment.getInReplyTo().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Comment", comment.getInReplyTo().getId()));
            if (!Objects.equals(replyTarget.getThread().getId(), thread.getId())) {
                throw new BadRequestAlertException("Reply target does not belong to thread", COMMENT_ENTITY_NAME, "replyThreadMismatch");
            }
            comment.setInReplyTo(replyTarget);
        }

        if (comment.getType() == CommentType.USER) {
            User author = userRepository.getUserWithGroupsAndAuthorities();
            comment.setAuthor(author);
        }

        comment.setThread(thread);
        return commentRepository.save(comment);
    }

    /**
     * Delete a comment by id.
     *
     * @param commentId the comment id
     */
    public void deleteComment(long commentId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        commentRepository.delete(comment);
    }

    /**
     * Update the resolved flag of a thread.
     *
     * @param threadId the thread id
     * @param resolved whether the thread is resolved
     * @return the updated thread
     */
    public CommentThread updateThreadResolvedState(long threadId, boolean resolved) {
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());
        thread.setResolved(resolved);
        return commentThreadRepository.save(thread);
    }

    /**
     * Update the outdated flag of a thread.
     *
     * @param threadId the thread id
     * @param outdated whether the thread is outdated
     * @return the updated thread
     */
    public CommentThread updateThreadOutdatedState(long threadId, boolean outdated) {
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());
        thread.setOutdated(outdated);
        return commentThreadRepository.save(thread);
    }

    /**
     * Update the content of a comment.
     *
     * @param commentId the comment id
     * @param content   the new content
     * @return the updated comment
     */
    public Comment updateCommentContent(long commentId, CommentContentDTO content) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        validateContentMatchesType(comment.getType(), content);
        comment.setContent(content);
        comment.setLastModifiedDate(Instant.now());
        return commentRepository.save(comment);
    }

    private void validateContentMatchesType(CommentType type, CommentContentDTO content) {
        if (content == null) {
            throw new BadRequestAlertException("Comment content must be set", COMMENT_ENTITY_NAME, "contentMissing");
        }

        boolean isValid = switch (type) {
            case USER -> content instanceof UserCommentContentDTO;
            case CONSISTENCY_CHECK -> content instanceof ConsistencyIssueCommentContentDTO;
        };

        if (!isValid) {
            throw new BadRequestAlertException("Comment content does not match type", COMMENT_ENTITY_NAME, "contentTypeMismatch");
        }
    }

    private CommentThread findThreadByIdElseThrow(long threadId) {
        return commentThreadRepository.findById(threadId).orElseThrow(() -> new EntityNotFoundException("CommentThread", threadId));
    }
}
