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
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.domain.competency.ContentChangeAccumulator;

/**
 * Distributed per-course accumulator backed by the Hazelcast map
 * {@code atlas-content-change-accumulator}. Exercise version created events call
 * {@link #record(long, long)} to merge the exercise id into the course's bucket. After the
 * debounce window elapses the scheduler calls {@link #claimDueBatch(long)} to atomically drain
 * and reset the bucket, applying the per-day cap before returning a batch.
 * <p>
 * Every mutation uses {@link IMap#executeOnKey} so that concurrent updates from different nodes
 * linearise against the single partition owner — we never read-modify-write on the client side,
 * which would lose events under contention.
 * <p>
 * Also provides a scheduler-local lock ({@link #tryClaimLock} / {@link #releaseLock}) that
 * coordinates concurrent scheduler ticks across nodes against the per-course key. This is *not*
 * the long-lived orchestrator lock from the thesis design — it only ensures one scheduler tick
 * drains a given course's batch at a time.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ContentChangeAccumulatorService {

    public static final String MAP_NAME = "atlas-content-change-accumulator";

    private static final long LOCK_LEASE_SECONDS = 5 * 60;

    private static final long LOCK_TRY_TIMEOUT_MILLIS = 50;

    private static final Logger log = LoggerFactory.getLogger(ContentChangeAccumulatorService.class);

    private final HazelcastInstance hazelcastInstance;

    private IMap<Long, ContentChangeAccumulator> map;

    private final Clock clock;

    private final int debounceWindowSeconds;

    private final int dailyCap;

    public ContentChangeAccumulatorService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, Clock clock, AtlasOrchestratorProperties properties) {
        this.hazelcastInstance = hazelcastInstance;
        this.clock = clock;
        this.debounceWindowSeconds = properties.debounceWindowSeconds();
        this.dailyCap = properties.maxDailyOrchestrations();
    }

    /** Resolved here (not in the constructor) so application startup is not blocked on Hazelcast. */
    @PostConstruct
    void initMap() {
        this.map = hazelcastInstance.getMap(MAP_NAME);
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
        map.executeOnKey(courseId, new MergeEntryProcessor(exerciseId, now, today));
    }

    /**
     * List every course id with an accumulator entry whose debounce window has elapsed and which
     * actually holds buffered content. Iterating the key set is acceptable because the map is
     * bounded by the number of courses currently debouncing (rarely more than a few dozen).
     *
     * @return course ids ready for the scheduler to attempt a claim against
     */
    public Set<Long> listDueCourseIds() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofSeconds(debounceWindowSeconds));
        Set<Long> due = new HashSet<>();
        for (var entry : map.entrySet()) {
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
     * the returned ids and must drive them through the orchestrator exactly once.
     *
     * @param courseId the course whose accumulated ids should be drained
     * @return the drained batch, or {@link Optional#empty()} when no batch is eligible right now
     */
    public Optional<BatchClaim> claimDueBatch(long courseId) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        BatchClaim claim = map.executeOnKey(courseId, new ClaimEntryProcessor(now, Duration.ofSeconds(debounceWindowSeconds), dailyCap, today, true, false));
        return Optional.ofNullable(claim);
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
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        BatchClaim claim = map.executeOnKey(courseId, new ClaimEntryProcessor(now, Duration.ofSeconds(debounceWindowSeconds), Integer.MAX_VALUE, today, false, true));
        return Optional.ofNullable(claim);
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
        if (exerciseIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        map.executeOnKey(courseId, new RequeueEntryProcessor(new HashSet<>(exerciseIds), now, today, true));
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
        if (exerciseIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        map.executeOnKey(courseId, new RequeueEntryProcessor(new HashSet<>(exerciseIds), now, today, false));
    }

    /**
     * Best-effort scheduler-local lock on a course key. Used by {@code ContentChangeScheduler} to
     * ensure only one node drains a given course's batch per tick. Returns {@code true} when the
     * lock was acquired, {@code false} when another tick already owns it. The lease bounds the
     * worst-case orchestrator run plus a safety margin, so a crashed node never blocks the queue
     * indefinitely.
     *
     * @param courseId course key to lock
     * @return whether the lock was acquired
     */
    public boolean tryClaimLock(long courseId) {
        try {
            return map.tryLock(courseId, LOCK_TRY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Release the scheduler-local lock previously acquired via {@link #tryClaimLock(long)}.
     * Safe to call even when the current thread does not hold the lock (Hazelcast logs and
     * ignores) — the scheduler always wraps acquire/release in a try/finally.
     *
     * @param courseId course key whose lock to release
     */
    public void releaseLock(long courseId) {
        try {
            map.unlock(courseId);
        }
        catch (IllegalMonitorStateException ex) {
            log.warn("atlas.automatic releaseLock courseId={} called without owning the lock: {}", courseId, ex.getMessage());
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

    /**
     * Hazelcast-side merge: append a single exercise id into the per-course accumulator, bumping
     * {@code lastEventTime} so the debounce window restarts. Runs atomically on the partition
     * owner — concurrent calls across nodes linearise through this processor, so no event is lost.
     */
    static final class MergeEntryProcessor implements EntryProcessor<Long, ContentChangeAccumulator, Void>, Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final long exerciseId;

        private final Instant now;

        private final LocalDate today;

        MergeEntryProcessor(long exerciseId, Instant now, LocalDate today) {
            this.exerciseId = exerciseId;
            this.now = now;
            this.today = today;
        }

        @Override
        public Void process(java.util.Map.Entry<Long, ContentChangeAccumulator> entry) {
            ContentChangeAccumulator current = entry.getValue();
            ContentChangeAccumulator next = current == null ? ContentChangeAccumulator.empty(now, today) : current;
            entry.setValue(next.with(exerciseId, now));
            return null;
        }
    }

    /**
     * Hazelcast-side claim: atomically drains the buffered ids and bumps the daily run counter.
     * Returns {@code null} when the debounce window has not elapsed, the accumulator has nothing
     * to claim, or the per-day cap has been reached — each case leaves the entry untouched.
     */
    static final class ClaimEntryProcessor implements EntryProcessor<Long, ContentChangeAccumulator, BatchClaim>, Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final Instant now;

        private final Duration debounceWindow;

        private final int dailyCap;

        private final LocalDate today;

        private final boolean countQuota;

        private final boolean skipDebounce;

        ClaimEntryProcessor(Instant now, Duration debounceWindow, int dailyCap, LocalDate today, boolean countQuota, boolean skipDebounce) {
            this.now = now;
            this.debounceWindow = debounceWindow;
            this.dailyCap = dailyCap;
            this.today = today;
            this.countQuota = countQuota;
            this.skipDebounce = skipDebounce;
        }

        @Override
        public @Nullable BatchClaim process(java.util.Map.Entry<Long, ContentChangeAccumulator> entry) {
            ContentChangeAccumulator current = entry.getValue();
            if (current == null || !current.hasContent()) {
                return null;
            }
            if (!skipDebounce && current.lastEventTime().plus(debounceWindow).isAfter(now)) {
                return null;
            }
            int effectiveDailyCount = today.equals(current.dailyRunCountDate()) ? current.dailyRunCount() : 0;
            if (countQuota && effectiveDailyCount >= dailyCap) {
                return null;
            }
            BatchClaim claim = new BatchClaim(current.exerciseIds());
            entry.setValue(current.claim(today, countQuota));
            return claim;
        }
    }

    /**
     * Hazelcast-side requeue: re-merge claimed ids atomically. When {@code refundDailyRun} is set
     * (concurrent-run requeue) one daily-run reservation is released so a retry-only tick does not
     * burn automatic quota; when unset (failed-run requeue) the reservation is kept so the per-day
     * cap bounds how many failed retries a course can attempt.
     */
    static final class RequeueEntryProcessor implements EntryProcessor<Long, ContentChangeAccumulator, Void>, Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final Set<Long> exerciseIds;

        private final Instant now;

        private final LocalDate today;

        private final boolean refundDailyRun;

        RequeueEntryProcessor(Set<Long> exerciseIds, Instant now, LocalDate today, boolean refundDailyRun) {
            this.exerciseIds = exerciseIds;
            this.now = now;
            this.today = today;
            this.refundDailyRun = refundDailyRun;
        }

        @Override
        public Void process(java.util.Map.Entry<Long, ContentChangeAccumulator> entry) {
            ContentChangeAccumulator current = entry.getValue();
            ContentChangeAccumulator next = current == null ? ContentChangeAccumulator.empty(now, today) : current;
            for (Long exerciseId : exerciseIds) {
                next = next.with(exerciseId, now);
            }
            entry.setValue(refundDailyRun ? next.refundDailyRun(today) : next);
            return null;
        }
    }

    /** Hook for tests: reset the map between cases without touching private state. */
    void clearForTesting() {
        map.clear();
    }

    /** Hook for integration test scenarios that must cross a day boundary. */
    LocalDate today() {
        return LocalDate.now(clock);
    }

}
