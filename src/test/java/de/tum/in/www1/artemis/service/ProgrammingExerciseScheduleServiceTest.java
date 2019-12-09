package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.TimeService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bitbucket")
class ProgrammingExerciseScheduleServiceTest {

    @Autowired
    ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @MockBean
    ProgrammingSubmissionService programmingSubmissionService;

    @MockBean
    VersionControlService versionControlService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    TimeService timeService;

    private ProgrammingExercise programmingExercise;

    // When the scheduler is invoked, there is a small delay until the runnable is called.
    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private final long SCHEDULER_TASK_TRIGGER_DELAY_MS = 200;

    @BeforeEach
    void init() {
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    private void verifyLockStudentRepositoryOperation(boolean wasCalled) throws Exception {
        int callCount = wasCalled ? 1 : 0;
        Set<StudentParticipation> studentParticipations = programmingExercise.getStudentParticipations();
        for (StudentParticipation studentParticipation : studentParticipations) {
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            Mockito.verify(versionControlService, Mockito.times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getRepositoryUrlAsUrl(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudent().getLogin());
            Mockito.verify(versionControlService, Mockito.times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getRepositoryUrlAsUrl(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudent().getLogin());
        }
    }

    @Test
    void shouldExecuteScheduledBuildAndTestAfterDueDate() throws Exception {
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        // Instructor build should have been triggered.
        Mockito.verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        Thread.sleep(1000);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(false);
        Mockito.verify(programmingSubmissionService, Mockito.never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() throws Exception {
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        Thread.sleep(1000);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(false);
        Mockito.verify(programmingSubmissionService, Mockito.never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledTwiceIfSameExercise() throws Exception {
        long delayMS = 200; // 100 ms.
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS) * 2));
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        Thread.sleep(delayMS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        Mockito.verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() throws Exception {
        long delayMS = 200;
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(false);
        Mockito.verify(programmingSubmissionService, Mockito.never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() throws Exception {
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExerciseRepository.save(programmingExercise);

        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise2 = programmingExerciseRepository.findAll().get(1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        programmingExerciseScheduleService.scheduleRunningExercisesOnStartup();

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true);
        Mockito.verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }
}
