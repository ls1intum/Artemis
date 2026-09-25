package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationBaseline;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationGrading;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Resolves bounded undo from canonical versions, never from streamed progress or an older successful run behind a partial mutation. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationVersionRecoveryService {

    private final GenerationRunStoreService runs;

    private final ExerciseVersionRepository versions;

    public GenerationVersionRecoveryService(GenerationRunStoreService runs, ExerciseVersionRepository versions) {
        this.runs = runs;
        this.versions = versions;
    }

    /**
     * Returns only the latest mutation's complete recovery pair. Missing or uncertain provenance fails closed.
     *
     * @param exerciseId protected destination
     * @return the complete version pair, when safe to offer restoration
     */
    public Optional<Recovery> find(long exerciseId) {
        return runs.findLatestMutation(exerciseId).stream().findFirst().flatMap(this::resolve);
    }

    private Optional<Recovery> resolve(AuthoringRun run) {
        if ((run.getStatus() != AuthoringRun.Status.SAVED && run.getStatus() != AuthoringRun.Status.NEEDS_REVIEW) || run.getBeforeVersionId() == null
                || run.getAfterVersionId() == null || run.getRepositoryBranch() == null || run.getRepositoryBranch().isBlank()) {
            return Optional.empty();
        }
        Optional<ExerciseVersion> before = versions.findById(run.getBeforeVersionId());
        Optional<ExerciseVersion> after = versions.findById(run.getAfterVersionId());
        if (before.isEmpty() || after.isEmpty() || !run.getExerciseId().equals(before.get().getExerciseId()) || !run.getExerciseId().equals(after.get().getExerciseId())) {
            return Optional.empty();
        }
        ExerciseSnapshotDTO previous = before.get().getExerciseSnapshot();
        ExerciseSnapshotDTO saved = after.get().getExerciseSnapshot();
        if (previous == null || saved == null || previous.id() != run.getExerciseId() || saved.id() != run.getExerciseId()) {
            return Optional.empty();
        }
        try {
            var baseline = new ExerciseGenerationBaseline(run.getJobId(), run.getKind() == AuthoringRun.Kind.CREATE ? GenerationMode.GENERATE : GenerationMode.ADAPT,
                    heads(previous.programmingData()), heads(saved.programmingData()), previous.problemStatement(), previous.title(), saved.problemStatement(), saved.title(),
                    run.getRepositoryBranch(), grading(previous.programmingData()), grading(saved.programmingData()));
            return Optional.of(new Recovery(run.getJobId(), run.getAfterVersionId(), previous, saved, baseline));
        }
        catch (IllegalArgumentException incompleteVersion) {
            return Optional.empty();
        }
    }

    /**
     * Records the restoration obligation before any repository can change.
     *
     * @param recovery exact version pair protected by the caller's mutation slot
     */
    public void started(Recovery recovery) {
        if (runs.markRestoreStarted(recovery.jobId(), recovery.afterVersionId(), Instant.now()) != 1) {
            throw new IllegalStateException("The restoration obligation could not be recorded");
        }
    }

    /**
     * Consumes exactly the version pair restored by the caller, after all repository and metadata steps succeeded.
     *
     * @param recovery version pair restored under the exercise mutation guard
     */
    public void consumed(Recovery recovery) {
        if (runs.markReverted(recovery.jobId(), recovery.afterVersionId(), Instant.now()) != 1) {
            throw new IllegalStateException("The restored authoring version could not be marked as consumed");
        }
    }

    private static Map<RepositoryType, String> heads(ProgrammingExerciseSnapshotDTO snapshot) {
        if (snapshot == null || snapshot.templateParticipation() == null || snapshot.solutionParticipation() == null) {
            throw new IllegalArgumentException("The programming version has incomplete repository identity");
        }
        var heads = new EnumMap<RepositoryType, String>(RepositoryType.class);
        heads.put(RepositoryType.TEMPLATE, snapshot.templateParticipation().commitId());
        heads.put(RepositoryType.SOLUTION, snapshot.solutionParticipation().commitId());
        heads.put(RepositoryType.TESTS, snapshot.testsCommitId());
        if (heads.values().stream().anyMatch(head -> head == null || head.isBlank())) {
            throw new IllegalArgumentException("The programming version has incomplete repository commits");
        }
        return Map.copyOf(heads);
    }

    private static GenerationGrading.Snapshot grading(ProgrammingExerciseSnapshotDTO snapshot) {
        var tests = new LinkedHashMap<String, GenerationGrading.Grading>();
        if (snapshot.testCases() != null) {
            for (var test : snapshot.testCases()) {
                if (test.testName() == null || test.testName().isBlank()
                        || tests.putIfAbsent(test.testName(), new GenerationGrading.Grading(test.weight(), test.visibility())) != null) {
                    throw new IllegalArgumentException("The programming version has incomplete or duplicate test identity");
                }
            }
        }
        return new GenerationGrading.Snapshot(tests);
    }

    /** Detached canonical snapshots and the repository/grading guards derived from them. */
    public record Recovery(String jobId, long afterVersionId, ExerciseSnapshotDTO before, ExerciseSnapshotDTO after, ExerciseGenerationBaseline baseline) {
    }
}
