package de.tum.cit.aet.artemis.communication.notifications.integration;

import static de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED;
import static de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED;
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

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.communication.domain.NotificationSetting;
import de.tum.cit.aet.artemis.communication.repository.NotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageReceiveService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class NotificationScheduleIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "notificationschedint";

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private ResultTestRepository resultRepository;

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
