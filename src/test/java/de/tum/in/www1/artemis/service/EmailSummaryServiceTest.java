package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class EmailSummaryServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "emailsummaryservice";

    @Autowired
    private EmailSummaryService weeklyEmailSummaryService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private User userWithActivatedWeeklySummaries;

    private User userWithDeactivatedWeeklySummaries;

    private Exercise exerciseReleasedYesterdayAndNotYetDue;

    private Exercise exerciseWithoutAReleaseDate;

    private Exercise exerciseReleasedYesterdayButAlreadyClosed;

    private Exercise exerciseReleasedTomorrow;

    private Exercise exerciseReleasedAMonthAgo;

    private static final String USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN = TEST_PREFIX + "student1";

    private static final String USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN = TEST_PREFIX + "student2";

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        // preparation of the test data where a user deactivated weekly summaries
        this.userWithDeactivatedWeeklySummaries = userUtilService.getUserByLogin(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN);
        NotificationSetting deactivatedWeeklySummarySetting = new NotificationSetting(userWithDeactivatedWeeklySummaries, false, false, true,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(deactivatedWeeklySummarySetting);

        // preparation of the test data where a user activated weekly summaries
        this.userWithActivatedWeeklySummaries = userUtilService.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN);

        NotificationSetting activatedWeeklySummarySetting = new NotificationSetting(userWithActivatedWeeklySummaries, false, true, true,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(activatedWeeklySummarySetting);

        // preparation of test course with exercises for weekly summary testing
        ZonedDateTime now = ZonedDateTime.now();

        Course course = courseUtilService.createCourse();
        Set<Exercise> allTestExercises = new HashSet<>();

        exerciseWithoutAReleaseDate = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exerciseWithoutAReleaseDate.setTitle("exerciseWithoutAReleaseDate");
        allTestExercises.add(exerciseWithoutAReleaseDate);

        exerciseReleasedYesterdayAndNotYetDue = TextExerciseFactory.generateTextExercise(now.minusDays(1), null, null, course);
        exerciseReleasedYesterdayAndNotYetDue.setTitle("exerciseReleasedYesterdayAndNotYetDue");
        exerciseReleasedYesterdayAndNotYetDue.setDifficulty(DifficultyLevel.EASY);
        allTestExercises.add(exerciseReleasedYesterdayAndNotYetDue);

        exerciseReleasedYesterdayButAlreadyClosed = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(5), null, course);
        exerciseReleasedYesterdayButAlreadyClosed.setTitle("exerciseReleasedYesterdayButAlreadyClosed");
        allTestExercises.add(exerciseReleasedYesterdayButAlreadyClosed);

        exerciseReleasedTomorrow = TextExerciseFactory.generateTextExercise(now.plusDays(1), null, null, course);
        exerciseReleasedTomorrow.setTitle("exerciseReleasedTomorrow");
        allTestExercises.add(exerciseReleasedTomorrow);

        exerciseReleasedAMonthAgo = TextExerciseFactory.generateTextExercise(now.minusMonths(1), null, null, course);
        exerciseReleasedAMonthAgo.setTitle("exerciseReleasedAMonthAgo");
        allTestExercises.add(exerciseReleasedAMonthAgo);

        course.setExercises(allTestExercises);
        courseRepository.save(course);

        exerciseRepository.saveAll(allTestExercises);
        weeklyEmailSummaryService.setScheduleInterval(Duration.ofDays(7));

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly selects exercises that are suited for weekly summaries
     */
    @Test
    void testIfPrepareWeeklyEmailSummariesCorrectlySelectsExercisesAndCreatesEmail() {
        var filteredUsers = weeklyEmailSummaryService.findRelevantUsersForSummary();
        assertThat(filteredUsers).contains(userWithActivatedWeeklySummaries);
        assertThat(filteredUsers).doesNotContain(userWithDeactivatedWeeklySummaries);

        weeklyEmailSummaryService.prepareEmailSummariesForUsers(Set.of(userWithActivatedWeeklySummaries));

        ArgumentCaptor<Set<Exercise>> captor = ArgumentCaptor.forClass(Set.class);
        verify(mailService, timeout(5000)).sendWeeklySummaryEmail(eq(userWithActivatedWeeklySummaries), captor.capture());
        verify(javaMailSender, timeout(5000)).send(any(MimeMessage.class));

        Set<Exercise> capturedExerciseSet = captor.getValue();

        assertThat(capturedExerciseSet).as("Weekly summary should contain exercises that were released yesterday and are not yet due.")
                .contains(exerciseReleasedYesterdayAndNotYetDue);

        assertThat(capturedExerciseSet)
                .doesNotContainAnyElementsOf(List.of(exerciseWithoutAReleaseDate, exerciseReleasedYesterdayButAlreadyClosed, exerciseReleasedTomorrow, exerciseReleasedAMonthAgo));

    }
}
