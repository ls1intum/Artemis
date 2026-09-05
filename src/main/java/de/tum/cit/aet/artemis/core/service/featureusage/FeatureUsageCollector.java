package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Accumulates feature usage in memory between flushes.
 * <p>
 * Recording is a map lookup and a few {@link LongAdder} increments, so it costs well under a microsecond and never
 * touches the database. Nothing user-identifying is held: a call contributes to a counter keyed by feature, UTC day and
 * role bucket, and the caller's identity is not read.
 * <p>
 * Counters are cumulative and the flush reports the difference to what it reported last time, rather than resetting them.
 * Resetting would race with concurrent recording and silently drop calls; a monotonic counter with a remembered
 * watermark cannot.
 * <p>
 * Recording never propagates a failure. A usage counter must not be able to break the request it is measuring.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class FeatureUsageCollector {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageCollector.class);

    private final FeatureUsageProperties properties;

    /**
     * The registry is resolved on first use rather than injected. Several services inject this collector, some of them close
     * to the startup path, and injecting the registry would put its repository and the JPA infrastructure behind it into
     * those dependency chains. Only the git and background paths need the registry at all; the REST path already has the
     * feature id.
     */
    private final ApplicationContext applicationContext;

    private volatile FeatureUsageRegistry registry;

    private final Map<UsageKey, UsageAccumulator> buckets = new ConcurrentHashMap<>();

    /**
     * How many observations may wait to be applied. One observation is a handful of primitives, so this costs tens of
     * kilobytes at most, and it is far more than a flush interval of traffic on a busy node.
     */
    private static final int PENDING_OBSERVATION_CAPACITY = 20_000;

    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    /**
     * Applies observations away from the thread that produced them, so that nothing about counting a request can lengthen
     * or interfere with it.
     * <p>
     * A dedicated single thread rather than the shared application executor: usage tracking must neither compete with
     * application work for threads nor be starved by it. One thread is enough, because applying an observation is a map
     * lookup and four counter updates, and a single applier removes all contention between recorders.
     * <p>
     * The queue is bounded and full means discard. That is the point rather than a limitation: a counter must never apply
     * back-pressure to a request, so losing statistics is strictly preferable to making somebody wait. Discards are
     * counted and reported by the flush.
     * <p>
     * {@link LinkedBlockingQueue} rather than {@link java.util.concurrent.ArrayBlockingQueue} deliberately: the array
     * variant guards the whole queue with one lock, so every request thread handing over an observation would contend
     * with the recording thread taking one off. The linked variant holds separate put and take locks, so producers never
     * contend with the consumer - which is the property that matters when the producers are request threads. It allocates
     * a node per observation, and that is the right trade here.
     */
    private final Executor recorder;

    private final LongAdder discardedObservations = new LongAdder();

    /**
     * Annotated because the package-private constructor below gives this class two candidates, which Spring will not
     * choose between on its own.
     */
    @Autowired
    public FeatureUsageCollector(FeatureUsageProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.recorder = boundedSingleThreadRecorder();
    }

    /**
     * Package-private so a test can apply observations on the calling thread and stay deterministic. Handing recording to
     * another thread is exactly what production needs and exactly what a unit test must not have to wait for.
     *
     * @param properties         the feature usage configuration
     * @param applicationContext used to resolve the registry on first use
     * @param recorder           applies observations; production hands them off, tests run them inline
     */
    FeatureUsageCollector(FeatureUsageProperties properties, ApplicationContext applicationContext, Executor recorder) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.recorder = recorder;
    }

    private Executor boundedSingleThreadRecorder() {
        // Silence on a discard would be worse than the loss: the page would under-report without saying so.
        RejectedExecutionHandler discardAndCount = (runnable, executor) -> discardedObservations.increment();
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(PENDING_OBSERVATION_CAPACITY), runnable -> {
            var thread = new Thread(runnable, "feature-usage-recorder");
            // a statistics thread must never hold up JVM shutdown
            thread.setDaemon(true);
            return thread;
        }, discardAndCount);
    }

    private FeatureUsageRegistry registry() {
        FeatureUsageRegistry resolved = registry;
        if (resolved == null) {
            resolved = applicationContext.getBean(FeatureUsageRegistry.class);
            registry = resolved;
        }
        return resolved;
    }

    /**
     * Whether usage is being recorded at all. Call sites that would otherwise do measurable work before recording (such
     * as reading a clock or resolving a role) should check this first.
     *
     * @return true if recording is enabled for this deployment
     */
    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Records one call of a feature whose id is already known, which is the case for every REST endpoint.
     *
     * @param featureId  the inventory row of the feature
     * @param callerRole the caller's highest global role
     * @param failed     whether the call failed
     * @param durationMs how long the call took
     */
    public void recordUsage(long featureId, Role callerRole, boolean failed, long durationMs) {
        if (!isEnabled()) {
            return;
        }
        long usageDay = currentUtcEpochDay();
        submit(() -> accumulate(featureId, usageDay, callerRole, failed, durationMs), () -> "feature " + featureId);
    }

    /**
     * Records one REST call, resolving the feature from its handler method.
     * <p>
     * The resolution happens on the recording thread rather than at the call site, so the request path never touches the
     * registry - including the one-off cost of creating that bean, and everything behind it, on the first request.
     *
     * @param handlerMethod the handler method that served the request
     * @param callerRole    the caller's highest global role
     * @param failed        whether the call failed
     * @param durationMs    how long the call took
     */
    public void recordRestUsage(Method handlerMethod, Role callerRole, boolean failed, long durationMs) {
        if (!isEnabled()) {
            return;
        }
        long usageDay = currentUtcEpochDay();
        submit(() -> {
            Long featureId = registry().restFeatureId(handlerMethod);
            if (featureId == null) {
                // not part of the inventory, for instance a handler registered after the startup pass
                return;
            }
            accumulate(featureId, usageDay, callerRole, failed, durationMs);
        }, () -> "handler " + handlerMethod);
    }

    /**
     * Records one use of a git or background feature, registering it in the inventory on first sighting.
     *
     * @param featureKind the namespace, {@link FeatureKind#GIT} or {@link FeatureKind#BACKGROUND}
     * @param module      the Artemis module the feature belongs to
     * @param identifier  the canonical identifier within the namespace
     * @param callerRole  the caller's highest global role, or {@link Role#ANONYMOUS} when there is no caller
     * @param failed      whether the operation failed
     * @param durationMs  how long the operation took
     */
    public void recordUsage(FeatureKind featureKind, String module, String identifier, Role callerRole, boolean failed, long durationMs) {
        if (!isEnabled()) {
            return;
        }
        long usageDay = currentUtcEpochDay();
        submit(() -> {
            Long featureId = registry().featureId(featureKind, module, identifier);
            if (featureId == null) {
                return;
            }
            accumulate(featureId, usageDay, callerRole, failed, durationMs);
        }, () -> featureKind + " feature " + identifier);
    }

    /**
     * Hands one observation to the recording thread. Never throws and never blocks: a full queue discards, so a counter
     * cannot make its caller wait, and a rejected or failing observation costs statistics rather than an operation.
     */
    private void submit(Runnable observation, Supplier<String> describe) {
        try {
            recorder.execute(() -> {
                try {
                    observation.run();
                }
                catch (Exception e) {
                    log.debug("Failed to record usage of {}", describe.get(), e);
                }
            });
        }
        catch (Exception e) {
            // a saturated or shut down recorder must not surface to the caller
            discardedObservations.increment();
        }
    }

    /**
     * The UTC day is read where the observation happens, not where it is applied.
     * <p>
     * Recording is deferred, so reading the clock on the recording thread would attribute a request made just before
     * midnight to the following day. Computed as an epoch day rather than a {@link LocalDate} to keep the call site free
     * of allocation.
     */
    private static long currentUtcEpochDay() {
        return Math.floorDiv(System.currentTimeMillis(), TimeUnit.DAYS.toMillis(1));
    }

    /**
     * Synchronized on the same monitor as {@link #drain} and {@link #reclaim}, so that looking a bucket up and updating
     * its counters cannot interleave with a flush removing that bucket.
     * <p>
     * Without it, the recorder can take an accumulator out of the map, a flush can decide the same bucket is a closed day
     * with nothing new and drop it, and the increments then land on a detached object and are lost. Deferring recording
     * widened that window from nanoseconds to however long an observation waits in the queue, which can easily cross the
     * UTC midnight that makes a bucket removable in the first place.
     * <p>
     * Affordable only because recording is deferred: this lock is taken by the single recording thread and the flush, and
     * never by a request thread. Synchronizing here while requests still accumulated inline would have serialised every
     * request in the application behind one monitor.
     */
    private synchronized void accumulate(long featureId, long usageDay, Role callerRole, boolean failed, long durationMs) {
        UsageKey key = new UsageKey(featureId, LocalDate.ofEpochDay(usageDay), callerRole);
        UsageAccumulator accumulator = buckets.computeIfAbsent(key, ignored -> new UsageAccumulator());
        accumulator.callCount.increment();
        if (failed) {
            accumulator.errorCount.increment();
        }
        int cappedDurationMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs));
        accumulator.durationSumMs.add(cappedDurationMs);
        accumulator.durationMaxMs.accumulateAndGet(cappedDurationMs, Math::max);
    }

    /**
     * Applies everything still queued, so a flush can see it.
     * <p>
     * Called before the flush on shutdown: recording is asynchronous, so without this a graceful restart would drop
     * whatever had not been applied yet, on top of the interval the design already accepts losing. Idempotent, and it
     * waits only a few seconds - a shutdown must not be held up by statistics either.
     *
     * @return true if everything queued was applied, false on timeout or interruption
     */
    public boolean applyPendingObservations() {
        if (!(recorder instanceof ThreadPoolExecutor pool)) {
            // an inline recorder has nothing pending by construction
            return true;
        }
        pool.shutdown();
        try {
            return pool.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void applyPendingObservationsOnShutdown() {
        if (!applyPendingObservations()) {
            log.warn("Feature usage recording did not finish within {}; some observations were not persisted", SHUTDOWN_GRACE);
        }
    }

    /**
     * How many observations were dropped because the recording queue was full, reset to zero by this call.
     * <p>
     * Reported by the flush rather than logged per drop: under the load that fills the queue, a line per discard would be
     * its own problem.
     *
     * @return the number of observations discarded since the previous call
     */
    public long consumeDiscardedObservationCount() {
        return discardedObservations.sumThenReset();
    }

    /**
     * Returns everything accumulated since the previous call and advances the watermarks.
     * <p>
     * Synchronized because the periodic flush and the flush on shutdown can otherwise overlap, and two threads advancing
     * the same watermark would report part of the usage twice.
     *
     * @param today the current UTC day, used to decide which closed buckets can be dropped
     * @return one delta per bucket whose counters moved since the previous flush
     */
    public synchronized List<FeatureUsageDelta> drain(LocalDate today) {
        List<FeatureUsageDelta> deltas = new ArrayList<>();
        Iterator<Map.Entry<UsageKey, UsageAccumulator>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UsageKey, UsageAccumulator> entry = iterator.next();
            UsageKey key = entry.getKey();
            UsageAccumulator accumulator = entry.getValue();

            long callCount = accumulator.callCount.sum();
            long errorCount = accumulator.errorCount.sum();
            long durationSumMs = accumulator.durationSumMs.sum();
            // Read before the gate and reported from this snapshot, so the value that is written is exactly the value the
            // watermark below then claims has been written. Re-reading it after the gate would let a concurrent update
            // slip in between the two and be marked as reported without ever having been.
            int durationMaxMs = accumulator.durationMaxMs.get();
            long callDelta = callCount - accumulator.flushedCallCount;
            long errorDelta = errorCount - accumulator.flushedErrorCount;
            long durationDelta = durationSumMs - accumulator.flushedDurationSumMs;
            boolean maximumRose = durationMaxMs > accumulator.flushedDurationMaxMs;
            // All four counters are examined, not just the call counter. The counters of one observation are updated one
            // after another, so a flush landing between them sees the call but not yet its error, its duration or its
            // maximum. Gating on calls alone would then advance the watermarks past that call and skip the bucket on the
            // next flush, because no new call arrived - and the failure, the latency and the slowest call of that request
            // would never be reported. The maximum needs a watermark of its own for this: it is a running maximum rather
            // than a sum, so there is no delta to notice it by.
            if (callDelta <= 0 && errorDelta <= 0 && durationDelta <= 0 && !maximumRose) {
                // A bucket of a day that is over and saw nothing since the last flush is finished with. Dropping it here
                // is what keeps the map bounded over a long uptime instead of holding every day the process has seen.
                if (key.usageDay().isBefore(today)) {
                    iterator.remove();
                }
                continue;
            }

            accumulator.flushedCallCount = callCount;
            accumulator.flushedErrorCount = errorCount;
            accumulator.flushedDurationSumMs = durationSumMs;
            accumulator.flushedDurationMaxMs = durationMaxMs;
            deltas.add(new FeatureUsageDelta(key.featureId(), key.usageDay(), key.callerRole(), callDelta, errorDelta, durationDelta, durationMaxMs));
        }
        return deltas;
    }

    /**
     * Returns a delta to the collector after its write failed, so the next flush reports it again instead of dropping
     * it.
     * <p>
     * {@link #drain} advances each bucket's watermark as it hands the delta out, which is what makes a drain report only
     * what is new. Without this, a write that failed - a transient database error, a lost connection - left its counts
     * behind the watermark and they were never reported again: the calls in that bucket were lost rather than retried.
     * <p>
     * The additive counters are wound back by the amount that was handed out. The maximum is wound back to zero instead,
     * because the delta carries the running maximum rather than an increment and the previous watermark is not recoverable
     * from it. Zero simply guarantees the next drain reports the maximum again, which is safe: the stored value is updated
     * to the greater of the two, so reporting a maximum twice cannot corrupt it.
     * <p>
     * This makes a failed flush at-least-once rather than at-most-once. A write that reached the database but reported
     * failure afterwards would be counted twice on the retry. That is the deliberate trade: for usage statistics, an
     * occasional double count on a transient error is worth less harm than silently losing a bucket, and losing one is
     * invisible while over-counting at least stays consistent with the monotonic counters.
     *
     * @param delta the delta whose write failed
     */
    public synchronized void reclaim(FeatureUsageDelta delta) {
        UsageAccumulator accumulator = buckets.get(new UsageKey(delta.featureId(), delta.usageDay(), delta.callerRole()));
        if (accumulator == null) {
            // The bucket is gone, which means its day is over and it had nothing new; there is nothing to retry into.
            return;
        }
        accumulator.flushedCallCount -= delta.callCount();
        accumulator.flushedErrorCount -= delta.errorCount();
        accumulator.flushedDurationSumMs -= delta.durationSumMs();
        accumulator.flushedDurationMaxMs = 0;
    }

    /**
     * The identity of one bucket.
     *
     * @param featureId  the feature being measured
     * @param usageDay   the UTC day, part of the key so a flush that crosses midnight still attributes correctly
     * @param callerRole the caller's highest global role
     */
    private record UsageKey(long featureId, LocalDate usageDay, Role callerRole) {
    }

    /**
     * Monotonic counters for one bucket. The {@code flushed*} fields are the watermarks of the last reported values and
     * are only ever touched by the flush, under this collector's monitor.
     */
    private static final class UsageAccumulator {

        private final LongAdder callCount = new LongAdder();

        private final LongAdder errorCount = new LongAdder();

        private final LongAdder durationSumMs = new LongAdder();

        private final AtomicInteger durationMaxMs = new AtomicInteger();

        private long flushedCallCount;

        private long flushedErrorCount;

        private long flushedDurationSumMs;

        private int flushedDurationMaxMs;
    }
}
