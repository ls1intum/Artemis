package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseLifecycleServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final long SCHEDULE_OFFSET_MS = 2_000;

    private static final long SCHEDULE_GAP_MS = 2_000;

    private static final long AWAIT_TIMEOUT_SECONDS = 15;

    @Autowired
    private ExerciseLifecycleService exerciseLifecycleService;

    @Test
    void testScheduleExerciseOnReleaseTask() {
        final ZonedDateTime now = ZonedDateTime.now();

        Exercise exercise = new TextExercise();
        exercise.setTitle("ExerciseLifecycleServiceTest:testScheduleExerciseOnReleaseTask");
        exercise.setReleaseDate(now.plus(SCHEDULE_OFFSET_MS, ChronoUnit.MILLIS));
        exercise.setDueDate(now.plus(SCHEDULE_OFFSET_MS + SCHEDULE_GAP_MS, ChronoUnit.MILLIS));
        exercise.setAssessmentDueDate(now.plus(SCHEDULE_OFFSET_MS + 2 * SCHEDULE_GAP_MS, ChronoUnit.MILLIS));

        MutableBoolean releaseTrigger = new MutableBoolean(false);
        MutableBoolean dueTrigger = new MutableBoolean(false);
        MutableBoolean assessmentDueTrigger = new MutableBoolean(false);

        final ScheduledFuture<?> releaseFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, releaseTrigger::setTrue);
        final ScheduledFuture<?> dueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, dueTrigger::setTrue);
        final ScheduledFuture<?> assessmentDueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, assessmentDueTrigger::setTrue);

        assertThat(releaseFuture.isDone()).isFalse();
        assertThat(dueFuture.isDone()).isFalse();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, false);
            assertEqual(assessmentDueTrigger, false);
        });

        assertThat(releaseFuture.isDone()).isTrue();
        assertThat(dueFuture.isDone()).isFalse();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertEqual(releaseTrigger, true);
            assertEqual(dueTrigger, true);
            assertEqual(assessmentDueTrigger, false);
        });

        assertThat(releaseFuture.isDone()).isTrue();
        assertThat(dueFuture.isDone()).isTrue();
        assertThat(assessmentDueFuture.isDone()).isFalse();

        await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
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
        exercise.setDueDate(ZonedDateTime.now().plus(SCHEDULE_OFFSET_MS, ChronoUnit.MILLIS));
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
