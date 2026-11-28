package de.tum.cit.aet.artemis.localvc.service;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsStaffRepositoryAccessLogRepository;

@Lazy
@Service
@Profile("scheduling & localvc")
public class AutomaticVcsAccessLogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticVcsAccessLogCleanupService.class);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    private final VcsStaffRepositoryAccessLogRepository vcsStaffRepositoryAccessLogRepository;

    @Value("${artemis.audit-events.retention-period:365}")
    private int vcsAccessLogRetentionPeriod;

    public AutomaticVcsAccessLogCleanupService(VcsAccessLogRepository vcsAccessLogRepository, VcsStaffRepositoryAccessLogRepository vcsStaffRepositoryAccessLogRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.vcsStaffRepositoryAccessLogRepository = vcsStaffRepositoryAccessLogRepository;
    }

    /**
     * Deletes all vcs access log entries from the database which have a timestamp older than vcsAccessLogRetentionPeriod days (365 by default), those of participations and
     * those of the tests and auxiliary repositories alike.
     * The lookups are backed by the indexes on the timestamp of both tables so they do not perform a full table scan.
     */
    @Scheduled(cron = "0 30 2 * * *") // execute this every night at 2:30:00 am
    public void cleanup() {
        var threshold = ZonedDateTime.now().minusDays(vcsAccessLogRetentionPeriod);
        var outDatedAccessLogs = vcsAccessLogRepository.findAllIdsBeforeDate(threshold);
        var outDatedStaffRepositoryAccessLogs = vcsStaffRepositoryAccessLogRepository.findAllIdsBeforeDate(threshold);
        log.info("Scheduled deletion of expired access log entries: deleting {} vcs access log entries and {} entries of tests and auxiliary repositories",
                outDatedAccessLogs.size(), outDatedStaffRepositoryAccessLogs.size());
        vcsAccessLogRepository.deleteAllById(outDatedAccessLogs);
        vcsStaffRepositoryAccessLogRepository.deleteAllById(outDatedStaffRepositoryAccessLogs);
    }
}
