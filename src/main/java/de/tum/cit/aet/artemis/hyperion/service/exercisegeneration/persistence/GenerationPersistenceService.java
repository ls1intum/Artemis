package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
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
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
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
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Persists a verified-complete generated exercise through Artemis's normal pipeline (commit repositories, trigger the canonical tests build for test-case sync, update the problem
 * statement, record an exercise version), the same path a manual instructor edit uses. Runs only after the differential oracle has accepted the exercise.
 * <p>
 * The three repositories (template, solution, tests) cannot commit inside a single database/git transaction, so a broad {@code @Transactional} would not make the multi-repository
 * write atomic anyway. Each push uses the captured remote head as an exact ref lease, and failures caught in-process compensate already-pushed repositories in reverse order.
 * Concurrent repository changes are never overwritten.
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

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final TempFileUtilService tempFileUtilService;

    private final Duration testCaseSyncTimeout;

    private final Duration testCaseSyncPoll;

    @Autowired
    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ResultRepository resultRepository, ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            TempFileUtilService tempFileUtilService) {
        this(defaultBranch, gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService,
                testCaseRepository, resultRepository, programmingExerciseRepository, programmingExerciseTaskService, tempFileUtilService, TEST_CASE_SYNC_TIMEOUT,
                TEST_CASE_SYNC_POLL);
    }

    // Package-private so tests can inject a shrunken sync wait and exercise the build-completion wait without sleeping for seconds.
    GenerationPersistenceService(String defaultBranch, GitService gitService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ContinuousIntegrationTriggerService continuousIntegrationTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository, ResultRepository resultRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, TempFileUtilService tempFileUtilService,
            Duration testCaseSyncTimeout, Duration testCaseSyncPoll) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.participationService = participationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseVersionService = exerciseVersionService;
        this.testCaseRepository = testCaseRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.tempFileUtilService = tempFileUtilService;
        this.testCaseSyncTimeout = testCaseSyncTimeout;
        this.testCaseSyncPoll = testCaseSyncPoll;
    }

    private static final RepositoryType[] PERSIST_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

    private static final int MAX_TITLE_LENGTH = 255;

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration TEST_CASE_SYNC_POLL = Duration.ofSeconds(3);

    public record RecoveryPersistResult(String draftBranch, Set<RepositoryType> savedRepositories) {

        public RecoveryPersistResult {
            savedRepositories = Set.copyOf(savedRepositories);
        }
    }

    public record PersistResult(Map<RepositoryType, String> prePersistHeads, Map<RepositoryType, String> postPersistHeads, String persistedProblemStatement, String persistedTitle,
            String repositoryBranch) {
    }

    /**
     * Persists generated repository files from a non-accepted run without touching the live default branch. The generated problem statement is not stored by this recovery path.
     * Rejected repository output is diverted to an isolated branch with no CI build and no exercise version; only the accepted path may update the canonical exercise.
     *
     * @param exercise the exercise to persist the draft into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the non-accepted generation outcome holding the produced files
     * @param jobId    the generation job id, used to name the isolated draft branch
     * @return the isolated branch and the repositories for which a draft commit was pushed
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        return persistRecoveryDraft(exercise, user, outcome, jobId, () -> true);
    }

    /**
     * Persists a non-accepted draft while aborting before each repository mutation if this node no longer owns the job.
     *
     * @param exercise              the exercise to persist the draft into
     * @param user                  the instructor performing the generation (commit author)
     * @param outcome               the non-accepted generation outcome holding the produced files
     * @param jobId                 the generation job id, used to name the isolated draft branch
     * @param stillOwnsMutationSlot guard checked before each durable mutation
     * @return the isolated branch and the repositories for which a draft commit was pushed
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId, BooleanSupplier stillOwnsMutationSlot) {
        String draftBranch = RECOVERY_DRAFT_BRANCH_PREFIX + jobId;
        String repositoryBranch = repositoryBranch(exercise);
        Set<RepositoryType> savedRepositories = EnumSet.noneOf(RepositoryType.class);
        assertStillOwnsMutationSlot(stillOwnsMutationSlot);
        if (commitDraftToIsolatedBranch(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE), repositoryBranch, draftBranch)) {
            savedRepositories.add(RepositoryType.TEMPLATE);
        }
        assertStillOwnsMutationSlot(stillOwnsMutationSlot);
        if (commitDraftToIsolatedBranch(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION), repositoryBranch, draftBranch)) {
            savedRepositories.add(RepositoryType.SOLUTION);
        }
        assertStillOwnsMutationSlot(stillOwnsMutationSlot);
        if (commitDraftToIsolatedBranch(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS), repositoryBranch, draftBranch)) {
            savedRepositories.add(RepositoryType.TESTS);
        }
        if (savedRepositories.isEmpty()) {
            throw new IllegalStateException("No repository draft was saved for exercise " + exercise.getId());
        }
        log.info("Recovered non-accepted repository draft for exercise {} onto isolated branch {} in {}", exercise.getId(), draftBranch, savedRepositories);
        return new RecoveryPersistResult(draftBranch, savedRepositories);
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
    private boolean commitDraftToIsolatedBranch(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles, String repositoryBranch,
            String draftBranch) {
        if (producedFiles == null || producedFiles.isEmpty()) {
            return false;
        }
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            throw new IllegalStateException("The " + repositoryType + " repository is unavailable; its generated files cannot be saved");
        }
        Repository repository = null;
        Path temporaryCheckout = null;
        try {
            temporaryCheckout = tempFileUtilService.createTempDirectory("hyperion-persist-");
            repository = gitService.getOrCheckoutRepository(uri, uri, temporaryCheckout.resolve("repository"), true, repositoryBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            if (gitService.isWorkingCopyClean(repository)) {
                return false;
            }
            gitService.stageAllChanges(repository);
            gitService.commitToIsolatedBranchAndPush(repository, draftBranch, "Hyperion generation draft (needs review; NOT applied to the live exercise)", user);
            return true;
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to commit the " + repositoryType + " recovery draft to the isolated branch for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            deleteTemporaryCheckout(temporaryCheckout);
        }
    }

    /**
     * Persists a verified generated exercise.
     * <p>
     * The three repositories are committed in {@link #PERSIST_ORDER} (tests last, so the test-triggered build sees the final solution). Because they cannot commit atomically, each
     * repository push uses the captured remote head as a ref lease. If a later repository fails, the already-committed repositories are force-reset to their captured commits
     * ({@link #compensate}). The exercise version is recorded only after every repository has committed.
     *
     * @param exercise the exercise to persist into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the accepted generation outcome holding the produced files
     * @return the pre- and post-persist commit HEADs captured for each changed repository. Returned only after every repository committed successfully, so the caller records a
     *         revertible baseline exclusively for a persist that actually applied changes.
     * @throws GenerationIncompleteException if a repository commit fails part-way through the sequence (the already-committed repositories are compensated first)
     */
    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome) {
        return persist(exercise, user, outcome, exercise.getProblemStatement(), exercise.getTitle());
    }

    /**
     * Persists a verified generated exercise, refusing to overwrite problem statement/title edits made after the generation job started.
     *
     * @param exercise                 the exercise to update
     * @param user                     the instructor performing the generation (commit author)
     * @param outcome                  the accepted generation outcome
     * @param expectedProblemStatement the problem statement observed when the job started
     * @param expectedTitle            the title observed when the job started
     * @return the repository heads captured before and after persistence
     */
    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle) {
        return persist(exercise, user, outcome, expectedProblemStatement, expectedTitle, () -> true);
    }

    /**
     * Persists a verified generated exercise while aborting before durable mutations if this node no longer owns the job.
     *
     * @param exercise                 the exercise to update
     * @param user                     the instructor performing the generation (commit author)
     * @param outcome                  the accepted generation outcome
     * @param expectedProblemStatement the problem statement observed when the job started
     * @param expectedTitle            the title observed when the job started
     * @param stillOwnsMutationSlot    guard checked before durable mutations
     * @return the repository heads captured before and after persistence
     */
    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle,
            BooleanSupplier stillOwnsMutationSlot) {
        return persistWithCommitMessage(exercise, user, outcome, expectedProblemStatement, expectedTitle, "Generate exercise with Hyperion", stillOwnsMutationSlot);
    }

    /**
     * Persists a verified generated exercise and includes its job id in repository commits for cross-repository recovery diagnostics.
     *
     * @param exercise                 the exercise to update
     * @param user                     the commit and version author
     * @param outcome                  the accepted generation outcome
     * @param expectedProblemStatement the problem statement observed when the job started
     * @param expectedTitle            the title observed when the job started
     * @param jobId                    the generation job identifier
     * @param stillOwnsMutationSlot    guard checked before durable mutations
     * @return the repository heads captured before and after persistence
     */
    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle, String jobId,
            BooleanSupplier stillOwnsMutationSlot) {
        return persistWithCommitMessage(exercise, user, outcome, expectedProblemStatement, expectedTitle, "Generate exercise with Hyperion (" + jobId + ")", stillOwnsMutationSlot);
    }

    private PersistResult persistWithCommitMessage(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle,
            String commitMessage, BooleanSupplier stillOwnsMutationSlot) {
        String repositoryBranch = repositoryBranch(exercise);
        // Capture each repository's pre-persist HEAD before writing it, so a later failure can revert the already-committed repositories to a consistent pre-generation state.
        Map<RepositoryType, String> prePersistHashes = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, String> postPersistHashes = new EnumMap<>(RepositoryType.class);
        List<RepositoryType> committed = new ArrayList<>();
        String testsCommitHash = null;
        String producedProblemStatement = outcome.producedProblemStatement();
        String originalProblemStatement = expectedProblemStatement;
        String originalTitle = expectedTitle;
        String targetTitle = exercise.getTitle();
        boolean shouldSaveProblemStatement = !producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement());
        // From-scratch only (statement was blank): reconcile the lean AI create page's brief-derived placeholder title to the agent's own H1. An adapt run keeps the
        // instructor's title.
        if (shouldSaveProblemStatement && (exercise.getProblemStatement() == null || exercise.getProblemStatement().isBlank())) {
            String generatedTitle = extractTitleFromH1(producedProblemStatement);
            if (generatedTitle != null) {
                targetTitle = generatedTitle;
            }
        }
        try {
            assertStillOwnsMutationSlot(stillOwnsMutationSlot);
            assertMetadataUnchangedSinceJobStart(exercise, originalProblemStatement, originalTitle);
            for (RepositoryType repositoryType : PERSIST_ORDER) {
                assertStillOwnsMutationSlot(stillOwnsMutationSlot);
                String commitHash = commitRepository(exercise, user, repositoryType, outcome.producedFiles(repositoryType), outcome.seedRepositoryHeads().get(repositoryType),
                        repositoryBranch, commitMessage, prePersistHashes, postPersistHashes);
                if (commitHash != null) {
                    committed.add(repositoryType);
                    if (repositoryType == RepositoryType.TESTS) {
                        testsCommitHash = commitHash;
                    }
                }
            }
            assertRepositoryHeadsStillMatch(exercise, repositoryBranch, outcome.seedRepositoryHeads(), postPersistHashes);
        }
        catch (RuntimeException e) {
            // Compensation: revert the already-committed repositories to their captured pre-persist commit so no publishable half-generated tree survives on the default branch.
            boolean fullyReverted = compensate(exercise, repositoryBranch, committed, prePersistHashes, postPersistHashes);
            String state = fullyReverted ? "the already-committed repositories were reverted to their previous state"
                    : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
        }

        boolean problemStatementChanged = false;
        String persistedProblemStatement = null;
        String persistedTitle = null;
        if (shouldSaveProblemStatement) {
            try {
                assertStillOwnsMutationSlot(stillOwnsMutationSlot);
                saveProblemStatementIfUnchanged(exercise, producedProblemStatement, targetTitle, originalProblemStatement, originalTitle);
                problemStatementChanged = true;
                persistedProblemStatement = producedProblemStatement.trim();
                persistedTitle = targetTitle;
                programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
            }
            catch (RuntimeException e) {
                boolean fullyReverted = compensate(exercise, repositoryBranch, committed, prePersistHashes, postPersistHashes);
                boolean metadataRestored = true;
                if (problemStatementChanged) {
                    metadataRestored = restoreProblemStatementIfUnchanged(exercise, originalProblemStatement, originalTitle, persistedProblemStatement, persistedTitle);
                }
                boolean fullyRestored = fullyReverted && metadataRestored;
                String state = fullyRestored ? "the Hyperion changes were reverted without overwriting concurrent metadata"
                        : "compensation could not fully restore the repositories and metadata; manual review is required before using the exercise";
                throw new GenerationIncompleteException("Saving the generated exercise failed while updating the problem statement after committing " + committed
                        + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
            }
        }

        // Trigger the canonical CI build for the tests (drives test-case sync + task binding asynchronously) and record the exercise version — only reached once every repository
        // committed.
        String expectedFinalProblemStatement = shouldSaveProblemStatement ? producedProblemStatement.trim() : originalProblemStatement;
        String expectedFinalTitle = shouldSaveProblemStatement ? targetTitle : originalTitle;
        AtomicReference<MetadataSnapshot> persistedMetadata = new AtomicReference<>();
        Runnable finalizationGuard = () -> {
            assertStillOwnsMutationSlot(stillOwnsMutationSlot);
            assertRepositoryHeadsStillMatch(exercise, repositoryBranch, outcome.seedRepositoryHeads(), postPersistHashes);
            persistedMetadata.set(assertMetadataMatches(exercise, expectedFinalProblemStatement, expectedFinalTitle));
        };
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, true, finalizationGuard);
        }
        catch (RuntimeException e) {
            boolean fullyReverted = compensate(exercise, repositoryBranch, committed, prePersistHashes, postPersistHashes);
            boolean metadataRestored = true;
            if (problemStatementChanged) {
                metadataRestored = restoreProblemStatementIfUnchanged(exercise, originalProblemStatement, originalTitle, persistedProblemStatement, persistedTitle);
            }
            boolean baselineResynced = fullyReverted && resyncBaselineTestsAfterCompensation(exercise, user, prePersistHashes.get(RepositoryType.TESTS));
            boolean fullyRestored = fullyReverted && metadataRestored && baselineResynced;
            String state = fullyRestored ? "the Hyperion changes were reverted without overwriting concurrent metadata"
                    : "compensation could not fully restore the repositories and metadata; manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed while recording the exercise version after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ". Cause: " + e.getMessage(), e);
        }
        log.info("Persisted generated exercise {} after test-case synchronisation completed", exercise.getId());
        prePersistHashes.keySet().retainAll(postPersistHashes.keySet());
        MetadataSnapshot metadata = persistedMetadata.get();
        return new PersistResult(nonNullCopy(prePersistHashes), nonNullCopy(postPersistHashes), metadata.problemStatement(), metadata.title(), repositoryBranch);
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

    private String repositoryBranch(ProgrammingExercise exercise) {
        String branch = exercise.getBuildConfig() != null ? exercise.getBuildConfig().getBranch() : null;
        return branch == null || branch.isBlank() ? defaultBranch : branch;
    }

    private void assertRepositoryHeadsStillMatch(ProgrammingExercise exercise, String repositoryBranch, Map<RepositoryType, String> seedHeads,
            Map<RepositoryType, String> postPersistHeads) {
        for (RepositoryType repositoryType : PERSIST_ORDER) {
            String expectedHead = postPersistHeads.getOrDefault(repositoryType, seedHeads.get(repositoryType));
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (expectedHead != null && (uri == null || !expectedHead.equals(gitService.getLastCommitHash(uri, repositoryBranch)))) {
                throw new IllegalStateException(
                        "The " + repositoryType + " repository changed while Hyperion was saving the generated exercise; refusing to finalize a stale repository set");
            }
        }
    }

    private static void assertStillOwnsMutationSlot(BooleanSupplier stillOwnsMutationSlot) {
        if (!stillOwnsMutationSlot.getAsBoolean()) {
            throw new IllegalStateException("Hyperion generation lost ownership of the exercise mutation slot; refusing to continue durable writes");
        }
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
            log.info("Re-synchronised exercise {} after reverting an adaptation", exercise.getId());
            return true;
        }
        catch (RuntimeException e) {
            log.error("Repositories and metadata were reverted for exercise {}, but the follow-up test-case sync/version update failed; manual review is required",
                    exercise.getId(), e);
            return false;
        }
    }

    public boolean canRestoreProblemStatementAndTitle(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle) {
        return currentMetadataMatchingExpectedOrTarget(exercise, expectedProblemStatement, expectedTitle, problemStatement, title).isPresent();
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
    private void syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, String testsCommitHash, boolean failOnFinalizationFailure) {
        syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, failOnFinalizationFailure, () -> {
        });
    }

    private void syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, String testsCommitHash, boolean failOnFinalizationFailure, Runnable finalizationGuard) {
        finalizationGuard.run();
        if (testsCommitHash != null) {
            TestsBuildSignal signal = triggerTestsBuild(exercise, testsCommitHash);
            zeroWeightBuildGateTestCases(exercise.getId(), signal, failOnFinalizationFailure);
        }
        finalizationGuard.run();
        try {
            exerciseVersionService.createExerciseVersionOrThrow(exercise, user);
        }
        catch (RuntimeException e) {
            if (failOnFinalizationFailure) {
                throw e;
            }
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
    }

    private boolean resyncBaselineTestsAfterCompensation(ProgrammingExercise exercise, User user, String testsCommitHash) {
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsCommitHash, true);
            return true;
        }
        catch (RuntimeException e) {
            log.error("Could not re-sync test cases for exercise {} after compensating a failed generation persist; manual review is required", exercise.getId(), e);
            return false;
        }
    }

    private boolean restoreProblemStatementIfUnchanged(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle) {
        try {
            Optional<MetadataSnapshot> currentMetadata = currentMetadataMatchingExpectedOrTarget(exercise, expectedProblemStatement, expectedTitle, problemStatement, title);
            if (currentMetadata.isEmpty()) {
                log.error("Could not restore the previous problem statement/title for exercise {} because it changed after the adaptation revert started", exercise.getId());
                return false;
            }
            MetadataSnapshot current = currentMetadata.get();
            int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), problemStatement, title, current.problemStatement(),
                    current.title());
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

    private record MetadataSnapshot(String problemStatement, String title) {
    }

    private Optional<MetadataSnapshot> currentMetadataMatchingExpectedOrTarget(ProgrammingExercise exercise, String expectedProblemStatement, String expectedTitle,
            String targetProblemStatement, String targetTitle) {
        Optional<ProgrammingExercise> currentExercise = programmingExerciseRepository.findById(exercise.getId());
        if (currentExercise.isEmpty()) {
            return Optional.empty();
        }
        String currentProblemStatement = currentExercise.get().getProblemStatement();
        String currentTitle = currentExercise.get().getTitle();
        if (metadataPairMatches(exercise.getId(), currentProblemStatement, currentTitle, expectedProblemStatement, expectedTitle)
                || metadataPairMatches(exercise.getId(), currentProblemStatement, currentTitle, targetProblemStatement, targetTitle)) {
            return Optional.of(new MetadataSnapshot(currentProblemStatement, currentTitle));
        }
        return Optional.empty();
    }

    private boolean metadataPairMatches(long exerciseId, String currentProblemStatement, String currentTitle, String expectedProblemStatement, String expectedTitle) {
        return problemStatementMetadataMatches(exerciseId, currentProblemStatement, expectedProblemStatement)
                && java.util.Objects.equals(normalizeMetadata(currentTitle), normalizeMetadata(expectedTitle));
    }

    private boolean problemStatementMetadataMatches(long exerciseId, String currentProblemStatement, String expectedProblemStatement) {
        if (java.util.Objects.equals(normalizeMetadata(currentProblemStatement), normalizeMetadata(expectedProblemStatement))) {
            return true;
        }
        return java.util.Objects.equals(normalizeMetadata(problemStatementWithTaskIdsRenderedAsNames(exerciseId, currentProblemStatement)),
                normalizeMetadata(problemStatementWithTaskIdsRenderedAsNames(exerciseId, expectedProblemStatement)));
    }

    private String problemStatementWithTaskIdsRenderedAsNames(long exerciseId, String problemStatement) {
        if (problemStatement == null) {
            return null;
        }
        ProgrammingExercise copy = new ProgrammingExercise();
        copy.setId(exerciseId);
        copy.setProblemStatement(problemStatement);
        try {
            programmingExerciseTaskService.replaceTestIdsWithNames(copy);
        }
        catch (RuntimeException e) {
            log.debug("Could not canonicalize task ids while comparing Hyperion problem-statement metadata for exercise {}", exerciseId, e);
        }
        return copy.getProblemStatement();
    }

    private static String normalizeMetadata(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private void saveProblemStatementIfUnchanged(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle) {
        String trimmedProblemStatement = problemStatement.trim();
        MetadataSnapshot current = currentMetadataMatchingSafeSave(exercise, expectedProblemStatement, expectedTitle, trimmedProblemStatement, title).orElseThrow(
                () -> new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits"));
        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), trimmedProblemStatement, title, current.problemStatement(),
                current.title());
        if (updatedRows != 1) {
            throw new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits");
        }
        if (!java.util.Objects.equals(exercise.getTitle(), title)) {
            exercise.setTitle(title);
        }
        exercise.setProblemStatement(trimmedProblemStatement);
    }

    private void assertMetadataUnchangedSinceJobStart(ProgrammingExercise exercise, String expectedProblemStatement, String expectedTitle) {
        Optional<ProgrammingExercise> currentExercise = programmingExerciseRepository.findById(exercise.getId());
        if (currentExercise.isEmpty()
                || !metadataPairMatches(exercise.getId(), currentExercise.get().getProblemStatement(), currentExercise.get().getTitle(), expectedProblemStatement, expectedTitle)) {
            throw new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits");
        }
    }

    private MetadataSnapshot assertMetadataMatches(ProgrammingExercise exercise, String expectedProblemStatement, String expectedTitle) {
        Optional<ProgrammingExercise> currentExercise = programmingExerciseRepository.findById(exercise.getId());
        if (currentExercise.isEmpty()
                || !metadataPairMatches(exercise.getId(), currentExercise.get().getProblemStatement(), currentExercise.get().getTitle(), expectedProblemStatement, expectedTitle)) {
            throw new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits");
        }
        return new MetadataSnapshot(currentExercise.get().getProblemStatement(), currentExercise.get().getTitle());
    }

    private Optional<MetadataSnapshot> currentMetadataMatchingSafeSave(ProgrammingExercise exercise, String expectedProblemStatement, String expectedTitle,
            String targetProblemStatement, String targetTitle) {
        return currentMetadataMatchingExpectedOrTarget(exercise, expectedProblemStatement, expectedTitle, targetProblemStatement, targetTitle);
    }

    /**
     * Force-resets committed repositories in reverse order after a failed multi-repository persist. Failures do not stop later compensation but make the result incomplete; a
     * repository without a captured prior commit cannot be reverted.
     *
     * @param exercise         the exercise whose repositories are compensated
     * @param committed        the repositories that were successfully committed and must be reverted
     * @param prePersistHashes the pre-persist commit hash captured per repository before it was written
     * @return {@code true} if every committed repository was reverted; {@code false} if any could not be
     */
    private boolean compensate(ProgrammingExercise exercise, String repositoryBranch, List<RepositoryType> committed, Map<RepositoryType, String> prePersistHashes,
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
            Path temporaryCheckout = null;
            Repository repository = null;
            try {
                temporaryCheckout = tempFileUtilService.createTempDirectory("hyperion-compensate-");
                repository = gitService.getOrCheckoutRepository(uri, uri, temporaryCheckout.resolve("repository"), true, repositoryBranch, false);
                if (repository == null) {
                    throw new IllegalStateException("Could not check out the repository to revert it");
                }
                String currentHash = gitService.getLastCommitHash(uri, repositoryBranch);
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
                gitService.resetToCommitAndForcePush(repository, preHash, postHash, repositoryBranch);
                log.info("Reverted the {} repository of exercise {} back to its pre-generation commit {} during persist compensation", repositoryType, exercise.getId(), preHash);
            }
            catch (Exception e) {
                fullyReverted = false;
                log.error("Failed to revert the {} repository of exercise {} back to {} during persist compensation; the exercise may be inconsistent", repositoryType,
                        exercise.getId(), preHash, e);
            }
            finally {
                if (repository != null) {
                    repository.closeBeforeDelete();
                }
                deleteTemporaryCheckout(temporaryCheckout);
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
            String repositoryBranch, String commitMessage, Map<RepositoryType, String> prePersistHashes, Map<RepositoryType, String> postPersistHashes) {
        if (producedFiles == null || producedFiles.isEmpty()) {
            return null;
        }
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            throw new IllegalStateException("The " + repositoryType + " repository is unavailable; its generated files cannot be saved");
        }
        Repository repository = null;
        Path temporaryCheckout = null;
        String prePersistHead = null;
        try {
            temporaryCheckout = tempFileUtilService.createTempDirectory("hyperion-persist-");
            repository = gitService.getOrCheckoutRepository(uri, uri, temporaryCheckout.resolve("repository"), true, repositoryBranch, false);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
            }
            // Capture the checked-out local HEAD after pull and before mutation, so compensation reverts to the exact parent this Hyperion commit was built on.
            prePersistHead = gitService.getLocalHeadHash(repository);
            prePersistHashes.put(repositoryType, prePersistHead);
            if (seedHead != null && !seedHead.equals(prePersistHead)) {
                throw new IllegalStateException(
                        "The " + repositoryType + " repository changed after Hyperion verified the generated exercise; refusing to overwrite newer instructor edits");
            }
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            if (gitService.isWorkingCopyClean(repository)) {
                return null;
            }
            gitService.stageAllChanges(repository);
            String postHash = gitService.commitStagedChanges(repository, commitMessage, user);
            gitService.pushCommitWithLease(repository, postHash, repositoryBranch, prePersistHead);
            if (postHash != null) {
                postPersistHashes.put(repositoryType, postHash);
            }
            return postHash;
        }
        catch (Exception e) {
            boolean manualReviewRequired = false;
            if (repository != null) {
                manualReviewRequired = reconcileAmbiguousCommitFailure(exercise, repositoryType, uri, repository, repositoryBranch, prePersistHead, e);
                try {
                    gitService.resetToOriginHead(repository);
                }
                catch (RuntimeException resetFailure) {
                    log.warn("Could not reset the {} repository working copy after a failed Hyperion commit for exercise {}: {}", repositoryType, exercise.getId(),
                            resetFailure.getMessage());
                }
            }
            String manualReviewMessage = manualReviewRequired ? " The remote repository state is ambiguous and requires manual review." : "";
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage() + manualReviewMessage,
                    e);
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            deleteTemporaryCheckout(temporaryCheckout);
        }
    }

    private boolean reconcileAmbiguousCommitFailure(ProgrammingExercise exercise, RepositoryType repositoryType, LocalVCRepositoryUri uri, Repository repository,
            String repositoryBranch, String prePersistHead, Exception commitFailure) {
        if (prePersistHead == null) {
            return false;
        }
        try {
            String localHead = gitService.getLocalHeadHash(repository);
            if (localHead == null || prePersistHead.equals(localHead)) {
                return false;
            }
            String remoteHead = gitService.getLastCommitHash(uri, repositoryBranch);
            if (!localHead.equals(remoteHead)) {
                if (!prePersistHead.equals(remoteHead)) {
                    IllegalStateException ambiguousFailure = new IllegalStateException("The remote branch advanced after the failed Hyperion commit; manual review is required");
                    commitFailure.addSuppressed(ambiguousFailure);
                    log.error("Could not determine whether the failed {} repository commit for exercise {} remains in remote history; current remote HEAD is {}", repositoryType,
                            exercise.getId(), remoteHead);
                    return true;
                }
                return false;
            }
            gitService.resetToCommitAndForcePush(repository, prePersistHead, localHead, repositoryBranch);
            log.info("Reverted the {} repository of exercise {} after its commit was pushed but reported as failed", repositoryType, exercise.getId());
            return false;
        }
        catch (Exception reconciliationFailure) {
            commitFailure.addSuppressed(reconciliationFailure);
            log.error("Could not reconcile the failed {} repository commit for exercise {}; the remote branch may contain an unrecorded Hyperion commit", repositoryType,
                    exercise.getId(), reconciliationFailure);
            return true;
        }
    }

    private static void deleteTemporaryCheckout(Path path) {
        if (path != null && !FileUtils.deleteQuietly(path.toFile())) {
            log.warn("Could not delete temporary Hyperion persistence checkout {}", path);
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
        Map<String, String> safeProducedFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
            String safePath = safeRepositoryPath(repository, entry.getKey());
            if (safeProducedFiles.putIfAbsent(safePath, entry.getValue()) != null) {
                throw new IllegalArgumentException("Multiple produced paths resolve to " + safePath);
            }
        }
        assertNoSymbolicLinks(repository);
        Map<String, String> persistableFiles = persistableProducedFiles(safeProducedFiles);
        deleteOrphanedFiles(repository, repositoryType, persistableFiles.keySet());
        for (Map.Entry<String, String> entry : persistableFiles.entrySet()) {
            String path = entry.getKey();
            Path repositoryRoot = repository.getLocalPath();
            boolean executable = repositoryRoot != null && Files.isExecutable(repositoryRoot.resolve(path));
            if (gitService.getFileByName(repository, path).isPresent()) {
                repositoryService.deleteFile(repository, path);
            }
            repositoryService.createFile(repository, path, new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)));
            if (executable && !repositoryRoot.resolve(path).toFile().setExecutable(true, false)) {
                throw new IOException("Could not restore executable mode for " + path);
            }
        }
    }

    private static void assertNoSymbolicLinks(Repository repository) throws IOException {
        Path repositoryRoot = repository.getLocalPath();
        if (repositoryRoot == null) {
            throw new IllegalStateException("The repository working tree is unavailable");
        }
        Path gitMetadata = repositoryRoot.resolve(".git").normalize();
        try (Stream<Path> paths = Files.find(repositoryRoot, Integer.MAX_VALUE, (path, attributes) -> !path.startsWith(gitMetadata) && attributes.isSymbolicLink())) {
            Optional<Path> symbolicLink = paths.findFirst();
            if (symbolicLink.isPresent()) {
                throw new IllegalArgumentException("The repository contains a symbolic link outside Git metadata: " + repositoryRoot.relativize(symbolicLink.get()));
            }
        }
    }

    private static String safeRepositoryPath(Repository repository, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("A produced file path must not be blank");
        }
        String portablePath = path.replace('\\', '/');
        Path relativePath = Path.of(portablePath).normalize();
        boolean windowsAbsolutePath = portablePath.length() >= 3 && Character.isLetter(portablePath.charAt(0)) && portablePath.charAt(1) == ':' && portablePath.charAt(2) == '/';
        if (relativePath.isAbsolute() || windowsAbsolutePath || relativePath.startsWith("..") || relativePath.toString().isBlank() || ".".equals(relativePath.toString())) {
            throw new IllegalArgumentException("Produced path is outside the repository: " + path);
        }
        Path repositoryRoot = repository.getLocalPath();
        if (repositoryRoot != null) {
            Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
            Path target = normalizedRoot.resolve(relativePath).normalize();
            if (!target.startsWith(normalizedRoot)) {
                throw new IllegalArgumentException("Produced path is outside the repository: " + path);
            }
            relativePath = normalizedRoot.relativize(target);
        }
        return relativePath.toString().replace('\\', '/');
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
     * freshly triggered tests-build to finish re-syncing, then zero-weights the build gates to match the oracle. Idempotent and a no-op for languages without build-gate cases.
     *
     * @param exerciseId the generated exercise whose build-gate test cases should be excluded from grading
     * @param signal     the pre-trigger baseline identifying the triggered build to wait for
     */
    private void zeroWeightBuildGateTestCases(long exerciseId, TestsBuildSignal signal, boolean failOnError) {
        try {
            Set<ProgrammingExerciseTestCase> testCases = awaitBuildProcessedTestCaseSet(exerciseId, signal, failOnError);
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
            if (failOnError) {
                throw new IllegalStateException("Interrupted while waiting for generated exercise test-case synchronization", e);
            }
        }
        catch (RuntimeException e) {
            if (failOnError) {
                throw e;
            }
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
     * @return the test-case set once the triggered build's result is visible, or the last set read after a best-effort timeout
     */
    private Set<ProgrammingExerciseTestCase> awaitBuildProcessedTestCaseSet(long exerciseId, TestsBuildSignal signal, boolean failOnTimeout) throws InterruptedException {
        long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (resultRepository.existsNewerTestResultForParticipationAndCommitHash(signal.solutionParticipationId(), signal.testsCommitHash(), signal.baselineLatestResultId())) {
                return testCaseRepository.findByExerciseId(exerciseId);
            }
            Thread.sleep(testCaseSyncPoll.toMillis());
        }
        if (failOnTimeout) {
            throw new IllegalStateException("Timed out waiting for the tests-build result of generated exercise " + exerciseId);
        }
        log.warn("Timed out waiting for the tests-build result of reverted exercise {}; a build-gate case may keep its weight until reconfigured", exerciseId);
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
