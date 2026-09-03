package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.FeatureUsageDaily;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;

/**
 * Writes the counters accumulated on this node into the daily buckets.
 * <p>
 * Runs on every node, not just the scheduling one, because each node accumulates its own counters and nothing else would
 * ever persist them.
 * <p>
 * Concurrent writes are handled by making the write additive: the update adds this node's delta to whatever is stored, so
 * two nodes flushing the same bucket cannot overwrite each other. The insert is the only step that can conflict, and it
 * happens at most once per bucket, so the loser of that race simply adds to the winner's row.
 * <p>
 * There is deliberately no {@code ON CONFLICT} or {@code ON DUPLICATE KEY UPDATE} here. Artemis runs on PostgreSQL and on
 * MySQL, and one portable statement pair is worth more than the round trip an upsert would save on a job that runs every
 * few minutes.
 * <p>
 * Up to one flush interval of counters is lost if a node is killed. That is an acceptable trade for usage analytics and
 * the reason the interval is minutes rather than hours.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class FeatureUsageFlushService {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageFlushService.class);

    private final FeatureUsageCollector collector;

    private final FeatureUsageDailyRepository featureUsageDailyRepository;

    public FeatureUsageFlushService(FeatureUsageCollector collector, FeatureUsageDailyRepository featureUsageDailyRepository) {
        this.collector = collector;
        this.featureUsageDailyRepository = featureUsageDailyRepository;
    }

    /**
     * Persists everything accumulated since the previous run.
     */
    @Scheduled(fixedRateString = "${artemis.feature-usage.flush-interval:5m}", initialDelayString = "${artemis.feature-usage.flush-interval:5m}")
    public void flush() {
        if (!collector.isEnabled()) {
            return;
        }
        try {
            writeDeltas(collector.drain(LocalDate.now(ZoneOffset.UTC)));
        }
        catch (Exception e) {
            // The counters are cumulative and every delta whose write failed is handed back to the collector, so a
            // failed run reports its buckets again on the next one rather than losing them. A throw from drain itself
            // leaves the watermarks untouched, so nothing is lost there either.
            log.error("Failed to flush feature usage counters", e);
        }
    }

    /**
     * Flushes on graceful shutdown, so a planned restart or a rolling deployment does not discard the current interval.
     */
    @PreDestroy
    public void flushOnShutdown() {
        // Recording is asynchronous, so the queue has to be applied before the counters are read. Called here as well as
        // from the collector's own @PreDestroy because bean destruction order is not guaranteed, and it is idempotent.
        collector.applyPendingObservations();
        flush();
    }

    private void writeDeltas(List<FeatureUsageDelta> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        int written = 0;
        for (FeatureUsageDelta delta : deltas) {
            if (write(delta)) {
                written++;
            }
            else {
                // drain() already advanced this bucket's watermark, so without giving the delta back these calls would
                // never be reported again - a failed write would lose the bucket rather than retry it.
                collector.reclaim(delta);
            }
        }
        long discarded = collector.consumeDiscardedObservationCount();
        if (discarded > 0) {
            // The counters are then a lower bound rather than a count, which is worth a line: the alternative to
            // dropping was making requests wait for a statistics queue.
            log.warn("Discarded {} feature usage observations since the previous flush because the recording queue was full", discarded);
        }
        if (written < deltas.size()) {
            log.warn("Flushed feature usage for {} of {} buckets; the rest were returned to the collector and are retried on the next flush", written, deltas.size());
        }
        else {
            log.debug("Flushed feature usage for {} of {} buckets", written, deltas.size());
        }
    }

    private boolean write(FeatureUsageDelta delta) {
        try {
            if (addToExistingBucket(delta)) {
                return true;
            }
            try {
                featureUsageDailyRepository.save(new FeatureUsageDaily(delta.featureId(), delta.usageDay(), delta.callerRole(), delta.callCount(), delta.errorCount(),
                        delta.durationSumMs(), delta.durationMaxMs()));
                return true;
            }
            catch (DataIntegrityViolationException e) {
                // another node inserted the same bucket between our update and our insert, so add to theirs instead
                return addToExistingBucket(delta);
            }
        }
        catch (Exception e) {
            log.warn("Could not flush feature usage bucket for feature {} on {}", delta.featureId(), delta.usageDay(), e);
            return false;
        }
    }

    private boolean addToExistingBucket(FeatureUsageDelta delta) {
        return featureUsageDailyRepository.addUsage(delta.featureId(), delta.usageDay(), delta.callerRole(), delta.callCount(), delta.errorCount(), delta.durationSumMs(),
                delta.durationMaxMs()) > 0;
    }
}
