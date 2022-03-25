package de.tum.in.www1.artemis.service.scheduled;

import java.time.*;
import java.util.*;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.EmailSummaryService;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class WeeklyEmailSummaryScheduleService {

    private final Logger log = LoggerFactory.getLogger(WeeklyEmailSummaryScheduleService.class);

    private final Environment environment;

    private final TaskScheduler scheduler;

    private final EmailSummaryService emailSummaryService;

    private LocalDateTime nextSummaryDate;

    private final Duration weekly = Duration.ofDays(7);

    public WeeklyEmailSummaryScheduleService(Environment environment, @Qualifier("taskScheduler") TaskScheduler scheduler, EmailSummaryService emailSummaryService) {
        this.environment = environment;
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

    /**
     * Prepare summary scheduling after server start up
     */
    @PostConstruct
    public void scheduleEmailSummariesOnStartUp() {
        try {
            Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
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
            emailSummaryService.prepareEmailSummaries();
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
