package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.VcsAccessLogRepository;

@Service
@Profile("scheduling & localvc")
public class AutomaticVcsAccessLogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticVcsAccessLogCleanupService.class);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    @Value("${artemis.audit-events.retention-period:120}")
    private int vcsAccessLogRetentionPeriod;

    public AutomaticVcsAccessLogCleanupService(VcsAccessLogRepository vcsAccessLogRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
    }

    /**
     * Deletes all vcs access log entries from the database which have a timestamp older than vcsAccessLogRetentionPeriod days (120 by default)
     */
    @Scheduled(cron = "0 30 2 * * *") // execute this every night at 2:30:00 am
    public void cleanup() {
        var outDatedAccessLogs = vcsAccessLogRepository.findAllIdsBeforeDate(ZonedDateTime.now().minusDays(vcsAccessLogRetentionPeriod));
        log.info("Scheduled deletion of expired access log entries: deleting {} vcs access log entries", outDatedAccessLogs.size());
        vcsAccessLogRepository.deleteAllById(outDatedAccessLogs);
    }
}
