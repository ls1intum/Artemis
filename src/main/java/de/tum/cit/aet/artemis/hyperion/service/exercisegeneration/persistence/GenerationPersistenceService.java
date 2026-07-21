package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.BuildGateTestNames;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GeneratedTestPlan;
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
 * Persists a verified-complete generated exercise through Artemis's normal pipeline (commit repositories, wait for the tests push to trigger test-case sync, update the problem
 * statement, record an exercise version), the same path a manual instructor edit uses. Runs only after the exercise passes mechanical verification.
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

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

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

    private final ProblemStatementMetadataUpdateService problemStatementMetadataUpdateService;

    private final TempFileUtilService tempFileUtilService;

    private final Duration testCaseSyncTimeout;

    private final Duration testCaseSyncPoll;

    @Autowired
    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch,
            @Value("${artemis.hyperion.generation.test-case-sync-timeout:PT10M}") Duration testCaseSyncTimeout,
            @Value("${artemis.hyperion.generation.test-case-sync-poll:PT3S}") Duration testCaseSyncPoll, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ResultRepository resultRepository, ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProblemStatementMetadataUpdateService problemStatementMetadataUpdateService, TempFileUtilService tempFileUtilService) {
        this(defaultBranch, gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService,
                testCaseRepository, resultRepository, programmingExerciseRepository, programmingExerciseTaskService, problemStatementMetadataUpdateService, tempFileUtilService,
                testCaseSyncTimeout, testCaseSyncPoll);
    }

    // Package-private so tests can inject a shrunken sync wait and exercise the build-completion wait without sleeping for seconds.
    GenerationPersistenceService(String defaultBranch, GitService gitService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ContinuousIntegrationTriggerService continuousIntegrationTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository, ResultRepository resultRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProblemStatementMetadataUpdateService problemStatementMetadataUpdateService, TempFileUtilService tempFileUtilService, Duration testCaseSyncTimeout,
            Duration testCaseSyncPoll) {
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
        this.problemStatementMetadataUpdateService = problemStatementMetadataUpdateService;
        this.tempFileUtilService = tempFileUtilService;
        this.testCaseSyncTimeout = testCaseSyncTimeout;
        this.testCaseSyncPoll = testCaseSyncPoll;
    }

    // Package-private convenience constructor for tests that do not exercise the narrow-transactional metadata/task-rebuild path directly (they mock this service instead).
    GenerationPersistenceService(String defaultBranch, GitService gitService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ContinuousIntegrationTriggerService continuousIntegrationTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository, ResultRepository resultRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, TempFileUtilService tempFileUtilService,
            Duration testCaseSyncTimeout, Duration testCaseSyncPoll) {
        this(defaultBranch, gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService,
                testCaseRepository, resultRepository, programmingExerciseRepository, programmingExerciseTaskService,
                new ProblemStatementMetadataUpdateService(programmingExerciseRepository, programmingExerciseTaskService), tempFileUtilService, testCaseSyncTimeout,
                testCaseSyncPoll);
    }

    private static final RepositoryType[] PERSIST_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private static final int MAX_TITLE_LENGTH = 255;

    public record PersistResult(Map<RepositoryType, String> prePersistHeads, Map<RepositoryType, String> postPersistHeads, String persistedProblemStatement, String persistedTitle,
            String repositoryBranch, boolean metadataChanged, Long savedExerciseVersionId) {

        public PersistResult(Map<RepositoryType, String> prePersistHeads, Map<RepositoryType, String> postPersistHeads, String persistedProblemStatement, String persistedTitle,
                String repositoryBranch) {
            this(prePersistHeads, postPersistHeads, persistedProblemStatement, persistedTitle, repositoryBranch, true, null);
        }
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome) {
        return persist(exercise, user, outcome, exercise.getProblemStatement(), exercise.getTitle());
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle) {
        return persist(exercise, user, outcome, expectedProblemStatement, expectedTitle, () -> true);
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle,
            BooleanSupplier stillOwnsMutationSlot) {
        return persistWithCommitMessage(exercise, user, outcome, expectedProblemStatement, expectedTitle, GenerationMode.GENERATE, "Generate exercise with Hyperion",
                stillOwnsMutationSlot);
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle, String jobId,
            BooleanSupplier stillOwnsMutationSlot) {
        return persist(exercise, user, outcome, expectedProblemStatement, expectedTitle, jobId, GenerationMode.GENERATE, stillOwnsMutationSlot);
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle, String jobId,
            GenerationMode mode, BooleanSupplier stillOwnsMutationSlot) {
        return persist(exercise, user, outcome, expectedProblemStatement, expectedTitle, jobId, mode, stillOwnsMutationSlot, () -> {
        });
    }

    public PersistResult persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle, String jobId,
            GenerationMode mode, BooleanSupplier stillOwnsMutationSlot, Runnable beforeDurableMutation) {
        String operation = mode == GenerationMode.ADAPT ? "Adapt" : "Generate";
        return persistWithCommitMessage(exercise, user, outcome, expectedProblemStatement, expectedTitle, mode, operation + " exercise with Hyperion (" + jobId + ")",
                stillOwnsMutationSlot, beforeDurableMutation);
    }

    private PersistResult persistWithCommitMessage(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle,
            GenerationMode mode, String commitMessage, BooleanSupplier stillOwnsMutationSlot) {
        return persistWithCommitMessage(exercise, user, outcome, expectedProblemStatement, expectedTitle, mode, commitMessage, stillOwnsMutationSlot, () -> {
        });
    }

    private PersistResult persistWithCommitMessage(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String expectedProblemStatement, String expectedTitle,
            GenerationMode mode, String commitMessage, BooleanSupplier stillOwnsMutationSlot, Runnable beforeDurableMutation) {
        if (!outcome.isMechanicallyVerified()) {
            throw new IllegalArgumentException("Refusing to persist an exercise that did not pass mechanical verification");
        }
        requirePersistenceInputsSafe(outcome);
        Runnable beforeFirstDurableMutation = oneShot(beforeDurableMutation);
        String repositoryBranch = repositoryBranch(exercise);
        // Capture each repository's pre-persist HEAD before writing it, so a later failure can revert the already-committed repositories to a consistent pre-generation state.
        Map<RepositoryType, String> prePersistHashes = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, String> postPersistHashes = new EnumMap<>(RepositoryType.class);
        List<RepositoryType> committed = new ArrayList<>();
        String testsCommitHash = null;
        TestsBuildSignal testsBuildSignal = null;
        String producedProblemStatement = outcome.producedProblemStatement();
        String originalProblemStatement = expectedProblemStatement;
        String originalTitle = expectedTitle;
        String targetTitle = exercise.getTitle();
        boolean shouldSaveProblemStatement = !producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement());
        // From-scratch only (statement was blank): reconcile the lean AI create page's brief-derived placeholder title to the agent's own H1. An adapt run keeps the
        // instructor's title.
        if (mode == GenerationMode.GENERATE && shouldSaveProblemStatement && (exercise.getProblemStatement() == null || exercise.getProblemStatement().isBlank())) {
            String generatedTitle = extractTitleFromH1(producedProblemStatement);
            if (generatedTitle != null) {
                targetTitle = generatedTitle;
            }
        }
        assertStillOwnsMutationSlot(stillOwnsMutationSlot);
        assertMetadataUnchangedSinceJobStart(exercise, originalProblemStatement, originalTitle);
        try {
            for (RepositoryType repositoryType : PERSIST_ORDER) {
                assertStillOwnsMutationSlot(stillOwnsMutationSlot);
                Map<String, String> producedFiles = outcome.producedFiles(repositoryType);
                if (repositoryType == RepositoryType.TESTS && producedFiles != null && !producedFiles.isEmpty()) {
                    testsBuildSignal = prepareTestsBuildSignal(exercise, null);
                }
                String commitHash = commitRepository(exercise, user, repositoryType, producedFiles, outcome.seedRepositoryHeads().get(repositoryType), repositoryBranch, mode,
                        commitMessage, prePersistHashes, postPersistHashes, stillOwnsMutationSlot, beforeFirstDurableMutation);
                if (commitHash != null) {
                    committed.add(repositoryType);
                    if (repositoryType == RepositoryType.TESTS) {
                        testsCommitHash = commitHash;
                        testsBuildSignal = testsBuildSignal.withTestsCommitHash(commitHash);
                    }
                }
            }
            assertRepositoryHeadsStillMatch(exercise, repositoryBranch, outcome.seedRepositoryHeads(), postPersistHashes);
        }
        catch (RuntimeException e) {
            // Compensation: revert the already-committed repositories to their captured pre-persist commit so no publishable half-generated tree survives on the default branch.
            boolean fullyReverted = compensateAndResyncBaseline(exercise, user, repositoryBranch, committed, prePersistHashes, postPersistHashes);
            // An ambiguous push (the local commit call reported failure, but the remote branch had already moved and reconciliation could not prove it back out) is never
            // compensated by compensateAndResyncBaseline: that helper only walks `committed`, which this repository was never added to because commitRepository threw before
            // returning a commit hash. Treat it conservatively: the live exercise MAY have changed, and the best-known (not confirmed) commit hash is surfaced rather than
            // silently reporting an empty, falsely-reassuring commit map.
            boolean ambiguousRemoteState = e instanceof AmbiguousCommitFailure;
            boolean liveExerciseChanged = ambiguousRemoteState || !fullyReverted;
            // EnumMap's Map-argument constructor throws IllegalArgumentException on an empty source map (it cannot infer the key type), so build it directly with the enum class
            // and populate conditionally instead of trying to wrap a possibly-empty map.
            Map<RepositoryType, String> reportedCommits = new EnumMap<>(RepositoryType.class);
            if (!fullyReverted) {
                reportedCommits.putAll(postPersistHashes);
            }
            String ambiguityNote = "";
            if (ambiguousRemoteState) {
                AmbiguousCommitFailure ambiguousFailure = (AmbiguousCommitFailure) e;
                if (ambiguousFailure.possiblyPushedCommitHash() != null) {
                    reportedCommits.put(ambiguousFailure.repositoryType(), ambiguousFailure.possiblyPushedCommitHash());
                }
                ambiguityNote = " The remote state of the " + ambiguousFailure.repositoryType()
                        + " repository could not be verified after its push reported failure; treat the exercise as changed until a maintainer confirms the actual remote commit.";
            }
            String state = fullyReverted && !ambiguousRemoteState ? "the already-committed repositories were reverted to their previous state"
                    : "compensation could not fully revert every repository (" + committed + "); manual review is required before using the exercise";
            throw new GenerationIncompleteException("Saving the generated exercise failed after committing " + committed
                    + "; the generation is INCOMPLETE and must not be published. " + state + ambiguityNote + ". Cause: " + e.getMessage(), e, liveExerciseChanged,
                    Map.copyOf(reportedCommits));
        }

        if (shouldSaveProblemStatement) {
            try {
                assertStillOwnsMutationSlot(stillOwnsMutationSlot);
                // The metadata write and the task rebuild it drives are one narrow @Transactional database unit (see ProblemStatementMetadataUpdateService): either both land or
                // neither does, so a task-rebuild failure never leaves a committed problem statement paired with a half-rebuilt task set.
                saveProblemStatementIfUnchanged(exercise, producedProblemStatement, targetTitle, originalProblemStatement, originalTitle, beforeFirstDurableMutation);
            }
            catch (RuntimeException e) {
                throw new GenerationIncompleteException("Saving the generated exercise failed while updating the problem statement after committing " + committed
                        + "; the mechanically verified repository commits remain saved for instructor review, but metadata finalization is INCOMPLETE. Cause: " + e.getMessage(), e,
                        true, postPersistHashes);
            }
        }

        // Internal LocalVC pushes bypass the HTTP/SSH post-receive hook, so finalization explicitly triggers the canonical tests build before waiting for test-case sync.
        String expectedFinalProblemStatement = shouldSaveProblemStatement ? producedProblemStatement.trim() : originalProblemStatement;
        String expectedFinalTitle = shouldSaveProblemStatement ? targetTitle : originalTitle;
        Map<RepositoryType, String> persistedRepositoryHeads = new EnumMap<>(RepositoryType.class);
        persistedRepositoryHeads.putAll(outcome.seedRepositoryHeads());
        persistedRepositoryHeads.putAll(postPersistHashes);
        AtomicReference<MetadataSnapshot> persistedMetadata = new AtomicReference<>();
        Runnable finalizationGuard = () -> {
            assertStillOwnsMutationSlot(stillOwnsMutationSlot);
            assertRepositoryHeadsStillMatch(exercise, repositoryBranch, outcome.seedRepositoryHeads(), postPersistHashes);
            persistedMetadata.set(assertMetadataMatches(exercise, expectedFinalProblemStatement, expectedFinalTitle));
        };
        Long savedExerciseVersionId;
        try {
            savedExerciseVersionId = syncTestCasesAndRecordVersion(exercise, user, testsCommitHash != null ? testsBuildSignal : null, true, finalizationGuard,
                    persistedRepositoryHeads, outcome.testPlanJson());
        }
        catch (RuntimeException e) {
            throw new GenerationIncompleteException("Saving the generated exercise failed while recording the exercise version after committing " + committed
                    + "; the mechanically verified repository and metadata changes remain saved for instructor review, but finalization is INCOMPLETE. Cause: " + e.getMessage(), e,
                    true, postPersistHashes);
        }
        log.info("Persisted generated exercise {} after test-case synchronisation completed", exercise.getId());
        prePersistHashes.keySet().retainAll(postPersistHashes.keySet());
        MetadataSnapshot metadata = persistedMetadata.get();
        return new PersistResult(nonNullCopy(prePersistHashes), nonNullCopy(postPersistHashes), metadata.problemStatement(), metadata.title(), repositoryBranch,
                shouldSaveProblemStatement, savedExerciseVersionId);
    }

    private static void requirePersistenceInputsSafe(GenerationOutcome outcome) {
        String producedProblemStatement = outcome.producedProblemStatement();
        SECRET_MATERIAL_POLICY.requireSafe("persistence/problem-statement.md",
                producedProblemStatement == null ? new byte[0] : producedProblemStatement.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.PERSISTENCE);
        for (RepositoryType repositoryType : PERSIST_ORDER) {
            Map<String, String> producedFiles = outcome.producedFiles(repositoryType);
            if (producedFiles == null) {
                continue;
            }
            for (Map.Entry<String, String> file : producedFiles.entrySet()) {
                String content = file.getValue();
                SECRET_MATERIAL_POLICY.requireSafe("persistence/" + repositoryType.name().toLowerCase(java.util.Locale.ROOT) + "/" + file.getKey(),
                        content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.PERSISTENCE);
            }
        }
    }

    private boolean compensateAndResyncBaseline(ProgrammingExercise exercise, User user, String repositoryBranch, List<RepositoryType> committed,
            Map<RepositoryType, String> prePersistHashes, Map<RepositoryType, String> postPersistHashes) {
        boolean testsCommitted = committed.contains(RepositoryType.TESTS);
        TestsBuildSignal compensationSignal = null;
        boolean signalPrepared = true;
        if (testsCommitted) {
            try {
                compensationSignal = prepareTestsBuildSignal(exercise, prePersistHashes.get(RepositoryType.TESTS));
            }
            catch (RuntimeException e) {
                signalPrepared = false;
                log.error("Could not capture the baseline test-build signal before compensating exercise {}; grading must be reviewed manually", exercise.getId(), e);
            }
        }
        boolean repositoriesReverted = compensate(exercise, repositoryBranch, committed, prePersistHashes, postPersistHashes);
        return repositoriesReverted && (!testsCommitted || (signalPrepared && resyncBaselineTestsAfterCompensation(exercise, user, compensationSignal)));
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

    private static Runnable oneShot(Runnable action) {
        AtomicBoolean started = new AtomicBoolean(false);
        return () -> {
            if (started.compareAndSet(false, true)) {
                action.run();
            }
        };
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
        return resyncAfterRevertWithSignal(exercise, user, testsCommitHash == null ? null : prepareTestsBuildSignal(exercise, testsCommitHash), problemStatement, title,
                expectedProblemStatement, expectedTitle);
    }

    boolean resyncAfterRevertWithSignal(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal, String problemStatement, String title,
            String expectedProblemStatement, String expectedTitle) {
        return resyncAfterRevertWithSignal(exercise, user, testsBuildSignal, problemStatement, title, expectedProblemStatement, expectedTitle, Map.of());
    }

    boolean resyncAfterRevertWithSignal(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal, String problemStatement, String title,
            String expectedProblemStatement, String expectedTitle, Map<RepositoryType, String> repositoryCommitIds) {
        if (!restoreProblemStatementIfUnchanged(exercise, problemStatement, title, expectedProblemStatement, expectedTitle)) {
            return false;
        }
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsBuildSignal, true, () -> {
            }, repositoryCommitIds);
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

    private Long syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal, boolean failOnFinalizationFailure) {
        return syncTestCasesAndRecordVersion(exercise, user, testsBuildSignal, failOnFinalizationFailure, () -> {
        }, Map.of());
    }

    /**
     * @return the id of the {@code ExerciseVersion} row created for this save, or {@code null} when no new version was recorded (a tolerated failure with
     *         {@code failOnFinalizationFailure == false}, or the version service judged the snapshot unchanged from the previous version)
     */
    private Long syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal, boolean failOnFinalizationFailure,
            Runnable finalizationGuard, Map<RepositoryType, String> repositoryCommitIds) {
        return syncTestCasesAndRecordVersion(exercise, user, testsBuildSignal, failOnFinalizationFailure, finalizationGuard, repositoryCommitIds, null);
    }

    private Long syncTestCasesAndRecordVersion(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal, boolean failOnFinalizationFailure,
            Runnable finalizationGuard, Map<RepositoryType, String> repositoryCommitIds, @Nullable String testPlanJson) {
        finalizationGuard.run();
        if (testsBuildSignal != null) {
            triggerTestsBuild(exercise, testsBuildSignal);
            zeroWeightBuildGateTestCases(exercise.getId(), testsBuildSignal, failOnFinalizationFailure);
            // Inside the guard, after the sync: the weight/visibility write is a durable mutation and must not happen once this job no longer owns the exercise.
            finalizationGuard.run();
            applyGeneratedTestPlan(exercise, testPlanJson);
        }
        finalizationGuard.run();
        try {
            return repositoryCommitIds.isEmpty() ? exerciseVersionService.createExerciseVersionOrThrow(exercise, user)
                    : exerciseVersionService.createExerciseVersionOrThrow(exercise, user, repositoryCommitIds);
        }
        catch (RuntimeException e) {
            if (failOnFinalizationFailure) {
                throw e;
            }
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
            return null;
        }
    }

    private boolean resyncBaselineTestsAfterCompensation(ProgrammingExercise exercise, User user, TestsBuildSignal testsBuildSignal) {
        try {
            syncTestCasesAndRecordVersion(exercise, user, testsBuildSignal, true);
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
            // The metadata write and the task rebuild it drives are one narrow @Transactional database unit, so a task-rebuild failure here never leaves a committed problem
            // statement paired with a half-rebuilt task set.
            int updatedRows = problemStatementMetadataUpdateService.updateProblemStatementAndTasks(exercise, problemStatement, title, current.problemStatement(), current.title());
            if (updatedRows != 1) {
                log.error("Could not restore the previous problem statement/title for exercise {} because it changed after the adaptation revert started", exercise.getId());
                return false;
            }
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

    private void saveProblemStatementIfUnchanged(ProgrammingExercise exercise, String problemStatement, String title, String expectedProblemStatement, String expectedTitle,
            Runnable beforeFirstDurableMutation) {
        String trimmedProblemStatement = problemStatement.trim();
        MetadataSnapshot current = currentMetadataMatchingSafeSave(exercise, expectedProblemStatement, expectedTitle, trimmedProblemStatement, title).orElseThrow(
                () -> new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits"));
        beforeFirstDurableMutation.run();
        // Narrow @Transactional unit (metadata CAS write + task rebuild only, no Git/CI inside): a task-rebuild failure rolls the metadata write back too, instead of leaving a
        // committed statement paired with a half-rebuilt task set.
        int updatedRows = problemStatementMetadataUpdateService.updateProblemStatementAndTasks(exercise, trimmedProblemStatement, title, current.problemStatement(),
                current.title());
        if (updatedRows != 1) {
            throw new IllegalStateException("The problem statement/title changed while Hyperion was saving the generated exercise; refusing to overwrite manual edits");
        }
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

    private String commitRepository(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles, String seedHead,
            String repositoryBranch, GenerationMode mode, String commitMessage, Map<RepositoryType, String> prePersistHashes, Map<RepositoryType, String> postPersistHashes,
            BooleanSupplier stillOwnsMutationSlot, Runnable beforeFirstDurableMutation) {
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
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles, mode);
            if (gitService.isWorkingCopyClean(repository)) {
                return null;
            }
            gitService.stageAllChanges(repository);
            String postHash = gitService.commitStagedChanges(repository, commitMessage, user);
            // The draft/ownership guard is intentionally adjacent to the external mutation. If eligibility changes while the push is in flight, the post-push check throws into
            // the existing reconciliation path, which resets this exact leased commit before the outer persistence compensation handles earlier repositories.
            assertStillOwnsMutationSlot(stillOwnsMutationSlot);
            beforeFirstDurableMutation.run();
            gitService.pushCommitWithLease(repository, postHash, repositoryBranch, prePersistHead);
            assertStillOwnsMutationSlot(stillOwnsMutationSlot);
            if (postHash != null) {
                postPersistHashes.put(repositoryType, postHash);
            }
            return postHash;
        }
        catch (Exception e) {
            CommitReconciliation reconciliation = CommitReconciliation.NOT_AMBIGUOUS;
            if (repository != null) {
                reconciliation = reconcileAmbiguousCommitFailure(exercise, repositoryType, uri, repository, repositoryBranch, prePersistHead, e);
                try {
                    gitService.resetToOriginHead(repository);
                }
                catch (RuntimeException resetFailure) {
                    log.warn("Could not reset the {} repository working copy after a failed Hyperion commit for exercise {}: {}", repositoryType, exercise.getId(),
                            resetFailure.getMessage());
                }
            }
            if (reconciliation.manualReviewRequired()) {
                throw new AmbiguousCommitFailure("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage()
                        + " The remote repository state is ambiguous and requires manual review.", e, repositoryType, reconciliation.possiblyPushedCommitHash());
            }
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            deleteTemporaryCheckout(temporaryCheckout);
        }
    }

    /**
     * Thrown instead of a plain {@link IllegalStateException} when a repository push fails in a way that cannot be conclusively resolved: the local commit succeeded, but the
     * remote branch could not be confirmed to still be at its pre-persist state, so whether the push actually landed remotely is unknown. Carries the repository and the
     * best-known (unconfirmed) commit hash so the caller can report the live exercise as conservatively changed and surface a concrete lead for manual review, instead of
     * collapsing to an empty, falsely-reassuring commit map.
     */
    static final class AmbiguousCommitFailure extends IllegalStateException {

        private final RepositoryType repositoryType;

        private final String possiblyPushedCommitHash;

        AmbiguousCommitFailure(String message, Throwable cause, RepositoryType repositoryType, String possiblyPushedCommitHash) {
            super(message, cause);
            this.repositoryType = repositoryType;
            this.possiblyPushedCommitHash = possiblyPushedCommitHash;
        }

        RepositoryType repositoryType() {
            return repositoryType;
        }

        String possiblyPushedCommitHash() {
            return possiblyPushedCommitHash;
        }
    }

    private record CommitReconciliation(boolean manualReviewRequired, String possiblyPushedCommitHash) {

        static final CommitReconciliation NOT_AMBIGUOUS = new CommitReconciliation(false, null);
    }

    private CommitReconciliation reconcileAmbiguousCommitFailure(ProgrammingExercise exercise, RepositoryType repositoryType, LocalVCRepositoryUri uri, Repository repository,
            String repositoryBranch, String prePersistHead, Exception commitFailure) {
        if (prePersistHead == null) {
            return CommitReconciliation.NOT_AMBIGUOUS;
        }
        String localHead = null;
        try {
            localHead = gitService.getLocalHeadHash(repository);
            if (localHead == null || prePersistHead.equals(localHead)) {
                return CommitReconciliation.NOT_AMBIGUOUS;
            }
            String remoteHead = gitService.getLastCommitHash(uri, repositoryBranch);
            if (!localHead.equals(remoteHead)) {
                if (!prePersistHead.equals(remoteHead)) {
                    IllegalStateException ambiguousFailure = new IllegalStateException("The remote branch advanced after the failed Hyperion commit; manual review is required");
                    commitFailure.addSuppressed(ambiguousFailure);
                    log.error("Could not determine whether the failed {} repository commit for exercise {} remains in remote history; current remote HEAD is {}", repositoryType,
                            exercise.getId(), remoteHead);
                    return new CommitReconciliation(true, localHead);
                }
                return CommitReconciliation.NOT_AMBIGUOUS;
            }
            gitService.resetToCommitAndForcePush(repository, prePersistHead, localHead, repositoryBranch);
            log.info("Reverted the {} repository of exercise {} after its commit was pushed but reported as failed", repositoryType, exercise.getId());
            return CommitReconciliation.NOT_AMBIGUOUS;
        }
        catch (Exception reconciliationFailure) {
            commitFailure.addSuppressed(reconciliationFailure);
            log.error("Could not reconcile the failed {} repository commit for exercise {}; the remote branch may contain an unrecorded Hyperion commit", repositoryType,
                    exercise.getId(), reconciliationFailure);
            return new CommitReconciliation(true, localHead);
        }
    }

    private static void deleteTemporaryCheckout(Path path) {
        if (path != null && !FileUtils.deleteQuietly(path.toFile())) {
            log.warn("Could not delete temporary Hyperion persistence checkout {}", path);
        }
    }

    private void mirrorProducedFilesIntoWorkingCopy(ProgrammingExercise exercise, Repository repository, RepositoryType repositoryType, Map<String, String> producedFiles,
            GenerationMode mode) throws IOException {
        Map<String, String> safeProducedFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
            String safePath = safeRepositoryPath(repository, entry.getKey());
            if (safeProducedFiles.putIfAbsent(safePath, entry.getValue()) != null) {
                throw new IllegalArgumentException("Multiple produced paths resolve to " + safePath);
            }
        }
        assertNoSymbolicLinks(repository);
        Map<String, String> persistableFiles = safeProducedFiles;
        deleteOrphanedFiles(repository, repositoryType, persistableFiles.keySet(), mode);
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

    private void deleteOrphanedFiles(Repository repository, RepositoryType repositoryType, Set<String> producedPaths, GenerationMode mode) {
        Map<String, FileType> trackedFiles = repositoryService.getFiles(repository);
        Path repositoryRoot = repository.getLocalPath();
        for (Map.Entry<String, FileType> tracked : trackedFiles.entrySet()) {
            String path = tracked.getKey();
            if (tracked.getValue() != FileType.FILE || producedPaths.contains(path)) {
                continue;
            }
            // Preserve binary scaffolding that generation never managed. A from-scratch generation deliberately cleared its source/test roots before verification, so stale binary
            // files in those roots must also be removed or persistence would resurrect artifacts that were never verified.
            if (repositoryRoot != null && BinaryContent.isBinaryFile(repositoryRoot.resolve(path))
                    && (mode == GenerationMode.ADAPT || !isGeneratedArtifactPath(repositoryType, path))) {
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

    private static boolean isGeneratedArtifactPath(RepositoryType repositoryType, String path) {
        return switch (repositoryType) {
            case TEMPLATE, SOLUTION -> path.startsWith("src/");
            case TESTS -> path.startsWith("test/") || path.startsWith("behavior/test/") || path.startsWith("structural/test/");
            default -> false;
        };
    }

    record TestsBuildSignal(long solutionParticipationId, String testsCommitHash, Long baselineLatestResultId) {

        TestsBuildSignal withTestsCommitHash(String testsCommitHash) {
            return new TestsBuildSignal(solutionParticipationId, testsCommitHash, baselineLatestResultId);
        }
    }

    TestsBuildSignal prepareTestsBuildSignal(ProgrammingExercise exercise, String commitHash) {
        ProgrammingExerciseParticipation solutionParticipation = participationService.retrieveSolutionParticipation(exercise);
        long solutionParticipationId = solutionParticipation.getId();
        Long baselineLatestResultId = resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(solutionParticipationId).map(Result::getId).orElse(null);
        return new TestsBuildSignal(solutionParticipationId, commitHash, baselineLatestResultId);
    }

    TestsBuildSignal triggerTestsBuild(ProgrammingExercise exercise, TestsBuildSignal signal) {
        try {
            ProgrammingExerciseParticipation solutionParticipation = participationService.retrieveSolutionParticipation(exercise);
            programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), signal.testsCommitHash());
            continuousIntegrationTriggerService.triggerRestrictedBuild(solutionParticipation, signal.testsCommitHash(), RepositoryType.TESTS);
            return signal;
        }
        catch (ContinuousIntegrationException e) {
            throw new IllegalStateException("Failed to trigger the test-case-syncing build for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
        catch (RuntimeException e) {
            throw new IllegalStateException("Unexpected error triggering the test-case-syncing build for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Applies the TESTS stage's grading plan ({@code test-plan.json}) to the freshly synchronized test cases: per-test weights (the specification's core-vs-edge tiers) and
     * {@code AFTER_DUE_DATE} visibility for hidden variants. Runs only on the main save path, AFTER {@link #syncTestCasesAndRecordVersion} has awaited the test-case sync, so the
     * cases exist. Strictly advisory: the plan was already gate-validated in the sandbox, and a failure here (a stale plan after a repair attempt, malformed JSON) degrades to
     * Artemis' grading defaults for EVERY case rather than failing a save that is otherwise complete — see the all-or-nothing rule below for why partial application is worse.
     */
    private void applyGeneratedTestPlan(ProgrammingExercise exercise, @Nullable String testPlanJson) {
        if (testPlanJson == null || testPlanJson.isBlank()) {
            return;
        }
        try {
            GeneratedTestPlan plan = GeneratedTestPlan.parse(testPlanJson);
            Map<String, ProgrammingExerciseTestCase> byName = testCaseRepository.findByExerciseId(exercise.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(ProgrammingExerciseTestCase::getTestName, testCase -> testCase, (first, second) -> first));
            // ALL-OR-NOTHING. The plan is written once and survives repair attempts, so a later attempt that renames or drops a test leaves a plan that only PARTLY describes
            // the saved tests. Applying such a plan is worse than applying none: the weights land, but a renamed hidden variant keeps Artemis' default ALWAYS visibility and the
            // overfit probe ships published, contradicting the specification the instructor reads. Skip wholesale and let every case keep the documented defaults.
            List<String> unresolvedNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(name -> !byName.containsKey(name)).toList();
            if (!unresolvedNames.isEmpty()) {
                log.warn(
                        "Not applying the generated test plan for exercise {}: it names {} test case(s) that the saved tests do not contain ({}), so it describes a different "
                                + "version of the tests. Grading keeps Artemis' defaults (weight 1, visible) for every case.",
                        exercise.getId(), unresolvedNames.size(), unresolvedNames);
                return;
            }
            List<ProgrammingExerciseTestCase> changed = new ArrayList<>();
            for (GeneratedTestPlan.Entry entry : plan.tests()) {
                ProgrammingExerciseTestCase testCase = byName.get(entry.name());
                if (BuildGateTestNames.isBuildGate(testCase.getTestName())) {
                    // Build gates are zero-weighted for oracle parity and must stay that way regardless of what the plan says.
                    continue;
                }
                testCase.setWeight(entry.weight());
                testCase.setVisibility("AFTER_DUE_DATE".equals(entry.visibility()) ? Visibility.AFTER_DUE_DATE : Visibility.ALWAYS);
                changed.add(testCase);
            }
            if (!changed.isEmpty()) {
                testCaseRepository.saveAll(changed);
            }
            log.info("Applied generated test plan to exercise {}: {} test case(s) weighted, {} hidden until the due date", exercise.getId(), changed.size(),
                    changed.stream().filter(testCase -> testCase.getVisibility() == Visibility.AFTER_DUE_DATE).count());
        }
        catch (RuntimeException e) {
            log.warn("Could not apply the generated test plan for exercise {} (grading falls back to defaults, weight 1 and visible): {}", exercise.getId(), e.getMessage());
        }
    }

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

    private Set<ProgrammingExerciseTestCase> awaitBuildProcessedTestCaseSet(long exerciseId, TestsBuildSignal signal, boolean failOnTimeout) throws InterruptedException {
        long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(signal.solutionParticipationId(), signal.testsCommitHash(),
                    signal.baselineLatestResultId())) {
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
