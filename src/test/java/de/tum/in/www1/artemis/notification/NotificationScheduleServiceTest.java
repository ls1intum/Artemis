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

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.user.UserUtilService;

class NotificationScheduleServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

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

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Exercise exercise;

    private User user;

    private long sizeBefore;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        final Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        sizeBefore = notificationRepository.count();
    }

    @Test
    @Timeout(10)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtReleaseDate() {
        notificationSettingRepository.save(new NotificationSetting(user, true, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED));
        exercise.setReleaseDate(now().plus(200, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);

        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(groupNotificationService, timeout(4000)).notifyAllGroupsAboutReleasedExercise(exercise);
        verify(mailService, timeout(4000).atLeastOnce()).sendNotification(any(), anySet(), any());
    }

    @Test
    @Timeout(10)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtAssessmentDueDate() {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        participationUtilService.addSubmission(exercise, textSubmission, TEST_PREFIX + "student1");

        Result manualResult = participationUtilService.createParticipationSubmissionAndResult(exercise.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1"), 10.0,
                10.0, 50, true);
        manualResult.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(manualResult);

        notificationSettingRepository.save(new NotificationSetting(user, true, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED));
        exercise.setAssessmentDueDate(now().plus(200, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);
        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(singleUserNotificationService, timeout(4000)).notifyUsersAboutAssessedExerciseSubmission(exercise);
        verify(javaMailSender, timeout(4000)).send(any(MimeMessage.class));
    }
}
