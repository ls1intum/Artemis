package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
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
public class ExerciseReviewCommentService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseReviewCommentService.class);

    private static final String COMMENT_ENTITY_NAME = "exerciseReviewComment";

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    private final CommentThreadRepository commentThreadRepository;

    private final CommentRepository commentRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final GitService gitService;

    public ExerciseReviewCommentService(CommentThreadRepository commentThreadRepository, CommentRepository commentRepository, ExerciseRepository exerciseRepository,
            ExerciseVersionRepository exerciseVersionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService,
            GitService gitService) {
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

        if (comment.getType() == CommentType.USER) {
            User author = userRepository.getUserWithGroupsAndAuthorities();
            comment.setAuthor(author);
        }

        if (commentRepository.countByThreadId(threadId) == 0) {
            comment.setInitialVersion(thread.getInitialVersion());
            comment.setInitialCommitSha(thread.getInitialCommitSha());
        }
        else {
            comment.setInitialVersion(resolveLatestVersion(thread));
            comment.setInitialCommitSha(resolveLatestCommitSha(thread.getTargetType(), thread.getAuxiliaryRepositoryId(), thread.getExercise().getId()));
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
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        CommentThread thread = comment.getThread();
        commentRepository.delete(comment);
        if (commentRepository.countByThreadId(thread.getId()) == 0) {
            commentThreadRepository.delete(thread);
        }
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
        CommentThread saved = commentThreadRepository.save(thread);
        return commentThreadRepository.findWithCommentsById(saved.getId()).orElse(saved);
    }

    /**
     * Update the content of a comment.
     *
     * @param commentId the comment id
     * @param content   the new content
     * @return the updated comment
     */
    public Comment updateCommentContent(long commentId, CommentContentDTO content) {
        Comment comment = commentRepository.findWithThreadById(commentId).orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.INSTRUCTOR, comment.getThread().getExercise().getId());
        validateContentMatchesType(comment.getType(), content);
        comment.setContent(content);
        comment.setLastModifiedDate(Instant.now());
        Comment saved = commentRepository.save(comment);
        return commentRepository.findWithThreadById(saved.getId()).orElse(saved);
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
        if (previousProgramming == null || currentProgramming == null) {
            return;
        }

        List<CommentThread> threads = commentThreadRepository.findByExerciseId(currentSnapshot.id());
        if (threads.isEmpty()) {
            return;
        }

        boolean updated = false;
        for (CommentThread thread : threads) {
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
                    updated = true;
                }
                if (result.newLine() != null && !Objects.equals(thread.getLineNumber(), result.newLine())) {
                    thread.setLineNumber(result.newLine());
                    updated = true;
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
                    updated = true;
                }
                if (result.newLine() != null && !Objects.equals(thread.getLineNumber(), result.newLine())) {
                    thread.setLineNumber(result.newLine());
                    updated = true;
                }
            }
            catch (Exception ex) {
                log.warn("Could not map line for thread {}: {}", thread.getId(), ex.getMessage());
            }
        }

        if (updated) {
            commentThreadRepository.saveAll(threads);
        }
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

    private Optional<RepoDiffInfo> resolveRepoDiffInfo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current, CommentThread thread) {
        return switch (thread.getTargetType()) {
            case TEMPLATE_REPO -> mapParticipationRepo(previous.templateParticipation(), current.templateParticipation());
            case SOLUTION_REPO -> mapParticipationRepo(previous.solutionParticipation(), current.solutionParticipation());
            case TEST_REPO -> mapTestRepo(previous, current);
            case AUXILIARY_REPO -> mapAuxRepo(previous, current, thread.getAuxiliaryRepositoryId());
            case PROBLEM_STATEMENT -> Optional.empty();
        };
    }

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
     * Maps a line number from an old commit to a new commit for a given file path.
     * Uses a bare repository and Git diff hunks to determine if the line moved or was modified.
     *
     * @param repositoryUri the repository to diff
     * @param filePath      repository-relative file path
     * @param oldCommit     base commit hash
     * @param newCommit     target commit hash
     * @param oldLine       1-based line number in the old commit
     * @return mapping result containing the new line or outdated state
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

    public record LineMappingResult(@Nullable Integer newLine, boolean outdated) {
    }

    private Optional<RepoDiffInfo> mapTestRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current) {
        if (previous.testsCommitId() == null || current.testsCommitId() == null || current.testRepositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(current.testRepositoryUri()), previous.testsCommitId(), current.testsCommitId()));
    }

    private Optional<RepoDiffInfo> mapAuxRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current, Long auxiliaryRepositoryId) {
        if (auxiliaryRepositoryId == null || previous.auxiliaryRepositories() == null || current.auxiliaryRepositories() == null) {
            return Optional.empty();
        }
        var previousRepo = previous.auxiliaryRepositories().stream().filter(repo -> repo.id() == auxiliaryRepositoryId).findFirst().orElse(null);
        var currentRepo = current.auxiliaryRepositories().stream().filter(repo -> repo.id() == auxiliaryRepositoryId).findFirst().orElse(null);
        if (previousRepo == null || currentRepo == null || previousRepo.commitId() == null || currentRepo.commitId() == null || currentRepo.repositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(currentRepo.repositoryUri()), previousRepo.commitId(), currentRepo.commitId()));
    }

    private ExerciseVersion resolveLatestVersion(CommentThread thread) {
        if (thread.getTargetType() != CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(thread.getExercise().getId()).orElse(null);
    }

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

    private record RepoDiffInfo(LocalVCRepositoryUri repositoryUri, String oldCommit, String newCommit) {
    }
}
