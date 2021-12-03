package de.tum.in.www1.artemis.service.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.util.TimeUtilService;

class ProgrammingExerciseScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private TimeUtilService timeUtilService;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @SpyBean
    private ScheduleService scheduleService;

    private ProgrammingExercise programmingExercise;

    // When the scheduler is invoked, there is a small delay until the runnable is called.
    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private static final long SCHEDULER_TASK_TRIGGER_DELAY_MS = 1500;

    @BeforeEach
    void init() {
        bitbucketRequestMockProvider.enableMockingOfRequests();
        doReturn(ObjectId.fromString("fffb09455885349da6e19d3ad7fd9c3404c5a0df")).when(gitService).getLastCommitHash(any());

        database.addUsers(3, 2, 0, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAll().get(0);

        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student3");
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
    }

    @AfterEach
    void tearDown() {
        // not yet finished scheduled futures may otherwise affect following tests
        for (final var lifecycle : ExerciseLifecycle.values()) {
            scheduleService.cancelScheduledTaskForLifecycle(programmingExercise.getId(), lifecycle);
        }

        database.resetDatabase();
        reset(scheduleService);
        bambooRequestMockProvider.reset();
        bitbucketRequestMockProvider.reset();
    }

    private void verifyLockStudentRepositoryOperation(boolean wasCalled) {
        final Set<StudentParticipation> studentParticipations = programmingExercise.getStudentParticipations();
        verifyLockStudentRepositoryOperation(wasCalled, studentParticipations);
    }

    private void verifyLockStudentRepositoryOperation(boolean wasCalled, StudentParticipation participation) {
        verifyLockStudentRepositoryOperation(wasCalled, List.of(participation));
    }

    private void verifyLockStudentRepositoryOperation(boolean wasCalled, Iterable<StudentParticipation> studentParticipations) {
        int callCount = wasCalled ? 1 : 0;
        for (StudentParticipation studentParticipation : studentParticipations) {
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            verify(versionControlService, times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getVcsRepositoryUrl(),
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
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), dueDateDelayMS));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        // Instructor build should have been triggered.
        verify(programmingSubmissionService, times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
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
        programmingExercise.setDueDate(plusMillis(ZonedDateTime.now(), delayMS / 2));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS * 2));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryOperation(true);
        verify(programmingSubmissionService, times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() throws Exception {
        long delayMS = 200;
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS));
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
        programmingExercise.setDueDate(plusMillis(ZonedDateTime.now(), delayMS / 2));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS));
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
        programmingExercise.setDueDate(plusMillis(ZonedDateTime.now(), delayMS / 2));
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
        verify(programmingExerciseGradingService, times(1)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), dueDateDelayMS));
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
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), dueDateDelayMS));
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

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verify(gitService, times(1)).combineAllCommitsOfRepositoryIntoOne(repositoryUrl);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void scheduleIndividualDueDateNoBuildAndTestDateLock() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        final List<StudentParticipation> studentParticipationsRegularDueDate = programmingExercise.getStudentParticipations().stream()
                .filter(participation -> !"student3".equals(participation.getStudent().get().getLogin())).toList();
        assertThat(studentParticipationsRegularDueDate).allMatch(participation -> participation.getIndividualDueDate() == null);

        // the repo-lock for the participation with a later due date should only have been called after that individual
        // due date has passed
        verifyLockStudentRepositoryOperation(true, studentParticipationsRegularDueDate);
        verifyLockStudentRepositoryOperation(false, participationIndividualDueDate);

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        verifyLockStudentRepositoryOperation(true, participationIndividualDueDate);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void scheduleIndividualDueDateBetweenDueDateAndBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, 2 * SCHEDULER_TASK_TRIGGER_DELAY_MS);
        // individual due date between regular due date and build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // not yet locked on regular due date
        verifyLockStudentRepositoryOperation(false, participationIndividualDueDate);
        verify(programmingSubmissionService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // locked after individual due date
        verifyLockStudentRepositoryOperation(true, participationIndividualDueDate);

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // after build and test date: no individual build and test actions are scheduled
        verify(programmingSubmissionService, never()).triggerBuildForParticipations(List.of(participationIndividualDueDate));
        verify(programmingSubmissionService, times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void scheduleIndividualDueDateAfterBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, delayMS);
        // individual due date after build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // special scheduling for both lock and build and test
        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void scheduleIndividualDueDateTestsAfterDueDateNoBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        // no build and test date, but after_due_date tests ⇒ score update needed
        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void cancelAllSchedulesOnRemovingExerciseDueDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // remove due date and schedule again
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // all schedules are cancelled
        InOrder cancelCalls = inOrder(scheduleService);
        cancelCalls.verify(scheduleService).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        cancelCalls.verify(scheduleService).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        for (final var participation : programmingExercise.getStudentParticipations()) {
            cancelCalls.verify(scheduleService).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(), ParticipationLifecycle.DUE);
            cancelCalls.verify(scheduleService).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void cancelIndividualSchedulesOnRemovingIndividualDueDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // remove individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(null);
        participationIndividualDueDate = participationRepository.save(participationIndividualDueDate);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // called twice: first time when removing potential old schedules before scheduling, second time only cancelling
        verify(scheduleService, times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateIndividualScheduleOnIndividualDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // change individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(plusMillis(now, 3 * delayMS));
        participationIndividualDueDate = participationRepository.save(participationIndividualDueDate);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // scheduling called twice, each scheduling cancels potential old schedules
        verify(scheduleService, times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void keepIndividualScheduleOnExerciseDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // change exercise due date and schedule again
        programmingExercise.setDueDate(plusMillis(now, delayMS));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, times(2)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void shouldScheduleExerciseIfAnyIndividualDueDateInFuture() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -1 * delayMS / 2, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void shouldCancelAllTasksIfSchedulingNoLongerNeeded() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -1 * delayMS / 2, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.RELEASE);
        verify(scheduleService, times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        verify(scheduleService, times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService, times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
    }

    /**
     * Sets the due date and build and test after due date for the {@code programmingExercise} to NOW + the delay.
     *
     * @param dueDateDelayMillis amount of milliseconds from reference in which the due date should be.
     * @param buildAndTestDateDelayMillis amount of milliseconds from reference in which the build and test after due date should be.
     */
    private void setupProgrammingExerciseDates(final ZonedDateTime reference, Long dueDateDelayMillis, Long buildAndTestDateDelayMillis) {
        if (dueDateDelayMillis != null) {
            programmingExercise.setDueDate(plusMillis(reference, dueDateDelayMillis));
        }
        else {
            programmingExercise.setDueDate(null);
        }

        if (buildAndTestDateDelayMillis != null) {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(reference, buildAndTestDateDelayMillis));
        }
        else {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        }

        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    private ProgrammingExerciseStudentParticipation setupParticipationIndividualDueDate(final ZonedDateTime reference, Long individualDueDateDelayMillis) {
        var participationIndividualDueDate = programmingExercise.getStudentParticipations().stream()
                .filter(participation -> "student3".equals(participation.getStudent().get().getLogin())).findFirst().get();

        if (individualDueDateDelayMillis != null) {
            participationIndividualDueDate.setIndividualDueDate(plusMillis(reference, individualDueDateDelayMillis));
        }
        else {
            participationIndividualDueDate.setIndividualDueDate(null);
        }

        return participationRepository.save((ProgrammingExerciseStudentParticipation) participationIndividualDueDate);
    }

    private ZonedDateTime plusMillis(final ZonedDateTime reference, long millis) {
        return reference.plusNanos(timeUtilService.milliSecondsToNanoSeconds(millis));
    }
}
