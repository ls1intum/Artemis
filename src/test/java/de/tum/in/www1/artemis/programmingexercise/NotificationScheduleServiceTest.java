package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;

public class NotificationScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Exercise exercise;

    private static final long DELAY_IN_SECONDS = 1;

    @BeforeEach
    public void init() {
        database.addUsers(1, 1, 1, 1);
        database.addCourseWithFileUploadExercise();
        exercise = exerciseRepository.findAll().get(0);
        ZonedDateTime exerciseDate = ZonedDateTime.now().plusSeconds(DELAY_IN_SECONDS);
        exercise.setReleaseDate(exerciseDate);
        exercise.setAssessmentDueDate(exerciseDate);
        exerciseRepository.save(exercise);

        SecurityUtils.setAuthorizationObject();
        assertThat(notificationRepository.count()).isEqualTo(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @Timeout(5)
    void shouldCreateNotificationAtReleaseDate() {
        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());
        await().until(() -> notificationRepository.count() > 0);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    @Test
    @Timeout(5)
    void shouldCreateNotificationAtAssessmentDueDate() {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        database.addSubmission(exercise, textSubmission, "student1");
        database.createParticipationSubmissionAndResult(exercise.getId(), database.getUserByLogin("student1"), 10.0, 10.0, 50, true);

        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > 0);
        verify(singleUserNotificationService, times(1)).notifyUsersAboutAssessedExerciseSubmission(exercise);
    }
}
