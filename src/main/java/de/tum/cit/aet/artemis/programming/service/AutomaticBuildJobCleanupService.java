package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticBuildJobCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticBuildJobCleanupService.class);

    private final BuildJobRepository buildJobRepository;

    @Value("${artemis.continuous-integration.build-job.retention-period:365}")
    private int buildJobRetentionPeriod;

    public AutomaticBuildJobCleanupService(BuildJobRepository buildJobRepository) {
        this.buildJobRepository = buildJobRepository;
    }

    /**
     * Deletes all build job entries from the database which have a submission date older than buildJobRetentionPeriod days.
     */
    @Scheduled(cron = "0 45 2 * * *") // execute this every night at 2:45:00 am
    public void cleanup() {
        var outdatedBuildJobIds = buildJobRepository.findAllIdsBeforeDate(ZonedDateTime.now().minusDays(buildJobRetentionPeriod));
        log.info("Scheduled deletion of expired build job entries: deleting {} build jobs", outdatedBuildJobIds.size());
        buildJobRepository.deleteAllById(outdatedBuildJobIds);
    }
}
