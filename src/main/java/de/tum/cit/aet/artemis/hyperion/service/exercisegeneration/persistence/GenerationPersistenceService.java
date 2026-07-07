package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.BuildGateTestNames;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.BinaryContent;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Persists a verified-complete generated exercise through Artemis's normal pipeline (commit repositories, trigger the canonical tests build for test-case sync, update the problem
 * statement, record an exercise version), the same path a manual instructor edit uses. Runs only after the differential oracle has accepted the exercise.
 * <p>
 * The three repositories (template, solution, tests) cannot commit inside a single database/git transaction, so a broad {@code @Transactional} would not make the multi-repository
 * write atomic anyway. Instead the persist captures each repository's pre-persist commit before writing it and, if a later repository fails, compensates by force-resetting the
 * already-committed repositories back to their captured commit, then raises {@link GenerationIncompleteException}. A crash between SOLUTION and TESTS therefore never leaves a
 * publishable, half-generated exercise on the live default branch: the exercise version (the publishable snapshot) is only recorded after all repositories committed successfully.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(GenerationPersistenceService.class);

    private final String defaultBranch;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseParticipationService participationService;

    private final ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingExerciseCreationUpdateService creationUpdateService;

    private final ExerciseVersionService exerciseVersionService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final Duration testCaseSyncTimeout;

    private final Duration testCaseSyncPoll;

    @Autowired
    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ProgrammingExerciseCreationUpdateService creationUpdateService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository, ResultRepository resultRepository,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService) {
        this(defaultBranch, gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService, creationUpdateService,
                exerciseVersionService, testCaseRepository, resultRepository, programmingExerciseRepositoryService, TEST_CASE_SYNC_TIMEOUT, TEST_CASE_SYNC_POLL);
    }

    // Package-private so tests can inject a shrunken sync wait and exercise the build-completion wait without sleeping for seconds.
    GenerationPersistenceService(String defaultBranch, GitService gitService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ContinuousIntegrationTriggerService continuousIntegrationTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingExerciseCreationUpdateService creationUpdateService, ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ResultRepository resultRepository, ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, Duration testCaseSyncTimeout, Duration testCaseSyncPoll) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.participationService = participationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.creationUpdateService = creationUpdateService;
        this.exerciseVersionService = exerciseVersionService;
        this.testCaseRepository = testCaseRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.testCaseSyncTimeout = testCaseSyncTimeout;
        this.testCaseSyncPoll = testCaseSyncPoll;
    }

    /** The repositories persisted, in the order they are committed. Tests are committed last so the test-triggered build sees the final solution. */
    private static final RepositoryType[] PERSIST_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    /** Prefix of the isolated branch a recovery draft is diverted to for an adapt target; the job id is appended so concurrent/repeated runs never collide on the ref. */
    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

    /** Exercise title column length; an H1 reconciled from a generated statement is capped to this. */
    private static final int MAX_TITLE_LENGTH = 255;

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration TEST_CASE_SYNC_POLL = Duration.ofSeconds(3);

    /** Unicode dashes U+2010..U+2015 the model leaks into prose/source; precompiled since {@link #normalizeTypography} runs once per produced file per persist. */
    private static final Pattern UNICODE_DASHES = Pattern.compile("[‐-―]");

    /**
     * The result of persisting a non-accepted recovery draft.
     *
     * @param liveExerciseUntouched {@code true} if the live default branch was left byte-identical (adapt target); {@code false} if committed to it (from-scratch target)
     * @param draftBranch           the isolated branch the draft was pushed to when {@code liveExerciseUntouched}; {@code null} otherwise
     */
    public record RecoveryPersistResult(boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Persists a non-accepted (recovered) draft WITHOUT regressing a working exercise. A from-scratch target (all repositories empty) is committed to the default branch via
     * {@link #persist}; an adapt target (any repository carries committed content) is left byte-identical and the draft diverted to an isolated branch with no CI build. The
     * adapt-vs-from-scratch decision is taken once up front so a later commit failure can never leave the exercise half-overwritten.
     *
     * @param exercise the exercise to persist the draft into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the non-accepted generation outcome holding the produced files
     * @param jobId    the generation job id, used to name the isolated draft branch
     * @return whether the live exercise was left untouched and, if so, the isolated branch the draft was pushed to
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        if (!anyRepositoryHasContent(exercise)) {
            persist(exercise, user, outcome);
            return new RecoveryPersistResult(false, null);
        }
        String draftBranch = RECOVERY_DRAFT_BRANCH_PREFIX + jobId;
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS), draftBranch);
        // No tests build and no exercise version: the live exercise did not change, so it must not be re-graded or re-versioned against the broken draft.
        log.info("Recovered adapt draft for exercise {} onto isolated branch {} (live exercise left untouched)", exercise.getId(), draftBranch);
        return new RecoveryPersistResult(true, draftBranch);
    }

    /**
     * @return {@code true} if any of the template/solution/tests repositories already tracks a non-{@code .git} file (an adapt target); {@code false} if all are empty.
     *         Fails closed to {@code true} when a repository cannot be inspected, so an inspection error never lets a failing draft overwrite the live exercise.
     */
    private boolean anyRepositoryHasContent(ProgrammingExercise exercise) {
        for (RepositoryType repositoryType : PERSIST_ORDER) {
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (uri == null) {
                continue;
            }
            try {
                Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
                if (repository == null) {
                    return true;
                }
                Map<String, FileType> trackedFiles = repositoryService.getFiles(repository);
                if (trackedFiles.values().stream().anyMatch(type -> type == FileType.FILE)) {
                    return true;
                }
            }
            catch (Exception e) {
                log.warn("Could not inspect the {} repository of exercise {} for adapt detection; treating it as an adapt target (safe default): {}", repositoryType,
                        exercise.getId(), e.getMessage());
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the produced files and commits them to an isolated branch (never the default branch), pushing only that branch. Uses the same orphan-mirroring as the default-branch
     * commit. A commit/push failure is propagated so recovery reports a real failure rather than a half-saved draft.
     *
     * @param exercise       the exercise being recovered
     * @param user           the commit author
     * @param repositoryType the repository to write
     * @param producedFiles  the files to commit (the sandbox-final tree)
     * @param draftBranch    the isolated branch to push the draft to
     */
    private void commitDraftToIsolatedBranch(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles, String draftBranch) {
        if (producedFiles == null || producedFiles.isEmpty()) {
            return;
        }
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            return;
        }
        try {
            Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            gitService.stageAllChanges(repository);
            gitService.commitToIsolatedBranchAndPush(repository, draftBranch, "Hyperion generation draft (needs review; NOT applied to the live exercise)", user);
            // Reset the working copy back to the default branch so a later default-branch operation does not see the diverted commit.
            gitService.resetToOriginHead(repository);
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to commit the " + repositoryType + " recovery draft to the isolated branch for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Persists a verified generated exercise.
     * <p>
     * The three repositories are committed in {@link #PERSIST_ORDER} (tests last, so the test-triggered build sees the final solution). Because they cannot commit atomically, each
     * repository's pre-persist commit is captured before it is written; if a later repository fails, the already-committed repositories are force-reset back to their captured
     * commit ({@link #compensate}) and {@link GenerationIncompleteException} is raised, so a mid-sequence failure never leaves a publishable half-generated exercise. The exercise
     * version — the publishable snapshot open editors and search see — is recorded only after every repository has committed.
     *
     * @param exercise the exercise to persist into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the accepted generation outcome holding the produced files
     * @return the pre-persist commit HEAD captured per repository BEFORE it was written (a revertible baseline for an accepted adapt applied in place); repositories with nothing
     *         to
     *         commit are absent. Returned only after every repository committed successfully, so the caller records a revertible baseline exclusively for a persist that actually
     *         applied changes.
     * @throws GenerationIncompleteException if a repository commit fails part-way through the sequence (the already-committed repositories are compensated first)
     */
    public Map<RepositoryType, String> persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome) {
        // Capture each repository's pre-persist HEAD before writing it, so a later failure can revert the already-committed repositories to a consistent pre-generation state.
        Map<RepositoryType, String> prePersistHashes = new EnumMap<>(RepositoryType.class);
        List<RepositoryType> committed = new ArrayList<>();
        String testsCommitHash = null;
        try {
            for (RepositoryType repositoryType : PERSIST_ORDER) {
                String commitHash = commitRepository(exercise, user, repositoryType, outcome.producedFiles(repositoryType), prePersistHashes);
                if (commitHash != null) {
                    committed.add(repositoryType);
                    if (repositoryType == RepositoryType.TESTS) {
                        testsCommitHash = commitHash;
                    }
                }
            }
        }
        catch (RuntimeException e) {
            // Compensation: revert the already-committed repositories to their captured pre-persist commit so no publishable half-generated tree survives on the default branch.
            boolean fullyReverted = compensate(exercise, committed, prePersistHashes);
            String state = fullyReverted ? "the already-committed repositories were reverted to their previous state"
                    : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
        }

        String producedProblemStatement = normalizeTypography(outcome.producedProblemStatement());
        if (!producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement())) {
            // From-scratch only (statement was blank): reconcile the lean AI create page's brief-derived placeholder title to the agent's own H1. updateProblemStatement saves the
            // whole entity, so the title persists in the same write. An adapt run keeps the instructor's title.
            if (exercise.getProblemStatement() == null || exercise.getProblemStatement().isBlank()) {
                String generatedTitle = extractTitleFromH1(producedProblemStatement);
                if (generatedTitle != null && !generatedTitle.equals(exercise.getTitle())) {
                    exercise.setTitle(generatedTitle);
                }
            }
            try {
                creationUpdateService.updateProblemStatement(exercise, producedProblemStatement, null);
            }
            catch (RuntimeException e) {
                log.warn("Failed to update problem statement for exercise {}: {}", exercise.getId(), e.getMessage());
            }
        }

        // Trigger the canonical CI build for the tests (drives test-case sync + task binding asynchronously) and record the exercise version — only reached once every repository
        // committed.
        syncTestCasesAndRecordVersion(exercise, user, testsCommitHash);
        log.info("Persisted generated exercise {} (test-case synchronisation will complete asynchronously via CI)", exercise.getId());
        return prePersistHashes;
    }

    /**
     * Re-synchronises the exercise after its repositories were force-reset back to a captured commit (the {@code revert this adaptation} affordance). The git reset itself is done
     * by the caller; this triggers the canonical tests build so test-case grading follows the reverted tests, re-applies the build-gate zero-weighting, and records a new exercise
     * version so open editors and search see the reverted state — exactly the post-commit steps {@link #persist} runs. Best-effort: a failure here leaves the repositories reverted
     * (the important part) and only logs.
     *
     * @param exercise        the exercise whose repositories were reset back to the baseline
     * @param user            the instructor performing the revert (exercise-version author)
     * @param testsCommitHash the tests repository's commit HEAD after the reset (drives the test-case-sync build); {@code null} skips the build
     */
    public void resyncAfterRevert(ProgrammingExercise exercise, User user, String testsCommitHash) {
        syncTestCasesAndRecordVersion(exercise, user, testsCommitHash);
        log.info("Re-synchronised exercise {} after reverting an adaptation (test-case synchronisation completes asynchronously via CI)", exercise.getId());
    }

    /**
     * Triggers the canonical tests build (when a tests commit exists) so test-case grading follows the committed tests, re-applies the build-gate zero-weighting, and records a new
     * exercise version so open editors and search see the committed state — the post-commit steps shared by {@link #persist} and {@link #resyncAfterRevert}. Version recording is
     * best-effort: a failure only logs, leaving the committed repositories (the durable part) intact.
     *
     * @param exercise        the exercise whose test cases to sync and version to record
     * @param user            the exercise-version author
     * @param testsCommitHash the tests repository's commit HEAD driving the test-case-sync build; {@code null} skips the build
     */
    private void syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, String testsCommitHash) {
        if (testsCommitHash != null) {
            TestsBuildSignal signal = triggerTestsBuild(exercise, testsCommitHash);
            zeroWeightBuildGateTestCases(exercise.getId(), signal);
        }
        try {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }
        catch (RuntimeException e) {
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
    }

    /**
     * Compensating action for a failed multi-repository persist: force-resets each already-committed repository back to the pre-persist commit captured in
     * {@code prePersistHashes},
     * in reverse commit order. Best-effort per repository: a single revert failure is logged and does not stop the others, but makes the overall result "not fully reverted" so the
     * caller can flag the exercise for manual review. A repository with no captured pre-persist hash (e.g. a brand-new empty repository with no prior commit) cannot be reverted
     * and
     * counts as not-fully-reverted.
     *
     * @param exercise         the exercise whose repositories are compensated
     * @param committed        the repositories that were successfully committed and must be reverted
     * @param prePersistHashes the pre-persist commit hash captured per repository before it was written
     * @return {@code true} if every committed repository was reverted; {@code false} if any could not be
     */
    private boolean compensate(ProgrammingExercise exercise, List<RepositoryType> committed, Map<RepositoryType, String> prePersistHashes) {
        boolean fullyReverted = true;
        for (int i = committed.size() - 1; i >= 0; i--) {
            RepositoryType repositoryType = committed.get(i);
            String preHash = prePersistHashes.get(repositoryType);
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (preHash == null || uri == null) {
                fullyReverted = false;
                log.error("Cannot revert the {} repository of exercise {} during persist compensation: no pre-persist commit was captured", repositoryType, exercise.getId());
                continue;
            }
            try {
                Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
                if (repository == null) {
                    throw new IllegalStateException("Could not check out the repository to revert it");
                }
                gitService.resetToCommitAndForcePush(repository, preHash, defaultBranch);
                log.info("Reverted the {} repository of exercise {} back to its pre-generation commit {} during persist compensation", repositoryType, exercise.getId(), preHash);
            }
            catch (Exception e) {
                fullyReverted = false;
                log.error("Failed to revert the {} repository of exercise {} back to {} during persist compensation; the exercise may be inconsistent", repositoryType,
                        exercise.getId(), preHash, e);
            }
        }
        return fullyReverted;
    }

    /**
     * Writes the produced files and commits, making the committed tree mirror the sandbox-final {@code producedFiles} (see {@link #deleteOrphanedFiles} for why). The
     * repository's pre-persist HEAD is captured into {@code prePersistHashes} BEFORE the commit, so the caller can revert this repository if a later one fails. A commit failure is
     * propagated so the caller does not report success after only some repositories were written.
     *
     * @param exercise         the exercise being persisted
     * @param user             the commit author
     * @param repositoryType   the repository to write
     * @param producedFiles    the files to commit (the sandbox-final tree the oracle validated)
     * @param prePersistHashes accumulator the captured pre-persist commit hash is written into
     * @return the new commit hash, or {@code null} when there was nothing to commit
     */
    private String commitRepository(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles,
            Map<RepositoryType, String> prePersistHashes) {
        if (producedFiles == null || producedFiles.isEmpty()) {
            return null;
        }
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            return null;
        }
        try {
            // Capture the pre-persist HEAD before mutating the repository, so this commit can be reverted if a subsequent repository fails (may be null for a repo with no commit).
            prePersistHashes.put(repositoryType, gitService.getLastCommitHash(uri));
            Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            repositoryService.commitChanges(repository, user);
            return gitService.getLastCommitHash(uri);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Makes the working copy mirror the sandbox-final {@code producedFiles}: removes the files the agent did not produce (see {@link #deleteOrphanedFiles}) then writes
     * every
     * produced file. Shared by the default-branch and isolated draft-branch commits so both produce an identical tree; the caller decides how to commit it.
     *
     * @param exercise       the exercise being persisted (drives the placeholder normalization)
     * @param repository     the checked-out repository working copy
     * @param repositoryType the repository type
     * @param producedFiles  the files to write (the sandbox-final tree)
     * @throws IOException if writing a file into the working copy fails
     */
    private void mirrorProducedFilesIntoWorkingCopy(ProgrammingExercise exercise, Repository repository, RepositoryType repositoryType, Map<String, String> producedFiles)
            throws IOException {
        deleteOrphanedFiles(repository, repositoryType, producedFiles.keySet());
        for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
            String path = entry.getKey();
            if (gitService.getFileByName(repository, path).isPresent()) {
                repositoryService.deleteFile(repository, path);
            }
            // Scrub model typography from source files too (producedFiles is always text, never binary).
            String content = normalizeTypography(entry.getValue());
            repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }
        // The produced tree can re-introduce raw ${...} placeholders (e.g. from the reference's run.sh); normalize to real-CI values as exercise creation does (idempotent if
        // clean).
        programmingExerciseRepositoryService.replacePlaceholders(exercise, repository);
    }

    /**
     * Deletes every tracked file the agent did not produce, so the committed tree mirrors the sandbox-final state rather than overlaying onto the scaffolded sample (which would
     * orphan the sample's test sources / structure oracle into real grading). Harness/manifest files (graded verbatim, immutable by contract) are never deleted, so a partial
     * read-back cannot wipe the harness. A single-file delete failure is logged and skipped — a leftover file is a quality issue, not a reason to abort an otherwise-valid persist.
     *
     * @param repository     the checked-out repository working copy
     * @param repositoryType the repository type (for logging)
     * @param producedPaths  the repository-relative paths the agent produced (the files that must survive)
     */
    private void deleteOrphanedFiles(Repository repository, RepositoryType repositoryType, Set<String> producedPaths) {
        Map<String, FileType> trackedFiles = repositoryService.getFiles(repository);
        Path repositoryRoot = repository.getLocalPath();
        for (Map.Entry<String, FileType> tracked : trackedFiles.entrySet()) {
            String path = tracked.getKey();
            if (tracked.getValue() != FileType.FILE || producedPaths.contains(path) || ExerciseIntegrityGate.isHarnessFile(path)) {
                continue;
            }
            // Never delete a scaffolded binary (e.g. gradle-wrapper.jar): it cannot survive the UTF-8 String round-trip so it is absent from producedFiles and would look orphaned.
            // The agent never edits it, so the byte-exact scaffolded original is correct — leave it to commit intact.
            if (repositoryRoot != null && BinaryContent.isBinaryFile(repositoryRoot.resolve(path))) {
                log.debug("Preserved scaffolded binary {} file {}", repositoryType, path);
                continue;
            }
            try {
                repositoryService.deleteFile(repository, path);
                log.debug("Removed orphaned {} file {} not produced by generation", repositoryType, path);
            }
            catch (IOException | RuntimeException e) {
                log.warn("Could not remove orphaned {} file {} during persist: {}", repositoryType, path, e.getMessage());
            }
        }
    }

    /**
     * The pre-trigger baseline needed to detect that the {@link #triggerTestsBuild triggered} tests-build has finished processing: the solution participation and the id of its
     * latest result before the build ran. {@code baselineLatestResultId} is {@code null} when no earlier result existed; {@code null} signal means the trigger itself failed.
     */
    private record TestsBuildSignal(long solutionParticipationId, Long baselineLatestResultId) {
    }

    private TestsBuildSignal triggerTestsBuild(ProgrammingExercise exercise, String commitHash) {
        try {
            ProgrammingExerciseParticipation solutionParticipation = participationService.retrieveSolutionParticipation(exercise);
            long solutionParticipationId = solutionParticipation.getId();
            // Capture the latest result BEFORE triggering so the wait keys on a strictly newer result than any pre-existing (e.g. exercise-setup) build, not on the case count.
            Long baselineLatestResultId = resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(solutionParticipationId).map(Result::getId).orElse(null);
            programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
            continuousIntegrationTriggerService.triggerBuild(solutionParticipation, commitHash, RepositoryType.TESTS);
            return new TestsBuildSignal(solutionParticipationId, baselineLatestResultId);
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to trigger the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
            return null;
        }
        catch (RuntimeException e) {
            log.warn("Unexpected error triggering the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * For C/C++ FACT exercises the synced report includes build-gate cases (CompileSort/TestConfigure) that PASS on the compiling template; the differential oracle exempts them
     * ({@link BuildGateTestNames}) but production grades every case, so without this a student submitting the untouched template would score above 0%. Waits (bounded) for the
     * freshly triggered tests-build to finish re-syncing, then zero-weights the build gates to match the oracle. Best-effort, idempotent, a no-op for languages without build-gate
     * cases.
     *
     * @param exerciseId the generated exercise whose build-gate test cases should be excluded from grading
     * @param signal     the pre-trigger baseline identifying the triggered build to wait for; {@code null} when the trigger failed (acts on the current set)
     */
    private void zeroWeightBuildGateTestCases(long exerciseId, TestsBuildSignal signal) {
        try {
            Set<ProgrammingExerciseTestCase> testCases = signal == null ? testCaseRepository.findByExerciseId(exerciseId) : awaitBuildProcessedTestCaseSet(exerciseId, signal);
            List<ProgrammingExerciseTestCase> buildGates = testCases.stream()
                    .filter(testCase -> BuildGateTestNames.isBuildGate(testCase.getTestName()) && testCase.getWeight() != null && testCase.getWeight() != 0.0).toList();
            if (buildGates.isEmpty()) {
                return;
            }
            buildGates.forEach(testCase -> testCase.setWeight(0.0));
            testCaseRepository.saveAll(buildGates);
            log.info("Zero-weighted {} build-gate test case(s) for generated exercise {} so the template grades at 0% (parity with the differential oracle): {}", buildGates.size(),
                    exerciseId, buildGates.stream().map(ProgrammingExerciseTestCase::getTestName).toList());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (RuntimeException e) {
            log.warn("Could not adjust build-gate test-case grading for generated exercise {} (a C/C++ template may grade >0% until reconfigured): {}", exerciseId, e.getMessage());
        }
    }

    /**
     * Waits (bounded by {@link #testCaseSyncTimeout}) for the tests-build triggered by {@link #triggerTestsBuild} to finish processing, then returns the re-synced test-case set.
     * The
     * wait keys on a strictly newer solution result than the pre-trigger baseline rather than on the test-case count: the grading pipeline saves the freshly re-synced cases
     * ({@code saveAll}) strictly before it saves the build's result, so a newer result guarantees the complete set is already committed. This is authoritative for both a fresh
     * generation (where the count grows as build-gate cases appear) and an adapt/revert that lands on the same count — a count-settle heuristic cannot tell the latter apart from
     * "not synced yet" and would either race on the stale pre-build set or spin the full timeout. A failed build still saves a result, so the wait also ends promptly on failure.
     *
     * @param exerciseId the exercise whose test-case set to await
     * @param signal     the pre-trigger baseline (solution participation and its latest result id) identifying the build to wait for
     * @return the test-case set once the triggered build's result is visible, or the last set read when the timeout was reached first
     */
    private Set<ProgrammingExerciseTestCase> awaitBuildProcessedTestCaseSet(long exerciseId, TestsBuildSignal signal) throws InterruptedException {
        long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            Long latestResultId = resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(signal.solutionParticipationId()).map(Result::getId).orElse(null);
            if (isNewerResult(latestResultId, signal.baselineLatestResultId())) {
                return testCaseRepository.findByExerciseId(exerciseId);
            }
            Thread.sleep(testCaseSyncPoll.toMillis());
        }
        log.warn("Timed out waiting for the tests-build result of generated exercise {}; a build-gate case may keep its weight until reconfigured", exerciseId);
        return testCaseRepository.findByExerciseId(exerciseId);
    }

    /**
     * True once the solution participation has a result newer than the pre-trigger baseline (any result when there was none before). Result ids are monotonic, so id order
     * suffices.
     */
    private static boolean isNewerResult(Long latestResultId, Long baselineLatestResultId) {
        return latestResultId != null && (baselineLatestResultId == null || latestResultId > baselineLatestResultId);
    }

    /**
     * Replaces typographic punctuation the model leaks (Unicode dashes {@code U+2010..U+2015}, non-breaking/narrow spaces, smart quotes, ellipsis) with ASCII equivalents. Applied
     * to
     * the problem statement and every generated source file. The substitution is safe: code spans are ASCII and no generation-capable language needs these characters in a literal.
     *
     * @param text the produced problem statement or source-file content (never {@code null})
     * @return the text normalised to ASCII
     */
    static String normalizeTypography(String text) {
        return UNICODE_DASHES.matcher(text).replaceAll("-").replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201C', '"')
                .replace('\u201D', '"').replace("\u2026", "...");
    }

    /**
     * Extracts the title from the first level-1 ATX heading ({@code # Title}); a {@code ## } heading does not match. Result is trimmed and capped at {@link #MAX_TITLE_LENGTH}.
     *
     * @param problemStatement the produced problem statement (must not be {@code null})
     * @return the H1 title, or {@code null} when the statement has no level-1 heading
     */
    static String extractTitleFromH1(String problemStatement) {
        for (String line : problemStatement.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).strip();
                if (title.isEmpty()) {
                    return null;
                }
                return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH).strip() : title;
            }
        }
        return null;
    }
}
