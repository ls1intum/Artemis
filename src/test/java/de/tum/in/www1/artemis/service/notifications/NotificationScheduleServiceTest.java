package de.tum.in.www1.artemis.service.notifications;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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
    void shouldCreateNotificationAtReleaseDate() {
        database.addCourseWithFileUploadExercise();
        Exercise exercise = exerciseRepository.findAll().get(0);
        long delayInMS = 200;
        ZonedDateTime exerciseReleaseDate = ZonedDateTime.now().plus(delayInMS, ChronoUnit.MILLIS);
        exercise.setReleaseDate(exerciseReleaseDate);
        exerciseRepository.save(exercise);

        instanceMessageReceiveService.processScheduleNotification(exercise.getId());

        verify(groupNotificationService, timeout(5000).times(1)).notifyAllGroupsAboutReleasedExercise(exercise);
    }
}
