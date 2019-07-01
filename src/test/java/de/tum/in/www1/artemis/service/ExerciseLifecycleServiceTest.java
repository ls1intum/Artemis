package de.tum.in.www1.artemis.service;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ExerciseLifecycleServiceTest {

    @Autowired
    ExerciseLifecycleService exerciseLifecycleService;

    @Test
    public void testScheduleExerciseOnReleaseTask() throws InterruptedException {
        final ZonedDateTime now = ZonedDateTime.now();

        Exercise exercise = new TextExercise().title("ExerciseLifecycleServiceTest:testScheduleExerciseOnReleaseTask").releaseDate(now.plusSeconds(1)).dueDate(now.plusSeconds(2))
                .assessmentDueDate(now.plusSeconds(3));

        MutableBoolean releaseTrigger = new MutableBoolean(false);
        MutableBoolean dueTrigger = new MutableBoolean(false);
        MutableBoolean assessmentDueTrigger = new MutableBoolean(false);

        final ScheduledFuture<?> releaseFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, releaseTrigger::setTrue);
        final ScheduledFuture<?> dueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, dueTrigger::setTrue);
        final ScheduledFuture<?> assessmentDueFuture = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, assessmentDueTrigger::setTrue);

        assertFalse(releaseFuture.isDone());
        assertFalse(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        Thread.sleep(1500);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, false);
        assertEqual(assessmentDueTrigger, false);

        assertTrue(releaseFuture.isDone());
        assertFalse(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        Thread.sleep(1000);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, true);
        assertEqual(assessmentDueTrigger, false);

        assertTrue(releaseFuture.isDone());
        assertTrue(dueFuture.isDone());
        assertFalse(assessmentDueFuture.isDone());

        Thread.sleep(1000);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, true);
        assertEqual(assessmentDueTrigger, true);

        assertTrue(releaseFuture.isDone());
        assertTrue(dueFuture.isDone());
        assertTrue(assessmentDueFuture.isDone());

        assertFalse(releaseFuture.isCancelled());
        assertFalse(dueFuture.isCancelled());
        assertFalse(assessmentDueFuture.isCancelled());
    }

    @Test
    public void testCancellationOfScheduledTask() throws InterruptedException {
        Exercise exercise = new TextExercise().title("ExerciseLifecycleServiceTest:testCancellationOfScheduledTask").dueDate(ZonedDateTime.now().plusSeconds(1));
        MutableBoolean trigger = new MutableBoolean(false);

        final ScheduledFuture<?> future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, trigger::setTrue);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEqual(trigger, false);

        Thread.sleep(500);

        future.cancel(false);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEqual(trigger, false);

        Thread.sleep(750);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEqual(trigger, false);
    }

    private void assertEqual(MutableBoolean testBoolean, boolean expected) {
        assertThat(testBoolean.toBoolean(), is(equalTo(expected)));
    }

}
