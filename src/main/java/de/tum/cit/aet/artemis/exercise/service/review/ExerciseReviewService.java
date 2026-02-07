package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
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

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final GitService gitService;

    public ExerciseReviewService(CommentThreadGroupRepository commentThreadGroupRepository, CommentThreadRepository commentThreadRepository, CommentRepository commentRepository,
            ExerciseRepository exerciseRepository, ExerciseVersionRepository exerciseVersionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService,
            GitService gitService) {
        this.commentThreadGroupRepository = commentThreadGroupRepository;
        this.commentThreadRepository = commentThreadRepository;
        this.commentRepository = commentRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.gitService = gitService;
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
    public Set<CommentThread> findThreadsWithCommentsByExerciseId(long exerciseId) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        return commentThreadRepository.findWithCommentsByExerciseId(exerciseId);
    }

    /**
     * Retrieve all comments for a thread, ordered by creation date.
     *
     * @param threadId the thread id
     * @return list of comments
     * @throws EntityNotFoundException if the thread does not exist
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
     * @param dto        the thread creation payload
     * @return the created thread and its initial comment
     * @throws BadRequestAlertException if validation fails
     * @throws EntityNotFoundException  if the exercise does not exist
     */
    public ThreadCreationResult createThread(long exerciseId, CreateCommentThreadDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", THREAD_ENTITY_NAME, "bodyMissing");
        }
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        validateThreadPayload(dto);

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
     * Create a new comment within a thread.
     *
     * @param threadId the thread id
     * @param dto      the comment content to create
     * @return the persisted comment
     * @throws EntityNotFoundException  if the thread does not exist
     * @throws BadRequestAlertException if validation fails
     */
    public Comment createUserComment(long threadId, UserCommentContentDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("Comment content must be set", COMMENT_ENTITY_NAME, "contentMissing");
        }
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());

        Comment comment = buildUserComment(thread, dto);
        return commentRepository.save(comment);
    }

    /**
     * Delete a comment by id.
     *
     * @param commentId the comment id
     * @throws EntityNotFoundException if the comment does not exist
     */
    @Transactional // Ensure atomic delete of comment, thread, and group to avoid orphaned data on failure.
    public void deleteComment(long commentId) {
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        CommentThread thread = comment.getThread();
        CommentThreadGroup group = thread.getGroup();
        commentRepository.delete(comment);
        if (commentRepository.countByThreadId(thread.getId()) == 0) {
            commentThreadRepository.delete(thread);
            if (group != null && commentThreadRepository.countByGroupId(group.getId()) == 0) {
                commentThreadGroupRepository.delete(group);
            }
        }
    }

    /**
     * Update the resolved flag of a thread.
     *
     * @param threadId the thread id
     * @param dto      whether the thread is resolved
     * @return the updated thread
     * @throws EntityNotFoundException  if the thread does not exist
     * @throws BadRequestAlertException if validation fails
     */
    public CommentThread updateThreadResolvedState(long threadId, UpdateThreadResolvedStateDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", THREAD_ENTITY_NAME, "bodyMissing");
        }
        CommentThread thread = findThreadByIdElseThrow(threadId);
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, thread.getExercise().getId());
        thread.setResolved(dto.resolved());
        CommentThread saved = commentThreadRepository.save(thread);
        return commentThreadRepository.findWithCommentsById(saved.getId()).orElse(saved);
    }

    /**
     * Update the content of a comment.
     *
     * @param commentId the comment id
     * @param dto       the new comment content
     * @return the updated comment
     * @throws EntityNotFoundException  if the comment does not exist
     * @throws BadRequestAlertException if validation fails
     */
    public Comment updateUserCommentContent(long commentId, UserCommentContentDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("Comment content must be set", COMMENT_ENTITY_NAME, "contentMissing");
        }
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        validateContentMatchesType(comment.getType(), dto);
        comment.setContent(dto);
        Comment saved = commentRepository.save(comment);
        return commentRepository.findWithThreadById(saved.getId()).orElse(saved);
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
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", THREAD_GROUP_ENTITY_NAME, "bodyMissing");
        }
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, exerciseId);
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));

        List<Long> threadIds = dto.threadIds();
        if (threadIds == null || threadIds.size() < 2) {
            throw new BadRequestAlertException("A thread group must contain at least two threads", THREAD_GROUP_ENTITY_NAME, "threadCountTooLow");
        }

        List<CommentThread> threads = commentThreadRepository.findAllById(threadIds);
        if (threads.size() != threadIds.size()) {
            throw new BadRequestAlertException("Some threads do not exist", THREAD_ENTITY_NAME, "threadMissing");
        }

        for (CommentThread thread : threads) {
            if (thread.getExercise() == null || !Objects.equals(thread.getExercise().getId(), exerciseId)) {
                throw new BadRequestAlertException("Thread exercise does not match request", THREAD_ENTITY_NAME, "exerciseMismatch");
            }
            if (thread.getGroup() != null) {
                throw new BadRequestAlertException("Thread already belongs to another group", THREAD_ENTITY_NAME, "threadGrouped");
            }
        }

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
     * @param groupId the group id
     * @throws EntityNotFoundException if the group does not exist
     */
    public void deleteGroup(long groupId) {
        CommentThreadGroup group = commentThreadGroupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("CommentThreadGroup", groupId));

        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, group.getExercise().getId());

        List<CommentThread> threads = commentThreadRepository.findByGroupId(groupId);
        if (!threads.isEmpty()) {
            for (CommentThread thread : threads) {
                thread.setGroup(null);
            }
            commentThreadRepository.saveAll(threads);
        }

        commentThreadGroupRepository.delete(group);
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
     * Validates that the comment content matches the given comment type.
     *
     * @param type    the comment type to validate against
     * @param content the content payload to validate
     */
    private void validateContentMatchesType(CommentType type, CommentContentDTO content) {
        if (type == null || content == null) {
            throw new BadRequestAlertException("Comment content and type must be set", COMMENT_ENTITY_NAME, "contentOrTypeMissing");
        }

        boolean isValid = switch (type) {
            case USER -> content instanceof UserCommentContentDTO;
            case CONSISTENCY_CHECK -> content instanceof ConsistencyIssueCommentContentDTO;
        };

        if (!isValid) {
            throw new BadRequestAlertException("Comment content does not match type", COMMENT_ENTITY_NAME, "contentTypeMismatch");
        }
    }

    /**
     * Validates thread payload invariants based on target type (problem statement vs repository targets).
     *
     * @param dto the thread creation payload to validate
     */
    private void validateThreadPayload(CreateCommentThreadDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("Thread DTO must be set", THREAD_ENTITY_NAME, "bodyMissing");
        }
        if (dto.targetType() == null) {
            throw new BadRequestAlertException("Thread target type must be set", THREAD_ENTITY_NAME, "targetTypeMissing");
        }
        if (dto.initialLineNumber() == null) {
            throw new BadRequestAlertException("Initial line number must be set", THREAD_ENTITY_NAME, "initialLineNumberMissing");
        }
        if (dto.initialLineNumber() < 1) {
            throw new BadRequestAlertException("Initial line number must be at least 1", THREAD_ENTITY_NAME, "initialLineNumberInvalid");
        }
        if (dto.initialComment() == null) {
            throw new BadRequestAlertException("Initial comment must be set", THREAD_ENTITY_NAME, "initialCommentMissing");
        }
        if (dto.targetType() != CommentThreadLocationType.PROBLEM_STATEMENT && dto.initialFilePath() == null) {
            throw new BadRequestAlertException("Initial file path is required for repository threads", THREAD_ENTITY_NAME, "initialFilePathMissing");
        }
        if (dto.targetType() == CommentThreadLocationType.PROBLEM_STATEMENT && dto.initialFilePath() != null) {
            throw new BadRequestAlertException("Initial file path is not allowed for problem statement threads", THREAD_ENTITY_NAME, "initialFilePathNotAllowed");
        }
        if (dto.targetType() != CommentThreadLocationType.PROBLEM_STATEMENT) {
            try {
                FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(dto.initialFilePath());
            }
            catch (IllegalArgumentException ex) {
                throw new BadRequestAlertException("Initial file path is invalid", THREAD_ENTITY_NAME, "initialFilePathInvalid");
            }
        }
        if (dto.targetType() != CommentThreadLocationType.AUXILIARY_REPO && dto.auxiliaryRepositoryId() != null) {
            throw new BadRequestAlertException("Auxiliary repository id is only allowed for auxiliary repository threads", THREAD_ENTITY_NAME, "auxiliaryRepositoryNotAllowed");
        }
        if (dto.targetType() == CommentThreadLocationType.AUXILIARY_REPO && dto.auxiliaryRepositoryId() == null) {
            throw new BadRequestAlertException("Auxiliary repository id is required for auxiliary repository threads", THREAD_ENTITY_NAME, "auxiliaryRepositoryMissing");
        }
    }

    /**
     * Update thread line numbers and outdated state based on a new exercise version (repository threads only).
     *
     * @param previousSnapshot the previous exercise snapshot
     * @param currentSnapshot  the current exercise snapshot
     */
    public void updateThreadsForVersionChange(ExerciseSnapshotDTO previousSnapshot, ExerciseSnapshotDTO currentSnapshot) {
        if (previousSnapshot == null || currentSnapshot == null) {
            return;
        }

        ProgrammingExerciseSnapshotDTO previousProgramming = previousSnapshot.programmingData();
        ProgrammingExerciseSnapshotDTO currentProgramming = currentSnapshot.programmingData();
        if (previousProgramming == null || currentProgramming == null || !Objects.equals(previousSnapshot.id(), currentSnapshot.id())) {
            return;
        }

        List<CommentThread> threads = commentThreadRepository.findByExerciseId(currentSnapshot.id());
        if (threads.isEmpty()) {
            return;
        }

        Set<CommentThread> modifiedThreads = new HashSet<>();
        for (CommentThread thread : threads) {
            boolean modified = false;
            if (thread.getLineNumber() == null) {
                continue;
            }
            if (thread.getTargetType() != CommentThreadLocationType.PROBLEM_STATEMENT && thread.getFilePath() == null) {
                continue;
            }

            if (thread.getTargetType() == CommentThreadLocationType.PROBLEM_STATEMENT) {
                LineMappingResult result = mapLineInText(previousSnapshot.problemStatement(), currentSnapshot.problemStatement(), thread.getLineNumber());
                if (result.outdated()) {
                    thread.setOutdated(true);
                    modified = true;
                }
                if (result.newLine() != null && !Objects.equals(thread.getLineNumber(), result.newLine())) {
                    thread.setLineNumber(result.newLine());
                    modified = true;
                }
                if (modified) {
                    modifiedThreads.add(thread);
                }
                continue;
            }

            Optional<RepoDiffInfo> diffInfo = resolveRepoDiffInfo(previousProgramming, currentProgramming, thread);
            if (diffInfo.isEmpty()) {
                continue;
            }

            RepoDiffInfo info = diffInfo.get();
            if (Objects.equals(info.oldCommit(), info.newCommit())) {
                continue;
            }

            try {
                LineMappingResult result = mapLine(info.repositoryUri(), thread.getFilePath(), info.oldCommit(), info.newCommit(), thread.getLineNumber());
                if (result.outdated()) {
                    thread.setOutdated(true);
                    modified = true;
                }
                if (result.newLine() != null && !Objects.equals(thread.getLineNumber(), result.newLine())) {
                    thread.setLineNumber(result.newLine());
                    modified = true;
                }
            }
            catch (Exception ex) {
                log.warn("Could not map line for thread {}: {}", thread.getId(), ex.getMessage());
            }
            if (modified) {
                modifiedThreads.add(thread);
            }
        }

        if (!modifiedThreads.isEmpty()) {
            commentThreadRepository.saveAll(modifiedThreads);
        }
    }

    /**
     * Maps a line number from an old commit to a new commit for a given file path.
     * Uses a bare repository and Git diff hunks to determine if the line moved or was modified.
     *
     * @param repositoryUri the repository to diff
     * @param filePath      repository-relative file path
     * @param oldCommit     base commit hash
     * @param newCommit     target commit hash
     * @param oldLine       1-based line number in the old commit
     * @return mapping result containing the new line or outdated state
     * @throws GitException if repository access fails or the commit trees cannot be resolved
     */
    public LineMappingResult mapLine(LocalVCRepositoryUri repositoryUri, String filePath, String oldCommit, String newCommit, int oldLine) {
        if (oldLine <= 0) {
            return new LineMappingResult(null, true);
        }

        try (Repository repository = gitService.getBareRepository(repositoryUri, false);
                ObjectReader reader = repository.newObjectReader();
                DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(false);

            ObjectId oldTree = repository.resolve(oldCommit + "^{tree}");
            ObjectId newTree = repository.resolve(newCommit + "^{tree}");
            if (oldTree == null || newTree == null) {
                throw new GitException("Cannot resolve commit trees for line mapping");
            }

            CanonicalTreeParser oldParser = new CanonicalTreeParser();
            oldParser.reset(reader, oldTree);
            CanonicalTreeParser newParser = new CanonicalTreeParser();
            newParser.reset(reader, newTree);

            List<DiffEntry> entries = diffFormatter.scan(oldParser, newParser);
            for (DiffEntry entry : entries) {
                String oldPath = entry.getOldPath();
                String newPath = entry.getNewPath();
                boolean matchesOld = filePath.equals(oldPath);
                boolean matchesNew = filePath.equals(newPath);
                if (!matchesOld && !matchesNew) {
                    continue;
                }

                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    return new LineMappingResult(null, true);
                }
                if (entry.getChangeType() == DiffEntry.ChangeType.ADD && matchesNew && !matchesOld) {
                    return new LineMappingResult(null, true);
                }

                EditList edits = diffFormatter.toFileHeader(entry).toEditList();
                return mapLineWithEdits(oldLine, edits);
            }

            return new LineMappingResult(oldLine, false);
        }
        catch (IOException ex) {
            throw new GitException("Cannot map line for file " + filePath, ex);
        }
    }

    /**
     * Maps a line number between two text snapshots using a diff algorithm.
     *
     * @param oldText old text snapshot (null treated as empty)
     * @param newText new text snapshot (null treated as empty)
     * @param oldLine 1-based line number in the old text
     * @return mapping result containing the new line or outdated state
     */
    public LineMappingResult mapLineInText(@Nullable String oldText, @Nullable String newText, int oldLine) {
        if (oldLine <= 0) {
            return new LineMappingResult(null, true);
        }

        String safeOld = oldText == null ? "" : oldText;
        String safeNew = newText == null ? "" : newText;
        RawText oldRaw = new RawText(safeOld.getBytes(StandardCharsets.UTF_8));
        RawText newRaw = new RawText(safeNew.getBytes(StandardCharsets.UTF_8));
        EditList edits = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS).diff(RawTextComparator.DEFAULT, oldRaw, newRaw);
        return mapLineWithEdits(oldLine, edits);
    }

    /**
     * Applies diff edits to map a line number and determine outdated status.
     *
     * @param oldLine the 1-based line number in the original content
     * @param edits   the diff edits between old and new content
     * @return the mapping result
     */
    private LineMappingResult mapLineWithEdits(int oldLine, EditList edits) {
        int zeroBasedLine = oldLine - 1;
        int mappedLine = zeroBasedLine;

        for (Edit edit : edits) {
            if (zeroBasedLine < edit.getBeginA()) {
                break;
            }
            if (zeroBasedLine >= edit.getEndA()) {
                int delta = (edit.getEndB() - edit.getBeginB()) - (edit.getEndA() - edit.getBeginA());
                mappedLine += delta;
                continue;
            }
            int offset = zeroBasedLine - edit.getBeginA();
            int newHunkLength = edit.getEndB() - edit.getBeginB();
            int newLine = newHunkLength == 0 ? edit.getBeginB() : edit.getBeginB() + Math.min(offset, newHunkLength - 1);
            return new LineMappingResult(newLine + 1, true);
        }

        return new LineMappingResult(mappedLine + 1, false);
    }

    /**
     * Result of a line mapping operation.
     *
     * @param newLine  the mapped 1-based line number, or {@code null} if the line can no longer be mapped
     * @param outdated whether the original line was modified by the change
     */
    public record LineMappingResult(@Nullable Integer newLine, boolean outdated) {
    }

    /**
     * Resolves repository diff information for a thread target based on exercise snapshots.
     *
     * @param previous the previous programming exercise snapshot
     * @param current  the current programming exercise snapshot
     * @param thread   the thread whose target determines the repository mapping
     * @return the diff info if applicable, otherwise empty
     */
    private Optional<RepoDiffInfo> resolveRepoDiffInfo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current, CommentThread thread) {
        if (previous == null || current == null) {
            return Optional.empty();
        }

        return switch (thread.getTargetType()) {
            case TEMPLATE_REPO -> mapParticipationRepo(previous.templateParticipation(), current.templateParticipation());
            case SOLUTION_REPO -> mapParticipationRepo(previous.solutionParticipation(), current.solutionParticipation());
            case TEST_REPO -> mapTestRepo(previous, current);
            case AUXILIARY_REPO -> mapAuxRepo(previous, current, thread.getAuxiliaryRepositoryId());
            case PROBLEM_STATEMENT -> Optional.empty();
        };
    }

    /**
     * Maps a participation-based repository to diff info if both snapshots contain commit and URI data.
     *
     * @param previous the previous participation snapshot
     * @param current  the current participation snapshot
     * @return the diff info if available, otherwise empty
     */
    private Optional<RepoDiffInfo> mapParticipationRepo(ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO previous,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO current) {
        if (previous == null || current == null) {
            return Optional.empty();
        }
        if (previous.commitId() == null || current.commitId() == null || current.repositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(current.repositoryUri()), previous.commitId(), current.commitId()));
    }

    /**
     * Maps the test repository between snapshots to diff info when commit data exists.
     *
     * @param previous the previous programming exercise snapshot
     * @param current  the current programming exercise snapshot
     * @return the diff info if available, otherwise empty
     */
    private Optional<RepoDiffInfo> mapTestRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current) {
        if (previous.testsCommitId() == null || current.testsCommitId() == null || current.testRepositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(current.testRepositoryUri()), previous.testsCommitId(), current.testsCommitId()));
    }

    /**
     * Maps an auxiliary repository between snapshots to diff info when commit data exists.
     *
     * @param previous              the previous programming exercise snapshot
     * @param current               the current programming exercise snapshot
     * @param auxiliaryRepositoryId the auxiliary repository id to resolve
     * @return the diff info if available, otherwise empty
     */
    private Optional<RepoDiffInfo> mapAuxRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current, Long auxiliaryRepositoryId) {
        if (auxiliaryRepositoryId == null || previous.auxiliaryRepositories() == null || current.auxiliaryRepositories() == null) {
            return Optional.empty();
        }
        var previousRepo = previous.auxiliaryRepositories().stream().filter(repo -> Objects.equals(repo.id(), auxiliaryRepositoryId)).findFirst().orElse(null);
        var currentRepo = current.auxiliaryRepositories().stream().filter(repo -> Objects.equals(repo.id(), auxiliaryRepositoryId)).findFirst().orElse(null);
        if (previousRepo == null || currentRepo == null || previousRepo.commitId() == null || currentRepo.commitId() == null || currentRepo.repositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(currentRepo.repositoryUri()), previousRepo.commitId(), currentRepo.commitId()));
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
        if (targetType == CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }

        ProgrammingExercise exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(exerciseId)
                .orElseThrow(() -> new BadRequestAlertException("Exercise is not a programming exercise", THREAD_ENTITY_NAME, "exerciseNotProgramming"));

        LocalVCRepositoryUri repositoryUri = switch (targetType) {
            case TEMPLATE_REPO -> exercise.getVcsTemplateRepositoryUri() != null ? exercise.getVcsTemplateRepositoryUri()
                    : exercise.getTemplateParticipation() != null ? exercise.getTemplateParticipation().getVcsRepositoryUri() : null;
            case SOLUTION_REPO -> exercise.getVcsSolutionRepositoryUri() != null ? exercise.getVcsSolutionRepositoryUri()
                    : exercise.getSolutionParticipation() != null ? exercise.getSolutionParticipation().getVcsRepositoryUri() : null;
            case TEST_REPO -> exercise.getVcsTestRepositoryUri();
            case AUXILIARY_REPO -> getAuxiliaryRepositoryUri(auxiliaryRepositoryId, exerciseId);
            case PROBLEM_STATEMENT -> null;
        };

        if (repositoryUri == null) {
            log.warn("Repository URI missing for thread target {} in exercise {}", targetType, exerciseId);
            return null;
        }

        return gitService.getLastCommitHash(repositoryUri);
    }

    /**
     * Resolves the LocalVC repository URI for the given auxiliary repository id and exercise.
     *
     * @param auxiliaryRepositoryId the auxiliary repository id
     * @param exerciseId            the exercise id
     * @return the resolved repository URI
     */
    private LocalVCRepositoryUri getAuxiliaryRepositoryUri(Long auxiliaryRepositoryId, long exerciseId) {
        if (auxiliaryRepositoryId == null) {
            throw new BadRequestAlertException("Auxiliary repository id is required", THREAD_ENTITY_NAME, "auxiliaryRepositoryMissing");
        }

        AuxiliaryRepository auxiliaryRepository = auxiliaryRepositoryRepository.findById(auxiliaryRepositoryId)
                .orElseThrow(() -> new EntityNotFoundException("AuxiliaryRepository", auxiliaryRepositoryId));
        if (auxiliaryRepository.getExercise() == null || auxiliaryRepository.getExercise().getId() == null
                || !Objects.equals(auxiliaryRepository.getExercise().getId(), exerciseId)) {
            throw new BadRequestAlertException("Auxiliary repository does not belong to exercise", THREAD_ENTITY_NAME, "auxiliaryRepositoryMismatch");
        }
        return auxiliaryRepository.getVcsRepositoryUri();
    }

    /**
     * Lightweight container for diff inputs used for line mapping.
     *
     * @param repositoryUri the repository to diff
     * @param oldCommit     the old commit hash
     * @param newCommit     the new commit hash
     */
    private record RepoDiffInfo(LocalVCRepositoryUri repositoryUri, String oldCommit, String newCommit) {
    }

    /**
     * Container for a newly created thread and its initial comment.
     *
     * @param thread  the created thread
     * @param comment the created initial comment
     */
    public record ThreadCreationResult(CommentThread thread, Comment comment) {
    }

    private Comment buildUserComment(CommentThread thread, UserCommentContentDTO dto) {
        User author = userRepository.getUserWithGroupsAndAuthorities();
        Comment comment = new Comment();
        comment.setType(CommentType.USER);
        comment.setContent(dto);
        comment.setAuthor(author);
        comment.setInitialVersion(resolveLatestVersion(thread));
        comment.setInitialCommitSha(resolveLatestCommitSha(thread.getTargetType(), thread.getAuxiliaryRepositoryId(), thread.getExercise().getId()));
        comment.setThread(thread);
        return comment;
    }
}
