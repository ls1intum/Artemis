package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;

class NotificationScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    private Exercise exercise;

    private User user;

    @BeforeEach
    void init() {
        database.addUsers(1, 1, 1, 1);
        user = database.getUserByLogin("student1");
        database.addCourseWithFileUploadExercise();
        exercise = exerciseRepository.findAll().get(0);
        ZonedDateTime exerciseDate = ZonedDateTime.now().plus(500, ChronoUnit.MILLIS); // 1 millisecond is not enough
        exercise.setReleaseDate(exerciseDate);
        exercise.setAssessmentDueDate(exerciseDate);
        exerciseRepository.save(exercise);
        assertThat(notificationRepository.count()).isZero();
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @Timeout(5)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtReleaseDate() {
        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED));
        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        await().until(() -> notificationRepository.count() > 0);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
        verify(javaMailSender, timeout(2000).times(1)).createMimeMessage();
    }

    @Test
    @Timeout(5)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtAssessmentDueDate() {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        database.addSubmission(exercise, textSubmission, "student1");
        database.createParticipationSubmissionAndResult(exercise.getId(), database.getUserByLogin("student1"), 10.0, 10.0, 50, true);
        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED));

        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > 0);
        verify(singleUserNotificationService, times(1)).notifyUsersAboutAssessedExerciseSubmission(exercise);
        verify(javaMailSender, timeout(2000).times(1)).createMimeMessage();
    }
}
