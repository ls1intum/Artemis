package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;

public class EmailSummaryServiceTest {

    private static EmailSummaryService weeklyEmailSummaryService;

    @Mock
    private static UserRepository userRepository;

    @Mock
    private static NotificationSettingRepository notificationSettingRepository;

    @Mock
    private static MailService mailService;

    @Mock
    private static CourseService courseService;

    @Mock
    private static NotificationSettingsService notificationSettingsService;

    private static Set<User> users;

    private static User userWithDeactivatedWeeklySummaries;

    private static final Long USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_ID = 33L;

    private static User userWithActivatedWeeklySummaries;

    private static final Long USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_ID = 27L;

    private static Course course;

    private static Exercise exerciseReleasedYesterdayAndNotYetDue;

    private static Exercise exerciseReleasedYesterdayButAlreadyClosed;

    private static Exercise exerciseReleasedTomorrow;

    private static Exercise exerciseReleasedAMonthAgo;

    private static Exercise exerciseWithoutAReleaseDate;

    private static Set<Exercise> exercises;

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

        // preparation of test course with exercises for weekly summary testing
        ZonedDateTime now = ZonedDateTime.now();

        exerciseWithoutAReleaseDate = new TextExercise();

        exerciseReleasedYesterdayAndNotYetDue = new TextExercise();
        exerciseReleasedYesterdayAndNotYetDue.setReleaseDate(now.minusDays(1));

        exerciseReleasedYesterdayButAlreadyClosed = new TextExercise();
        exerciseReleasedYesterdayButAlreadyClosed.setReleaseDate(now.minusDays(1));
        exerciseReleasedYesterdayButAlreadyClosed.setDueDate(now.minusHours(5));

        exerciseReleasedTomorrow = new TextExercise();
        exerciseReleasedTomorrow.setReleaseDate(now.plusDays(1));

        exerciseReleasedAMonthAgo = new ModelingExercise();
        exerciseReleasedAMonthAgo.setReleaseDate(now.minusMonths(1));

        exercises = new HashSet<>();
        exercises.add(exerciseWithoutAReleaseDate);
        exercises.add(exerciseReleasedTomorrow);
        exercises.add(exerciseReleasedAMonthAgo);
        exercises.add(exerciseReleasedYesterdayAndNotYetDue);
        exercises.add(exerciseReleasedYesterdayButAlreadyClosed);

        course = new Course();
        course.setExercises(exercises);

        // preparation of the additional test data like mocks

        users = new HashSet<>();
        users.add(userWithDeactivatedWeeklySummaries);
        users.add(userWithActivatedWeeklySummaries);

        mailService = mock(MailService.class);

        userRepository = mock(UserRepository.class);
        when(userRepository.findAllWithGroupsAndAuthorities()).thenReturn(users);

        courseService = mock(CourseService.class);
        when(courseService.findAllActiveWithExercisesAndLecturesAndExamsForUser(userWithActivatedWeeklySummaries)).thenReturn(List.of(course));

        notificationSettingsService = mock(NotificationSettingsService.class);
        when(notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(activatedWeeklySummarySettingsSet)).thenReturn(activatedWeeklySummarySettingsSet);
        when(notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(deactivatedWeeklySummarySettingsSet)).thenReturn(deactivatedWeeklySummarySettingsSet);

        notificationSettingRepository = mock(NotificationSettingRepository.class);
        when(notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_ID))
                .thenReturn(deactivatedWeeklySummarySettingsSet);
        when(notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_ID)).thenReturn(activatedWeeklySummarySettingsSet);

        weeklyEmailSummaryService = new EmailSummaryService(mailService, userRepository, notificationSettingRepository, courseService, notificationSettingsService,
                Duration.ofDays(7));
    }

    /**
     * Prepares and cleans the mocks that are modified during the tests
     */
    @BeforeEach
    public void cleanMocks() {
        reset(mailService);
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly filters out users who deactivated weekly summaries.
     */
    @Test
    public void testIfPrepareWeeklyEmailSummariesCorrectlyFiltersOutUsers() {
        weeklyEmailSummaryService.prepareEmailSummaries();
        verify(courseService, times(1)).findAllActiveWithExercisesAndLecturesAndExamsForUser(userWithActivatedWeeklySummaries);
        verify(courseService, times(0)).findAllActiveWithExercisesAndLecturesAndExamsForUser(userWithDeactivatedWeeklySummaries);
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly selects exercises that are suited for weekly summaries
     */
    @Test
    public void testIfPrepareWeeklyEmailSummariesCorrectlySelectsExercises() {
        ArgumentCaptor<Set<Exercise>> exerciseSetCaptor = ArgumentCaptor.forClass((Class) HashSet.class);

        weeklyEmailSummaryService.prepareEmailSummaries();

        verify(mailService, times(1)).sendWeeklySummaryEmail(eq(userWithActivatedWeeklySummaries), exerciseSetCaptor.capture());
        Set<Exercise> capturedExerciseSet = exerciseSetCaptor.getValue();
        assertThat(capturedExerciseSet).as("Weekly summary should contain exercises that were released yesterday and are not yet due.")
                .contains(exerciseReleasedYesterdayAndNotYetDue);
        assertThat(capturedExerciseSet).as("Weekly summary should not contain any other of the test exercises.").hasSize(1);
    }
}
