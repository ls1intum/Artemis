package de.tum.in.www1.artemis.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.util.TimeService;

class ProgrammingExerciseScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    TimeService timeService;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    private ProgrammingExercise programmingExercise;

    // When the scheduler is invoked, there is a small delay until the runnable is called.
    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private final long SCHEDULER_TASK_TRIGGER_DELAY_MS = 1500;

    @BeforeEach
    void init() {
        bitbucketRequestMockProvider.enableMockingOfRequests();
        doReturn(ObjectId.fromString("fffb09455885349da6e19d3ad7fd9c3404c5a0df")).when(gitService).getLastCommitHash(any());

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

    private void verifyLockStudentRepositoryOperation(boolean wasCalled) {
        int callCount = wasCalled ? 1 : 0;
        Set<StudentParticipation> studentParticipations = programmingExercise.getStudentParticipations();
        for (StudentParticipation studentParticipation : studentParticipations) {
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            verify(versionControlService, Mockito.times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getVcsRepositoryUrl(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudents());
            verify(versionControlService, Mockito.times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getVcsRepositoryUrl(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudents());
        }
    }

    private void mockStudentRepoLocks() throws URISyntaxException {
        for (final var participation : programmingExercise.getStudentParticipations()) {
            final var repositorySlug = (programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
            bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), participation.getStudents());
        }
    }

    @Test
    void shouldExecuteScheduledBuildAndTestAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 1000;
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(dueDateDelayMS)));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        // Instructor build should have been triggered.
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() throws Exception {
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledTwiceIfSameExercise() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200; // 200 ms.
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS * 2)));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() throws Exception {
        long delayMS = 200;
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS)));
        programmingExerciseRepository.save(programmingExercise);

        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise2 = programmingExerciseRepository.findAll().get(1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    void shouldScheduleExercisesWithManualAssessment() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Only lock participations
        verifyLockStudentRepositoryOperation(true);
        // but do not build all
        verify(programmingSubmissionService, Mockito.times(0)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }
}
