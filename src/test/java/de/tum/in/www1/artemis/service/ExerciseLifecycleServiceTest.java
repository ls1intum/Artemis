package de.tum.in.www1.artemis.service;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.time.ZonedDateTime;

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

        Exercise exercise = new TextExercise().title("ExerciseLifecycleServiceTest").releaseDate(now.plusSeconds(2)).dueDate(now.plusSeconds(5))
                .assessmentDueDate(now.plusSeconds(8));

        MutableBoolean releaseTrigger = new MutableBoolean(false);
        MutableBoolean dueTrigger = new MutableBoolean(false);
        MutableBoolean assessmentDueTrigger = new MutableBoolean(false);

        exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, releaseTrigger::setTrue);
        exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, dueTrigger::setTrue);
        exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, assessmentDueTrigger::setTrue);

        Thread.sleep(2500);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, false);
        assertEqual(assessmentDueTrigger, false);

        Thread.sleep(3500);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, true);
        assertEqual(assessmentDueTrigger, false);

        Thread.sleep(2500);
        assertEqual(releaseTrigger, true);
        assertEqual(dueTrigger, true);
        assertEqual(assessmentDueTrigger, true);
    }

    private void assertEqual(MutableBoolean testBoolean, boolean expected) {
        assertThat(testBoolean.toBoolean(), is(equalTo(expected)));
    }

}
