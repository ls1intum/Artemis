package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;

/**
 * Prunes {@code feature_usage_daily} nightly.
 * <p>
 * Without this the table would grow for as long as the server runs. It grows slowly, on the order of a couple of thousand
 * rows a day, but the analysis has no use for a bucket from three years ago, so keeping it would be cost without benefit.
 * <p>
 * The default retention of 400 days is chosen so the longest window the admin page offers (180 days) can still be
 * compared against the same period a year earlier. A shorter retention would prune exactly the data a year-over-year
 * comparison needs at exactly the moment it needs it.
 * <p>
 * A single bulk delete is enough here, unlike the audit log pruning which has to delete entity by entity because of an
 * element collection with a restricting foreign key. This table has no children and nothing references it. It is also
 * pruned from the start rather than after years of accumulation, so a run only ever removes about one day of rows.
 * <p>
 * The inventory in {@code tracked_feature} is deliberately not pruned. It is small and bounded by the number of features
 * that have ever existed, and an entry whose buckets have all expired is still the answer to "was this ever used".
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticFeatureUsageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticFeatureUsageCleanupService.class);

    private final FeatureUsageDailyRepository featureUsageDailyRepository;

    private final FeatureUsageProperties properties;

    public AutomaticFeatureUsageCleanupService(FeatureUsageDailyRepository featureUsageDailyRepository, FeatureUsageProperties properties) {
        this.featureUsageDailyRepository = featureUsageDailyRepository;
        this.properties = properties;
    }

    /**
     * Deletes expired daily usage buckets.
     */
    // execute this every night at 3:25:00 am, offset from the other nightly cleanups so they do not contend
    @Scheduled(cron = "0 25 3 * * *")
    public void cleanup() {
        int retentionInDays = properties.retentionPeriod();
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionInDays);
        try {
            int deleted = featureUsageDailyRepository.deleteAllOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Scheduled deletion of expired feature usage: removed {} daily buckets from before {} (retention {} days)", deleted, cutoff, retentionInDays);
            }
        }
        catch (Exception e) {
            log.error("Failed to prune feature usage buckets from before {} (retention {} days)", cutoff, retentionInDays, e);
        }
    }
}
