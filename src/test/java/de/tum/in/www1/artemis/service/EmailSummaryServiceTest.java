package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

class EmailSummaryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    @Captor
    ArgumentCaptor<Set<Exercise>> exerciseSetCaptor;

    private Exercise exerciseReleasedYesterdayAndNotYetDue;

    private final static String USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN = "student1";

    private final static String USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN = "student2";

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    void setUp() {
        database.addUsers(2, 0, 0, 0);

        // preparation of the test data where a user deactivated weekly summaries
        User userWithDeactivatedWeeklySummaries = database.getUserByLogin(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN);
        NotificationSetting deactivatedWeeklySummarySetting = new NotificationSetting(userWithDeactivatedWeeklySummaries, false, false,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(deactivatedWeeklySummarySetting);

        // preparation of the test data where a user activated weekly summaries
        User userWithActivatedWeeklySummaries = database.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN);

        NotificationSetting activatedWeeklySummarySetting = new NotificationSetting(userWithActivatedWeeklySummaries, false, true,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(activatedWeeklySummarySetting);

        // preparation of test course with exercises for weekly summary testing
        ZonedDateTime now = ZonedDateTime.now();

        Course course = database.createCourse();
        Set<Exercise> allTestExercises = new HashSet<>();

        Exercise exerciseWithoutAReleaseDate = ModelFactory.generateTextExercise(null, null, null, course);
        exerciseWithoutAReleaseDate.setTitle("exerciseWithoutAReleaseDate");
        allTestExercises.add(exerciseWithoutAReleaseDate);

        exerciseReleasedYesterdayAndNotYetDue = ModelFactory.generateTextExercise(now.minusDays(1), null, null, course);
        exerciseReleasedYesterdayAndNotYetDue.setTitle("exerciseReleasedYesterdayAndNotYetDue");
        exerciseReleasedYesterdayAndNotYetDue.setDifficulty(DifficultyLevel.EASY);
        allTestExercises.add(exerciseReleasedYesterdayAndNotYetDue);

        Exercise exerciseReleasedYesterdayButAlreadyClosed = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(5), null, course);
        exerciseReleasedYesterdayButAlreadyClosed.setTitle("exerciseReleasedYesterdayButAlreadyClosed");
        allTestExercises.add(exerciseReleasedYesterdayButAlreadyClosed);

        Exercise exerciseReleasedTomorrow = ModelFactory.generateTextExercise(now.plusDays(1), null, null, course);
        exerciseReleasedTomorrow.setTitle("exerciseReleasedTomorrow");
        allTestExercises.add(exerciseReleasedTomorrow);

        Exercise exerciseReleasedAMonthAgo = ModelFactory.generateTextExercise(now.minusMonths(1), null, null, course);
        exerciseReleasedAMonthAgo.setTitle("exerciseReleasedAMonthAgo");
        allTestExercises.add(exerciseReleasedAMonthAgo);

        course.setExercises(allTestExercises);
        courseRepository.save(course);

        exerciseRepository.saveAll(allTestExercises);

        weeklyEmailSummaryService.setScheduleInterval(Duration.ofDays(7));

        // deactivate weekly email summary for currently available users to make testing easier (needed for verify())
        List<User> currentUsers = userRepository.findAll();
        currentUsers.remove(userWithActivatedWeeklySummaries);
        currentUsers.remove(userWithDeactivatedWeeklySummaries);
        if (!currentUsers.isEmpty()) {
            currentUsers.forEach(user -> notificationSettingRepository.save(new NotificationSetting(user, false, false, NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY)));
        }

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly selects exercises that are suited for weekly summaries
     */
    @Test
    void testIfPrepareWeeklyEmailSummariesCorrectlySelectsExercisesAndCreatesEmail() {

        weeklyEmailSummaryService.prepareEmailSummaries();

        verify(mailService, timeout(1000).times(1)).sendWeeklySummaryEmail(eq(database.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN)), exerciseSetCaptor.capture());
        Set<Exercise> capturedExerciseSet = exerciseSetCaptor.getValue();
        assertThat(capturedExerciseSet).as("Weekly summary should contain exercises that were released yesterday and are not yet due.")
                .contains(exerciseReleasedYesterdayAndNotYetDue);
        assertThat(capturedExerciseSet.size()).as("Weekly summary should not contain any other of the test exercises.").isEqualTo(1);

        // check if email is created/send
        verify(javaMailSender, timeout(1000).times(1)).send(any(MimeMessage.class));
    }
}
