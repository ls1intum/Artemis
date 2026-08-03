package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Prunes the persisted lecture ingestion history so it does not grow unbounded: on the scheduling node, on a schedule,
 * it deletes entries older than the configured retention (default 30 days). Both the retention window and the schedule
 * are configurable.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class IngestionHistoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IngestionHistoryCleanupService.class);

    private final int retentionDays;

    private final IngestionProgressService ingestionProgressService;

    public IngestionHistoryCleanupService(@Value("${artemis.iris.ingestion-history.retention-days:30}") int retentionDays, IngestionProgressService ingestionProgressService) {
        this.retentionDays = retentionDays;
        this.ingestionProgressService = ingestionProgressService;
    }

    /**
     * Deletes ingestion history entries older than the retention window on the configured schedule (default daily).
     */
    @Scheduled(cron = "${artemis.iris.ingestion-history.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    public void pruneOldHistory() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(retentionDays);
        int deleted = ingestionProgressService.pruneHistoryBefore(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} ingestion history entries older than {} days", deleted, retentionDays);
        }
    }
}
