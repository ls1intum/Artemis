package de.tum.in.www1.artemis.programmingexercise;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.util.TimeUtilService;

class ProgrammingExerciseScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private TimeUtilService timeUtilService;

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

        database.addUsers(2, 2, 0, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
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
        }
    }

    private void mockStudentRepoLocks() throws URISyntaxException {
        for (final var participation : programmingExercise.getStudentParticipations()) {
            final var repositorySlug = (programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
            bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), participation.getStudents());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldExecuteScheduledBuildAndTestAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 1000;
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(dueDateDelayMS)));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        // Instructor build should have been triggered.
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must not be called.
        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() throws Exception {
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must not be called.
        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // Update all scores should not have been triggered.
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledTwiceIfSameExercise() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200; // 200 ms.
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS / 2)));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS)));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS * 2)));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() throws Exception {
        long delayMS = 200;
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS)));
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(false);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS)));
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
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithManualAssessment() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(delayMS / 2)));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Only lock participations
        verifyLockStudentRepositoryOperation(true);
        // but do not build all
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldUpdateScoresIfHasTestsAfterDueDateAndNoBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.save(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // has AFTER_DUE_DATE tests and no additional build after due date => update the scores to show those test cases in it
        verify(programmingExerciseGradingService, Mockito.times(1)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, Mockito.times(0)).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(dueDateDelayMS)));
        programmingExerciseRepository.save(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
        // has AFTER_DUE_DATE tests, but also buildAfterDueDate => do not update results, but use the results created on additional build run
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasNoTestsAfterDueDate(boolean hasBuildAndTestAfterDueDate) throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        if (hasBuildAndTestAfterDueDate) {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusNanos(timeUtilService.milliSecondsToNanoSeconds(dueDateDelayMS)));
        }
        else {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        }
        programmingExerciseRepository.save(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach(testCase -> testCase.setVisibility(Visibility.ALWAYS));
        programmingExerciseTestCaseRepository.saveAll(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true);
        if (hasBuildAndTestAfterDueDate) {
            verify(programmingSubmissionService, Mockito.times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        else {
            verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        // no tests marked as AFTER_DUE_DATE => do not update scores on due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCombineTemplateBeforeRelease() throws Exception {
        ProgrammingExercise programmingExerciseWithTemplate = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        VcsRepositoryUrl repositoryUrl = programmingExerciseWithTemplate.getVcsTemplateRepositoryUrl();
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(repositoryUrl);

        programmingExercise.releaseDate(ZonedDateTime.now().plusSeconds(Constants.SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS + 1));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(1500);

        verify(gitService, times(1)).combineAllCommitsOfRepositoryIntoOne(repositoryUrl);
    }

    // ToDo: participation with individual due date between due date and build and test date

    // ToDo: participation with individual due date after due date and build and test date

    // ToDo: participation with individual due date in exercise without build and test date

    // ToDo: exercise with mix of participants with and without individual due dates

    // ToDo: participation with individual due date in exercise with tests after_due_date but no build and test date

    // ToDo: cancellation of schedules when removing a due date

    // ToDo: update of schedule when changing a due date

    // ToDo: keeping individual schedule when changing due date of an exercise
}
