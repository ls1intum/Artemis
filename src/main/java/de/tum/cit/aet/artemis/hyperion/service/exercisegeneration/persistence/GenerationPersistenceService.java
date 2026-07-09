package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
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

    private final ExerciseVersionService exerciseVersionService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final Duration testCaseSyncTimeout;

    private final Duration testCaseSyncPoll;

    @Autowired
    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ResultRepository resultRepository, ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this(defaultBranch, gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService,
                testCaseRepository, resultRepository, programmingExerciseRepositoryService, programmingExerciseRepository, programmingExerciseTaskService, TEST_CASE_SYNC_TIMEOUT,
                TEST_CASE_SYNC_POLL);
    }

    // Package-private so tests can inject a shrunken sync wait and exercise the build-completion wait without sleeping for seconds.
    GenerationPersistenceService(String defaultBranch, GitService gitService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ContinuousIntegrationTriggerService continuousIntegrationTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository, ResultRepository resultRepository,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, Duration testCaseSyncTimeout, Duration testCaseSyncPoll) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.participationService = participationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseVersionService = exerciseVersionService;
        this.testCaseRepository = testCaseRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.testCaseSyncTimeout = testCaseSyncTimeout;
        this.testCaseSyncPoll = testCaseSyncPoll;
    }

    /** The repositories persisted, in the order they are committed. Tests are committed last so the test-triggered build sees the final solution. */
    private static final RepositoryType[] PERSIST_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    /** Prefix of the isolated branch a recovery draft is diverted to; the job id is appended so concurrent/repeated runs never collide on the ref. */
    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

    /** Exercise title column length; an H1 reconciled from a generated statement is capped to this. */
    private static final int MAX_TITLE_LENGTH = 255;

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration TEST_CASE_SYNC_POLL = Duration.ofSeconds(3);

    /**
     * The result of persisting a non-accepted recovery draft.
     *
     * @param liveExerciseUntouched {@code true} if the live default branch was left byte-identical
     * @param draftBranch           the isolated branch the draft was pushed to
     */
    public record RecoveryPersistResult(boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Commit heads captured while persisting an accepted generation.
     *
     * @param prePersistHeads  the default-branch heads before Hyperion committed each changed repository
     * @param postPersistHeads the heads immediately after Hyperion committed each changed repository
     */
    public record PersistResult(Map<RepositoryType, String> prePersistHeads, Map<RepositoryType, String> postPersistHeads) {
    }

    /**
     * Persists a non-accepted (recovered) draft WITHOUT touching the live default branch. Rejected output is always diverted to an isolated branch with no CI build and no exercise
     * version; only the accepted path may update the canonical exercise.
     *
     * @param exercise the exercise to persist the draft into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the non-accepted generation outcome holding the produced files
     * @param jobId    the generation job id, used to name the isolated draft branch
     * @return whether the live exercise was left untouched and, if so, the isolated branch the draft was pushed to
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        String draftBranch = RECOVERY_DRAFT_BRANCH_PREFIX + jobId;
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS), draftBranch);
        log.info("Recovered non-accepted draft for exercise {} onto isolated branch {} (live exercise left untouched)", exercise.getId(), draftBranch);
        return new RecoveryPersistResult(true, draftBranch);
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
        Repository repository = null;
        try {
            repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            gitService.stageAllChanges(repository);
            gitService.commitToIsolatedBranchAndPush(repository, draftBranch, "Hyperion generation draft (needs review; NOT applied to the live exercise)", user);
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to commit the " + repositoryType + " recovery draft to the isolated branch for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
        finally {
            if (repository != null) {
                // Always reset the working copy back to the default branch so a later default-branch operation cannot see the diverted draft commit, even if the isolated push
                // failed after creating the local commit.
                gitService.resetToOriginHead(repository);
            }
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
     * @return the pre- and post-persist commit HEADs captured for each changed repository. Returned only after every repository committed successfully, so the caller records a
     *         revertible baseline exclusively for a persist that actually applied changes.
     * @throws GenerationIncompleteException if a repository commit fails part-way through the sequence (the already-committed repositories are compensated first)
     */
    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome) {
        // Capture each repository's pre-persist HEAD before writing it, so a later failure can revert the already-committed repositories to a consistent pre-generation state.
        Map<RepositoryType, String> prePersistHashes = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, String> postPersistHashes = new EnumMap<>(RepositoryType.class);
        List<RepositoryType> committed = new ArrayList<>();
        String testsCommitHash = null;
        try {
            for (RepositoryType repositoryType : PERSIST_ORDER) {
                String commitHash = commitRepository(exercise, user, repositoryType, outcome.producedFiles(repositoryType), outcome.seedRepositoryHeads().get(repositoryType),
                        prePersistHashes, postPersistHashes);
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
            boolean fullyReverted = compensate(exercise, committed, prePersistHashes, postPersistHashes);
            String state = fullyReverted ? "the already-committed repositories were reverted to their previous state"
                    : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
        }

        String producedProblemStatement = outcome.producedProblemStatement();
        String originalProblemStatement = exercise.getProblemStatement();
        String originalTitle = exercise.getTitle();
        boolean problemStatementChanged = false;
        String persistedProblemStatement = null;
        String persistedTitle = null;
        if (!producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement())) {
            String targetTitle = exercise.getTitle();
            // From-scratch only (statement was blank): reconcile the lean AI create page's brief-derived placeholder title to the agent's own H1. An adapt run keeps the
            // instructor's title.
            if (exercise.getProblemStatement() == null || exercise.getProblemStatement().isBlank()) {
                String generatedTitle = extractTitleFromH1(producedProblemStatement);
                if (generatedTitle != null && !generatedTitle.equals(exercise.getTitle())) {
                    exercise.setTitle(generatedTitle);
                    targetTitle = generatedTitle;
                }
            }
            try {
                saveProblemStatementIfUnchanged(exercise, producedProblemStatement, targetTitle, originalProblemStatement, originalTitle);
                problemStatementChanged = true;
                persistedProblemStatement = producedProblemStatement.trim();
                persistedTitle = targetTitle;
                programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
            }
            catch (RuntimeException e) {
                boolean fullyReverted = compensate(exercise, committed, prePersistHashes, postPersistHashes);
                if (problemStatementChanged) {
                    restoreProblemStatementIfUnchanged(exercise, originalProblemStatement, originalTitle, persistedProblemStatement, persistedTitle);
                }
                String state = fullyReverted ? "the already-committed repositories were reverted to their previous state"
                        : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
                throw new GenerationIncompleteException("Saving the generated exercise failed while updating the problem statement after committing " + committed
                        + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
            }
        }

        // Trigger the canonical CI build for the tests (drives test-case sync + task binding asynchronously) and record the exercise version — only reached once every repository
        // committed.
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, true);
        }
        catch (RuntimeException e) {
            boolean fullyReverted = compensate(exercise, committed, prePersistHashes, postPersistHashes);
            if (problemStatementChanged) {
                restoreProblemStatementIfUnchanged(exercise, originalProblemStatement, originalTitle, persistedProblemStatement, persistedTitle);
            }
            if (fullyReverted) {
                resyncBaselineTestsAfterCompensation(exercise, user, prePersistHashes.get(RepositoryType.TESTS));
            }
            String state = fullyReverted ? "the already-committed repositories were reverted to their previous state"
                    : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed while recording the exercise version after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
        }
        log.info("Persisted generated exercise {} (test-case synchronisation will complete asynchronously via CI)", exercise.getId());
        return new PersistResult(nonNullCopy(prePersistHashes), nonNullCopy(postPersistHashes));
    }

    private static Map<RepositoryType, String> nonNullCopy(Map<RepositoryType, String> source) {
        Map<RepositoryType, String> copy = new EnumMap<>(RepositoryType.class);
        for (Map.Entry<RepositoryType, String> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }

    /**
     * Re-synchronises the exercise after its repositories were force-reset back to a captured commit (the {@code revert this adaptation} affordance). The git reset itself is done
     * by the caller; this triggers the canonical tests build so test-case grading follows the reverted tests, re-applies the build-gate zero-weighting, and records a new exercise
     * version so open editors and search see the reverted state — exactly the post-commit steps {@link #persist} runs. Best-effort: a failure here leaves the repositories reverted
     * (the important part) and only logs.
     *
     * @param exercise                 the exercise whose repositories were reset back to the baseline
     * @param user                     the instructor performing the revert (exercise-version author)
     * @param testsCommitHash          the tests repository's commit HEAD after the reset (drives the test-case-sync build); {@code null} skips the build
     * @param problemStatement         the problem statement captured before the adaptation
     * @param title                    the title captured before the adaptation
     * @param expectedProblemStatement the problem statement captured immediately after the adaptation; used as a compare-and-set guard
     * @param expectedTitle            the title captured immediately after the adaptation; used as a compare-and-set guard
     * @return {@code true} if the metadata was restored and re-sync was attempted; {@code false} if concurrent metadata edits made the restore unsafe
     */
    public boolean resyncAfterRevert(ProgrammingExercise exercise, User user, String testsCommitHash, String problemStatement, String title, String expectedProblemStatement,
            String expectedTitle) {
        if (!restoreProblemStatementIfUnchanged(exercise, problemStatement, title, expectedProblemStatement, expectedTitle)) {
            return false;
        }
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, false);
            log.info("Re-synchronised exercise {} after reverting an adaptation (test-case synchronisation completes asynchronously via CI)", exercise.getId());
            return true;
        }
        catch (RuntimeException e) {
            log.error("Repositories and metadata were reverted for exercise {}, but the follow-up test-case sync/version update failed; manual review is required",
                    exercise.getId(), e);
            return false;
        }
    }

    /**
     * Triggers the canonical tests build (when a tests commit exists) so test-case grading follows the committed tests, re-applies the build-gate zero-weighting, and records a new
     * exercise version so open editors and search see the committed state — the post-commit steps shared by {@link #persist} and {@link #resyncAfterRevert}. Accepted generation
     * treats version creation as mandatory; revert keeps it best-effort because the repository reset is already the desired recovery action.
     *
     * @param exercise        the exercise whose test cases to sync and version to record
     * @param user            the exercise-version author
     * @param testsCommitHash the tests repository's commit HEAD driving the test-case-sync build; {@code null} skips the build
     */
    private void syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, String testsCommitHash, boolean failOnVersionFailure) {
        if (testsCommitHash != null) {
            TestsBuildSignal signal = triggerTestsBuild(exercise, testsCommitHash);
            zeroWeightBuildGateTestCases(exercise.getId(), signal);
        }
        try {
            exerciseVersionService.createExerciseVersionOrThrow(exercise, user);
        }
        catch (RuntimeException e) {
            if (failOnVersionFailure) {
                throw e;
            }
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
    }

    private void resyncBaselineTestsAfterCompensation(ProgrammingExercise exercise, User user, String testsCommitHash) {
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, false);
        }
        catch (RuntimeException e) {
            log.error("Could not re-sync test cases for exercise {} after compensating a failed generation persist; manual review is required", exercise.getId(), e);
        }
    }

    private boolean restoreProblemStatementIfUnchanged(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle) {
        try {
            int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), problemStatement, title, expectedProblemStatement,
                    expectedTitle);
            if (updatedRows != 1) {
                log.error("Could not restore the previous problem statement/title for exercise {} because it changed after the adaptation revert started", exercise.getId());
                return false;
            }
            exercise.setTitle(title);
            exercise.setProblemStatement(problemStatement);
            programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
            return true;
        }
        catch (RuntimeException restoreFailure) {
            log.error("Could not restore the previous problem statement/title for exercise {} after reverting an adaptation; manual review is required", exercise.getId(),
                    restoreFailure);
            return false;
        }
    }

    private void saveProblemStatementIfUnchanged(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle) {
        String trimmedProblemStatement = problemStatement.trim();
        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), trimmedProblemStatement, title, expectedProblemStatement,
                expectedTitle);
        if (updatedRows != 1) {
            throw new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits");
        }
        if (!java.util.Objects.equals(exercise.getTitle(), title)) {
            exercise.setTitle(title);
        }
        exercise.setProblemStatement(trimmedProblemStatement);
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
    private boolean compensate(ProgrammingExercise exercise, List<RepositoryType> committed, Map<RepositoryType, String> prePersistHashes,
            Map<RepositoryType, String> postPersistHashes) {
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
                String currentHash = gitService.getLastCommitHash(uri);
                String postHash = postPersistHashes.get(repositoryType);
                if (preHash.equals(currentHash)) {
                    continue;
                }
                if (postHash == null || currentHash == null) {
                    fullyReverted = false;
                    log.error("Refusing to compensate the {} repository of exercise {}: missing current HEAD ({}) or Hyperion's committed HEAD ({})", repositoryType,
                            exercise.getId(), currentHash, postHash);
                    continue;
                }
                if (!postHash.equals(currentHash)) {
                    fullyReverted = false;
                    log.error("Refusing to compensate the {} repository of exercise {}: current HEAD {} differs from Hyperion's committed HEAD {}", repositoryType,
                            exercise.getId(), currentHash, postHash);
                    continue;
                }
                gitService.resetToCommitAndForcePush(repository, preHash, postHash, defaultBranch);
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
    private String commitRepository(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles, String seedHead,
            Map<RepositoryType, String> prePersistHashes, Map<RepositoryType, String> postPersistHashes) {
        if (producedFiles == null || producedFiles.isEmpty()) {
            return null;
        }
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            return null;
        }
        try {
            Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            // Capture the checked-out local HEAD after pull and before mutation, so compensation reverts to the exact parent this Hyperion commit was built on.
            String prePersistHead = gitService.getLocalHeadHash(repository);
            prePersistHashes.put(repositoryType, prePersistHead);
            if (seedHead != null && !seedHead.equals(prePersistHead)) {
                throw new IllegalStateException(
                        "The " + repositoryType + " repository changed after Hyperion verified the generated exercise; refusing to overwrite newer instructor edits");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            repositoryService.commitChanges(repository, user);
            // Capture the local commit we just created. Do not re-read the remote here: a concurrent manual push after Hyperion's push must not become Hyperion-owned state.
            String postHash = gitService.getLocalHeadHash(repository);
            if (postHash != null) {
                postPersistHashes.put(repositoryType, postHash);
            }
            return postHash;
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
        Map<String, String> persistableFiles = persistableProducedFiles(producedFiles);
        deleteOrphanedFiles(repository, repositoryType, persistableFiles.keySet());
        for (Map.Entry<String, String> entry : persistableFiles.entrySet()) {
            String path = entry.getKey();
            if (gitService.getFileByName(repository, path).isPresent()) {
                repositoryService.deleteFile(repository, path);
            }
            repositoryService.createFile(repository, path, new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        // The produced tree can re-introduce raw ${...} placeholders (e.g. from the reference's run.sh); normalize to real-CI values as exercise creation does (idempotent if
        // clean).
        programmingExerciseRepositoryService.replacePlaceholders(exercise, repository);
    }

    private static Map<String, String> persistableProducedFiles(Map<String, String> producedFiles) {
        Map<String, String> persistableFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
            if (!"problem-statement.md".equals(normalizeRepositoryPath(entry.getKey()))) {
                persistableFiles.put(entry.getKey(), entry.getValue());
            }
        }
        return persistableFiles;
    }

    private static String normalizeRepositoryPath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    /**
     * Deletes every tracked text file the agent did not produce, so the committed tree mirrors the sandbox-final state rather than overlaying onto the scaffolded sample (which
     * would orphan the sample's test sources, structure oracle, or harness into real grading). A delete failure aborts the persist: a leftover tracked file means the committed
     * tree would differ from the sandbox tree the oracle verified.
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
            if (tracked.getValue() != FileType.FILE || producedPaths.contains(path)) {
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
                throw new IllegalStateException("Could not remove orphaned " + repositoryType + " file " + path + " during persist", e);
            }
        }
    }

    /**
     * The signal needed to detect that the {@link #triggerTestsBuild triggered} tests-build has finished processing: the solution participation, tests commit hash, and latest
     * result before the build. Production associates TEST build results by participation + commit hash + submission type, not by the specific submission id created here.
     */
    private record TestsBuildSignal(long solutionParticipationId, String testsCommitHash, Long baselineLatestResultId) {
    }

    private TestsBuildSignal triggerTestsBuild(ProgrammingExercise exercise, String commitHash) {
        try {
            ProgrammingExerciseParticipation solutionParticipation = participationService.retrieveSolutionParticipation(exercise);
            long solutionParticipationId = solutionParticipation.getId();
            // Capture the latest result BEFORE triggering so the wait keys on a strictly newer result than any pre-existing (e.g. exercise-setup) build, not on the case count.
            Long baselineLatestResultId = resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(solutionParticipationId).map(Result::getId).orElse(null);
            programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
            continuousIntegrationTriggerService.triggerBuild(solutionParticipation, commitHash, RepositoryType.TESTS);
            return new TestsBuildSignal(solutionParticipationId, commitHash, baselineLatestResultId);
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to trigger the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
            throw new IllegalStateException("Failed to trigger the test-case-syncing build for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
        catch (RuntimeException e) {
            log.warn("Unexpected error triggering the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
            throw new IllegalStateException("Unexpected error triggering the test-case-syncing build for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * For C/C++ FACT exercises the synced report includes build-gate cases (CompileSort/TestConfigure) that PASS on the compiling template; the differential oracle exempts them
     * ({@link BuildGateTestNames}) but production grades every case, so without this a student submitting the untouched template would score above 0%. Waits (bounded) for the
     * freshly triggered tests-build to finish re-syncing, then zero-weights the build gates to match the oracle. Best-effort, idempotent, a no-op for languages without build-gate
     * cases.
     *
     * @param exerciseId the generated exercise whose build-gate test cases should be excluded from grading
     * @param signal     the pre-trigger baseline identifying the triggered build to wait for
     */
    private void zeroWeightBuildGateTestCases(long exerciseId, TestsBuildSignal signal) {
        try {
            Set<ProgrammingExerciseTestCase> testCases = awaitBuildProcessedTestCaseSet(exerciseId, signal);
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
     * The wait keys on a newer TEST result for the same tests commit hash. The grading pipeline saves the freshly re-synced cases strictly before it saves that result, so seeing
     * it guarantees the complete set is already committed while matching production's commit-hash based result association.
     *
     * @param exerciseId the exercise whose test-case set to await
     * @param signal     the pre-trigger baseline (solution participation and its latest result id) identifying the build to wait for
     * @return the test-case set once the triggered build's result is visible, or the last set read when the timeout was reached first
     */
    private Set<ProgrammingExerciseTestCase> awaitBuildProcessedTestCaseSet(long exerciseId, TestsBuildSignal signal) throws InterruptedException {
        long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (resultRepository.existsNewerTestResultForParticipationAndCommitHash(signal.solutionParticipationId(), signal.testsCommitHash(), signal.baselineLatestResultId())) {
                return testCaseRepository.findByExerciseId(exerciseId);
            }
            Thread.sleep(testCaseSyncPoll.toMillis());
        }
        log.warn("Timed out waiting for the tests-build result of generated exercise {}; a build-gate case may keep its weight until reconfigured", exerciseId);
        return testCaseRepository.findByExerciseId(exerciseId);
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
