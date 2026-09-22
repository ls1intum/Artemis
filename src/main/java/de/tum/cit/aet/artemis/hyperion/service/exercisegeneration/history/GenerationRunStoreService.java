package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;

/** Short-lived activity and undo references. Saved content belongs to the existing exercise version history, not a second database journal. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRunStoreService {

    private final Duration retention;

    private final DistributedMap<String, AuthoringRun> runs;

    private final DistributedMap<Long, Mutation> latest;

    private final DistributedMap<String, Long> sequence;

    public GenerationRunStoreService(DistributedDataProvider data, @Value("${artemis.hyperion.generation.terminal-replay-ttl:PT4H}") Duration retention) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Activity retention must be positive");
        }
        this.retention = retention;
        runs = data.getExpiringMap("hyperion-authoring-activity", retention);
        latest = data.getExpiringMap("hyperion-authoring-latest-mutation", retention);
        sequence = data.getMap("hyperion-authoring-activity-sequence");
    }

    /**
     * Records a detached identity once. Expiry is fixed at admission, not extended by delayed callbacks.
     *
     * @param run admitted identity
     * @return detached stored identity with its pagination cursor
     */
    public AuthoringRun save(AuthoringRun run) {
        AuthoringRun value = new AuthoringRun(run);
        value.setId(nextId());
        if (runs.putIfAbsent(value.getJobId(), value) != null) {
            throw new IllegalStateException("The authoring run is already recorded");
        }
        return new AuthoringRun(value);
    }

    private long nextId() {
        for (int attempt = 0; attempt < 100; attempt++) {
            Long previous = sequence.get("next");
            long next = Math.max(System.currentTimeMillis(), previous == null ? 1 : Math.addExact(previous, 1));
            if (previous == null ? sequence.putIfAbsent("next", next) == null : sequence.replace("next", previous, next)) {
                return next;
            }
        }
        throw new IllegalStateException("Could not allocate activity cursor");
    }

    public Optional<AuthoringRun> findByJobId(String jobId) {
        return Optional.ofNullable(runs.get(jobId)).map(AuthoringRun::new);
    }

    public List<AuthoringRun> findByOwnerIdAndJobIdIn(long ownerId, Collection<String> jobIds) {
        return jobIds.stream().distinct().map(this::findByJobId).flatMap(Optional::stream).filter(run -> Objects.equals(run.getOwnerId(), ownerId)).toList();
    }

    public List<AuthoringRun> findOwnedBefore(long ownerId, Long beforeId, Pageable page) {
        return runs.values().stream().filter(run -> Objects.equals(run.getOwnerId(), ownerId) && (beforeId == null || run.getId() < beforeId))
                .sorted(Comparator.comparing(AuthoringRun::getId).reversed()).limit(page.getPageSize()).map(AuthoringRun::new).toList();
    }

    /**
     * Never searches behind an expired, partial or consumed latest mutation for an older successful undo.
     *
     * @param exerciseId destination exercise
     * @return the latest retained mutation, if any
     */
    public List<AuthoringRun> findLatestMutation(long exerciseId) {
        Mutation mutation = latest.get(exerciseId);
        return mutation == null ? List.of() : findByJobId(mutation.jobId()).filter(run -> run.getRevertedAt() == null).stream().toList();
    }

    /**
     * Links a write-once baseline before the caller can modify an exercise.
     *
     * @param jobId     admitted identity
     * @param versionId existing exercise version
     * @param branch    repository branch
     * @param startedAt mutation start
     * @return one if accepted, zero if expired or already started
     */
    public int linkBeforeVersion(String jobId, long versionId, String branch, Instant startedAt) {
        int changed = update(jobId, run -> run.getMutationStartedAt() == null && run.getFinishedAt() == null, run -> {
            run.setBeforeVersionId(versionId);
            run.setRepositoryBranch(branch);
            run.setMutationStartedAt(startedAt);
        });
        if (changed == 1) {
            AuthoringRun run = findByJobId(jobId).orElseThrow();
            Mutation replacement = new Mutation(jobId, run.getId());
            for (int attempt = 0; attempt < 100; attempt++) {
                Mutation previous = latest.get(run.getExerciseId());
                if (previous != null && previous.sequence() >= run.getId()) {
                    throw new IllegalStateException("A newer mutation already owns recovery");
                }
                if (previous == null ? latest.putIfAbsent(run.getExerciseId(), replacement) == null : latest.replace(run.getExerciseId(), previous, replacement)) {
                    latest.refreshTimeToLive(run.getExerciseId(), retention);
                    return changed;
                }
            }
            throw new IllegalStateException("Could not record the latest mutation");
        }
        return changed;
    }

    public int linkAfterVersion(String jobId, long versionId) {
        return update(jobId, run -> run.getBeforeVersionId() != null && run.getAfterVersionId() == null && run.getFinishedAt() == null, run -> run.setAfterVersionId(versionId));
    }

    /**
     * Accepts only the first terminal outcome, without resurrecting expired activity.
     *
     * @param jobId      admitted identity
     * @param status     terminal outcome
     * @param finishedAt completion time
     * @param changed    whether the live exercise changed
     * @return one if accepted, zero otherwise
     */
    public int complete(String jobId, AuthoringRun.Status status, Instant finishedAt, Boolean changed) {
        return update(jobId, run -> run.getFinishedAt() == null, run -> {
            run.setStatus(status);
            run.setFinishedAt(finishedAt);
            run.setLiveExerciseChanged(changed);
        });
    }

    public int markReverted(String jobId, long expectedVersionId, Instant revertedAt) {
        return update(jobId, run -> Objects.equals(run.getAfterVersionId(), expectedVersionId) && run.getRevertedAt() == null, run -> run.setRevertedAt(revertedAt));
    }

    /**
     * Marks the exact retained version pair; the separate mutation slot protects interrupted restoration.
     *
     * @param jobId             admitted identity
     * @param expectedVersionId saved version
     * @param startedAt         restoration start
     * @return one if accepted, zero if expired, replaced or consumed
     */
    public int markRestoreStarted(String jobId, long expectedVersionId, Instant startedAt) {
        return update(jobId, run -> Objects.equals(run.getAfterVersionId(), expectedVersionId) && run.getRevertedAt() == null, run -> {
            if (run.getRestoreStartedAt() == null) {
                run.setRestoreStartedAt(startedAt);
            }
        });
    }

    private int update(String jobId, Predicate<AuthoringRun> eligible, Consumer<AuthoringRun> change) {
        for (int attempt = 0; attempt < 100; attempt++) {
            AuthoringRun current = runs.get(jobId);
            if (current == null || !eligible.test(current)) {
                return 0;
            }
            AuthoringRun replacement = new AuthoringRun(current);
            change.accept(replacement);
            if (runs.replace(jobId, current, replacement)) {
                return 1;
            }
        }
        throw new IllegalStateException("Could not update authoring activity");
    }

    public record Mutation(String jobId, long sequence) implements java.io.Serializable {
    }
}
