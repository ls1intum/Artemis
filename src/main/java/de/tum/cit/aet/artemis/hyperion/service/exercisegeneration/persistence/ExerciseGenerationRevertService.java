package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import jakarta.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Retains the pre-run state of the latest accepted generation or adaptation so an instructor can safely undo it. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ExerciseGenerationRevertService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationRevertService.class);

    // Keep the established map name so rolling deployments retain existing adaptation baselines.
    private static final String BASELINE_MAP_NAME = "hyperion-exercise-adaptation-baselines";

    /** Latest-only, bounded recovery window; this intentionally is not a durable history. */
    private static final int BASELINE_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** The repositories reset by a revert, in the same order the persist commits them (tests last so the re-sync build sees the reverted solution). */
    private static final RepositoryType[] REVERT_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private final HazelcastInstance hazelcastInstance;

    private final GitService gitService;

    private final GenerationPersistenceService persistenceService;

    private final TempFileUtilService tempFileUtilService;

    private final String defaultBranch;

    private IMap<Long, AdaptationBaseline> baselineMap;

    public ExerciseGenerationRevertService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, GitService gitService,
            GenerationPersistenceService persistenceService, TempFileUtilService tempFileUtilService,
            @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.hazelcastInstance = hazelcastInstance;
        this.gitService = gitService;
        this.persistenceService = persistenceService;
        this.tempFileUtilService = tempFileUtilService;
        this.defaultBranch = defaultBranch;
    }

    @PostConstruct
    public void init() {
        baselineMap = hazelcastInstance.getMap(BASELINE_MAP_NAME);
    }

    /**
     * Records a baseline using the exercise's current metadata as the post-run guard.
     *
     * @param exercise         the persisted exercise
     * @param jobId            the completed job
     * @param mode             whether the run generated or adapted the exercise
     * @param preRunHeads      repository heads before persistence
     * @param postRunHeads     repository heads after persistence
     * @param problemStatement problem statement before persistence
     * @param title            title before persistence
     */
    public void recordBaseline(ProgrammingExercise exercise, String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads, Map<RepositoryType, String> postRunHeads,
            String problemStatement, String title) {
        recordBaseline(exercise, jobId, mode, preRunHeads, postRunHeads, problemStatement, title, exercise.getProblemStatement(), exercise.getTitle());
    }

    /**
     * Records a baseline only after a guarded persist has completed successfully. Failures leave the run saved but not undoable.
     *
     * @param exercise                        the persisted exercise
     * @param jobId                           the completed job
     * @param mode                            whether the run generated or adapted the exercise
     * @param preRunHeads                     repository heads before persistence
     * @param postRunHeads                    repository heads after persistence
     * @param problemStatement                problem statement before persistence
     * @param title                           title before persistence
     * @param expectedCurrentProblemStatement problem statement written by the run
     * @param expectedCurrentTitle            title written by the run
     */
    public void recordBaseline(ProgrammingExercise exercise, String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads, Map<RepositoryType, String> postRunHeads,
            String problemStatement, String title, String expectedCurrentProblemStatement, String expectedCurrentTitle) {
        try {
            baselineMap.delete(exercise.getId());
            Map<RepositoryType, String> heads = new LinkedHashMap<>();
            Map<RepositoryType, String> expectedCurrentHeads = new LinkedHashMap<>();
            for (RepositoryType repositoryType : REVERT_ORDER) {
                String head = preRunHeads.get(repositoryType);
                if (head != null) {
                    LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
                    if (uri == null) {
                        log.warn(
                                "Could not record the generation baseline for the {} repository of exercise {} because the repository URI is missing; this run will not be revertible",
                                repositoryType, exercise.getId());
                        return;
                    }
                    String expectedCurrentHead = postRunHeads.get(repositoryType);
                    if (expectedCurrentHead == null) {
                        log.warn("Could not record the generation baseline for the {} repository of exercise {} because Hyperion's post-run HEAD is missing", repositoryType,
                                exercise.getId());
                        return;
                    }
                    heads.put(repositoryType, head);
                    expectedCurrentHeads.put(repositoryType, expectedCurrentHead);
                }
            }
            baselineMap.set(exercise.getId(),
                    new AdaptationBaseline(jobId, mode, heads, expectedCurrentHeads, problemStatement, title, expectedCurrentProblemStatement, expectedCurrentTitle),
                    BASELINE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Recorded revertible generation baseline for exercise {} (job {}): {} repository head(s)", exercise.getId(), jobId, heads.size());
        }
        catch (RuntimeException e) {
            log.warn("Could not record the generation baseline for exercise {} (job {}); this run will not be revertible: {}", exercise.getId(), jobId, e.getMessage());
        }
    }

    /**
     * Returns the job whose retained baseline can currently be undone.
     *
     * @param exerciseId the exercise whose baseline should be inspected
     * @return the revertible job id, or empty when no baseline remains
     */
    public Optional<String> findRevertibleJobId(long exerciseId) {
        return Optional.ofNullable(baselineMap.get(exerciseId)).map(AdaptationBaseline::jobId);
    }

    /**
     * Returns the retained run metadata from one baseline-map read.
     *
     * @param exerciseId the exercise whose baseline should be inspected
     * @return the retained job and mode, or empty when no baseline remains
     */
    public Optional<RevertibleRun> findRevertibleRun(long exerciseId) {
        return Optional.ofNullable(baselineMap.get(exerciseId)).map(baseline -> new RevertibleRun(baseline.jobId(), baseline.mode()));
    }

    /**
     * Reverts the most recent accepted run: resets template/solution/tests back to the commits captured before persistence, then re-synchronises grading. Idempotent
     * against a missing baseline (returns {@code false}); the baseline is consumed on a successful revert so it is not offered twice.
     *
     * @param exercise the exercise to revert
     * @param user     the instructor performing the revert (exercise-version author)
     * @return the revert result, or empty when there is no retained baseline to revert to
     */
    public Optional<RevertResult> revert(ProgrammingExercise exercise, User user) {
        return revert(exercise, user, () -> true);
    }

    /**
     * Reverts the most recent accepted run while aborting before durable mutations if this node no longer owns the job.
     *
     * @param exercise              the exercise to revert
     * @param user                  the instructor performing the revert (exercise-version author)
     * @param stillOwnsMutationSlot guard checked before durable mutations
     * @return the revert result, or empty when there is no retained baseline to revert to
     */
    public Optional<RevertResult> revert(ProgrammingExercise exercise, User user, BooleanSupplier stillOwnsMutationSlot) {
        AdaptationBaseline baseline = baselineMap.get(exercise.getId());
        if (baseline == null) {
            return Optional.empty();
        }
        RevertResult result = revertToBaseline(exercise, user, baseline, stillOwnsMutationSlot);
        // Consume the baseline only after every captured repository was reset. On a partial failure, keep it so a retry can reset the remaining repositories instead of stranding
        // the exercise in a half-reverted state.
        if (result.fullyReverted()) {
            baselineMap.remove(exercise.getId(), baseline);
        }
        return Optional.of(result);
    }

    /**
     * Resets the exercise's repositories back to the captured baseline commits and re-synchronises grading. Package-private and baseline-driven (no Hazelcast) so the git behaviour
     * is unit-testable with a mocked {@link GitService}.
     *
     * @param exercise the exercise to revert
     * @param user     the instructor performing the revert
     * @param baseline the captured pre-run baseline
     * @return which repositories were reverted and whether every captured repository was reverted successfully
     */
    RevertResult revertToBaseline(ProgrammingExercise exercise, User user, AdaptationBaseline baseline) {
        return revertToBaseline(exercise, user, baseline, () -> true);
    }

    RevertResult revertToBaseline(ProgrammingExercise exercise, User user, AdaptationBaseline baseline, BooleanSupplier stillOwnsMutationSlot) {
        if (!metadataCanBeReverted(exercise.getProblemStatement(), baseline.expectedProblemStatement(), baseline.problemStatement())
                || !metadataCanBeReverted(exercise.getTitle(), baseline.expectedTitle(), baseline.title()) || !persistenceService.canRestoreProblemStatementAndTitle(exercise,
                        baseline.problemStatement(), baseline.title(), baseline.expectedProblemStatement(), baseline.expectedTitle())) {
            log.error("Refusing to revert generation metadata for exercise {} because the current problem statement/title no longer matches the captured generated state",
                    exercise.getId());
            return new RevertResult(false, List.of());
        }

        List<RepositoryType> reverted = new ArrayList<>();
        boolean fullyReverted = true;
        for (RepositoryType repositoryType : REVERT_ORDER) {
            String head = baseline.headFor(repositoryType);
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (head == null || uri == null) {
                continue;
            }
            if (!stillOwnsMutationSlot.getAsBoolean()) {
                log.error("Stopped reverting generated changes for exercise {} because this node lost the exercise mutation slot before resetting {}", exercise.getId(),
                        repositoryType);
                fullyReverted = false;
                break;
            }
            Repository repository = null;
            Path temporaryCheckout = null;
            try {
                temporaryCheckout = tempFileUtilService.createTempDirectory("hyperion-revert-");
                repository = gitService.getOrCheckoutRepository(uri, uri, temporaryCheckout.resolve("repository"), true, defaultBranch, false);
                if (repository == null) {
                    throw new IllegalStateException("Could not check out the repository to revert it");
                }
                String currentHead = gitService.getLastCommitHash(uri);
                if (head.equals(currentHead)) {
                    refreshCachedCheckout(uri);
                    reverted.add(repositoryType);
                    continue;
                }
                String expectedCurrentHead = baseline.expectedCurrentHeadFor(repositoryType);
                if (expectedCurrentHead == null) {
                    throw new IllegalStateException("No post-run HEAD was captured for this repository; refusing to force-push without clobber protection");
                }
                if (!expectedCurrentHead.equals(currentHead)) {
                    throw new IllegalStateException("Current repository HEAD " + currentHead + " differs from the generated commit " + expectedCurrentHead);
                }
                gitService.resetToCommitAndForcePush(repository, head, expectedCurrentHead, defaultBranch);
                refreshCachedCheckout(uri);
                reverted.add(repositoryType);
                log.info("Reverted the {} repository of exercise {} back to its pre-generated commit {}", repositoryType, exercise.getId(), head);
            }
            catch (Exception e) {
                fullyReverted = false;
                log.error("Failed to revert the {} repository of exercise {} back to {}; the exercise may be inconsistent", repositoryType, exercise.getId(), head, e);
            }
            finally {
                if (repository != null) {
                    repository.closeBeforeDelete();
                }
                if (temporaryCheckout != null && !FileUtils.deleteQuietly(temporaryCheckout.toFile())) {
                    log.warn("Could not delete temporary Hyperion generation-revert checkout {}", temporaryCheckout);
                }
            }
        }
        if (fullyReverted) {
            if (!stillOwnsMutationSlot.getAsBoolean()) {
                log.error("Stopped reverting generated changes for exercise {} because this node lost the exercise mutation slot before metadata/test-case resync",
                        exercise.getId());
                return new RevertResult(false, List.copyOf(reverted));
            }
            // Re-sync grading to the reverted tests (best-effort); the tests HEAD is the captured baseline commit we just reset to. A partial revert deliberately skips this so
            // test-case/build metadata cannot be refreshed against only part of a mixed repository state.
            fullyReverted = persistenceService.resyncAfterRevert(exercise, user, baseline.headFor(RepositoryType.TESTS), baseline.problemStatement(), baseline.title(),
                    baseline.expectedProblemStatement(), baseline.expectedTitle());
        }
        return new RevertResult(fullyReverted, List.copyOf(reverted));
    }

    private void refreshCachedCheckout(LocalVCRepositoryUri uri) {
        try {
            Repository cachedRepository = gitService.getOrCheckoutRepository(uri, false, defaultBranch, false);
            if (cachedRepository != null) {
                gitService.fetchAll(cachedRepository);
                gitService.reset(cachedRepository, "origin/" + defaultBranch);
            }
        }
        catch (Exception e) {
            log.warn("Could not refresh the cached repository after reverting {}", uri, e);
            gitService.deleteLocalRepository(uri);
        }
    }

    private static boolean metadataCanBeReverted(String currentValue, String expectedAdaptedValue, String targetBaselineValue) {
        String current = normalizeMetadata(currentValue);
        return Objects.equals(current, normalizeMetadata(expectedAdaptedValue)) || Objects.equals(current, normalizeMetadata(targetBaselineValue));
    }

    private static String normalizeMetadata(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /**
     * The outcome of reverting generated changes.
     *
     * @param fullyReverted        {@code true} if every captured repository was reset successfully; {@code false} if any could not be (needs manual review)
     * @param revertedRepositories the repositories that were reset back to their baseline commit
     */
    public record RevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories) {
    }

    /**
     * @param jobId the retained job
     * @param mode  its mode, or {@code null} for a baseline serialized by an older node
     */
    public record RevertibleRun(String jobId, @Nullable GenerationMode mode) {
    }
}
