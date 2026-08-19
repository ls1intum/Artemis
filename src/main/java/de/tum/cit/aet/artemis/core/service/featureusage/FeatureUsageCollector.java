package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public FeatureUsageCollector(FeatureUsageProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
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
        try {
            accumulate(featureId, callerRole, failed, durationMs);
        }
        catch (Exception e) {
            log.debug("Failed to record usage of feature {}", featureId, e);
        }
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
        try {
            Long featureId = registry().featureId(featureKind, module, identifier);
            if (featureId == null) {
                return;
            }
            accumulate(featureId, callerRole, failed, durationMs);
        }
        catch (Exception e) {
            log.debug("Failed to record usage of {} feature {}", featureKind, identifier, e);
        }
    }

    private void accumulate(long featureId, Role callerRole, boolean failed, long durationMs) {
        UsageKey key = new UsageKey(featureId, LocalDate.now(ZoneOffset.UTC), callerRole);
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
     * Returns everything accumulated since the previous call and advances the watermarks.
     * <p>
     * Synchronized because the periodic flush and the flush on shutdown can otherwise overlap, and two threads advancing
     * the same watermark would report part of the usage twice.
     *
     * @param today the current UTC day, used to decide which closed buckets can be dropped
     * @return one delta per bucket that saw at least one call since the previous flush
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
            long callDelta = callCount - accumulator.flushedCallCount;
            if (callDelta <= 0) {
                // A bucket of a day that is over and saw nothing since the last flush is finished with. Dropping it here
                // is what keeps the map bounded over a long uptime instead of holding every day the process has seen.
                if (key.usageDay().isBefore(today)) {
                    iterator.remove();
                }
                continue;
            }

            long errorDelta = errorCount - accumulator.flushedErrorCount;
            long durationDelta = durationSumMs - accumulator.flushedDurationSumMs;
            accumulator.flushedCallCount = callCount;
            accumulator.flushedErrorCount = errorCount;
            accumulator.flushedDurationSumMs = durationSumMs;
            deltas.add(new FeatureUsageDelta(key.featureId(), key.usageDay(), key.callerRole(), callDelta, errorDelta, durationDelta, accumulator.durationMaxMs.get()));
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
     * Only the additive counters are wound back. {@code durationMaxMs} is a maximum rather than a sum, so re-reporting
     * it is harmless.
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
    }
}
