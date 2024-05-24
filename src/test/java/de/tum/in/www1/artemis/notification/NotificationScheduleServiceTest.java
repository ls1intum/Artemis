package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED;
import static java.time.ZonedDateTime.now;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.temporal.ChronoUnit;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.exercise.text.TextExerciseFactory;
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
    private ParticipationUtilService participationUtilService;

    private Exercise exercise;

    private User user;

    private long sizeBefore;

    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private static final long DELAY_MS = 200;

    private static final long TIMEOUT_MS = 5000;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        final Course course = courseUtilService.addEmptyCourse();
        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exercise.setMaxPoints(5.0);
        exerciseRepository.saveAndFlush(exercise);

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        sizeBefore = notificationRepository.count();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtReleaseDate() {
        notificationSettingRepository.saveAndFlush(new NotificationSetting(user, true, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED));
        exercise.setReleaseDate(now().plus(DELAY_MS, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);

        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(groupNotificationService, timeout(TIMEOUT_MS)).notifyAllGroupsAboutReleasedExercise(exercise);
        verify(mailService, timeout(TIMEOUT_MS).atLeastOnce()).sendNotification(any(), anySet(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtAssessmentDueDate() {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        participationUtilService.addSubmission(exercise, textSubmission, TEST_PREFIX + "student1");

        Result manualResult = participationUtilService.createParticipationSubmissionAndResult(exercise.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1"), 10.0,
                10.0, 50, true);
        manualResult.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.saveAndFlush(manualResult);

        notificationSettingRepository.saveAndFlush(new NotificationSetting(user, true, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED));
        exercise.setAssessmentDueDate(now().plus(DELAY_MS, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);
        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > sizeBefore);
        verify(singleUserNotificationService, timeout(TIMEOUT_MS)).notifyUsersAboutAssessedExerciseSubmission(exercise);
        verify(javaMailSender, timeout(TIMEOUT_MS)).send(any(MimeMessage.class));
    }
}
