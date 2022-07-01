package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;

class ExerciseLifecycleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseLifecycleService exerciseLifecycleService;

    @Test
    void testScheduleExerciseOnReleaseTask() {
        final ZonedDateTime now = ZonedDateTime.now();

        Exercise exercise = new TextExercise();
        exercise.setTitle("ExerciseLifecycleServiceTest:testScheduleExerciseOnReleaseTask");
        exercise.setReleaseDate(now.plus(200, ChronoUnit.MILLIS));
        exercise.setDueDate(now.plus(400, ChronoUnit.MILLIS));
        exercise.setAssessmentDueDate(now.plus(600, ChronoUnit.MILLIS));

        MutableBoolean releaseTrigger = new MutableBoolean(false);
        MutableBoolean dueTrigger = new MutableBoolean(false);
        MutableBoolean assessmentDueTrigger = new MutableBoolean(false);

        final ScheduledFuture<?> releaseFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, releaseTrigger::setTrue);
        final ScheduledFuture<?> dueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, dueTrigger::setTrue);
        final ScheduledFuture<?> assessmentDueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, assessmentDueTrigger::setTrue);

        assertFalse(releaseFuture.isDone());
        assertFalse(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        await().untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, false);
            assertEqual(assessmentDueTrigger, false);
        });

        assertTrue(releaseFuture.isDone());
        assertFalse(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        await().untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, true);
            assertEqual(assessmentDueTrigger, false);
        });

        assertTrue(releaseFuture.isDone());
        assertTrue(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        await().untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, true);
            assertEqual(assessmentDueTrigger, true);
        });

        assertTrue(releaseFuture.isDone());
        assertTrue(dueFuture.isDone());
        assertTrue(assessmentDueFuture.isDone());

        assertFalse(releaseFuture.isCancelled());
        assertFalse(dueFuture.isCancelled());
        assertFalse(assessmentDueFuture.isCancelled());
    }

    @Test
    void testCancellationOfScheduledTask() {
        Exercise exercise = new TextExercise();
        exercise.setTitle("ExerciseLifecycleServiceTest:testCancellationOfScheduledTask");
        exercise.setDueDate(ZonedDateTime.now().plus(200, ChronoUnit.MILLIS));
        MutableBoolean trigger = new MutableBoolean(false);

        final ScheduledFuture<?> future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, trigger::setTrue);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEqual(trigger, false);

        future.cancel(false);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEqual(trigger, false);

        await().untilAsserted(() -> {
            assertTrue(future.isDone());
            assertTrue(future.isCancelled());
            assertEqual(trigger, false);
        });
    }

    private void assertEqual(MutableBoolean testBoolean, boolean expected) {
        assertThat(testBoolean.toBoolean()).isEqualTo(expected);
    }
}
