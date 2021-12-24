package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class EmailSummaryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private EmailSummaryService weeklyEmailSummaryService;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private UserRepository userRepository;

    private User userWithDeactivatedWeeklySummaries;

    private final String USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN = "student1";

    private User userWithActivatedWeeklySummaries;

    private final String USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN = "student2";

    private Course course;

    private Exercise exerciseReleasedYesterdayAndNotYetDue;

    private Exercise exerciseReleasedYesterdayButAlreadyClosed;

    private Exercise exerciseReleasedTomorrow;

    private Exercise exerciseReleasedAMonthAgo;

    private Exercise exerciseWithoutAReleaseDate;

    private Set<Exercise> allTestExercises;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    public void setUp() {
        database.addUsers(2, 0, 0, 0);

        // deactivate weekly email summary for admin to make testing easier
        User artemisAdmin = database.getUserByLogin("artemis_admin");
        notificationSettingRepository.save(new NotificationSetting(artemisAdmin, false, false, NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY));
        userRepository.save(artemisAdmin);

        // preparation of the test data where a user deactivated weekly summaries
        userWithDeactivatedWeeklySummaries = database.getUserByLogin(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN);
        NotificationSetting deactivatedWeeklySummarySetting = new NotificationSetting(userWithDeactivatedWeeklySummaries, false, false,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(deactivatedWeeklySummarySetting);

        // preparation of the test data where a user activated weekly summaries
        userWithActivatedWeeklySummaries = database.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN);

        NotificationSetting activatedWeeklySummarySetting = new NotificationSetting(userWithActivatedWeeklySummaries, false, true,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(activatedWeeklySummarySetting);

        // preparation of test course with exercises for weekly summary testing
        ZonedDateTime now = ZonedDateTime.now();

        course = database.createCourse();
        allTestExercises = new HashSet<>();

        exerciseWithoutAReleaseDate = ModelFactory.generateTextExercise(null, null, null, course);
        exerciseWithoutAReleaseDate.setTitle("exerciseWithoutAReleaseDate");
        allTestExercises.add(exerciseWithoutAReleaseDate);

        exerciseReleasedYesterdayAndNotYetDue = ModelFactory.generateTextExercise(now.minusDays(1), null, null, course);
        exerciseReleasedYesterdayAndNotYetDue.setTitle("exerciseReleasedYesterdayAndNotYetDue");
        exerciseReleasedYesterdayAndNotYetDue.setDifficulty(DifficultyLevel.EASY);
        allTestExercises.add(exerciseReleasedYesterdayAndNotYetDue);

        exerciseReleasedYesterdayButAlreadyClosed = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(5), null, course);
        exerciseReleasedYesterdayButAlreadyClosed.setTitle("exerciseReleasedYesterdayButAlreadyClosed");
        allTestExercises.add(exerciseReleasedYesterdayButAlreadyClosed);

        exerciseReleasedTomorrow = ModelFactory.generateTextExercise(now.plusDays(1), null, null, course);
        exerciseReleasedTomorrow.setTitle("exerciseReleasedTomorrow");
        allTestExercises.add(exerciseReleasedTomorrow);

        exerciseReleasedAMonthAgo = ModelFactory.generateTextExercise(now.minusMonths(1), null, null, course);
        ;
        exerciseReleasedAMonthAgo.setTitle("exerciseReleasedAMonthAgo");
        allTestExercises.add(exerciseReleasedAMonthAgo);

        course.setExercises(allTestExercises);
        courseRepository.save(course);

        allTestExercises.forEach(exercise -> exerciseRepository.save(exercise));

        weeklyEmailSummaryService.setScheduleInterval(Duration.ofDays(7));
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly selects exercises that are suited for weekly summaries
     */
    @Test
    public void testIfPrepareWeeklyEmailSummariesCorrectlySelectsExercisesAndCreatesEmail() {
        ArgumentCaptor<Set<Exercise>> exerciseSetCaptor = ArgumentCaptor.forClass((Class) HashSet.class);

        weeklyEmailSummaryService.prepareEmailSummaries();

        verify(mailService, timeout(1000).times(1)).sendWeeklySummaryEmail(eq(database.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN)), exerciseSetCaptor.capture());
        Set<Exercise> capturedExerciseSet = exerciseSetCaptor.getValue();
        assertThat(capturedExerciseSet).as("Weekly summary should contain exercises that were released yesterday and are not yet due.")
                .contains(exerciseReleasedYesterdayAndNotYetDue);
        assertThat(capturedExerciseSet.size()).as("Weekly summary should not contain any other of the test exercises.").isEqualTo(1);

        // check if email is created/send
        verify(javaMailSender, timeout(1000).times(1)).createMimeMessage();
    }
}
