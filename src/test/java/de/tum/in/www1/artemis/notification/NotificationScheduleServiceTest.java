package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED;
import static java.time.ZonedDateTime.now;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.temporal.ChronoUnit;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;

class NotificationScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "notificationschedserv";

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private ResultRepository resultRepository;

    private Exercise exercise;

    private User user;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        user = database.getUserByLogin(TEST_PREFIX + "student1");
        final Course course = database.addCourseWithModelingAndTextExercise();
        exercise = database.getFirstExerciseWithType(course, TextExercise.class);
        exercise.setReleaseDate(now().plus(500, ChronoUnit.MILLIS));
        exercise.setAssessmentDueDate(now().plus(2, ChronoUnit.SECONDS));
        exerciseRepository.save(exercise);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    @Timeout(10)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtReleaseDate() {
        long sizeBefore = notificationRepository.count();
        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED));
        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(groupNotificationService, timeout(4000).times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
        verify(javaMailSender, timeout(4000).times(1)).send(any(MimeMessage.class));
    }

    @Test
    @Timeout(10)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtAssessmentDueDate() {
        long sizeBefore = notificationRepository.count();
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        database.addSubmission(exercise, textSubmission, TEST_PREFIX + "student1");
        Result manualResult = database.createParticipationSubmissionAndResult(exercise.getId(), database.getUserByLogin(TEST_PREFIX + "student1"), 10.0, 10.0, 50, true);
        manualResult.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(manualResult);

        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED));

        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(singleUserNotificationService, timeout(4000).times(1)).notifyUsersAboutAssessedExerciseSubmission(exercise);
        verify(javaMailSender, timeout(4000).times(1)).send(any(MimeMessage.class));
    }
}
