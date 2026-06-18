package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.domain.competency.ContentChangeAccumulator;
import de.tum.cit.aet.artemis.localci.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalMap;

/**
 * Distributed per-course accumulator for Atlas content-change events. Exercise version created
 * events call {@link #record(long, long)} to merge the exercise id into the course's bucket. After
 * the debounce window elapses the scheduler calls {@link #claimDueBatch(long)} to atomically drain
 * and reset the bucket, applying the per-day cap before returning a batch.
 * <p>
 * State lives in the cluster-shared {@code atlas-content-change-accumulator} map obtained through
 * the {@link DistributedDataProvider} abstraction, so the pipeline works on both Hazelcast and Redis
 * deployments without depending on {@code HazelcastInstance} directly. Every mutation runs inside a
 * per-key {@link DistributedMap#lock(Object) lock}/{@link DistributedMap#unlock(Object) unlock}
 * critical section so concurrent updates from different nodes linearise against the key owner — we
 * never lose events under contention.
 * <p>
 * When no {@link DistributedDataProvider} is configured (e.g. a core node using an external CI
 * instead of the integrated LocalCI, where the abstraction is not on the classpath), the accumulator
 * falls back to a node-local map: auto-orchestration then only observes changes recorded on the same
 * node. This is acceptable for the (off-by-default) feature; the supported clustered deployments run
 * with a provider present.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ContentChangeAccumulatorService {

    public static final String MAP_NAME = "atlas-content-change-accumulator";

    private static final Logger log = LoggerFactory.getLogger(ContentChangeAccumulatorService.class);

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private volatile DistributedMap<Long, ContentChangeAccumulator> map;

    private final Clock clock;

    private final int debounceWindowSeconds;

    private final int dailyCap;

    public ContentChangeAccumulatorService(Optional<DistributedDataProvider> distributedDataProvider, Clock clock, AtlasOrchestratorProperties properties) {
        this.distributedDataProvider = distributedDataProvider;
        this.clock = clock;
        this.debounceWindowSeconds = properties.debounceWindowSeconds();
        this.dailyCap = properties.maxDailyOrchestrations();
    }

    /**
     * Resolve the shared accumulator map lazily (not in the constructor) so application startup is
     * not blocked on the distributed data layer. Falls back to a node-local map when no provider is
     * configured.
     */
    private DistributedMap<Long, ContentChangeAccumulator> map() {
        DistributedMap<Long, ContentChangeAccumulator> resolved = map;
        if (resolved == null) {
            synchronized (this) {
                resolved = map;
                if (resolved == null) {
                    resolved = distributedDataProvider.<DistributedMap<Long, ContentChangeAccumulator>>map(provider -> provider.getMap(MAP_NAME)).orElseGet(() -> {
                        log.warn(
                                "No DistributedDataProvider available; Atlas content-change accumulator falls back to a node-local map. Auto-orchestration only observes changes recorded on this node.");
                        return new LocalMap<>();
                    });
                    map = resolved;
                }
            }
        }
        return resolved;
    }

    /**
     * Record an exercise change for a course. Caller must have already filtered exam exercises and
     * other ineligible content — this method does not revalidate.
     *
     * @param courseId   the course the exercise belongs to
     * @param exerciseId id of the exercise that changed
     */
    public void record(long courseId, long exerciseId) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        DistributedMap<Long, ContentChangeAccumulator> currentMap = map();
        currentMap.lock(courseId);
        try {
            ContentChangeAccumulator current = currentMap.get(courseId);
            ContentChangeAccumulator next = current == null ? ContentChangeAccumulator.empty(now, today) : current;
            currentMap.put(courseId, next.with(exerciseId, now));
        }
        finally {
            currentMap.unlock(courseId);
        }
    }

    /**
     * List every course id with an accumulator entry whose debounce window has elapsed and which
     * actually holds buffered content. Iterating the entry set is acceptable because the map is
     * bounded by the number of courses currently debouncing (rarely more than a few dozen).
     *
     * @return course ids ready for the scheduler to attempt a claim against
     */
    public Set<Long> listDueCourseIds() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofSeconds(debounceWindowSeconds));
        Set<Long> due = new HashSet<>();
        for (var entry : map().entrySet()) {
            ContentChangeAccumulator acc = entry.getValue();
            if (acc == null || !acc.hasContent()) {
                continue;
            }
            if (!acc.lastEventTime().isAfter(cutoff)) {
                due.add(entry.getKey());
            }
        }
        return due;
    }

    /**
     * Atomically claim the accumulated batch for a course. Returns {@link Optional#empty()} when:
     * <ul>
     * <li>no entry exists,</li>
     * <li>the debounce window has not yet elapsed,</li>
     * <li>the accumulator is empty, or</li>
     * <li>the per-day cap has been reached.</li>
     * </ul>
     * On success the map entry is reset (id sets cleared, daily counter bumped); the caller owns
     * the returned ids and must drive them through the orchestrator exactly once. The lock-guarded
     * drain guarantees that only one scheduler tick — on any node — claims a given course's batch.
     *
     * @param courseId the course whose accumulated ids should be drained
     * @return the drained batch, or {@link Optional#empty()} when no batch is eligible right now
     */
    public Optional<BatchClaim> claimDueBatch(long courseId) {
        return claim(courseId, dailyCap, true, false);
    }

    /**
     * Force-drain the accumulated batch for a course, ignoring the debounce window and the per-day
     * cap. Used by the manual "suggest competencies" path so a user-initiated run flushes any
     * queued changes immediately and prevents the scheduler from re-processing the same ids after
     * the debounce window elapses. Returns {@link Optional#empty()} only when the accumulator has
     * nothing buffered for the course.
     *
     * @param courseId the course whose accumulated ids should be drained
     * @return the drained batch, or {@link Optional#empty()} when the accumulator is empty
     */
    public Optional<BatchClaim> claimBatchNow(long courseId) {
        return claim(courseId, Integer.MAX_VALUE, false, true);
    }

    /**
     * Atomically drain and reset a course's bucket under the per-key lock. {@code countQuota} bumps
     * the daily-run counter (scheduled claims) and enforces {@code cap}; {@code skipDebounce}
     * bypasses the debounce check (manual force-drain). Leaves the entry untouched and returns empty
     * when nothing is eligible.
     */
    private Optional<BatchClaim> claim(long courseId, int cap, boolean countQuota, boolean skipDebounce) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        Duration debounceWindow = Duration.ofSeconds(debounceWindowSeconds);
        DistributedMap<Long, ContentChangeAccumulator> currentMap = map();
        currentMap.lock(courseId);
        try {
            ContentChangeAccumulator current = currentMap.get(courseId);
            if (current == null || !current.hasContent()) {
                return Optional.empty();
            }
            if (!skipDebounce && current.lastEventTime().plus(debounceWindow).isAfter(now)) {
                return Optional.empty();
            }
            int effectiveDailyCount = today.equals(current.dailyRunCountDate()) ? current.dailyRunCount() : 0;
            if (countQuota && effectiveDailyCount >= cap) {
                return Optional.empty();
            }
            BatchClaim claim = new BatchClaim(current.exerciseIds());
            currentMap.put(courseId, current.claim(today, countQuota));
            return Optional.of(claim);
        }
        finally {
            currentMap.unlock(courseId);
        }
    }

    /**
     * Requeue a batch that was claimed via {@link #claimDueBatch(long)} but could not actually run
     * because a concurrent orchestration held the per-course run lock. Re-merges the exercise ids
     * (restarting the debounce window) and releases the daily-run reservation taken by the claim in
     * a single atomic step, so retry-only ticks never consume automatic quota. A no-op for an empty
     * id set.
     *
     * @param courseId    the course whose batch is being requeued
     * @param exerciseIds the exercise ids to re-add to the accumulator
     */
    public void requeueAfterConcurrentRun(long courseId, Set<Long> exerciseIds) {
        requeue(courseId, exerciseIds, true);
    }

    /**
     * Requeue a batch that was claimed via {@link #claimDueBatch(long)} but failed to run (LLM
     * error, internal prep error, missing chat client, or an exception thrown before any competency
     * was mutated). Re-merges the exercise ids (restarting the debounce window) so the changes are
     * retried on a later tick rather than being silently discarded. Unlike
     * {@link #requeueAfterConcurrentRun}, the daily-run reservation is <em>kept</em>: the run was
     * actually attempted, so the per-day cap bounds how many failed retries a course can burn before
     * giving up for the day. A no-op for an empty id set.
     * <p>
     * Must only be called when no competency mutation was committed (status {@code FAILED}, never
     * {@code PARTIAL}) — re-running a partially applied batch would re-apply the committed changes.
     *
     * @param courseId    the course whose batch is being requeued
     * @param exerciseIds the exercise ids to re-add to the accumulator
     */
    public void requeueAfterFailedRun(long courseId, Set<Long> exerciseIds) {
        requeue(courseId, exerciseIds, false);
    }

    /**
     * Re-merge claimed ids atomically under the per-key lock. When {@code refundDailyRun} is set
     * (concurrent-run requeue) one daily-run reservation is released so a retry-only tick does not
     * burn automatic quota; when unset (failed-run requeue) the reservation is kept so the per-day
     * cap bounds how many failed retries a course can attempt.
     */
    private void requeue(long courseId, Set<Long> exerciseIds, boolean refundDailyRun) {
        if (exerciseIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        Set<Long> idsToRequeue = new HashSet<>(exerciseIds);
        DistributedMap<Long, ContentChangeAccumulator> currentMap = map();
        currentMap.lock(courseId);
        try {
            ContentChangeAccumulator current = currentMap.get(courseId);
            ContentChangeAccumulator next = current == null ? ContentChangeAccumulator.empty(now, today) : current;
            for (Long exerciseId : idsToRequeue) {
                next = next.with(exerciseId, now);
            }
            currentMap.put(courseId, refundDailyRun ? next.refundDailyRun(today) : next);
        }
        finally {
            currentMap.unlock(courseId);
        }
    }

    /** Package-private for tests that inject a mutable clock to fast-forward across day boundaries. */
    Clock clock() {
        return clock;
    }

    /** The completion snapshot returned by {@link #claimDueBatch(long)}. */
    public record BatchClaim(Set<Long> exerciseIds) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** Hook for tests: reset the map between cases without touching private state. */
    void clearForTesting() {
        map().clear();
    }

    /** Hook for integration test scenarios that must cross a day boundary. */
    LocalDate today() {
        return LocalDate.now(clock);
    }

}
