package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Service responsible for executing a scheduled job to retrigger builds for programming submissions without results.
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class ProgrammingSubmissionWithoutResultScheduleService {

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private static final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionWithoutResultScheduleService.class);

    public ProgrammingSubmissionWithoutResultScheduleService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingTriggerService programmingTriggerService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingTriggerService = programmingTriggerService;
    }

    /**
     * Find the latest programming submissions per participation older than 2 hours but not older than 2 days without results and retrigger their builds.
     * Only the most recent submission per participation in the time range is considered to avoid spamming the build system.
     * By default, this method is scheduled to run every day at 2 AM.
     */
    @Scheduled(cron = "${artemis.scheduling.programming-exercises-retrigger-submission-without-result-time: 0 0 2 * * *}")
    public void retriggerSubmissionsWithoutResults() {
        checkSecurityUtils();
        log.info("Retriggering latest submission per participation without results that are older than two hours but not older than 2 days.");

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime twoHoursAgo = now.minusHours(2);
        ZonedDateTime twoDaysAgo = now.minusDays(2);

        Pageable pageable = PageRequest.of(0, 50, Sort.by("submissionDate").ascending());
        int processedCount = 0;
        Slice<ProgrammingSubmission> slice;
        do {
            slice = programmingSubmissionRepository.findLatestProgrammingSubmissionsWithoutResultsInTimeRange(twoDaysAgo, twoHoursAgo, pageable);
            for (ProgrammingSubmission submission : slice.getContent()) {
                try {
                    programmingTriggerService.triggerBuildAndNotifyUser(submission);
                    processedCount++;
                    log.debug("Retriggered build for submission {}", submission.getId());
                }
                catch (Exception e) {
                    log.error("Error while retriggering build for submission {}: {}", submission.getId(), e.getMessage(), e);
                }
            }
            if (slice.hasNext()) {
                pageable = slice.nextPageable();
            }
        }
        while (slice.hasNext());

        log.info("Retriggered builds for {} programming submissions without results.", processedCount);
    }

    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

}
