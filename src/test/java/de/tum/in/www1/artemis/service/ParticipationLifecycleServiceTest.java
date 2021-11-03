package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;

public class ParticipationLifecycleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ParticipationLifecycleService participationLifecycleService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    private final Runnable noop = () -> {
    };

    @BeforeEach
    public void reset() {
        SecurityUtils.setAuthorizationObject();

        database.addUsers(1, 1, 1, 1);
        Course course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).findAny().orElseThrow();
        participation = database.addStudentParticipationForProgrammingExercise(this.programmingExercise, "student1");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void scheduleTaskNoBuildAndTestDate() {
        setupExerciseAndParticipation(null, ZonedDateTime.now().plusHours(1));

        // should not be scheduled at all
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat((Future<?>) scheduledTask).isNull();
    }

    @Test
    public void scheduleTaskOnlyBuildAndTestAfterDueDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(1), null);

        // should still be scheduled even if no individual due date affects the scheduling
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask.getDelay(TimeUnit.MINUTES)).isCloseTo(60, Offset.offset(1L));
    }

    @Test
    public void scheduleTaskDueDateBeforeBuildAndTestDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusHours(1));

        // scheduling should choose proper build and test after due date as date
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask.getDelay(TimeUnit.MINUTES)).isCloseTo(120, Offset.offset(1L));
    }

    @Test
    public void scheduleTaskDueDateAfterBuildAndTestDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        // scheduling should choose individual due date (after build and test date) as scheduling date
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask.getDelay(TimeUnit.MINUTES)).isCloseTo(120, Offset.offset(1L));
    }

    @Test
    public void scheduleOnIndividualDueDate() {
        setupExerciseAndParticipation(null, ZonedDateTime.now().plusHours(2));

        // scheduling should choose individual due date as scheduling date
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.DUE, noop);
        assertThat(scheduledTask.getDelay(TimeUnit.MINUTES)).isCloseTo(120, Offset.offset(1L));
    }

    @Test
    public void scheduleNoDueDate() {
        setupExerciseAndParticipation(null, null);
        programmingExercise.setDueDate(null);

        // should not be scheduled at all
        ScheduledFuture<?> scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.DUE, noop);
        assertThat((Future<?>) scheduledTask).isNull();
    }

    private void setupExerciseAndParticipation(ZonedDateTime exerciseBuildAndTestDate, ZonedDateTime individualDueDate) {
        programmingExercise.setDueDate(ZonedDateTime.now());
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(exerciseBuildAndTestDate);

        participation.setIndividualDueDate(individualDueDate);
        participation.setExercise(programmingExercise);
    }
}
