package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.in.www1.artemis.service.scheduled.WeeklyEmailSummaryScheduleService;

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
    private static Environment environment;

    @Mock
    private static EmailSummaryService emailSummaryService;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    static void setUp() {
        environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {});

        emailSummaryService = mock(EmailSummaryService.class);

        scheduler = new ThreadPoolTaskScheduler();
        schedulerSpy = spy(scheduler);

        weeklyEmailSummaryService = new WeeklyEmailSummaryScheduleService(environment, schedulerSpy, emailSummaryService);
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

        verify(schedulerSpy, times(1)).scheduleAtFixedRate(any(), instantCaptor.capture(), eq(Duration.ofDays(7)));
        LocalDateTime capturedTriggerTime = LocalDateTime.ofInstant(instantCaptor.getValue(), zoneOffset);
        assertThat(capturedTriggerTime).as("The initial trigger for weekly summaries should be on the soonest Friday. (I.e. next Friday or today (if Friday))")
                .isAfterOrEqualTo(LocalDateTime.now());
        assertThat(capturedTriggerTime.getDayOfWeek()).as("Weekly summaries should be triggered on Friday").isEqualTo(DayOfWeek.FRIDAY);
        assertThat(capturedTriggerTime.getHour()).as("Weekly summaries should be triggered at 17:00").isEqualTo(17);
    }
}
