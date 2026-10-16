package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    private final DistributedMap<Long, List<RunReference>> ownerRuns;

    private final DistributedMap<String, Long> sequence;

    public GenerationRunStoreService(DistributedDataProvider data, @Value("${artemis.hyperion.generation.terminal-replay-ttl:PT4H}") Duration retention) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Activity retention must be positive");
        }
        this.retention = retention;
        runs = data.getExpiringMap("hyperion-authoring-activity", retention);
        latest = data.getExpiringMap("hyperion-authoring-latest-mutation", retention);
        ownerRuns = data.getExpiringMap("hyperion-authoring-owner-runs", retention);
        sequence = data.getMap("hyperion-authoring-activity-sequence");
    }

    /**
     * Records a detached identity once. Unsuccessful runs expire from admission; a saved run gets a new undo window when it completes.
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
        try {
            indexRun(value);
        }
        catch (RuntimeException exception) {
            runs.remove(value.getJobId(), value);
            throw exception;
        }
        return new AuthoringRun(value);
    }

    private void indexRun(AuthoringRun run) {
        long ownerId = run.getOwnerId();
        ownerRuns.lock(ownerId);
        try {
            List<RunReference> references = ownerRuns.get(ownerId);
            if (references == null) {
                references = retainedReferences(ownerId);
            }
            Map<String, AuthoringRun> retained = runs.getAll(references.stream().map(RunReference::jobId).collect(Collectors.toSet()));
            List<RunReference> updated = new ArrayList<>(references.stream().filter(reference -> retained.containsKey(reference.jobId())).toList());
            if (updated.stream().noneMatch(reference -> reference.jobId().equals(run.getJobId()))) {
                updated.add(new RunReference(run.getId(), run.getJobId()));
            }
            updated.sort(Comparator.comparingLong(RunReference::id).reversed());
            ownerRuns.put(ownerId, List.copyOf(updated), retention);
        }
        finally {
            ownerRuns.unlock(ownerId);
        }
    }

    private List<RunReference> retainedReferences(long ownerId) {
        return runs.values().stream().filter(run -> Objects.equals(run.getOwnerId(), ownerId)).map(run -> new RunReference(run.getId(), run.getJobId()))
                .sorted(Comparator.comparingLong(RunReference::id).reversed()).toList();
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

    /**
     * Reads one owner page from its index, with a one-time backfill when that index is absent.
     *
     * @param ownerId  author whose runs are requested
     * @param beforeId exclusive cursor, or null for the newest page
     * @param page     requested page size
     * @return detached retained runs, newest first
     */
    public List<AuthoringRun> findOwnedBefore(long ownerId, Long beforeId, Pageable page) {
        List<RunReference> references = ownerRuns.get(ownerId);
        if (references == null) {
            ownerRuns.lock(ownerId);
            try {
                references = ownerRuns.get(ownerId);
                if (references == null) {
                    references = retainedReferences(ownerId);
                    ownerRuns.put(ownerId, references, retention);
                }
            }
            finally {
                ownerRuns.unlock(ownerId);
            }
        }
        List<AuthoringRun> pageRuns = new ArrayList<>(page.getPageSize());
        List<RunReference> candidates = references.stream().filter(reference -> beforeId == null || reference.id() < beforeId).toList();
        for (int offset = 0; offset < candidates.size() && pageRuns.size() < page.getPageSize(); offset += page.getPageSize()) {
            List<RunReference> batch = candidates.subList(offset, Math.min(offset + page.getPageSize(), candidates.size()));
            Set<String> jobIds = batch.stream().map(RunReference::jobId).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, AuthoringRun> found = runs.getAll(jobIds);
            for (RunReference reference : batch) {
                AuthoringRun run = found.get(reference.jobId());
                if (run != null && Objects.equals(run.getOwnerId(), ownerId) && Objects.equals(run.getId(), reference.id())) {
                    pageRuns.add(new AuthoringRun(run));
                    if (pageRuns.size() == page.getPageSize()) {
                        return pageRuns;
                    }
                }
            }
        }
        return pageRuns;
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
        int updated = update(jobId, run -> run.getFinishedAt() == null, run -> {
            run.setStatus(status);
            run.setFinishedAt(finishedAt);
            run.setLiveExerciseChanged(changed);
        });
        if (updated == 1 && (status == AuthoringRun.Status.SAVED || status == AuthoringRun.Status.NEEDS_REVIEW)) {
            AuthoringRun run = runs.get(jobId);
            if (run != null) {
                runs.refreshTimeToLive(jobId, retention);
                Mutation mutation = latest.get(run.getExerciseId());
                if (mutation != null && mutation.jobId().equals(jobId)) {
                    latest.refreshTimeToLive(run.getExerciseId(), retention);
                }
            }
        }
        return updated;
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

    public record RunReference(long id, String jobId) implements java.io.Serializable {
    }
}
