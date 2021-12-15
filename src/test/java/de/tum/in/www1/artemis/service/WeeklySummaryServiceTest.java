package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;
import de.tum.in.www1.artemis.service.scheduled.WeeklySummaryService;

public class WeeklySummaryServiceTest {

    private static WeeklySummaryService weeklySummaryService;

    @Mock
    private static UserRepository userRepository;

    @Mock
    private static NotificationSettingRepository notificationSettingRepository;

    @Mock
    private static Environment environment;

    @Mock
    private static MailService mailService;

    @Mock
    private static CourseService courseService;

    @Mock
    private static TaskScheduler scheduler;

    @Mock
    private static NotificationSettingsService notificationSettingsService;

    private static Set<User> users;

    private static User userWithDeactivatedWeeklySummaries;

    private static final Long USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_ID = 33L;

    private static User userWithActivatedWeeklySummaries;

    private static final Long USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_ID = 27L;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        // preparation of the test data where a user deactivated weekly summaries
        userWithDeactivatedWeeklySummaries = new User();
        userWithDeactivatedWeeklySummaries.setId(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_ID);

        NotificationSetting deactivatedWeeklySummarySetting = new NotificationSetting();
        deactivatedWeeklySummarySetting.setSettingId(NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        deactivatedWeeklySummarySetting.setEmail(false);

        Set<NotificationSetting> deactivatedWeeklySummarySettingsSet = new HashSet<>();
        deactivatedWeeklySummarySettingsSet.add(deactivatedWeeklySummarySetting);

        // preparation of the test data where a user activated weekly summaries
        userWithActivatedWeeklySummaries = new User();
        userWithActivatedWeeklySummaries.setId(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_ID);

        NotificationSetting activatedWeeklySummarySetting = new NotificationSetting();
        activatedWeeklySummarySetting.setSettingId(NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        activatedWeeklySummarySetting.setEmail(true);

        Set<NotificationSetting> activatedWeeklySummarySettingsSet = new HashSet<>();
        activatedWeeklySummarySettingsSet.add(activatedWeeklySummarySetting);

        // preparation of the additional test data like mocks

        users = new HashSet<>();
        users.add(userWithDeactivatedWeeklySummaries);
        users.add(userWithActivatedWeeklySummaries);

        environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {});

        mailService = mock(MailService.class);

        userRepository = mock(UserRepository.class);
        when(userRepository.findAllWithGroupsAndAuthorities()).thenReturn(users);

        courseService = mock(CourseService.class);

        notificationSettingsService = mock(NotificationSettingsService.class);

        notificationSettingRepository = mock(NotificationSettingRepository.class);
        when(notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_ID))
                .thenReturn(deactivatedWeeklySummarySettingsSet);
        when(notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_ID)).thenReturn(activatedWeeklySummarySettingsSet);

        scheduler = mock(TaskScheduler.class);

        weeklySummaryService = new WeeklySummaryService(environment, mailService, userRepository, notificationSettingRepository, courseService, scheduler,
                notificationSettingsService);
    }

    @BeforeEach
    public void cleanMocksAndTestVariables() {
        reset(scheduler);
    }

    /**
     * Tests if the main method scheduleWeeklySummariesOnStartUp uses the correct times.
     */
    @Test
    public void testIfWeeklySummariesAreScheduledAtTheCorrectTimes() {
        // needed as a parameter for the task scheduler
        ZoneOffset zoneOffset = ZonedDateTime.now().getZone().getRules().getOffset(Instant.now());
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);

        weeklySummaryService.scheduleWeeklySummariesOnStartUp();

        verify(scheduler, times(1)).scheduleAtFixedRate(any(), instantCaptor.capture(), eq(Duration.ofDays(7)));
        LocalDateTime capturedTriggerTime = LocalDateTime.ofInstant(instantCaptor.getValue(), zoneOffset);
        assertThat(capturedTriggerTime).as("The initial trigger for weekly summaries should be on the soonest Friday. (I.e. next Friday or today (if Friday))")
                .isAfterOrEqualTo(LocalDateTime.now());
        assertThat(capturedTriggerTime.getDayOfWeek()).as("Weekly summaries should be triggered on Friday").isEqualTo(DayOfWeek.FRIDAY);
        assertThat(capturedTriggerTime.getHour()).as("Weekly summaries should be triggered at 17:00").isEqualTo(17);
    }

    /**
     * Tests if the method/runnable scheduleWeeklySummaries correctly filters out users who deactivated weekly summaries.
     */
    @Test
    public void testIfScheduleWeeklySummariesCorrectlyFiltersOutUsers() {
        weeklySummaryService.scheduleWeeklySummariesOnStartUp();

        // TODO currently this can not be tested, due to the long (7 days long) waiting -> could be tested if the methods would be public or if there was a boolean flag to change
        // the times for testing
        verify(courseService, times(1)).findAllActiveWithExercisesAndLecturesAndExamsForUser(userWithActivatedWeeklySummaries);
        verify(courseService, times(0)).findAllActiveWithExercisesAndLecturesAndExamsForUser(userWithDeactivatedWeeklySummaries);
    }
}
