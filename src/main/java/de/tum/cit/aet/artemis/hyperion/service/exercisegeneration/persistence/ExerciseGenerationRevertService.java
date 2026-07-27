package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import jakarta.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
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

/** Retains the pre-run state of the latest mechanically verified generation or adaptation so an instructor can safely undo it. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ExerciseGenerationRevertService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationRevertService.class);

    private static final String BASELINE_MAP_NAME = "hyperion-exercise-generation-baselines";

    /** A bounded latest-only recovery window, deliberately not a durable history. */
    private static final int BASELINE_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** The same order the persist commits them, so tests come last and the re-sync build sees the reverted solution. */
    private static final RepositoryType[] REVERT_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private final HazelcastInstance hazelcastInstance;

    private final GitService gitService;

    private final GenerationPersistenceService persistenceService;

    private final TempFileUtilService tempFileUtilService;

    private final String defaultBranch;

    private IMap<Long, ExerciseGenerationBaseline> baselineMap;

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
     * Records what a completed run has to be rolled back to. Call this only after a guarded persist succeeded: a failure here leaves the run saved but not undoable, and every
     * "expected current" argument is a guard that later refuses the revert if something other than this run has since touched the exercise.
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
     * @param repositoryBranch                the branch persistence actually committed to, which is the branch the revert must reset
     * @return whether automatic revert is available for this run
     */
    public boolean recordBaseline(ProgrammingExercise exercise, String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads,
            Map<RepositoryType, String> postRunHeads, String problemStatement, String title, String expectedCurrentProblemStatement, String expectedCurrentTitle,
            String repositoryBranch) {
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
                        return false;
                    }
                    String expectedCurrentHead = postRunHeads.get(repositoryType);
                    if (expectedCurrentHead == null) {
                        log.warn("Could not record the generation baseline for the {} repository of exercise {} because Hyperion's post-run HEAD is missing", repositoryType,
                                exercise.getId());
                        return false;
                    }
                    heads.put(repositoryType, head);
                    expectedCurrentHeads.put(repositoryType, expectedCurrentHead);
                }
            }
            baselineMap.set(exercise.getId(), new ExerciseGenerationBaseline(jobId, mode, heads, expectedCurrentHeads, problemStatement, title, expectedCurrentProblemStatement,
                    expectedCurrentTitle, repositoryBranch), BASELINE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Recorded revertible generation baseline for exercise {} (job {}): {} repository head(s)", exercise.getId(), jobId, heads.size());
            return true;
        }
        catch (RuntimeException e) {
            log.warn("Could not record the generation baseline for exercise {} (job {}); this run will not be revertible: {}", exercise.getId(), jobId, e.getMessage());
            return false;
        }
    }

    public Optional<String> findRevertibleJobId(long exerciseId) {
        return Optional.ofNullable(baselineMap.get(exerciseId)).map(ExerciseGenerationBaseline::jobId);
    }

    /**
     * Callers must invoke this whenever a later run's persistence stops in anything but a clean, fully-verified save. A retained baseline was captured relative to the repository
     * state <em>before</em> that later run started, so applying it on top of the run's partial or unconfirmed changes would mix two never jointly-verified states. Idempotent.
     *
     * @param exerciseId the exercise whose baseline should no longer be offered for automatic revert
     */
    public void invalidateBaseline(long exerciseId) {
        baselineMap.delete(exerciseId);
        log.info("Invalidated the automatic-revert baseline for exercise {} before a later run began durable mutation", exerciseId);
    }

    public Optional<RevertibleRun> findRevertibleRun(long exerciseId) {
        return Optional.ofNullable(baselineMap.get(exerciseId)).map(baseline -> new RevertibleRun(baseline.jobId(), baseline.mode()));
    }

    /**
     * Resets the repositories to the commits captured before persistence, then re-synchronises grading. There is deliberately no unguarded entry point: force-pushing an
     * exercise's repositories from a node that no longer owns the job would race the node that does.
     *
     * @param exercise              the exercise to revert
     * @param user                  the instructor performing the revert (exercise-version author)
     * @param stillOwnsMutationSlot re-checked before each durable mutation, so a lost slot stops the revert instead of finishing it
     * @return the revert result, or empty when there is no retained baseline to revert to
     */
    public Optional<RevertResult> revert(ProgrammingExercise exercise, User user, BooleanSupplier stillOwnsMutationSlot) {
        ExerciseGenerationBaseline baseline = baselineMap.get(exercise.getId());
        if (baseline == null) {
            return Optional.empty();
        }
        RevertResult result = revertToBaseline(exercise, user, baseline, stillOwnsMutationSlot);
        // Consumed only once every captured repository was reset; a partial failure keeps it so a retry can finish instead of stranding the exercise half-reverted.
        if (result.fullyReverted()) {
            baselineMap.remove(exercise.getId(), baseline);
        }
        return Optional.of(result);
    }

    private RevertResult revertToBaseline(ProgrammingExercise exercise, User user, ExerciseGenerationBaseline baseline, BooleanSupplier stillOwnsMutationSlot) {
        if (!metadataCanBeReverted(exercise.getProblemStatement(), baseline.expectedProblemStatement(), baseline.problemStatement())
                || !metadataCanBeReverted(exercise.getTitle(), baseline.expectedTitle(), baseline.title()) || !persistenceService.canRestoreProblemStatementAndTitle(exercise,
                        baseline.problemStatement(), baseline.title(), baseline.expectedProblemStatement(), baseline.expectedTitle())) {
            log.error("Refusing to revert generation metadata for exercise {} because the current problem statement/title no longer matches the captured generated state",
                    exercise.getId());
            return new RevertResult(false, List.of());
        }

        List<RepositoryType> reverted = new ArrayList<>();
        String repositoryBranch = baseline.repositoryBranch() == null || baseline.repositoryBranch().isBlank() ? defaultBranch : baseline.repositoryBranch();
        GenerationPersistenceService.TestsBuildSignal testsBuildSignal = baseline.headFor(RepositoryType.TESTS) == null ? null
                : persistenceService.prepareTestsBuildSignal(exercise, baseline.headFor(RepositoryType.TESTS));
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
                repository = gitService.getOrCheckoutRepository(uri, uri, temporaryCheckout.resolve("repository"), true, repositoryBranch, false);
                if (repository == null) {
                    throw new IllegalStateException("Could not check out the repository to revert it");
                }
                String currentHead = gitService.getLastCommitHash(uri, repositoryBranch);
                if (head.equals(currentHead)) {
                    refreshCachedCheckout(uri, repositoryBranch);
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
                gitService.resetToCommitAndForcePush(repository, head, expectedCurrentHead, repositoryBranch);
                refreshCachedCheckout(uri, repositoryBranch);
                reverted.add(repositoryType);
                log.info("Reverted the {} repository of exercise {} back to its pre-generated commit {}", repositoryType, exercise.getId(), head);
            }
            catch (Exception e) {
                fullyReverted = false;
                log.error("Failed to revert the {} repository of exercise {} back to {}; the exercise may be inconsistent", repositoryType, exercise.getId(), head, e);
                break;
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
            // Only after every required reset completed, so the tests build sees the fully reverted tree.
            try {
                Map<RepositoryType, String> revertedRepositoryHeads = captureRepositoryHeads(exercise, repositoryBranch, baseline);
                fullyReverted = persistenceService.resyncAfterRevertWithSignal(exercise, user, testsBuildSignal, baseline.problemStatement(), baseline.title(),
                        baseline.expectedProblemStatement(), baseline.expectedTitle(), revertedRepositoryHeads);
            }
            catch (RuntimeException e) {
                log.error("Failed to trigger the grading re-sync after reverting exercise {}; retaining the baseline for retry", exercise.getId(), e);
                fullyReverted = false;
            }
        }
        return new RevertResult(fullyReverted, List.copyOf(reverted));
    }

    private Map<RepositoryType, String> captureRepositoryHeads(ProgrammingExercise exercise, String repositoryBranch, ExerciseGenerationBaseline baseline) {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        for (RepositoryType repositoryType : REVERT_ORDER) {
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (uri == null) {
                continue;
            }
            String head = baseline.headFor(repositoryType);
            if (head == null) {
                head = gitService.getLastCommitHash(uri, repositoryBranch);
            }
            if (head == null) {
                throw new IllegalStateException("Could not resolve the " + repositoryType + " repository HEAD after reverting it");
            }
            heads.put(repositoryType, head);
        }
        return Map.copyOf(heads);
    }

    private void refreshCachedCheckout(LocalVCRepositoryUri uri, String repositoryBranch) {
        try {
            Repository cachedRepository = gitService.getOrCheckoutRepository(uri, false, repositoryBranch, false);
            if (cachedRepository != null) {
                gitService.fetchAll(cachedRepository);
                gitService.reset(cachedRepository, "origin/" + repositoryBranch);
            }
        }
        catch (Exception e) {
            log.warn("Could not refresh the cached repository after reverting {}", uri, e);
            gitService.deleteLocalRepository(uri);
        }
    }

    private static boolean metadataCanBeReverted(String currentValue, String expectedCurrentValue, String targetBaselineValue) {
        String current = normalizeMetadata(currentValue);
        return Objects.equals(current, normalizeMetadata(expectedCurrentValue)) || Objects.equals(current, normalizeMetadata(targetBaselineValue));
    }

    private static String normalizeMetadata(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /** A {@code fullyReverted} of {@code false} leaves the exercise part-way between the two states and needs manual review. */
    public record RevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories) {
    }

    public record RevertibleRun(String jobId, GenerationMode mode) {
    }
}
