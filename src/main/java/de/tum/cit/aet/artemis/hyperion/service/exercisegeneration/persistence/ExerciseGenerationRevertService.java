package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Retains the pre-run state of the latest mechanically verified generation or adaptation so an instructor can safely undo it. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ExerciseGenerationRevertService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationRevertService.class);

    private static final String BASELINE_MAP_NAME = "hyperion-exercise-generation-baselines";

    /** A bounded latest-only recovery window, not a durable history. */
    private static final int BASELINE_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** The same order the persist commits them, so tests come last and the re-sync build sees the reverted solution. */
    private static final RepositoryType[] REVERT_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private final DistributedDataProvider distributedDataProvider;

    private final GenerationRepositoryRevertService repositories;

    private final GenerationPersistenceService persistenceService;

    private final String defaultBranch;

    private DistributedMap<Long, ExerciseGenerationBaseline> baselineMap;

    public ExerciseGenerationRevertService(DistributedDataProvider distributedDataProvider, GenerationRepositoryRevertService repositories,
            GenerationPersistenceService persistenceService, @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.distributedDataProvider = distributedDataProvider;
        this.repositories = repositories;
        this.persistenceService = persistenceService;
        this.defaultBranch = defaultBranch;
    }

    @PostConstruct
    public void init() {
        baselineMap = distributedDataProvider.getExpiringMap(BASELINE_MAP_NAME, Duration.ofSeconds(BASELINE_TTL_SECONDS));
    }

    /**
     * Records what a completed run has to be rolled back to. Call this only after a guarded persist succeeded: a failure here leaves the run saved but not undoable. Every
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
     * @param repositoryBranch                the branch persistence committed to, which is the branch the revert must reset
     * @param previousGrading                 grading before repository synchronization and plan application
     * @param savedGrading                    grading after persistence, checked before an undo overwrites it
     * @return whether automatic revert is available for this run
     */
    public boolean recordBaseline(ProgrammingExercise exercise, String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads,
            Map<RepositoryType, String> postRunHeads, String problemStatement, String title, String expectedCurrentProblemStatement, String expectedCurrentTitle,
            String repositoryBranch, GenerationGrading.Snapshot previousGrading, GenerationGrading.Snapshot savedGrading) {
        try {
            baselineMap.remove(exercise.getId());
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
            baselineMap.put(exercise.getId(), new ExerciseGenerationBaseline(jobId, mode, heads, expectedCurrentHeads, problemStatement, title, expectedCurrentProblemStatement,
                    expectedCurrentTitle, repositoryBranch, previousGrading, savedGrading));
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
     * Invalidates the previous undo baseline before the next run's first durable mutation. Safe to call repeatedly.
     *
     * @param exerciseId the exercise whose baseline should no longer be offered for automatic revert
     */
    public void invalidateBaseline(long exerciseId) {
        baselineMap.remove(exerciseId);
        log.info("Invalidated the automatic-revert baseline for exercise {} before a later run began durable mutation", exerciseId);
    }

    public Optional<RevertibleRun> findRevertibleRun(long exerciseId) {
        return Optional.ofNullable(baselineMap.get(exerciseId)).map(baseline -> new RevertibleRun(baseline.jobId(), baseline.mode()));
    }

    /**
     * Restores repositories, metadata and grading under the caller's exercise-mutation slot. Refuses to overwrite subsequent instructor changes.
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
        if (!persistenceService.canRestoreGrading(exercise.getId(), baseline.previousGrading(), baseline.savedGrading())) {
            log.warn("Refusing to revert exercise {} because its grading no longer matches the generated or original state", exercise.getId());
            return new RevertResult(false, List.of(), false);
        }
        if (!metadataCanBeReverted(exercise.getProblemStatement(), baseline.expectedProblemStatement(), baseline.problemStatement())
                || !metadataCanBeReverted(exercise.getTitle(), baseline.expectedTitle(), baseline.title()) || !persistenceService.canRestoreProblemStatementAndTitle(exercise,
                        baseline.problemStatement(), baseline.title(), baseline.expectedProblemStatement(), baseline.expectedTitle())) {
            log.error("Refusing to revert generation metadata for exercise {} because the current problem statement/title no longer matches the captured generated state",
                    exercise.getId());
            return new RevertResult(false, List.of(), false);
        }

        String repositoryBranch = baseline.repositoryBranch() == null || baseline.repositoryBranch().isBlank() ? defaultBranch : baseline.repositoryBranch();
        GenerationPersistenceService.TestsBuildSignal testsBuildSignal = baseline.headFor(RepositoryType.TESTS) == null ? null
                : persistenceService.prepareTestsBuildSignal(exercise, baseline.headFor(RepositoryType.TESTS));
        var repositoryResult = repositories.revert(exercise, baseline, repositoryBranch, stillOwnsMutationSlot);
        boolean fullyReverted = repositoryResult.fullyReverted();
        List<RepositoryType> reverted = repositoryResult.revertedRepositories();
        if (fullyReverted) {
            if (!stillOwnsMutationSlot.getAsBoolean()) {
                log.error("Stopped reverting generated changes for exercise {} because this node lost the exercise mutation slot before metadata/test-case resync",
                        exercise.getId());
                return new RevertResult(false, List.copyOf(reverted), true);
            }
            // Only after every required reset completed, so the tests build sees the fully reverted tree.
            try {
                Map<RepositoryType, String> revertedRepositoryHeads = repositories.captureRepositoryHeads(exercise, repositoryBranch, baseline);
                fullyReverted = persistenceService.resyncAfterRevertWithSignal(exercise, user, testsBuildSignal, baseline.problemStatement(), baseline.title(),
                        baseline.expectedProblemStatement(), baseline.expectedTitle(), revertedRepositoryHeads, baseline.previousGrading(), baseline.savedGrading(),
                        stillOwnsMutationSlot);
            }
            catch (RuntimeException e) {
                log.error("Failed to trigger the grading re-sync after reverting exercise {}; retaining the baseline for retry", exercise.getId(), e);
                fullyReverted = false;
            }
        }
        return new RevertResult(fullyReverted, List.copyOf(reverted), true);
    }

    private static boolean metadataCanBeReverted(String currentValue, String expectedCurrentValue, String targetBaselineValue) {
        String current = normalizeMetadata(currentValue);
        return Objects.equals(current, normalizeMetadata(expectedCurrentValue)) || Objects.equals(current, normalizeMetadata(targetBaselineValue));
    }

    private static String normalizeMetadata(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /** A false {@code fullyReverted} means undo was refused or failed; some repositories may already have been reset. */
    public record RevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories, boolean mutationAttempted) {
    }

    public record RevertibleRun(String jobId, GenerationMode mode) {
    }
}
