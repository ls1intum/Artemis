package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.cit.aet.artemis.communication.service.EmailSummaryService;
import de.tum.cit.aet.artemis.communication.service.WeeklyEmailSummaryScheduleService;
import de.tum.cit.aet.artemis.core.service.ProfileService;

/**
 * Tests if the WeeklyEmailSummaryScheduleService correctly calls the EmailSummaryService with the expected times and intervals
 */
class WeeklyEmailSummaryScheduleServiceTest {

    private static WeeklyEmailSummaryScheduleService weeklyEmailSummaryService;

    @Autowired
    private static TaskScheduler scheduler;

    @Spy
    private static TaskScheduler schedulerSpy;

    @Mock
    private static ProfileService profileService;

    @Mock
    private static EmailSummaryService emailSummaryService;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    static void setUp() {
        profileService = mock(ProfileService.class);
        when(profileService.isDevActive()).thenReturn(false);

        emailSummaryService = mock(EmailSummaryService.class);

        scheduler = new ThreadPoolTaskScheduler();
        schedulerSpy = spy(scheduler);

        weeklyEmailSummaryService = new WeeklyEmailSummaryScheduleService(profileService, schedulerSpy, emailSummaryService);
    }

    /**
     * Tests if the main method scheduleWeeklySummariesOnStartUp uses the correct times.
     */
    @Test
    void testIfWeeklySummariesAreScheduledAtTheCorrectTimes() {
        // needed as a parameter for the task scheduler
        ZoneOffset zoneOffset = ZonedDateTime.now().getZone().getRules().getOffset(Instant.now());
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);

        weeklyEmailSummaryService.scheduleEmailSummariesOnStartUp();

        verify(schedulerSpy).scheduleAtFixedRate(any(), instantCaptor.capture(), eq(Duration.ofDays(7)));
        LocalDateTime capturedTriggerTime = LocalDateTime.ofInstant(instantCaptor.getValue(), zoneOffset);
        assertThat(capturedTriggerTime).as("The initial trigger for weekly summaries should be on the soonest Friday. (I.e. next Friday or today (if Friday))")
                .isAfterOrEqualTo(LocalDateTime.now());
        assertThat(capturedTriggerTime.getDayOfWeek()).as("Weekly summaries should be triggered on Friday").isEqualTo(DayOfWeek.FRIDAY);
        assertThat(capturedTriggerTime.getHour()).as("Weekly summaries should be triggered at 17:00").isEqualTo(17);
    }
}
