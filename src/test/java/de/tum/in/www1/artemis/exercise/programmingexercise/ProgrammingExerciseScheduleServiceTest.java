package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

class ProgrammingExerciseScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "programmingexercisescheduleservice";

    @Autowired
    private InstanceMessageReceiveService instanceMessageReceiveService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private ProgrammingExercise programmingExercise;

    private final LocalRepository studentRepository = new LocalRepository(defaultBranch);

    // When the scheduler is invoked, there is a small delay until the runnable is called.
    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private static final long SCHEDULER_TASK_TRIGGER_DELAY_MS = 1000;

    @BeforeEach
    void init() throws Exception {
        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        doReturn(ObjectId.fromString("fffb09455885349da6e19d3ad7fd9c3404c5a0df")).when(gitService).getLastCommitHash(any());

        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // not yet finished scheduled futures may otherwise affect following tests
        scheduleService.clearAllTasks();

        // TODO: find a better solution in the future, because this makes the tests slower
        // Some futures might already run while all tasks are cancelled. Waiting a bit makes sure the mocks are not called by the futures after the reset.
        // Otherwise, the following test might fail.
        Thread.sleep(500);  // ok

        bambooRequestMockProvider.reset();
        bitbucketRequestMockProvider.reset();
    }

    private void verifyLockStudentRepositoryAndParticipationOperation(boolean wasCalled, long timeoutInMs) {
        final Set<StudentParticipation> studentParticipations = programmingExercise.getStudentParticipations();
        verifyLockStudentRepositoryAndParticipationOperation(wasCalled, studentParticipations, timeoutInMs);
    }

    private void verifyLockStudentRepositoryAndParticipationOperation(boolean wasCalled, StudentParticipation participation, long timeoutInMs) {
        verifyLockStudentRepositoryAndParticipationOperation(wasCalled, List.of(participation), timeoutInMs);
    }

    private void verifyLockStudentRepositoryAndParticipationOperation(boolean wasCalled, Iterable<StudentParticipation> studentParticipations, long timeoutInMs) {
        int callCount = wasCalled ? 1 : 0;
        for (StudentParticipation studentParticipation : studentParticipations) {
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            verify(versionControlService, timeout(timeoutInMs).times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getVcsRepositoryUrl(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudents());
            verify(programmingExerciseParticipationService, timeout(timeoutInMs).times(callCount)).lockStudentParticipation(programmingExercise,
                    programmingExerciseStudentParticipation);
        }
    }

    private void mockStudentRepoLocks() throws URISyntaxException, GitAPIException {
        for (final var participation : programmingExercise.getStudentParticipations()) {
            final var repositorySlug = (programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
            bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), participation.getStudents());
            doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                    .getOrCheckoutRepository((ProgrammingExerciseParticipation) participation);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldExecuteScheduledBuildAndTestAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 200;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), dueDateDelayMS));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryAndParticipationOperation(true, dueDateDelayMS);
        // Instructor build should have been triggered.
        verify(programmingTriggerService, timeout(dueDateDelayMS).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() throws InterruptedException {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);  // ok

        // Lock student repository must not be called.
        verifyLockStudentRepositoryAndParticipationOperation(false, 0);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() throws InterruptedException {
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(SCHEDULER_TASK_TRIGGER_DELAY_MS);    // ok

        // Lock student repository must not be called.
        verifyLockStudentRepositoryAndParticipationOperation(false, 0);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
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

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryAndParticipationOperation(true, delayMS * 2);
        verify(programmingTriggerService, timeout(delayMS * 2).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() throws InterruptedException {
        long delayMS = 200;
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS));
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);      // ok

        verifyLockStudentRepositoryAndParticipationOperation(false, 0);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() throws Exception {
        mockStudentRepoLocks();
        long delayMS = 800;
        programmingExercise.setDueDate(plusMillis(ZonedDateTime.now(), delayMS / 2));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), delayMS));
        programmingExerciseRepository.save(programmingExercise);

        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise2 = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verifyLockStudentRepositoryAndParticipationOperation(true, delayMS);
        verify(programmingTriggerService, timeout(5000).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
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

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);      // ok

        // Only lock participations
        verifyLockStudentRepositoryAndParticipationOperation(true, delayMS);
        // but do not build all
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldUpdateScoresIfHasTestsAfterDueDateAndNoBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 500;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.save(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);   // ok

        verifyLockStudentRepositoryAndParticipationOperation(true, dueDateDelayMS);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // has AFTER_DUE_DATE tests and no additional build after due date => update the scores to show those test cases in it
        verify(programmingExerciseGradingService, timeout(5000).times(1)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        final var dueDateDelayMS = 500;
        programmingExercise.setDueDate(ZonedDateTime.now().plus(dueDateDelayMS / 2, ChronoUnit.MILLIS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(plusMillis(ZonedDateTime.now(), dueDateDelayMS));
        programmingExerciseRepository.save(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);   // ok

        verifyLockStudentRepositoryAndParticipationOperation(true, dueDateDelayMS / 2);
        verify(programmingTriggerService, timeout(dueDateDelayMS).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
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

        Thread.sleep(dueDateDelayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);   // ok

        verifyLockStudentRepositoryAndParticipationOperation(true, dueDateDelayMS / 2);
        if (hasBuildAndTestAfterDueDate) {
            verify(programmingTriggerService, timeout(dueDateDelayMS).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        else {
            verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        // no tests marked as AFTER_DUE_DATE => do not update scores on due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCombineTemplateBeforeRelease() throws Exception {
        ProgrammingExercise programmingExerciseWithTemplate = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        VcsRepositoryUrl repositoryUrl = programmingExerciseWithTemplate.getVcsTemplateRepositoryUrl();
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(repositoryUrl);

        programmingExercise.setReleaseDate(ZonedDateTime.now().plusSeconds(Constants.SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS + 1));
        programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(gitService, timeout(5000).times(1)).combineAllCommitsOfRepositoryIntoOne(repositoryUrl);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateNoBuildAndTestDateLock() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 400;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var login = TEST_PREFIX + "student3";
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 3 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS, login);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        var studentParticipationsRegularDueDate = getParticipationsWithoutIndividualDueDate();
        assertThat(studentParticipationsRegularDueDate).hasSize(2).allMatch(participation -> !participation.getStudent().get().getLogin().equals(login));

        var studentParticipationIndividualDueDate = getParticipation(login);
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotNull();
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotEqualTo(programmingExercise.getDueDate());

        // the repo-lock for the participation with a later due date should only have been called after that individual due date has passed
        verifyLockStudentRepositoryAndParticipationOperation(true, studentParticipationsRegularDueDate, delayMS);
        // first the operation should not be called
        verifyLockStudentRepositoryAndParticipationOperation(false, participationIndividualDueDate, 0);
        // after some time the operation should be called as well (verify waits up to 5s until this condition is fulfilled)
        verifyLockStudentRepositoryAndParticipationOperation(true, participationIndividualDueDate, 3 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateBetweenDueDateAndBuildAndTestDate() throws Exception {
        bitbucketRequestMockProvider.reset();
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, 2 * SCHEDULER_TASK_TRIGGER_DELAY_MS);
        // individual due date between regular due date and build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);      // ok

        // not yet locked on regular due date
        verifyLockStudentRepositoryAndParticipationOperation(false, participationIndividualDueDate, 0);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());

        // locked after individual due date
        verifyLockStudentRepositoryAndParticipationOperation(true, participationIndividualDueDate, 2 * delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        Thread.sleep(delayMS + SCHEDULER_TASK_TRIGGER_DELAY_MS);      // ok

        // after build and test date: no individual build and test actions are scheduled
        verify(programmingTriggerService, never()).triggerBuildForParticipations(List.of(participationIndividualDueDate));
        verify(programmingTriggerService, timeout(2 * SCHEDULER_TASK_TRIGGER_DELAY_MS).times(1)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateAfterBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, delayMS);
        // individual due date after build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // special scheduling for both lock and build and test
        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE),
                any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateTestsAfterDueDateNoBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 500;
        final ZonedDateTime now = ZonedDateTime.now();

        // no build and test date, but after_due_date tests ⇒ score update needed
        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(5000).times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelAllSchedulesOnRemovingExerciseDueDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 500;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(5000).times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // remove due date and schedule again
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // all schedules are cancelled
        InOrder cancelCalls = inOrder(scheduleService);
        cancelCalls.verify(scheduleService, timeout(5000)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        cancelCalls.verify(scheduleService, timeout(5000)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        for (final var participation : programmingExercise.getStudentParticipations()) {
            cancelCalls.verify(scheduleService, timeout(5000)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.DUE);
            cancelCalls.verify(scheduleService, timeout(5000)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelIndividualSchedulesOnRemovingIndividualDueDate() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(5000).times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // remove individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(null);
        participationIndividualDueDate = participationRepository.save(participationIndividualDueDate);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // called twice: first time when removing potential old schedules before scheduling, second time only cancelling
        verify(scheduleService, timeout(5000).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateIndividualScheduleOnIndividualDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 500;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(5000).times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // change individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(plusMillis(now, 3 * delayMS));
        participationIndividualDueDate = participationRepository.save(participationIndividualDueDate);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // scheduling called twice, each scheduling cancels potential old schedules
        verify(scheduleService, timeout(5000).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(5000).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void keepIndividualScheduleOnExerciseDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 500;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, delayMS / 2, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().get().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(5000).times(1)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // change exercise due date and schedule again
        programmingExercise.setDueDate(plusMillis(now, delayMS));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(5000).times(2)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExerciseIfAnyIndividualDueDateInFuture() throws Exception {
        mockStudentRepoLocks();
        final long delayMS = 200;
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -1 * delayMS / 2, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * delayMS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(5000).times(1)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCancelAllTasksIfSchedulingNoLongerNeeded() throws Exception {
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
        verify(scheduleService, timeout(5000).times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.RELEASE);
        verify(scheduleService, timeout(5000).times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        verify(scheduleService, timeout(5000).times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService, timeout(5000).times(1)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void foo() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExamViaExerciseGroupOrCourseMember();
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam = examRepository.save(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1").getParticipation();
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.save(studentExam);

        instanceMessageReceiveService.processExamWorkingTimeChangeDuringConduction(studentExam.getId());

        verify(versionControlService, timeout(200).times(1)).setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUrl(), examExercise.getProjectKey(),
                participation.getStudents());
    }

    /**
     * Sets the due date and build and test after due date for the {@code programmingExercise} to NOW + the delay.
     *
     * @param dueDateDelayMillis          amount of milliseconds from reference in which the due date should be.
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

    private ProgrammingExerciseStudentParticipation setupParticipationIndividualDueDate(final ZonedDateTime reference, Long individualDueDateDelayMillis, String login) {
        var participationIndividualDueDate = getParticipation(login);

        if (individualDueDateDelayMillis != null) {
            participationIndividualDueDate.setIndividualDueDate(plusMillis(reference, individualDueDateDelayMillis));
        }
        else {
            participationIndividualDueDate.setIndividualDueDate(null);
        }

        return participationRepository.save((ProgrammingExerciseStudentParticipation) participationIndividualDueDate);
    }

    private StudentParticipation getParticipation(String login) {
        return programmingExercise.getStudentParticipations().stream().filter(participation -> login.equals(participation.getStudent().get().getLogin())).findFirst().get();
    }

    private List<StudentParticipation> getParticipationsWithoutIndividualDueDate() {
        return programmingExercise.getStudentParticipations().stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
    }

    private ZonedDateTime plusMillis(final ZonedDateTime reference, long millis) {
        return reference.plus(millis, ChronoUnit.MILLIS);
    }
}
