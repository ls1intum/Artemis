package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.InlineCodeChangeDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Service responsible for mapping review thread anchors and inline-fix ranges across exercise versions.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseReviewVersionChangeService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseReviewVersionChangeService.class);

    private final CommentThreadRepository commentThreadRepository;

    private final CommentRepository commentRepository;

    private final GitService gitService;

    public ExerciseReviewVersionChangeService(CommentThreadRepository commentThreadRepository, CommentRepository commentRepository, GitService gitService) {
        this.commentThreadRepository = commentThreadRepository;
        this.commentRepository = commentRepository;
        this.gitService = gitService;
    }

    /**
     * Update thread line numbers and outdated state based on a new exercise version.
     * This also updates consistency-inline-fix ranges for comments that have a suggested fix.
     * Mapping work is grouped by target/repository/file to avoid repeated git and database operations.
     *
     * @param previousSnapshot the previous exercise snapshot
     * @param currentSnapshot  the current exercise snapshot
     * @return modified threads that should be synchronized with active editors
     */
    public List<CommentThread> updateThreadsForVersionChange(ExerciseSnapshotDTO previousSnapshot, ExerciseSnapshotDTO currentSnapshot) {
        if (previousSnapshot == null || currentSnapshot == null) {
            return List.of();
        }
        if (!Objects.equals(previousSnapshot.id(), currentSnapshot.id())) {
            return List.of();
        }

        List<CommentThread> threads = commentThreadRepository.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(currentSnapshot.id());
        if (threads.isEmpty()) {
            return List.of();
        }

        ProgrammingExerciseSnapshotDTO previousProgramming = previousSnapshot.programmingData();
        ProgrammingExerciseSnapshotDTO currentProgramming = currentSnapshot.programmingData();
        boolean hasProgrammingSnapshots = previousProgramming != null && currentProgramming != null;

        Map<Long, ThreadMappingTask> mappingTasksByThreadId = new HashMap<>();
        Map<RepoDiffKey, Set<String>> requiredFilePathsByRepoDiff = new HashMap<>();
        boolean hasProblemStatementTasks = false;

        for (CommentThread thread : threads) {
            Long threadId = thread.getId();
            Integer lineNumber = thread.getLineNumber();
            if (threadId == null || lineNumber == null) {
                continue;
            }

            if (thread.getTargetType() == CommentThreadLocationType.PROBLEM_STATEMENT) {
                mappingTasksByThreadId.put(threadId, ThreadMappingTask.forProblemStatement(thread));
                hasProblemStatementTasks = true;
                continue;
            }

            String filePath = thread.getFilePath();
            if (!hasProgrammingSnapshots || filePath == null || filePath.isBlank()) {
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

            RepoDiffKey repoDiffKey = new RepoDiffKey(info.repositoryUri(), info.oldCommit(), info.newCommit());
            mappingTasksByThreadId.put(threadId, ThreadMappingTask.forRepository(thread, repoDiffKey, filePath));
            requiredFilePathsByRepoDiff.computeIfAbsent(repoDiffKey, ignored -> new HashSet<>()).add(filePath);
        }

        if (mappingTasksByThreadId.isEmpty()) {
            return List.of();
        }

        EditList problemStatementEdits = hasProblemStatementTasks ? calculateTextEdits(previousSnapshot.problemStatement(), currentSnapshot.problemStatement()) : null;
        Map<RepoDiffKey, Map<String, FileMappingPlan>> filePlansByRepoDiff = prepareRepoFilePlans(requiredFilePathsByRepoDiff);

        Set<CommentThread> modifiedThreads = new HashSet<>();
        for (ThreadMappingTask task : mappingTasksByThreadId.values()) {
            LineMappingResult mappedAnchor = mapAnchorForTask(task, problemStatementEdits, filePlansByRepoDiff);
            if (mappedAnchor == null) {
                continue;
            }
            if (applyAnchorUpdate(task.thread(), mappedAnchor)) {
                modifiedThreads.add(task.thread());
            }
        }

        List<Comment> consistencyComments = findConsistencyCommentsByThreadIds(mappingTasksByThreadId.keySet());
        Set<Comment> modifiedComments = new HashSet<>();
        for (Comment comment : consistencyComments) {
            Long threadId = comment.getThread() != null ? comment.getThread().getId() : null;
            if (threadId == null) {
                continue;
            }

            ThreadMappingTask task = mappingTasksByThreadId.get(threadId);
            if (task == null) {
                continue;
            }
            if (!(comment.getContent() instanceof ConsistencyIssueCommentContentDTO consistencyContent)) {
                continue;
            }

            InlineCodeChangeDTO inlineFix = consistencyContent.suggestedFix();
            if (inlineFix == null) {
                continue;
            }

            LineRangeMappingResult mappedRange = mapInlineFixRangeForTask(task, inlineFix, problemStatementEdits, filePlansByRepoDiff);
            if (mappedRange == null) {
                continue;
            }

            if (mappedRange.outdated() && !task.thread().isOutdated()) {
                task.thread().setOutdated(true);
                modifiedThreads.add(task.thread());
            }

            int mappedStart = mappedRange.newStartLine() != null ? mappedRange.newStartLine() : inlineFix.startLine();
            int mappedEnd = mappedRange.newEndLine() != null ? mappedRange.newEndLine() : inlineFix.endLine();
            if (mappedEnd < mappedStart) {
                mappedEnd = mappedStart;
            }

            if (mappedStart == inlineFix.startLine() && mappedEnd == inlineFix.endLine()) {
                continue;
            }

            InlineCodeChangeDTO updatedInlineFix = new InlineCodeChangeDTO(mappedStart, mappedEnd, inlineFix.expectedCode(), inlineFix.replacementCode(), inlineFix.applied());
            comment.setContent(new ConsistencyIssueCommentContentDTO(consistencyContent.severity(), consistencyContent.category(), consistencyContent.text(), updatedInlineFix));
            modifiedComments.add(comment);
        }

        if (!modifiedThreads.isEmpty()) {
            commentThreadRepository.saveAll(modifiedThreads);
        }
        if (!modifiedComments.isEmpty()) {
            commentRepository.saveAll(modifiedComments);
        }
        return List.copyOf(modifiedThreads);
    }

    /**
     * Maps a line number from an old commit to a new commit for a given file path.
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
        RepoDiffKey repoDiffKey = new RepoDiffKey(repositoryUri, oldCommit, newCommit);
        try {
            Map<String, FileMappingPlan> plans = computeRepositoryFilePlans(repoDiffKey, Set.of(filePath));
            FileMappingPlan filePlan = plans.getOrDefault(filePath, FileMappingPlan.unchanged());
            return mapLineWithPlan(oldLine, filePlan);
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
        EditList edits = calculateTextEdits(oldText, newText);
        return mapLineWithEdits(oldLine, edits);
    }

    /**
     * Loads consistency comments for the given set of thread ids in one query.
     *
     * @param threadIds thread ids that are part of the current version-change mapping run
     * @return consistency comments belonging to the provided threads
     */
    private List<Comment> findConsistencyCommentsByThreadIds(Collection<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return List.of();
        }
        return commentRepository.findByThreadIdsAndType(threadIds, CommentType.CONSISTENCY_CHECK);
    }

    /**
     * Maps a thread anchor line for a prepared mapping task.
     *
     * @param task                  mapping task for one thread
     * @param problemStatementEdits precomputed text edits for problem statement mapping
     * @param filePlansByRepoDiff   precomputed per-file plans grouped by repository diff
     * @return mapped line result, or {@code null} when the task cannot be mapped in the current snapshot state
     */
    @Nullable
    private LineMappingResult mapAnchorForTask(ThreadMappingTask task, @Nullable EditList problemStatementEdits,
            Map<RepoDiffKey, Map<String, FileMappingPlan>> filePlansByRepoDiff) {
        int oldLine = task.thread().getLineNumber();
        if (oldLine <= 0) {
            return new LineMappingResult(null, true);
        }

        if (task.source() == MappingSource.PROBLEM_STATEMENT) {
            if (problemStatementEdits == null) {
                return null;
            }
            return mapLineWithEdits(oldLine, problemStatementEdits);
        }

        RepoDiffKey repoDiffKey = task.repoDiffKey();
        String filePath = task.filePath();
        if (repoDiffKey == null || filePath == null) {
            return null;
        }
        Map<String, FileMappingPlan> filePlans = filePlansByRepoDiff.get(repoDiffKey);
        if (filePlans == null) {
            return null;
        }
        FileMappingPlan filePlan = filePlans.getOrDefault(filePath, FileMappingPlan.unchanged());
        return mapLineWithPlan(oldLine, filePlan);
    }

    /**
     * Maps a consistency-inline-fix line range for a prepared mapping task.
     *
     * @param task                  mapping task for one thread
     * @param inlineFix             inline-fix payload to remap
     * @param problemStatementEdits precomputed text edits for problem statement mapping
     * @param filePlansByRepoDiff   precomputed per-file plans grouped by repository diff
     * @return mapped range result, or {@code null} when the task cannot be mapped in the current snapshot state
     */
    @Nullable
    private LineRangeMappingResult mapInlineFixRangeForTask(ThreadMappingTask task, InlineCodeChangeDTO inlineFix, @Nullable EditList problemStatementEdits,
            Map<RepoDiffKey, Map<String, FileMappingPlan>> filePlansByRepoDiff) {
        int oldStart = inlineFix.startLine();
        int oldEnd = inlineFix.endLine();
        if (oldStart <= 0 || oldEnd < oldStart) {
            return new LineRangeMappingResult(null, null, true);
        }

        if (task.source() == MappingSource.PROBLEM_STATEMENT) {
            if (problemStatementEdits == null) {
                return null;
            }
            return mapRangeWithEdits(oldStart, oldEnd, problemStatementEdits);
        }

        RepoDiffKey repoDiffKey = task.repoDiffKey();
        String filePath = task.filePath();
        if (repoDiffKey == null || filePath == null) {
            return null;
        }
        Map<String, FileMappingPlan> filePlans = filePlansByRepoDiff.get(repoDiffKey);
        if (filePlans == null) {
            return null;
        }
        FileMappingPlan filePlan = filePlans.getOrDefault(filePath, FileMappingPlan.unchanged());
        return mapRangeWithPlan(oldStart, oldEnd, filePlan);
    }

    /**
     * Applies an already computed anchor mapping result to a thread and reports whether it changed.
     *
     * @param thread       thread to update
     * @param mappedAnchor previously computed anchor mapping result
     * @return {@code true} when at least one persisted thread field changed
     */
    private boolean applyAnchorUpdate(CommentThread thread, LineMappingResult mappedAnchor) {
        boolean modified = false;
        if (mappedAnchor.outdated() && !thread.isOutdated()) {
            thread.setOutdated(true);
            modified = true;
        }
        if (mappedAnchor.newLine() != null && !Objects.equals(thread.getLineNumber(), mappedAnchor.newLine())) {
            thread.setLineNumber(mappedAnchor.newLine());
            modified = true;
        }
        return modified;
    }

    /**
     * Precomputes file-mapping plans for each repository diff key.
     * Failures for one repository group are logged and skipped without aborting the whole update.
     *
     * @param requiredFilePathsByRepoDiff required file paths grouped by repository diff inputs
     * @return prepared mapping plans per repository diff key
     */
    private Map<RepoDiffKey, Map<String, FileMappingPlan>> prepareRepoFilePlans(Map<RepoDiffKey, Set<String>> requiredFilePathsByRepoDiff) {
        Map<RepoDiffKey, Map<String, FileMappingPlan>> filePlansByRepoDiff = new HashMap<>();
        for (Map.Entry<RepoDiffKey, Set<String>> entry : requiredFilePathsByRepoDiff.entrySet()) {
            try {
                filePlansByRepoDiff.put(entry.getKey(), computeRepositoryFilePlans(entry.getKey(), entry.getValue()));
            }
            catch (Exception ex) {
                log.warn("Could not prepare repository line mapping for diff {}: {}", entry.getKey(), ex.getMessage());
                filePlansByRepoDiff.put(entry.getKey(), createUnmappableFilePlans(entry.getValue()));
            }
        }
        return filePlansByRepoDiff;
    }

    /**
     * Creates unmappable plans for all tracked file paths of one repository diff.
     *
     * @param requiredFilePaths repository-relative file paths that need mapping
     * @return mapping plans that conservatively mark every path as unmappable
     */
    private Map<String, FileMappingPlan> createUnmappableFilePlans(Set<String> requiredFilePaths) {
        Map<String, FileMappingPlan> filePlans = new HashMap<>();
        for (String requiredFilePath : requiredFilePaths) {
            filePlans.put(requiredFilePath, FileMappingPlan.unmappable());
        }
        return filePlans;
    }

    /**
     * Builds per-file mapping plans for one repository diff.
     * Plans are either unchanged, edit-based, or unmappable (e.g. deleted/renamed path mismatch).
     *
     * @param repoDiffKey       repository diff inputs
     * @param requiredFilePaths repository-relative file paths that need mapping
     * @return mapping plan by file path
     * @throws IOException if git tree/diff access fails
     */
    private Map<String, FileMappingPlan> computeRepositoryFilePlans(RepoDiffKey repoDiffKey, Set<String> requiredFilePaths) throws IOException {
        Map<String, FileMappingPlan> filePlans = new HashMap<>();
        for (String requiredFilePath : requiredFilePaths) {
            filePlans.put(requiredFilePath, FileMappingPlan.unchanged());
        }

        try (Repository repository = gitService.getBareRepository(repoDiffKey.repositoryUri(), false);
                ObjectReader reader = repository.newObjectReader();
                DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(false);

            ObjectId oldTree = repository.resolve(repoDiffKey.oldCommit() + "^{tree}");
            ObjectId newTree = repository.resolve(repoDiffKey.newCommit() + "^{tree}");
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
                boolean matchesOld = requiredFilePaths.contains(oldPath);
                boolean matchesNew = requiredFilePaths.contains(newPath);
                if (!matchesOld && !matchesNew) {
                    continue;
                }

                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE && matchesOld) {
                    markFileUnmappable(filePlans, oldPath);
                    continue;
                }
                if (entry.getChangeType() == DiffEntry.ChangeType.ADD && matchesNew && !matchesOld) {
                    markFileUnmappable(filePlans, newPath);
                    continue;
                }

                if (matchesOld && matchesNew && Objects.equals(oldPath, newPath)) {
                    setFileEdits(filePlans, oldPath, diffFormatter.toFileHeader(entry).toEditList());
                    continue;
                }

                if (matchesOld) {
                    markFileUnmappable(filePlans, oldPath);
                }
                if (matchesNew) {
                    markFileUnmappable(filePlans, newPath);
                }
            }
        }

        return filePlans;
    }

    /**
     * Marks one tracked file plan as unmappable.
     *
     * @param filePlans mapping plans by file path
     * @param filePath  file path to mark
     */
    private void markFileUnmappable(Map<String, FileMappingPlan> filePlans, String filePath) {
        if (filePath == null || !filePlans.containsKey(filePath)) {
            return;
        }
        filePlans.put(filePath, FileMappingPlan.unmappable());
    }

    /**
     * Stores edit-based mapping data for one tracked file path unless it is already unmappable.
     *
     * @param filePlans mapping plans by file path
     * @param filePath  file path to update
     * @param edits     diff edits for this file
     */
    private void setFileEdits(Map<String, FileMappingPlan> filePlans, String filePath, EditList edits) {
        if (filePath == null || !filePlans.containsKey(filePath)) {
            return;
        }
        if (filePlans.get(filePath).type() == FileMappingType.UNMAPPABLE) {
            return;
        }
        filePlans.put(filePath, FileMappingPlan.edits(edits));
    }

    /**
     * Maps a single line using a prepared file mapping plan.
     *
     * @param oldLine  line in old content (1-based)
     * @param filePlan plan describing how this file changed
     * @return mapped line result
     */
    private LineMappingResult mapLineWithPlan(int oldLine, FileMappingPlan filePlan) {
        if (oldLine <= 0) {
            return new LineMappingResult(null, true);
        }
        return switch (filePlan.type()) {
            case UNCHANGED -> new LineMappingResult(oldLine, false);
            case UNMAPPABLE -> new LineMappingResult(null, true);
            case EDITS -> mapLineWithEdits(oldLine, Objects.requireNonNull(filePlan.edits()));
        };
    }

    /**
     * Maps a line range using a prepared file mapping plan.
     *
     * @param oldStartLine first line in old content (1-based, inclusive)
     * @param oldEndLine   last line in old content (1-based, inclusive)
     * @param filePlan     plan describing how this file changed
     * @return mapped range result
     */
    private LineRangeMappingResult mapRangeWithPlan(int oldStartLine, int oldEndLine, FileMappingPlan filePlan) {
        if (oldStartLine <= 0 || oldEndLine < oldStartLine) {
            return new LineRangeMappingResult(null, null, true);
        }
        return switch (filePlan.type()) {
            case UNCHANGED -> new LineRangeMappingResult(oldStartLine, oldEndLine, false);
            case UNMAPPABLE -> new LineRangeMappingResult(null, null, true);
            case EDITS -> mapRangeWithEdits(oldStartLine, oldEndLine, Objects.requireNonNull(filePlan.edits()));
        };
    }

    /**
     * Computes text diff edits between two plain-text snapshots.
     *
     * @param oldText old text snapshot (nullable)
     * @param newText new text snapshot (nullable)
     * @return edit list between both snapshots
     */
    private EditList calculateTextEdits(@Nullable String oldText, @Nullable String newText) {
        String safeOld = oldText == null ? "" : oldText;
        String safeNew = newText == null ? "" : newText;
        RawText oldRaw = new RawText(safeOld.getBytes(StandardCharsets.UTF_8));
        RawText newRaw = new RawText(safeNew.getBytes(StandardCharsets.UTF_8));
        return DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS).diff(RawTextComparator.DEFAULT, oldRaw, newRaw);
    }

    /**
     * Maps one line through an edit list.
     *
     * @param oldLine line in old content (1-based)
     * @param edits   edit list between old and new content
     * @return mapped line result
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
     * Maps a line range through an edit list and determines whether the range became outdated.
     *
     * @param oldStartLine first line in old content (1-based, inclusive)
     * @param oldEndLine   last line in old content (1-based, inclusive)
     * @param edits        edit list between old and new content
     * @return mapped range result
     */
    private LineRangeMappingResult mapRangeWithEdits(int oldStartLine, int oldEndLine, EditList edits) {
        if (oldStartLine <= 0 || oldEndLine < oldStartLine) {
            return new LineRangeMappingResult(null, null, true);
        }

        LineMappingResult startResult = mapLineWithEdits(oldStartLine, edits);
        LineMappingResult endResult = mapLineWithEdits(oldEndLine, edits);
        boolean outdated = startResult.outdated() || endResult.outdated() || isRangeTouchedByEdits(oldStartLine, oldEndLine, edits);

        return new LineRangeMappingResult(startResult.newLine(), endResult.newLine(), outdated);
    }

    /**
     * Checks whether any edit touches the provided old-content range.
     *
     * @param oldStartLine first line in old content (1-based, inclusive)
     * @param oldEndLine   last line in old content (1-based, inclusive)
     * @param edits        edit list between old and new content
     * @return {@code true} when the range intersects with an edit
     */
    private boolean isRangeTouchedByEdits(int oldStartLine, int oldEndLine, EditList edits) {
        int start = oldStartLine - 1;
        int end = oldEndLine - 1;
        for (Edit edit : edits) {
            if (edit.getBeginA() == edit.getEndA()) {
                // Pure insertion: mark outdated only if insertion happens inside the selected range.
                int insertionPoint = edit.getBeginA();
                if (insertionPoint > start && insertionPoint <= end) {
                    return true;
                }
                continue;
            }

            int editStart = edit.getBeginA();
            int editEnd = edit.getEndA() - 1;
            if (editStart <= end && editEnd >= start) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves repository diff metadata for one thread target from both programming snapshots.
     *
     * @param previous previous programming snapshot
     * @param current  current programming snapshot
     * @param thread   thread whose target determines the repository mapping
     * @return repository diff metadata if available
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
     * Resolves participation repository diff metadata.
     *
     * @param previous previous participation snapshot
     * @param current  current participation snapshot
     * @return repository diff metadata if available
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
     * Resolves test repository diff metadata.
     *
     * @param previous previous programming snapshot
     * @param current  current programming snapshot
     * @return repository diff metadata if available
     */
    private Optional<RepoDiffInfo> mapTestRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current) {
        if (previous.testsCommitId() == null || current.testsCommitId() == null || current.testRepositoryUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoDiffInfo(new LocalVCRepositoryUri(current.testRepositoryUri()), previous.testsCommitId(), current.testsCommitId()));
    }

    /**
     * Resolves auxiliary repository diff metadata for a specific auxiliary repository id.
     *
     * @param previous              previous programming snapshot
     * @param current               current programming snapshot
     * @param auxiliaryRepositoryId auxiliary repository id
     * @return repository diff metadata if available
     */
    private Optional<RepoDiffInfo> mapAuxRepo(ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current, @Nullable Long auxiliaryRepositoryId) {
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
     * Result of a line mapping operation.
     *
     * @param newLine  the mapped 1-based line number, or {@code null} if the line can no longer be mapped
     * @param outdated whether the original line was modified by the change
     */
    public record LineMappingResult(@Nullable Integer newLine, boolean outdated) {
    }

    private record LineRangeMappingResult(@Nullable Integer newStartLine, @Nullable Integer newEndLine, boolean outdated) {
    }

    private enum MappingSource {
        PROBLEM_STATEMENT, REPOSITORY
    }

    private record ThreadMappingTask(CommentThread thread, MappingSource source, @Nullable RepoDiffKey repoDiffKey, @Nullable String filePath) {

        /**
         * Creates a mapping task for a problem-statement thread.
         *
         * @param thread thread to map
         * @return initialized mapping task
         */
        private static ThreadMappingTask forProblemStatement(CommentThread thread) {
            return new ThreadMappingTask(thread, MappingSource.PROBLEM_STATEMENT, null, null);
        }

        /**
         * Creates a mapping task for a repository-backed thread.
         *
         * @param thread      thread to map
         * @param repoDiffKey repository diff key for grouped processing
         * @param filePath    repository-relative file path
         * @return initialized mapping task
         */
        private static ThreadMappingTask forRepository(CommentThread thread, RepoDiffKey repoDiffKey, String filePath) {
            return new ThreadMappingTask(thread, MappingSource.REPOSITORY, repoDiffKey, filePath);
        }
    }

    private record RepoDiffInfo(LocalVCRepositoryUri repositoryUri, String oldCommit, String newCommit) {
    }

    private record RepoDiffKey(LocalVCRepositoryUri repositoryUri, String oldCommit, String newCommit) {
    }

    private enum FileMappingType {
        UNCHANGED, EDITS, UNMAPPABLE
    }

    private record FileMappingPlan(FileMappingType type, @Nullable EditList edits) {

        /**
         * Creates a plan for unchanged files.
         *
         * @return unchanged mapping plan
         */
        private static FileMappingPlan unchanged() {
            return new FileMappingPlan(FileMappingType.UNCHANGED, null);
        }

        /**
         * Creates a plan for files that cannot be line-mapped.
         *
         * @return unmappable mapping plan
         */
        private static FileMappingPlan unmappable() {
            return new FileMappingPlan(FileMappingType.UNMAPPABLE, null);
        }

        /**
         * Creates a plan backed by concrete diff edits.
         *
         * @param edits file edits
         * @return edit-based mapping plan
         */
        private static FileMappingPlan edits(EditList edits) {
            return new FileMappingPlan(FileMappingType.EDITS, edits);
        }
    }
}
