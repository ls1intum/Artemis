package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;

public class NotificationScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private static final long DELAY_IN_SECONDS = 2;

    private static final long TIME_MULTIPLICITY_TO_REDUCE_TEST_FLAKINESS = 2;

    @Test
    void shouldCreateNotificationAtReleaseDate() throws Exception {
        database.addCourseWithFileUploadExercise();
        Exercise exercise = exerciseRepository.findAll().get(0);
        ZonedDateTime exerciseReleaseDate = ZonedDateTime.now().plusSeconds(DELAY_IN_SECONDS);
        exercise.setReleaseDate(exerciseReleaseDate);
        exerciseRepository.save(exercise);

        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exercise.getId());

        Thread.sleep(DELAY_IN_SECONDS * TIME_MULTIPLICITY_TO_REDUCE_TEST_FLAKINESS * 1000);

        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    @Test
    void shouldCreateNotificationAtAssessmentDueDate() throws Exception {
        database.addUsers(1, 1, 1, 1);
        database.addCourseWithFileUploadExercise();
        Exercise exercise = exerciseRepository.findAll().get(0);
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusSeconds(DELAY_IN_SECONDS));
        exerciseRepository.save(exercise);
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text("Text");
        textSubmission.submitted(true);
        database.addSubmission(exercise, textSubmission, "student1");

        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(textSubmission.getId());

        Thread.sleep(DELAY_IN_SECONDS * TIME_MULTIPLICITY_TO_REDUCE_TEST_FLAKINESS * 1000);

        verify(singleUserNotificationService, times(1)).notifyUserAboutAssessedExerciseSubmission(exercise, database.getUserByLogin("student1"));
    }
}
