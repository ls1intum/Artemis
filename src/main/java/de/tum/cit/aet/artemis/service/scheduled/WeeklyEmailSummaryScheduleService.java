package de.tum.cit.aet.artemis.service.scheduled;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.config.StartupDelayConfig.EMAIL_SUMMARY_SCHEDULE_DELAY_SEC;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.EmailSummaryService;
import de.tum.cit.aet.artemis.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class WeeklyEmailSummaryScheduleService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyEmailSummaryScheduleService.class);

    private final ProfileService profileService;

    private final TaskScheduler scheduler;

    private final EmailSummaryService emailSummaryService;

    private LocalDateTime nextSummaryDate;

    private final Duration weekly = Duration.ofDays(7);

    public WeeklyEmailSummaryScheduleService(ProfileService profileService, @Qualifier("taskScheduler") TaskScheduler scheduler, EmailSummaryService emailSummaryService) {
        this.profileService = profileService;
        this.scheduler = scheduler;
        this.emailSummaryService = emailSummaryService;
        // time based variables needed for scheduling
        this.nextSummaryDate = ZonedDateTime.now().toLocalDateTime().with(DayOfWeek.FRIDAY).withHour(17).withMinute(0);
        if (nextSummaryDate.isBefore(ZonedDateTime.now().toLocalDateTime())) {
            // if the current time is e.g. 18:00 on a Friday the nextSummaryDate has to moved to next Friday
            this.nextSummaryDate = this.nextSummaryDate.plusWeeks(1);
        }
        this.emailSummaryService.setScheduleInterval(weekly);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleEmailSummariesOnStartUp, Instant.now().plusSeconds(EMAIL_SUMMARY_SCHEDULE_DELAY_SEC));
    }

    /**
     * Prepare summary scheduling after server start up
     */
    public void scheduleEmailSummariesOnStartUp() {
        try {
            if (profileService.isDevActive()) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();

            ZoneOffset zoneOffset = ZonedDateTime.now().getZone().getRules().getOffset(Instant.now());

            scheduler.scheduleAtFixedRate(scheduleEmailSummaries(), nextSummaryDate.toInstant(zoneOffset), weekly);
            // For local testing
            // scheduler.scheduleAtFixedRate(scheduleEmailSummaries(), ZonedDateTime.now().toLocalDateTime().toInstant(zoneOffset), Duration.ofMinutes(3));

            log.info("Scheduled email summaries on start up.");
        }
        catch (Exception exception) {
            log.error("Failed to start WeeklyEmailSummaryScheduleService", exception);
        }
    }

    /**
     * Begin the process of email summaries
     * i.e. find all active Artemis users that have weekly summaries enabled in their notification settings
     * and initiate the creation of summary emails for each found user.
     */
    Runnable scheduleEmailSummaries() {
        return () -> {
            checkSecurityUtils();
            emailSummaryService.prepareEmailSummariesAsynchronously();
        };
    }

    /**
     * Checks and sets the needed authentication
     */
    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }
}
