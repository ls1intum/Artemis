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
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
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
 * Persists a verified-complete generated exercise through Artemis's normal pipeline.
 * <p>
 * This runs only after the {@link AuthoritativeVerificationService} has accepted the exercise. For each changed repository it writes the produced files into the working copy and
 * commits;
 * it then triggers the canonical CI build for the tests so the platform's own pipeline discovers and synchronises the
 * {@link de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase}
 * set (the same path a manual instructor edit uses), updates the problem statement if it changed, and records a new exercise version (which refreshes search indexing and
 * notifies open editors).
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

    /**
     * Prefix of the isolated branch a recovery draft is committed to when the target is an adapt of an already-working exercise. The job id is appended so concurrent runs and
     * repeated runs never collide on the same ref, and an instructor can correlate the branch with the run that produced it.
     */
    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

    /**
     * The result of persisting a non-accepted recovery draft.
     *
     * @param liveExerciseUntouched {@code true} if the live default branch was left byte-identical (adapt target); {@code false} if the draft was committed to the default branch
     *                                  (from-scratch target)
     * @param draftBranch           the isolated branch the draft was pushed to when {@code liveExerciseUntouched} is {@code true}; {@code null} otherwise
     */
    public record RecoveryPersistResult(boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Persists a non-accepted (recovered) generation draft WITHOUT ever regressing a previously-working exercise. A from-scratch target (all repositories empty) is committed to
     * the
     * default branch exactly as {@link #persist} does; an adapt target (any repository already carries committed content) is left byte-identical and the draft is diverted to an
     * isolated branch with no CI build, so a broken draft can never replace a working exercise (the recorded {@link ExerciseVersion} is a read-only snapshot, not a git revert).
     * The
     * decision is taken atomically across all three repositories so a partial commit can never half-regress the exercise.
     *
     * @param exercise the exercise to persist the draft into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the non-accepted generation outcome holding the produced files
     * @param jobId    the generation job id, used to name the isolated draft branch so concurrent/repeated runs never collide
     * @return the result describing whether the live exercise was left untouched and, if so, the isolated branch the draft was pushed to
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        // Decide adapt-vs-from-scratch before writing anything, so a later commit failure can never leave the exercise half-overwritten.
        boolean adaptTarget = anyRepositoryHasContent(exercise);
        if (!adaptTarget) {
            // From-scratch: commit the draft to the default branch exactly as an accepted run does.
            persist(exercise, user, outcome);
            return new RecoveryPersistResult(false, null);
        }
        // Adapt: divert every repository's draft to an isolated branch and leave the live default branch byte-identical.
        String draftBranch = RECOVERY_DRAFT_BRANCH_PREFIX + jobId;
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS), draftBranch);
        // No triggerTestsBuild and no createExerciseVersion: the live exercise did not change, so it must not be re-graded or re-versioned against the broken draft.
        log.info("Recovered adapt draft for exercise {} onto isolated branch {} (the live exercise was left untouched)", exercise.getId(), draftBranch);
        return new RecoveryPersistResult(true, draftBranch);
    }

    /**
     * @return {@code true} if any of the template/solution/tests repositories already tracks at least one non-{@code .git} file on the default branch (an adapt target);
     *         {@code false}
     *         if all are empty (a from-scratch target). Fails CLOSED to {@code true} (treat as adapt, the safe choice that never overwrites) if a repository cannot be inspected,
     *         so
     *         an inspection error can never cause the live exercise to be overwritten by a failing draft.
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
                    // Cannot inspect: fail closed to "has content" so we never overwrite a possibly-working exercise.
                    return true;
                }
                Map<String, FileType> trackedFiles = repositoryService.getFiles(repository);
                boolean hasTrackedFile = trackedFiles.entrySet().stream().anyMatch(entry -> entry.getValue() == FileType.FILE);
                if (hasTrackedFile) {
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
     * Writes the produced files into a repository's working copy and commits them to an ISOLATED branch (never the default branch), pushing only that branch. The same
     * orphan-mirroring/harness-protection as the default-branch commit applies so the draft branch is a faithful image of the sandbox-final tree. A commit/push failure for one
     * repository is propagated so the recovery reports a real failure rather than a half-saved draft.
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
            // Reset the working copy back to the untouched default branch so this checkout is not left sitting on the diverted commit for any later default-branch operation.
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
        // 1. Commit each repository's produced files, tests last so the test-triggered build sees the final solution. Three separate git repositories cannot commit atomically, so
        // on
        // a mid-sequence failure we report which repositories were already written rather than claiming nothing changed.
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

        // 2. Update the problem statement if the agent changed it. Normalise typographic punctuation first: gpt-oss reliably emits a non-breaking hyphen (U+2011) in compound
        // modifiers
        // like "non-negative" and the occasional en/em dash, which it keeps producing even when the prompt forbids them — a deterministic post-pass guarantees the student-facing,
        // accessibility-sensitive document is plain ASCII regardless of model compliance.
        String producedProblemStatement = normalizeTypography(outcome.producedProblemStatement());
        if (!producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement())) {
            // From-scratch generation only (the statement was blank before this run): the lean AI create page persists a verbose brief-derived placeholder title; reconcile it to
            // the
            // agent's own H1 heading so the course list, editor tab and exercise read with a clean title (e.g. "Roman Numerals") instead of the requirements lead-in. An adapt run
            // (the
            // statement already existed) keeps the instructor's title untouched. updateProblemStatement saves the whole entity, so setting the title here persists it in the same
            // write.
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

        // 3. Trigger the canonical CI build for the tests; its result drives test-case synchronisation and task binding asynchronously, exactly as a manual edit to the tests repo
        // does.
        if (testsCommitHash != null) {
            // Snapshot the test-case count BEFORE triggering, so the zero-weight step can wait past a stale/partial set for the complete sync. See zeroWeightBuildGateTestCases.
            int testCaseCountBeforeBuild = testCaseRepository.findByExerciseId(exercise.getId()).size();
            triggerTestsBuild(exercise, testsCommitHash);
            // 3b. Align production grading with the differential oracle: zero-weight the build-gate cases. See zeroWeightBuildGateTestCases.
            zeroWeightBuildGateTestCases(exercise.getId(), testCaseCountBeforeBuild);
        }

        // 4. Record a new exercise version, snapshotting the committed repository state (the durable source of truth) and refreshing search indexing / notifying open editors.
        try {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }
        catch (RuntimeException e) {
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
        log.info("Persisted generated exercise {} (test-case synchronisation will complete asynchronously via CI)", exercise.getId());
    }

    /**
     * Writes the produced files into a repository's working copy and commits, making the committed tree EXACTLY mirror the sandbox-final {@code producedFiles} (see
     * {@link #deleteOrphanedFiles} for why mirroring rather than overlaying is required). A commit failure is propagated, not swallowed, so the caller does not report success
     * after
     * only some repositories were written.
     *
     * @param exercise       the exercise being persisted
     * @param user           the commit author
     * @param repositoryType the repository to write
     * @param producedFiles  the files to commit (the sandbox-final tree the oracle validated)
     * @return the new commit hash, or {@code null} only when there was nothing to commit
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
            // Propagate: the task service turns this into a clear failure event rather than a false "saved" message with a half-written exercise.
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Makes the repository's working copy EXACTLY mirror the sandbox-final {@code producedFiles}: removes the tracked files the agent did not produce (see
     * {@link #deleteOrphanedFiles}) and then writes every produced file (replacing any existing one). Shared by the default-branch commit and the isolated draft-branch commit so
     * both produce an identical tree from the same produced files; the caller decides how to commit it.
     *
     * @param exercise       the exercise being persisted (its checkout layout drives the placeholder normalization below)
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
            // Deterministically scrub typographic dashes / non-breaking spaces from the agent's SOURCE files too — gpt-oss reliably leaks a U+2011 into comments and even live
            // exception
            // messages ("size must be non-negative"), which look fine on screen but break the moment a student greps or copy-pastes. The prompt forbids it, but a soft guard on an
            // invisible
            // character is unreliable, so we enforce it here on the committed bytes (same normalization the problem statement gets). producedFiles only ever holds text — binaries
            // are
            // excluded from the String pipeline — so this never touches a binary; the rare exercise whose data literal needs one of these characters is not in the
            // generation-capable set.
            String content = normalizeTypography(entry.getValue());
            repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }
        // The agent's produced tree can re-introduce raw ${...} placeholders (e.g. by copying the worked reference's run.sh), so normalize them to the real-CI checkout values the
        // same way exercise creation does — idempotent for already-clean harnesses, corrective otherwise. See ProgrammingExerciseRepositoryService#replacePlaceholders.
        programmingExerciseRepositoryService.replacePlaceholders(exercise, repository);
    }

    /**
     * Deletes every tracked file in the repository that the agent did NOT produce, so the committed tree mirrors the sandbox-final state rather than overlaying onto the scaffolded
     * canonical sample (which would orphan the sample's test sources / structure oracle into real grading). Build/harness/manifest files (graded verbatim, immutable by contract)
     * are
     * NEVER deleted, so a partial read-back cannot wipe the harness; only the agent-editable SOURCE files that are now orphaned are removed. A delete failure for a single file is
     * logged and skipped — a leftover file is a quality issue, not a reason to abort an otherwise-valid persist.
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
            // Never delete a scaffolded binary (e.g. gradle/wrapper/gradle-wrapper.jar): it cannot survive the UTF-8 String round-trip so the read-back excludes it from
            // producedFiles,
            // which would make it look orphaned. The agent never edits it, so the byte-exact scaffolded original in the working tree is correct — leave it untouched so it commits
            // intact.
            if (repositoryRoot != null && BinaryContent.isBinaryFile(repositoryRoot.resolve(path))) {
                log.debug("Preserved scaffolded binary {} file {} (excluded from the String pipeline; kept byte-exact from the scaffold)", repositoryType, path);
                continue;
            }
            try {
                repositoryService.deleteFile(repository, path);
                log.debug("Removed orphaned {} file {} not produced by generation (mirroring the sandbox-final tree)", repositoryType, path);
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

    /** The exercise title column length; an H1 reconciled from a generated statement is capped to this so an unusually long heading can never break the persist. */
    private static final int MAX_TITLE_LENGTH = 255;

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration TEST_CASE_SYNC_POLL = Duration.ofSeconds(3);

    // Effective bounds for the build-gate sync wait; defaults to the constants above. A test seam (setTestCaseSyncTimingForTests) shrinks them so unit tests need not sleep.
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
     * The tests-build (just triggered) synchronises the test cases ASYNCHRONOUSLY at the default weight {@code 1.0}. For C/C++ FACT exercises the report includes build-gate cases
     * (CompileSort/TestConfigure) that PASS on the compiling template; the differential oracle exempts them ({@link BuildGateTestNames}) but production grades EVERY discovered
     * case,
     * so without this a student submitting the untouched template would score above 0%. Wait (bounded) for the cases to appear, then zero-weight exactly the build gates so
     * production grading matches the oracle. Best-effort: a timeout or any error never fails the already-accepted exercise — it only leaves the rare C/C++ template scoring >0%
     * until
     * reconfigured. Runs on the generation executor (the persist is {@code @Async}), so the bounded wait blocks no request thread. A no-op for every language without build-gate
     * cases (Java/Python/…) and idempotent (re-running leaves the weights at 0).
     *
     * @param exerciseId               the generated exercise whose build-gate test cases should be excluded from grading
     * @param testCaseCountBeforeBuild the test-case count observed before the tests-build was triggered (the stale/partial baseline to wait past)
     */
    private void zeroWeightBuildGateTestCases(long exerciseId, int testCaseCountBeforeBuild) {
        try {
            long deadline = System.nanoTime() + testCaseSyncTimeout.toNanos();
            Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exerciseId);
            // Wait for the freshly triggered tests-build to re-sync the COMPLETE set: the count must move off the pre-build baseline (an exercise-setup build may have left a
            // partial
            // set) AND then settle (unchanged across one poll), so a build gate that appears only in the full sync is not missed. Best-effort: the bounded deadline caps the wait.
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
     * Replaces the typographic punctuation the model leaks into the student-facing problem statement with the plain ASCII equivalents: any Unicode dash in the
     * {@code U+2010..U+2015}
     * range (most often the non-breaking hyphen {@code U+2011} in "non-negative") becomes a hyphen-minus, and the non-breaking / narrow-no-break spaces become a normal space.
     * These
     * are pure typography in prose; code spans in a problem statement are ASCII, so the substitution never changes a meaningful literal. Leaves all other characters untouched.
     *
     * @param problemStatement the produced problem statement (may be {@code null})
     * @return the statement with typographic dashes/spaces normalised to ASCII, or {@code null} if the input was {@code null}
     */
    static String normalizeTypography(String problemStatement) {
        if (problemStatement == null) {
            return null;
        }
        // Dashes (U+2010..U+2015) -> hyphen-minus; non-breaking / narrow-no-break spaces -> normal space; smart single/double quotes -> ASCII ' and "; ellipsis -> "...". Applied
        // to both
        // the problem statement and every generated source file: gpt-oss leaks these into comments, exception messages and string literals where they read fine on screen but break
        // a grep
        // or copy-paste. The generation-capable language set has no exercise whose identifier/data literal legitimately needs one of these characters, so the substitution is
        // always safe.
        return problemStatement.replaceAll("[\u2010-\u2015]", "-").replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"').replace("\u2026", "...");
    }

    /**
     * Extracts the title from the first level-1 ATX heading ({@code # Title}) of a generated problem statement, used to reconcile a from-scratch AI exercise's placeholder title
     * with
     * the agent's own heading. A {@code ## } (level-2) heading does not match. Returns {@code null} when there is no leading H1 (then the placeholder title is kept). The result is
     * trimmed and capped at the exercise-title column length so an unusually long heading can never break the save.
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
