package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;

public class NotificationScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Test
    void shouldCreateNotificationAtReleaseDate() throws Exception {
        database.addCourseWithFileUploadExercise();
        Exercise exercise = exerciseRepository.findAll().get(0);
        long delayInSeconds = 1;
        ZonedDateTime exerciseReleaseDate = ZonedDateTime.now().plusSeconds(delayInSeconds);
        exercise.setReleaseDate(exerciseReleaseDate);
        exerciseRepository.save(exercise);

        instanceMessageReceiveService.processScheduleNotification(exercise.getId());

        Thread.sleep(delayInSeconds * 1000);

        verify(groupNotificationService, times(1)).notifyStudentAndTutorGroupAboutReleasedExercise(exercise);
    }
}
