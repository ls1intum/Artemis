package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
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
 * statement, record an exercise version), the same path a manual instructor edit uses. Runs only after {@link AuthoritativeVerificationService} has accepted the exercise.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
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

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ProgrammingExerciseCreationUpdateService creationUpdateService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.participationService = participationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.creationUpdateService = creationUpdateService;
        this.exerciseVersionService = exerciseVersionService;
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
    }

    /** Prefix of the isolated branch a recovery draft is diverted to for an adapt target; the job id is appended so concurrent/repeated runs never collide on the ref. */
    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

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
     *         Fails CLOSED to {@code true} when a repository cannot be inspected, so an inspection error never lets a failing draft overwrite the live exercise.
     */
    private boolean anyRepositoryHasContent(ProgrammingExercise exercise) {
        for (RepositoryType repositoryType : new RepositoryType[] { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS }) {
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
     * Writes the produced files and commits them to an ISOLATED branch (never the default branch), pushing only that branch. Uses the same orphan-mirroring as the default-branch
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
     *
     * @param exercise the exercise to persist into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the accepted generation outcome holding the produced files
     */
    public void persist(ProgrammingExercise exercise, User user, GenerationOutcome outcome) {
        // Commit tests last so the test-triggered build sees the final solution. The three repositories cannot commit atomically, so a mid-sequence failure reports which were
        // already written rather than claiming nothing changed.
        List<RepositoryType> committed = new ArrayList<>();
        String testsCommitHash;
        try {
            if (commitRepository(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE)) != null) {
                committed.add(RepositoryType.TEMPLATE);
            }
            if (commitRepository(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION)) != null) {
                committed.add(RepositoryType.SOLUTION);
            }
            testsCommitHash = commitRepository(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS));
            if (testsCommitHash != null) {
                committed.add(RepositoryType.TESTS);
            }
        }
        catch (RuntimeException e) {
            throw new IllegalStateException("Saving the generated exercise failed after committing " + committed + "; the remaining repositories were not updated. The exercise is "
                    + "partially saved — review it before using it. Cause: " + e.getMessage(), e);
        }

        // Update the problem statement if the agent changed it.
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

        // Trigger the canonical CI build for the tests; its result drives test-case synchronisation and task binding asynchronously, as a manual tests-repo edit does.
        if (testsCommitHash != null) {
            // Snapshot the count BEFORE triggering so zeroWeightBuildGateTestCases can wait past a stale/partial set for the complete sync.
            int testCaseCountBeforeBuild = testCaseRepository.findByExerciseId(exercise.getId()).size();
            triggerTestsBuild(exercise, testsCommitHash);
            zeroWeightBuildGateTestCases(exercise.getId(), testCaseCountBeforeBuild);
        }

        // Record a new exercise version, snapshotting the committed state and refreshing search indexing / notifying open editors.
        try {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }
        catch (RuntimeException e) {
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
        log.info("Persisted generated exercise {} (test-case synchronisation will complete asynchronously via CI)", exercise.getId());
    }

    /**
     * Writes the produced files and commits, making the committed tree EXACTLY mirror the sandbox-final {@code producedFiles} (see {@link #deleteOrphanedFiles} for why). A commit
     * failure is propagated so the caller does not report success after only some repositories were written.
     *
     * @param exercise       the exercise being persisted
     * @param user           the commit author
     * @param repositoryType the repository to write
     * @param producedFiles  the files to commit (the sandbox-final tree the oracle validated)
     * @return the new commit hash, or {@code null} when there was nothing to commit
     */
    private String commitRepository(ProgrammingExercise exercise, User user, RepositoryType repositoryType, Map<String, String> producedFiles) {
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
            mirrorProducedFilesIntoWorkingCopy(exercise, repository, repositoryType, producedFiles);
            repositoryService.commitChanges(repository, user);
            return gitService.getLastCommitHash(uri);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Makes the working copy EXACTLY mirror the sandbox-final {@code producedFiles}: removes the files the agent did not produce (see {@link #deleteOrphanedFiles}) then writes
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
     * Deletes every tracked file the agent did NOT produce, so the committed tree mirrors the sandbox-final state rather than overlaying onto the scaffolded sample (which would
     * orphan the sample's test sources / structure oracle into real grading). Harness/manifest files (graded verbatim, immutable by contract) are NEVER deleted, so a partial
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

    private void triggerTestsBuild(ProgrammingExercise exercise, String commitHash) {
        try {
            ProgrammingExerciseParticipation solutionParticipation = participationService.retrieveSolutionParticipation(exercise);
            programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
            continuousIntegrationTriggerService.triggerBuild(solutionParticipation, commitHash, RepositoryType.TESTS);
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to trigger the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
        }
        catch (RuntimeException e) {
            log.warn("Unexpected error triggering the test-case-syncing build for exercise {}: {}", exercise.getId(), e.getMessage());
        }
    }

    /** Exercise title column length; an H1 reconciled from a generated statement is capped to this. */
    private static final int MAX_TITLE_LENGTH = 255;

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration TEST_CASE_SYNC_POLL = Duration.ofSeconds(3);

    // Mutable so setTestCaseSyncTimingForTests can shrink them; defaults to the constants above.
    private Duration testCaseSyncTimeout = TEST_CASE_SYNC_TIMEOUT;

    private Duration testCaseSyncPoll = TEST_CASE_SYNC_POLL;

    /**
     * Test seam: shrink the bounded test-case-sync wait so unit tests exercise the baseline-settle logic without sleeping for seconds.
     *
     * @param timeout the maximum time to wait for the complete test-case set
     * @param poll    the interval between polls
     */
    void setTestCaseSyncTimingForTests(Duration timeout, Duration poll) {
        this.testCaseSyncTimeout = timeout;
        this.testCaseSyncPoll = poll;
    }

    /**
     * For C/C++ FACT exercises the synced report includes build-gate cases (CompileSort/TestConfigure) that PASS on the compiling template; the differential oracle exempts them
     * ({@link BuildGateTestNames}) but production grades EVERY case, so without this a student submitting the untouched template would score above 0%. Waits (bounded) for the
     * complete set, then zero-weights the build gates to match the oracle. Best-effort, idempotent, a no-op for languages without build-gate cases.
     *
     * @param exerciseId               the generated exercise whose build-gate test cases should be excluded from grading
     * @param testCaseCountBeforeBuild the test-case count observed before the tests-build was triggered (the stale/partial baseline to wait past)
     */
    private void zeroWeightBuildGateTestCases(long exerciseId, int testCaseCountBeforeBuild) {
        try {
            long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
            Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exerciseId);
            // Wait for the complete re-sync: the count must move off the pre-build baseline (setup may have left a partial set) AND then settle, so a gate that appears only in the
            // full sync is not missed.
            int previousCount = -1;
            while (System.nanoTime() < deadline && (testCases.size() == testCaseCountBeforeBuild || testCases.size() != previousCount)) {
                previousCount = testCases.size();
                Thread.sleep(testCaseSyncPoll.toMillis());
                testCases = testCaseRepository.findByExerciseId(exerciseId);
            }
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
     * Replaces typographic punctuation the model leaks (Unicode dashes {@code U+2010..U+2015}, non-breaking/narrow spaces, smart quotes, ellipsis) with ASCII equivalents. Applied
     * to
     * the problem statement and every generated source file. The substitution is safe: code spans are ASCII and no generation-capable language needs these characters in a literal.
     *
     * @param problemStatement the produced problem statement (may be {@code null})
     * @return the statement normalised to ASCII, or {@code null} if the input was {@code null}
     */
    static String normalizeTypography(String problemStatement) {
        if (problemStatement == null) {
            return null;
        }
        return problemStatement.replaceAll("[\u2010-\u2015]", "-").replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"').replace("\u2026", "...");
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
