package de.tum.cit.aet.artemis.communication.notification;

import static java.time.ZonedDateTime.now;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageReceiveService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class GroupNotificationScheduleServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "notificationschedserv";

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Exercise exercise;

    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private static final long DELAY_MS = 200;

    private static final long TIMEOUT_MS = 5000;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        final Course course = courseUtilService.addEmptyCourse();
        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exercise.setMaxPoints(5.0);
        exerciseRepository.saveAndFlush(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateNotificationAndEmailAtReleaseDate() {
        exercise.setReleaseDate(now().plus(DELAY_MS, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);

        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        verify(groupNotificationService, timeout(TIMEOUT_MS)).notifyAllGroupsAboutReleasedExercise(exercise);
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

        exercise.setAssessmentDueDate(now().plus(DELAY_MS, ChronoUnit.MILLIS));
        exerciseRepository.saveAndFlush(exercise);
        Long exerciseId = exercise.getId();
        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exerciseId);

        // Use argThat matcher because the notification service reloads the exercise from the database,
        // creating a different object instance than the one we have here
        verify(singleUserNotificationService, timeout(TIMEOUT_MS)).notifyUsersAboutAssessedExerciseSubmission(argThat(ex -> ex != null && ex.getId().equals(exerciseId)));
        verify(javaMailSender, timeout(TIMEOUT_MS)).send(any(MimeMessage.class));
    }
}
