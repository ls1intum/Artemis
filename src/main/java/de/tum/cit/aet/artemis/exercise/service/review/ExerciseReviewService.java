package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.InlineCodeChangeDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewRepositoryService.ConsistencyTargetRepositoryUris;
import de.tum.cit.aet.artemis.exercise.service.review.validation.ExerciseReviewValidationUtil;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseReviewService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseReviewService.class);

    private static final String COMMENT_ENTITY_NAME = "exerciseReviewComment";

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    private static final String THREAD_GROUP_ENTITY_NAME = "exerciseReviewCommentThreadGroup";

    private final CommentThreadGroupRepository commentThreadGroupRepository;

    private final CommentThreadRepository commentThreadRepository;

    private final CommentRepository commentRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final UserRepository userRepository;

    private final GitService gitService;

    private final ExerciseReviewRepositoryService exerciseReviewRepositoryService;

    public ExerciseReviewService(CommentThreadGroupRepository commentThreadGroupRepository, CommentThreadRepository commentThreadRepository, CommentRepository commentRepository,
            ExerciseRepository exerciseRepository, ExerciseVersionRepository exerciseVersionRepository, UserRepository userRepository, GitService gitService,
            ExerciseReviewRepositoryService exerciseReviewRepositoryService) {
        this.commentThreadGroupRepository = commentThreadGroupRepository;
        this.commentThreadRepository = commentThreadRepository;
        this.commentRepository = commentRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.userRepository = userRepository;
        this.gitService = gitService;
        this.exerciseReviewRepositoryService = exerciseReviewRepositoryService;
    }

    /**
     * Retrieve all comment threads for an exercise with their comments loaded.
     *
     * @param exerciseId the exercise id
     * @return list of comment threads with comments
     */
    public Set<CommentThread> findThreadsWithCommentsByExerciseId(long exerciseId) {
        return commentThreadRepository.findWithCommentsByExerciseId(exerciseId);
    }

    /**
     * Create a new comment thread for an exercise.
     *
     * @param exerciseId the exercise id
     * @param dto        the thread creation payload
     * @return the created thread and its initial comment
     * @throws BadRequestAlertException if validation fails
     * @throws EntityNotFoundException  if the exercise does not exist
     */
    public ThreadCreationResult createThread(long exerciseId, CreateCommentThreadDTO dto) {
        ExerciseReviewValidationUtil.validateThreadPayload(dto, THREAD_ENTITY_NAME);

        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        ExerciseVersion initialVersion = resolveInitialVersion(dto.targetType(), exerciseId);
        String initialCommitSha = resolveLatestCommitSha(dto.targetType(), dto.auxiliaryRepositoryId(), exerciseId);
        CommentThread thread = dto.toEntity(initialVersion, initialCommitSha);
        thread.setExercise(exercise);
        Comment comment = buildUserComment(thread, dto.initialComment());

        thread.getComments().add(comment);
        thread = commentThreadRepository.save(thread);
        return new ThreadCreationResult(thread, comment);
    }

    /**
     * Persists newly detected consistency-check issues as review comment threads.
     * Existing consistency-check threads are kept untouched.
     * For each consistency issue, all related locations with existing repository files are persisted as threads.
     * A thread group is created only when an issue has multiple persisted locations.
     * Invalid issues are ignored to keep consistency-check processing resilient.
     * The returned list contains only persisted threads with non-null ids so callers can safely treat them as created entities.
     *
     * @param exerciseId the programming exercise id that owns the review comments
     * @param issues     the newly detected consistency issues to persist as review comments
     * @return the persisted consistency-check threads created from the given issues
     */
    public List<CommentThread> createConsistencyCheckThreads(long exerciseId, List<ConsistencyIssueDTO> issues) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new BadRequestAlertException("Exercise is not a programming exercise", THREAD_ENTITY_NAME, "exerciseNotProgramming");
        }

        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        ConsistencyTargetRepositoryUris repositoryUrisByTarget = exerciseReviewRepositoryService.resolveTargetRepositoryUris(exerciseId);
        Map<CommentThreadLocationType, String> initialCommitShasByTarget = exerciseReviewRepositoryService.resolveTargetCommitShas(repositoryUrisByTarget);
        ExerciseVersion latestProblemStatementVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId).orElse(null);

        List<CommentThread> threadsToPersist = new ArrayList<>();
        List<CommentThreadGroup> groupsToPersist = new ArrayList<>();
        List<CommentThread> createdThreads = new ArrayList<>();
        for (ConsistencyIssueDTO issue : issues) {
            Optional<String> validationError = ExerciseReviewValidationUtil.validateConsistencyIssue(issue);
            if (validationError.isPresent()) {
                log.warn("Skipping invalid consistency issue for exercise {}: {}", exerciseId, validationError.get());
                continue;
            }

            List<ConsistencyThreadLocation> locations = mapConsistencyIssueLocations(issue, exerciseId, repositoryUrisByTarget);
            if (locations.isEmpty()) {
                log.warn("Skipping consistency issue for exercise {} because no related repository location exists", exerciseId);
                continue;
            }

            if (locations.size() > 1) {
                CommentThreadGroup group = createConsistencyCheckGroup(exercise);
                Set<CommentThread> groupedThreads = new HashSet<>();
                for (ConsistencyThreadLocation location : locations) {
                    CommentThread thread = buildConsistencyCheckThread(exercise, location, initialCommitShasByTarget, latestProblemStatementVersion);
                    thread.setGroup(group);
                    Comment comment = buildConsistencyCheckComment(thread, issue, location, exercise, repositoryUrisByTarget, exerciseId);
                    thread.getComments().add(comment);
                    groupedThreads.add(thread);
                }
                group.setThreads(groupedThreads);
                groupsToPersist.add(group);
                createdThreads.addAll(groupedThreads);
                continue;
            }

            for (ConsistencyThreadLocation location : locations) {
                CommentThread thread = buildConsistencyCheckThread(exercise, location, initialCommitShasByTarget, latestProblemStatementVersion);
                Comment comment = buildConsistencyCheckComment(thread, issue, location, exercise, repositoryUrisByTarget, exerciseId);
                thread.getComments().add(comment);
                threadsToPersist.add(thread);
                createdThreads.add(thread);
            }
        }

        if (!groupsToPersist.isEmpty()) {
            commentThreadGroupRepository.saveAll(groupsToPersist);
        }

        if (!threadsToPersist.isEmpty()) {
            // Persist only ungrouped (single-location) consistency threads.
            // Grouped threads are persisted via CommentThreadGroup save with cascade.
            commentThreadRepository.saveAll(threadsToPersist);
        }
        List<CommentThread> persistedThreads = createdThreads.stream().filter(thread -> thread.getId() != null).toList();
        if (persistedThreads.size() != createdThreads.size()) {
            log.warn("Skipping {} consistency-check threads without ids after persistence for exercise {}", createdThreads.size() - persistedThreads.size(), exerciseId);
        }
        return persistedThreads;
    }

    /**
     * Create a new comment within a thread.
     *
     * @param exerciseId the exercise id from the request path
     * @param threadId   the thread id
     * @param dto        the comment content to create
     * @return the persisted comment
     * @throws EntityNotFoundException  if the thread does not exist
     * @throws BadRequestAlertException if validation fails or the thread exercise does not match the request exercise
     */
    public Comment createUserComment(long exerciseId, long threadId, UserCommentContentDTO dto) {
        ExerciseReviewValidationUtil.validateUserCommentPayload(dto, COMMENT_ENTITY_NAME);
        CommentThread thread = findThreadByIdElseThrow(threadId);
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, thread.getExercise().getId(), THREAD_ENTITY_NAME);

        Comment comment = buildUserComment(thread, dto);
        return commentRepository.save(comment);
    }

    /**
     * Delete a comment by id with cascade cleanup.
     * If the deleted comment was the last one in its thread, the thread is removed.
     * If that thread was the last in its group, the group is removed as well.
     *
     * @param exerciseId the exercise id from the request path
     * @param commentId  the comment id
     * @throws EntityNotFoundException  if the comment does not exist
     * @throws BadRequestAlertException if the comment exercise does not match the request exercise
     */
    public void deleteComment(long exerciseId, long commentId) {
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, comment.getThread().getExercise().getId(), COMMENT_ENTITY_NAME);

        long threadId = comment.getThread().getId();
        Long groupId = comment.getThread().getGroup() != null ? comment.getThread().getGroup().getId() : null;

        commentRepository.deleteById(comment.getId());

        if (commentRepository.countByThreadId(threadId) == 0) {
            commentThreadRepository.deleteById(threadId);
            if (groupId != null && commentThreadRepository.countByGroupId(groupId) == 0) {
                commentThreadGroupRepository.deleteById(groupId);
            }
        }
    }

    /**
     * Update the resolved flag of a thread.
     *
     * @param exerciseId the exercise id from the request path
     * @param threadId   the thread id
     * @param dto        whether the thread is resolved
     * @return the updated thread
     * @throws EntityNotFoundException  if the thread does not exist
     * @throws BadRequestAlertException if validation fails or the thread exercise does not match the request exercise
     */
    public CommentThread updateThreadResolvedState(long exerciseId, long threadId, UpdateThreadResolvedStateDTO dto) {
        ExerciseReviewValidationUtil.validateResolvedStatePayload(dto, THREAD_ENTITY_NAME);
        CommentThread thread = findThreadByIdElseThrow(threadId);
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, thread.getExercise().getId(), THREAD_ENTITY_NAME);
        thread.setResolved(dto.resolved());
        CommentThread saved = commentThreadRepository.save(thread);
        return commentThreadRepository.findWithCommentsById(saved.getId()).orElse(saved);
    }

    /**
     * Update the resolved flag of all threads in a thread group.
     *
     * @param exerciseId the exercise id from the request path
     * @param groupId    the thread group id
     * @param dto        whether the group threads are resolved
     * @return updated group threads with comments
     * @throws EntityNotFoundException  if the group does not exist
     * @throws BadRequestAlertException if validation fails or the group exercise does not match the request exercise
     */
    public List<CommentThread> updateGroupResolvedState(long exerciseId, long groupId, UpdateThreadResolvedStateDTO dto) {
        ExerciseReviewValidationUtil.validateResolvedStatePayload(dto, THREAD_GROUP_ENTITY_NAME);
        CommentThreadGroup group = commentThreadGroupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("CommentThreadGroup", groupId));
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, group.getExercise().getId(), THREAD_GROUP_ENTITY_NAME);

        List<CommentThread> threads = List.copyOf(new LinkedHashSet<>(commentThreadRepository.findWithCommentsByGroupId(groupId)));
        if (threads.isEmpty()) {
            return List.of();
        }

        boolean desiredResolvedState = dto.resolved();
        boolean modified = false;
        for (CommentThread thread : threads) {
            if (thread.isResolved() != desiredResolvedState) {
                thread.setResolved(desiredResolvedState);
                modified = true;
            }
        }
        if (modified) {
            commentThreadRepository.saveAll(threads);
        }

        return threads;
    }

    /**
     * Update the content of a comment.
     *
     * @param exerciseId the exercise id from the request path
     * @param commentId  the comment id
     * @param dto        the new comment content
     * @return the updated comment
     * @throws EntityNotFoundException  if the comment does not exist
     * @throws BadRequestAlertException if validation fails or the comment exercise does not match the request exercise
     */
    public Comment updateUserCommentContent(long exerciseId, long commentId, UserCommentContentDTO dto) {
        ExerciseReviewValidationUtil.validateUserCommentPayload(dto, COMMENT_ENTITY_NAME);
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, comment.getThread().getExercise().getId(), COMMENT_ENTITY_NAME);
        ExerciseReviewValidationUtil.validateContentMatchesType(comment.getType(), dto, COMMENT_ENTITY_NAME);
        comment.setContent(dto);
        Comment saved = commentRepository.save(comment);
        return commentRepository.findWithThreadById(saved.getId()).orElse(saved);
    }

    /**
     * Marks an inline suggested fix of a consistency-check comment as applied.
     * This operation is intentionally limited to toggling the {@code applied} flag only.
     *
     * @param exerciseId the exercise id from the request path
     * @param commentId  the comment id
     * @return the updated comment
     * @throws EntityNotFoundException  if the comment does not exist
     * @throws BadRequestAlertException if validation fails or the comment exercise does not match the request exercise
     */
    public Comment markConsistencyInlineFixApplied(long exerciseId, long commentId) {
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, comment.getThread().getExercise().getId(), COMMENT_ENTITY_NAME);

        if (comment.getType() != CommentType.CONSISTENCY_CHECK) {
            throw new BadRequestAlertException("Only consistency-check comments support inline-fix apply updates", COMMENT_ENTITY_NAME, "inlineFixNotSupported");
        }
        if (!(comment.getContent() instanceof ConsistencyIssueCommentContentDTO consistencyContent)) {
            throw new BadRequestAlertException("Comment content does not match type", COMMENT_ENTITY_NAME, "contentTypeMismatch");
        }

        InlineCodeChangeDTO inlineFix = consistencyContent.suggestedFix();
        if (inlineFix == null) {
            throw new BadRequestAlertException("Comment has no inline fix to apply", COMMENT_ENTITY_NAME, "inlineFixMissing");
        }
        if (Boolean.TRUE.equals(inlineFix.applied())) {
            return comment;
        }

        comment.setContent(new ConsistencyIssueCommentContentDTO(consistencyContent.severity(), consistencyContent.category(), consistencyContent.text(),
                new InlineCodeChangeDTO(inlineFix.startLine(), inlineFix.endLine(), inlineFix.expectedCode(), inlineFix.replacementCode(), true)));
        return commentRepository.save(comment);
    }

    /**
     * Create a new comment thread group for an exercise.
     *
     * @param exerciseId the exercise id
     * @param dto        the group creation payload
     * @return the persisted group
     * @throws EntityNotFoundException  if the exercise does not exist
     * @throws BadRequestAlertException if validation fails
     */
    public CommentThreadGroup createGroup(long exerciseId, CreateCommentThreadGroupDTO dto) {
        List<Long> threadIds = ExerciseReviewValidationUtil.validateGroupPayload(dto, THREAD_GROUP_ENTITY_NAME);
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));

        List<CommentThread> threads = commentThreadRepository.findAllById(threadIds);
        ExerciseReviewValidationUtil.validateGroupThreads(threads, threadIds.size(), exerciseId, THREAD_ENTITY_NAME);

        CommentThreadGroup group = new CommentThreadGroup();
        group.setExercise(exercise);
        CommentThreadGroup saved = commentThreadGroupRepository.save(group);
        for (CommentThread thread : threads) {
            thread.setGroup(saved);
        }
        commentThreadRepository.saveAll(threads);
        saved.setThreads(new java.util.HashSet<>(threads));
        return saved;
    }

    /**
     * Delete a comment thread group by id.
     *
     * @param exerciseId the exercise id from the request path
     * @param groupId    the group id
     * @return ids of threads that were detached from the deleted group
     * @throws EntityNotFoundException  if the group does not exist
     * @throws BadRequestAlertException if the group exercise does not match the request exercise
     */
    public List<Long> deleteGroup(long exerciseId, long groupId) {
        CommentThreadGroup group = commentThreadGroupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("CommentThreadGroup", groupId));

        ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(exerciseId, group.getExercise().getId(), THREAD_GROUP_ENTITY_NAME);

        List<CommentThread> threads = commentThreadRepository.findByGroupId(groupId);
        List<Long> threadIds = new ArrayList<>();
        if (!threads.isEmpty()) {
            for (CommentThread thread : threads) {
                thread.setGroup(null);
                threadIds.add(thread.getId());
            }
            commentThreadRepository.saveAll(threads);
        }

        commentThreadGroupRepository.delete(group);
        return List.copyOf(threadIds);
    }

    /**
     * Finds a comment thread by id or throws an {@link EntityNotFoundException}.
     *
     * @param threadId the thread id
     * @return the found thread
     */
    private CommentThread findThreadByIdElseThrow(long threadId) {
        return commentThreadRepository.findById(threadId).orElseThrow(() -> new EntityNotFoundException("CommentThread", threadId));
    }

    /**
     * Resolves the latest exercise version for problem-statement threads.
     *
     * @param thread the comment thread
     * @return the latest version for problem-statement threads, otherwise {@code null}
     */
    private ExerciseVersion resolveLatestVersion(CommentThread thread) {
        if (thread.getTargetType() != CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(thread.getExercise().getId()).orElse(null);
    }

    /**
     * Resolves the initial exercise version for a newly created problem-statement thread.
     *
     * @param targetType the thread target type
     * @param exerciseId the exercise id
     * @return the initial exercise version, or {@code null} for repository-based threads
     * @throws BadRequestAlertException if no exercise version exists for the problem statement
     */
    public ExerciseVersion resolveInitialVersion(CommentThreadLocationType targetType, long exerciseId) {
        if (targetType != CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId)
                .orElseThrow(() -> new BadRequestAlertException("No exercise version available for problem statement thread", THREAD_ENTITY_NAME, "initialVersionMissing"));
    }

    /**
     * Resolves the latest commit SHA for the repository associated with a review thread target.
     *
     * @param targetType            the thread target type (repository-based only)
     * @param auxiliaryRepositoryId the auxiliary repository id when {@code targetType} is {@code AUXILIARY_REPO}
     * @param exerciseId            the exercise id
     * @return the latest commit SHA, or {@code null} for problem-statement threads or missing repository URIs
     * @throws BadRequestAlertException if the exercise is not a programming exercise
     */
    public String resolveLatestCommitSha(CommentThreadLocationType targetType, Long auxiliaryRepositoryId, long exerciseId) {
        String commitSha = exerciseReviewRepositoryService.resolveLatestCommitSha(targetType, auxiliaryRepositoryId, exerciseId);
        if (commitSha == null && targetType != CommentThreadLocationType.PROBLEM_STATEMENT) {
            log.warn("Repository URI missing for thread target {} in exercise {}", targetType, exerciseId);
        }
        return commitSha;
    }

    /**
     * Builds a persisted consistency-check thread from a mapped issue location.
     * Problem statement threads intentionally do not store a file path.
     * Problem-statement threads store the latest exercise version at creation time when available.
     * Repository-based threads store the initial commit SHA at creation time.
     *
     * @param exercise                      the exercise owning the thread
     * @param location                      the mapped consistency issue location
     * @param initialCommitShasByTarget     pre-resolved latest commit SHAs by repository-backed target type
     * @param latestProblemStatementVersion the cached latest exercise version for problem-statement targets
     * @return a new consistency-check thread entity
     */
    private CommentThread buildConsistencyCheckThread(Exercise exercise, ConsistencyThreadLocation location, Map<CommentThreadLocationType, String> initialCommitShasByTarget,
            @Nullable ExerciseVersion latestProblemStatementVersion) {
        CommentThread thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(location.targetType());
        thread.setInitialFilePath(location.filePath());
        thread.setFilePath(location.filePath());
        thread.setInitialVersion(resolveConsistencyInitialVersion(location.targetType(), latestProblemStatementVersion));
        thread.setInitialCommitSha(resolveConsistencyInitialCommitSha(location.targetType(), initialCommitShasByTarget));
        thread.setInitialLineNumber(location.lineNumber());
        thread.setLineNumber(location.lineNumber());
        thread.setOutdated(false);
        thread.setResolved(false);
        return thread;
    }

    /**
     * Resolves the initial version for a consistency-check thread from the pre-resolved problem-statement version.
     *
     * @param targetType                    thread target type
     * @param latestProblemStatementVersion cached latest exercise version for problem-statement targets
     * @return the cached version for problem-statement targets, otherwise {@code null}
     */
    private ExerciseVersion resolveConsistencyInitialVersion(CommentThreadLocationType targetType, @Nullable ExerciseVersion latestProblemStatementVersion) {
        if (targetType != CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return latestProblemStatementVersion;
    }

    /**
     * Resolves the initial commit SHA for a consistency-check thread location from pre-resolved target SHAs.
     *
     * @param targetType                thread target type
     * @param initialCommitShasByTarget latest commit SHA lookup by target type
     * @return the resolved initial commit SHA, or {@code null} for problem-statement threads and unavailable repository SHAs
     */
    private String resolveConsistencyInitialCommitSha(CommentThreadLocationType targetType, Map<CommentThreadLocationType, String> initialCommitShasByTarget) {
        if (targetType == CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return initialCommitShasByTarget.get(targetType);
    }

    /**
     * Creates the initial consistency-check comment for a generated thread.
     *
     * @param thread                 the target thread
     * @param issue                  the consistency issue source data
     * @param location               the mapped location this thread represents
     * @param exercise               the owning exercise
     * @param repositoryUrisByTarget pre-resolved repository URIs by target
     * @param exerciseId             exercise id used for logging context
     * @return the initialized consistency-check comment
     */
    private Comment buildConsistencyCheckComment(CommentThread thread, ConsistencyIssueDTO issue, ConsistencyThreadLocation location, Exercise exercise,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget, long exerciseId) {
        User author = userRepository.getUser();

        Comment comment = new Comment();
        comment.setType(CommentType.CONSISTENCY_CHECK);
        comment.setContent(new ConsistencyIssueCommentContentDTO(issue.severity(), issue.category(), buildConsistencyIssueText(issue),
                buildInlineSuggestedFix(thread, location, exercise, repositoryUrisByTarget, exerciseId)));
        comment.setAuthor(author);
        comment.setThread(thread);
        return comment;
    }

    /**
     * Builds the rendered consistency issue text saved in the comment content.
     *
     * @param issue the consistency issue source data
     * @return the rendered issue text with optional suggested fix
     */
    private String buildConsistencyIssueText(ConsistencyIssueDTO issue) {
        String description = issue.description();
        String suggestedFix = issue.suggestedFix();
        // Defensive fallback: render description-only if suggestedFix is absent or blank.
        if (suggestedFix == null || suggestedFix.isBlank()) {
            return description;
        }
        return description + "\n\nSuggested fix: " + suggestedFix;
    }

    /**
     * Builds an inline code change from one mapped consistency location when a simple inline replacement is available.
     * Returns {@code null} when no per-location inline replacement should be persisted.
     *
     * @param thread                 persisted thread metadata
     * @param location               mapped consistency location
     * @param exercise               owning exercise
     * @param repositoryUrisByTarget resolved repository URIs by target
     * @param exerciseId             exercise id used for logging context
     * @return inline change DTO or {@code null}
     */
    @Nullable
    private InlineCodeChangeDTO buildInlineSuggestedFix(CommentThread thread, ConsistencyThreadLocation location, Exercise exercise,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget, long exerciseId) {
        String replacementCode = location.suggestedInlineFix();
        if (replacementCode == null) {
            return null;
        }

        String expectedCode = resolveExpectedCodeForLocation(thread, location, exercise, repositoryUrisByTarget, exerciseId);
        if (expectedCode == null) {
            log.warn("Skipping inline suggested fix for exercise {} at {}:{} because expected code could not be resolved", exerciseId, thread.getTargetType(),
                    thread.getLineNumber());
            return null;
        }

        return new InlineCodeChangeDTO(location.startLine(), location.endLine(), expectedCode, replacementCode, false);
    }

    @Nullable
    private String resolveExpectedCodeForLocation(CommentThread thread, ConsistencyThreadLocation location, Exercise exercise,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget, long exerciseId) {
        return switch (thread.getTargetType()) {
            case PROBLEM_STATEMENT -> extractLineRange(exercise.getProblemStatement(), location.startLine(), location.endLine());
            case TEMPLATE_REPO, SOLUTION_REPO, TEST_REPO, AUXILIARY_REPO -> {
                String filePath = thread.getFilePath();
                String commitSha = thread.getInitialCommitSha();
                if (filePath == null || filePath.isBlank() || commitSha == null || commitSha.isBlank()) {
                    yield null;
                }
                LocalVCRepositoryUri repositoryUri = resolveRepositoryUri(thread.getTargetType(), thread.getAuxiliaryRepositoryId(), repositoryUrisByTarget);
                if (repositoryUri == null) {
                    yield null;
                }
                String fileContent = readFileContentAtCommit(repositoryUri, commitSha, filePath, exerciseId, thread.getTargetType());
                yield extractLineRange(fileContent, location.startLine(), location.endLine());
            }
        };
    }

    @Nullable
    private LocalVCRepositoryUri resolveRepositoryUri(CommentThreadLocationType targetType, @Nullable Long auxiliaryRepositoryId,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        return switch (targetType) {
            case TEMPLATE_REPO, SOLUTION_REPO, TEST_REPO -> repositoryUrisByTarget.repositoryUrisByTargetType().get(targetType);
            case AUXILIARY_REPO -> auxiliaryRepositoryId == null ? null : repositoryUrisByTarget.auxiliaryRepositoryUrisById().get(auxiliaryRepositoryId);
            case PROBLEM_STATEMENT -> null;
        };
    }

    @Nullable
    private String readFileContentAtCommit(LocalVCRepositoryUri repositoryUri, String commitSha, String filePath, long exerciseId, CommentThreadLocationType targetType) {
        try (Repository repository = gitService.getBareRepository(repositoryUri, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId commitId = repository.resolve(commitSha + "^{commit}");
            if (commitId == null) {
                return null;
            }
            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
                if (treeWalk == null || treeWalk.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
                    return null;
                }
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
        catch (Exception ex) {
            log.warn("Failed to read expected code for exercise {} (target {}, file {}) at commit {}", exerciseId, targetType, filePath, commitSha, ex);
            return null;
        }
    }

    @Nullable
    private String extractLineRange(@Nullable String content, int startLine, int endLine) {
        if (content == null || startLine < 1 || endLine < startLine) {
            return null;
        }
        String[] lines = content.split("\\R", -1);
        if (endLine > lines.length) {
            return null;
        }
        return String.join("\n", Arrays.copyOfRange(lines, startLine - 1, endLine));
    }

    /**
     * Creates a new thread group for one consistency issue.
     *
     * @param exercise the owning exercise
     * @return the new unsaved thread group
     */
    private CommentThreadGroup createConsistencyCheckGroup(Exercise exercise) {
        CommentThreadGroup group = new CommentThreadGroup();
        group.setExercise(exercise);
        return group;
    }

    /**
     * Maps a consistency issue to one or more review-thread locations.
     * Assumes the issue and all locations were validated beforehand.
     *
     * @param issue                  the consistency issue
     * @param exerciseId             exercise id used for logging context
     * @param repositoryUrisByTarget repository URI lookups for consistency-check targets
     * @return normalized list of thread locations
     */
    private List<ConsistencyThreadLocation> mapConsistencyIssueLocations(ConsistencyIssueDTO issue, long exerciseId, ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        return issue.relatedLocations().stream().map(location -> mapConsistencyIssueLocation(location, exerciseId, repositoryUrisByTarget)).flatMap(Optional::stream).distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Maps a single artifact location to an internal thread location.
     *
     * @param location               the artifact location from Hyperion output
     * @param exerciseId             exercise id used for logging context
     * @param repositoryUrisByTarget repository URI lookups for consistency-check targets
     * @return normalized thread location
     */
    private Optional<ConsistencyThreadLocation> mapConsistencyIssueLocation(ArtifactLocationDTO location, long exerciseId, ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        int lineNumber = location.endLine();
        return switch (location.type()) {
            case PROBLEM_STATEMENT -> Optional.of(new ConsistencyThreadLocation(CommentThreadLocationType.PROBLEM_STATEMENT, null, lineNumber, location.startLine(),
                    location.endLine(), location.suggestedInlineFix()));
            case TEMPLATE_REPOSITORY -> mapRepositoryConsistencyIssueLocation(CommentThreadLocationType.TEMPLATE_REPO, null, location.filePath(), lineNumber, location.startLine(),
                    location.endLine(), location.suggestedInlineFix(), exerciseId, repositoryUrisByTarget);
            case SOLUTION_REPOSITORY -> mapRepositoryConsistencyIssueLocation(CommentThreadLocationType.SOLUTION_REPO, null, location.filePath(), lineNumber, location.startLine(),
                    location.endLine(), location.suggestedInlineFix(), exerciseId, repositoryUrisByTarget);
            case TESTS_REPOSITORY -> mapRepositoryConsistencyIssueLocation(CommentThreadLocationType.TEST_REPO, null, location.filePath(), lineNumber, location.startLine(),
                    location.endLine(), location.suggestedInlineFix(), exerciseId, repositoryUrisByTarget);
        };
    }

    /**
     * Maps one repository-based consistency location if the referenced file exists in the target repository.
     *
     * @param targetType             repository-backed thread target type
     * @param auxiliaryRepositoryId  auxiliary repository id when {@code targetType} is {@code AUXILIARY_REPO}
     * @param filePath               repository-relative file path
     * @param lineNumber             1-based line number
     * @param exerciseId             exercise id for logging context
     * @param repositoryUrisByTarget repository URI lookups for consistency-check targets
     * @return mapped location, or empty if the file does not exist in the repository
     */
    private Optional<ConsistencyThreadLocation> mapRepositoryConsistencyIssueLocation(CommentThreadLocationType targetType, @Nullable Long auxiliaryRepositoryId, String filePath,
            int lineNumber, int startLine, int endLine, @Nullable String suggestedInlineFix, long exerciseId, ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        Optional<String> validationError = exerciseReviewRepositoryService.validateFileExists(targetType, auxiliaryRepositoryId, filePath, repositoryUrisByTarget);
        if (validationError.isPresent()) {
            log.warn("Skipping consistency issue location for exercise {} because {}", exerciseId, validationError.get());
            return Optional.empty();
        }
        return Optional.of(new ConsistencyThreadLocation(targetType, filePath, lineNumber, startLine, endLine, suggestedInlineFix));
    }

    /**
     * Builds a user-authored comment entity for a thread.
     *
     * @param thread the target thread
     * @param dto    the user comment content
     * @return the initialized user comment
     */
    private Comment buildUserComment(CommentThread thread, UserCommentContentDTO dto) {
        User author = userRepository.getUser();
        Comment comment = new Comment();
        comment.setType(CommentType.USER);
        comment.setContent(dto);
        comment.setAuthor(author);
        comment.setInitialVersion(resolveLatestVersion(thread));
        comment.setInitialCommitSha(resolveLatestCommitSha(thread.getTargetType(), thread.getAuxiliaryRepositoryId(), thread.getExercise().getId()));
        comment.setThread(thread);
        return comment;
    }

    /**
     * Internal normalized representation of a consistency issue location used to create review threads.
     *
     * @param targetType         target location type for the thread
     * @param filePath           repository-relative file path; {@code null} for problem statement
     * @param lineNumber         1-based line number anchor
     * @param startLine          first line in the location range
     * @param endLine            last line in the location range
     * @param suggestedInlineFix optional per-location inline replacement
     */
    private record ConsistencyThreadLocation(CommentThreadLocationType targetType, @Nullable String filePath, int lineNumber, int startLine, int endLine,
            @Nullable String suggestedInlineFix) {
    }

    /**
     * Container for a newly created thread and its initial comment.
     *
     * @param thread  the created thread
     * @param comment the created initial comment
     */
    public record ThreadCreationResult(CommentThread thread, Comment comment) {
    }
}
