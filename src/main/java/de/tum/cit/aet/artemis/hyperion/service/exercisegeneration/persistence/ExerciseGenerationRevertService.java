package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Retains the pre-run state of the latest mechanically verified generation or adaptation so an instructor can safely undo it. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ExerciseGenerationRevertService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationRevertService.class);

    private final GenerationVersionRecoveryService recovery;

    private final GenerationRestoreMetadataService metadata;

    private final GenerationRestoreTestCasesService testCases;

    private final GenerationRepositoryRevertService repositories;

    private final GenerationPersistenceService persistenceService;

    private final String defaultBranch;

    public ExerciseGenerationRevertService(GenerationVersionRecoveryService recovery, GenerationRestoreMetadataService metadata, GenerationRestoreTestCasesService testCases,
            GenerationRepositoryRevertService repositories, GenerationPersistenceService persistenceService,
            @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.recovery = recovery;
        this.metadata = metadata;
        this.testCases = testCases;
        this.repositories = repositories;
        this.persistenceService = persistenceService;
        this.defaultBranch = defaultBranch;
    }

    public Optional<String> findRevertibleJobId(long exerciseId) {
        return recovery.find(exerciseId).map(GenerationVersionRecoveryService.Recovery::jobId);
    }

    public Optional<RevertibleRun> findRevertibleRun(long exerciseId) {
        return recovery.find(exerciseId).map(pair -> new RevertibleRun(pair.jobId(), pair.baseline().mode()));
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
        var pair = recovery.find(exercise.getId());
        if (pair.isEmpty()) {
            return Optional.empty();
        }
        RevertResult result = revertToBaseline(exercise, user, pair.get(), stillOwnsMutationSlot);
        // Consumed only once every captured repository was reset; a partial failure keeps it so a retry can finish instead of stranding the exercise half-reverted.
        if (result.fullyReverted()) {
            try {
                recovery.consumed(pair.get());
            }
            catch (RuntimeException failure) {
                log.error("Restored exercise {}, but could not complete its recovery record; retaining the recovery guard", exercise.getId(), failure);
                return Optional.of(new RevertResult(false, result.revertedRepositories(), true));
            }
        }
        return Optional.of(result);
    }

    private RevertResult revertToBaseline(ProgrammingExercise exercise, User user, GenerationVersionRecoveryService.Recovery pair, BooleanSupplier stillOwnsMutationSlot) {
        ExerciseGenerationBaseline baseline = pair.baseline();
        if (!metadata.canRestore(exercise.getId(), pair) || !testCases.canRestore(exercise.getId(), pair)) {
            return new RevertResult(false, List.of(), false);
        }
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

        if (!stillOwnsMutationSlot.getAsBoolean()) {
            return new RevertResult(false, List.of(), true);
        }
        recovery.started(pair);
        String repositoryBranch = baseline.repositoryBranch() == null || baseline.repositoryBranch().isBlank() ? defaultBranch : baseline.repositoryBranch();
        var repositoryResult = repositories.revert(exercise, baseline, repositoryBranch, stillOwnsMutationSlot);
        boolean fullyReverted = repositoryResult.fullyReverted();
        List<RepositoryType> reverted = repositoryResult.revertedRepositories();
        if (fullyReverted) {
            if (!stillOwnsMutationSlot.getAsBoolean()) {
                log.error("Stopped reverting generated changes for exercise {} because this node lost the exercise mutation slot before metadata/test-case resync",
                        exercise.getId());
                return new RevertResult(false, List.copyOf(reverted), true);
            }
            // Canonical metadata, not a successful rebuild, defines the previous version of an incomplete draft.
            try {
                Map<RepositoryType, String> revertedRepositoryHeads = repositories.captureRepositoryHeads(exercise, repositoryBranch, baseline);
                metadata.restore(exercise.getId(), pair, stillOwnsMutationSlot);
                testCases.restore(exercise.getId(), pair, stillOwnsMutationSlot);
                fullyReverted = persistenceService.resyncAfterRevertWithSignal(exercise, user, null, baseline.problemStatement(), baseline.title(),
                        baseline.expectedProblemStatement(), baseline.expectedTitle(), revertedRepositoryHeads, baseline.previousGrading(), baseline.savedGrading(),
                        stillOwnsMutationSlot);
            }
            catch (RuntimeException e) {
                log.error("Failed to restore version metadata for exercise {}; retaining the baseline for retry", exercise.getId(), e);
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

    /** A failed mutation attempt requires recovery; a clean preflight refusal does not create a new recovery obligation. */
    public record RevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories, boolean mutationAttempted) {
    }

    public record RevertibleRun(String jobId, GenerationMode mode) {
    }
}
