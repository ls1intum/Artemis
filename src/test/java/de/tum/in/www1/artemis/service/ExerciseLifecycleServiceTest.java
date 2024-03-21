package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;

class ExerciseLifecycleServiceTest extends AbstractSpringIntegrationIndependentTest {

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

        assertThat(releaseFuture.isDone()).isFalse();
        assertThat(dueFuture.isDone()).isFalse();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().pollInterval(50, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, false);
            assertEqual(assessmentDueTrigger, false);
        });

        assertThat(releaseFuture.isDone()).isTrue();
        assertThat(dueFuture.isDone()).isFalse();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().pollInterval(50, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, true);
            assertEqual(assessmentDueTrigger, false);
        });

        assertThat(releaseFuture.isDone()).isTrue();
        assertThat(dueFuture.isDone()).isTrue();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().pollInterval(50, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, true);
            assertEqual(assessmentDueTrigger, true);
        });

        assertThat(releaseFuture.isDone()).isTrue();
        assertThat(dueFuture.isDone()).isTrue();
        assertThat(assessmentDueFuture.isDone()).isTrue();

        assertThat(releaseFuture.isCancelled()).isFalse();
        assertThat(dueFuture.isCancelled()).isFalse();
        assertThat(assessmentDueFuture.isCancelled()).isFalse();
    }

    @Test
    void testCancellationOfScheduledTask() {
        Exercise exercise = new TextExercise();
        exercise.setTitle("ExerciseLifecycleServiceTest:testCancellationOfScheduledTask");
        exercise.setDueDate(ZonedDateTime.now().plus(200, ChronoUnit.MILLIS));
        MutableBoolean trigger = new MutableBoolean(false);

        final ScheduledFuture<?> future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, trigger::setTrue);

        assertThat(future.isDone()).isFalse();
        assertThat(future.isCancelled()).isFalse();
        assertEqual(trigger, false);

        future.cancel(false);

        assertThat(future.isDone()).isTrue();
        assertThat(future.isCancelled()).isTrue();
        assertEqual(trigger, false);

        await().untilAsserted(() -> {
            assertThat(future.isDone()).isTrue();
            assertThat(future.isCancelled()).isTrue();
            assertEqual(trigger, false);
        });
    }

    private void assertEqual(MutableBoolean testBoolean, boolean expected) {
        assertThat(testBoolean.toBoolean()).isEqualTo(expected);
    }
}
