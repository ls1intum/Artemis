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
        database.addCourseWithFileUploadExercise();
        exercise = exerciseRepository.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @Timeout(5)
    void shouldCreateNotificationAtReleaseDate() {
        SecurityUtils.setAuthorizationObject();
        ZonedDateTime exerciseReleaseDate = ZonedDateTime.now().plusSeconds(DELAY_IN_SECONDS);
        exercise.setReleaseDate(exerciseReleaseDate);
        exerciseRepository.save(exercise);

        assertThat(notificationRepository.count()).isEqualTo(0);

        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());

        await().until(() -> notificationRepository.count() > 0);

        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    @Test
    @Timeout(5)
    void shouldCreateNotificationAtAssessmentDueDate() {
        SecurityUtils.setAuthorizationObject();
        database.addUsers(1, 1, 1, 1);
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusSeconds(DELAY_IN_SECONDS));
        exerciseRepository.save(exercise);

        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        database.addSubmission(exercise, textSubmission, "student1");

        assertThat(notificationRepository.count()).isEqualTo(0);

        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(textSubmission.getId());

        await().until(() -> notificationRepository.count() > 0);

        verify(singleUserNotificationService, times(1)).notifyUserAboutAssessedExerciseSubmission(exercise, database.getUserByLogin("student1"));
    }
}
