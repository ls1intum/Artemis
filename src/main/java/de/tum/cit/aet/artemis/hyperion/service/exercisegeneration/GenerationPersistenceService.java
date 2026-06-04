package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
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
 * notifies open editors). All of this reuses existing services rather than re-implementing them.
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

    public GenerationPersistenceService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingSubmissionService programmingSubmissionService, ProgrammingExerciseCreationUpdateService creationUpdateService,
            ExerciseVersionService exerciseVersionService) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.participationService = participationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.creationUpdateService = creationUpdateService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Prefix of the isolated branch a recovery draft is committed to when the target is an adapt of an already-working exercise. The job id is appended so concurrent runs and
     * repeated runs never collide on the same ref, and an instructor can correlate the branch with the run that produced it.
     */
    static final String RECOVERY_DRAFT_BRANCH_PREFIX = "hyperion-draft/";

    /**
     * The result of persisting a non-accepted recovery draft.
     * <p>
     * The single field that matters for the safety invariant is {@code liveExerciseUntouched}: when {@code true} the working exercise on the default branch was NOT modified (the
     * draft was diverted to {@code draftBranch} for review), so a failed adapt cannot regress a previously-working exercise. When {@code false} the draft was committed to the
     * default branch the normal way — only ever chosen for a from-scratch target, which had nothing to lose.
     *
     * @param liveExerciseUntouched {@code true} if the live default branch was left byte-identical (adapt target); {@code false} if the draft was committed to the default branch
     *                                  (from-scratch target)
     * @param draftBranch           the isolated branch the draft was pushed to when {@code liveExerciseUntouched} is {@code true}; {@code null} otherwise
     */
    public record RecoveryPersistResult(boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Persists a non-accepted (recovered) generation draft WITHOUT ever regressing a previously-working exercise.
     * <p>
     * <strong>The safety invariant.</strong> An accepted run overwrites the live exercise on purpose (that is the whole point of generation). A non-accepted run must not: if the
     * target is an <em>adapt</em> of an already-working, shipped exercise, committing the best-effort BROKEN draft onto the default branch would silently replace a working
     * exercise
     * with a failing one (a real regression with no clean restore — the recorded {@link ExerciseVersion} is a read-only metadata snapshot, not a one-action git revert). So this
     * method first decides, per repository, whether the target already carries committed content (adapt) or is empty (from-scratch):
     * <ul>
     * <li><strong>From-scratch</strong> (all of template/solution/tests are empty): there is nothing to lose, so the draft is committed to the default branch exactly as
     * {@link #persist} does — the instructor opens the exercise and edits the draft in place.</li>
     * <li><strong>Adapt</strong> (any repository already has committed content): the live default branch is left BYTE-IDENTICAL. The draft is pushed to an isolated branch
     * ({@code hyperion-draft/<jobId>}) per repository instead, and NO CI build is triggered against the live solution. The working exercise keeps working and grading exactly as
     * before; the recovered draft is preserved on the isolated branch for the instructor to diff/merge deliberately.</li>
     * </ul>
     * The decision is taken atomically across all three repositories (if ANY is an adapt target, ALL are diverted to isolated branches) so a partial commit can never half-regress
     * the exercise — either every repository's default branch is untouched, or the from-scratch overwrite proceeds normally.
     *
     * @param exercise the exercise to persist the draft into
     * @param user     the instructor performing the generation (commit author)
     * @param outcome  the non-accepted generation outcome holding the produced files
     * @param jobId    the generation job id, used to name the isolated draft branch so concurrent/repeated runs never collide
     * @return the result describing whether the live exercise was left untouched and, if so, the isolated branch the draft was pushed to
     */
    public RecoveryPersistResult persistRecoveryDraft(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        // Decide adapt-vs-from-scratch up front, BEFORE writing anything, by inspecting whether any target repository already tracks content. Doing this first makes the choice
        // atomic across all three repositories: a later commit failure can never leave the exercise half-overwritten, because in adapt mode the default branch is never touched at
        // all.
        boolean adaptTarget = anyRepositoryHasContent(exercise);
        if (!adaptTarget) {
            // From-scratch: nothing to lose. Commit the draft to the default branch exactly as an accepted run does so it is editable in place.
            persist(exercise, user, outcome);
            return new RecoveryPersistResult(false, null);
        }
        // Adapt: never overwrite the live default branch with a failing draft. Divert every repository's draft to an isolated branch and leave the working exercise byte-identical.
        String draftBranch = RECOVERY_DRAFT_BRANCH_PREFIX + jobId;
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TEMPLATE, outcome.producedFiles(RepositoryType.TEMPLATE), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.SOLUTION, outcome.producedFiles(RepositoryType.SOLUTION), draftBranch);
        commitDraftToIsolatedBranch(exercise, user, RepositoryType.TESTS, outcome.producedFiles(RepositoryType.TESTS), draftBranch);
        // Deliberately NO triggerTestsBuild and NO createExerciseVersion: the live exercise did not change, so it must neither be re-graded against the broken draft nor recorded
        // as a
        // new version. The draft lives only on the isolated branch for review.
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
     * Writes the produced files into a repository's working copy and commits them to an ISOLATED branch (never the default branch), pushing only that branch so the live exercise
     * on
     * the default branch is left byte-identical. The same orphan-mirroring/harness-protection as the default-branch commit applies so the draft branch is a faithful image of the
     * sandbox-final tree. A commit/push failure for one repository is propagated so the recovery reports a real failure rather than a half-saved draft.
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
            deleteOrphanedFiles(repository, repositoryType, producedFiles.keySet());
            for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
                String path = entry.getKey();
                if (gitService.getFileByName(repository, path).isPresent()) {
                    repositoryService.deleteFile(repository, path);
                }
                repositoryService.createFile(repository, path, new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)));
            }
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
        // 1. Commit each repository's produced files (template, solution, then tests last so the test-triggered build sees the final solution). The three repositories are separate
        // git repositories, so this cannot be a single atomic transaction; we commit in a stable order and, if a later commit fails, report exactly which repositories were already
        // written rather than claiming nothing changed — the instructor can then review the partially-saved exercise (the new ExerciseVersion below also snapshots the state).
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

        // 2. Update the problem statement if the agent changed it.
        String producedProblemStatement = outcome.producedProblemStatement();
        if (!producedProblemStatement.isBlank() && !producedProblemStatement.equals(exercise.getProblemStatement())) {
            try {
                creationUpdateService.updateProblemStatement(exercise, producedProblemStatement, null);
            }
            catch (RuntimeException e) {
                log.warn("Failed to update problem statement for exercise {}: {}", exercise.getId(), e.getMessage());
            }
        }

        // 3. Trigger the canonical CI build for the tests. This is the one legitimate real build; its result drives the platform's test-case synchronisation and task binding,
        // which complete asynchronously through the normal result-processing pipeline (exactly as a manual instructor edit to the tests repository does).
        if (testsCommitHash != null) {
            triggerTestsBuild(exercise, testsCommitHash);
        }

        // 4. Record a new exercise version, snapshotting all repository commit hashes and refreshing search indexing / notifying open editors. Test-case synchronisation from the
        // build above lands asynchronously; the version captures the committed repository state, which is the durable source of truth.
        try {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }
        catch (RuntimeException e) {
            log.warn("Failed to create exercise version for exercise {}: {}", exercise.getId(), e.getMessage());
        }
        log.info("Persisted generated exercise {} (test-case synchronisation will complete asynchronously via CI)", exercise.getId());
    }

    /**
     * Writes the produced files into a repository's working copy and commits, making the committed tree EXACTLY mirror the sandbox-final {@code producedFiles}. A commit failure is
     * propagated (not swallowed) so the caller does not report success after only some repositories were written.
     * <p>
     * <strong>Why the tree must mirror, not just overlay.</strong> The repository was scaffolded from the canonical sample before generation (for a from-scratch run the
     * template/solution sources are cleared, but the TESTS repo is kept VERBATIM — the canonical behaviour test and structural oracle for a <em>different</em> exercise). The agent
     * removes those sample test sources inside the sandbox and writes its own, so the sandbox-final tree the differential oracle accepted contains ONLY the generated exercise's
     * tests. If persist merely overlaid {@code producedFiles} onto the scaffolded git tree it would leave the canonical sample's orphaned test sources behind (e.g. Java's
     * {@code SortingExampleBehaviorTest.java} referencing classes the generated solution does not have, or Python's {@code behavior/behavior_test.py} /
     * {@code structural/structural_test.py}). Those orphans never run in the sandbox {@code verify.sh} oracle but DO run in real Artemis grading, so the reference solution would
     * score below 100% on the real pipeline while the sandbox accepted — the exact verify.sh-vs-production divergence. Deleting every tracked file absent from
     * {@code producedFiles}
     * closes it: the persisted tree is byte-for-byte the set the oracle validated.
     * <p>
     * Build/harness/manifest files (graded verbatim, protected by the harness-immutability gate) are never deleted even if a flaky read-back dropped them from
     * {@code producedFiles},
     * so a partial extraction can corrupt the build but can never silently wipe the harness. The agent legitimately edits only test SOURCE files; those ARE removed when orphaned.
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
            deleteOrphanedFiles(repository, repositoryType, producedFiles.keySet());
            for (Map.Entry<String, String> entry : producedFiles.entrySet()) {
                String path = entry.getKey();
                if (gitService.getFileByName(repository, path).isPresent()) {
                    repositoryService.deleteFile(repository, path);
                }
                repositoryService.createFile(repository, path, new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)));
            }
            repositoryService.commitChanges(repository, user);
            return gitService.getLastCommitHash(uri);
        }
        catch (Exception e) {
            // Propagate: the task service turns this into a clear failure event rather than a false "saved" message with a half-written exercise.
            throw new IllegalStateException("Failed to commit the " + repositoryType + " repository for exercise " + exercise.getId() + ": " + e.getMessage(), e);
        }
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
            // Never delete a scaffolded BINARY (e.g. gradle/wrapper/gradle-wrapper.jar). A binary cannot survive the UTF-8 String round-trip, so it is deliberately EXCLUDED from
            // producedFiles on read-back (WorkspaceArchive.readTar) — which would make it look like an orphan here. The agent never edits these, so the correct, byte-exact state
            // is
            // the scaffolded original already in the working tree: leave it untouched (do not delete, do not rewrite) so it commits intact. Detected by content (NUL/non-UTF-8), so
            // a genuinely-textual run.sh/build.sh source is NOT mistaken for binary.
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
}
