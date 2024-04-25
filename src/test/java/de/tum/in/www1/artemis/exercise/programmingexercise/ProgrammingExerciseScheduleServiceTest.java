package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
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
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

class ProgrammingExerciseScheduleServiceTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

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

    private static final long DELAY_MS = 300;

    private static final long TIMEOUT_MS = 5000;

    @BeforeEach
    void init() throws Exception {
        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");
        gitlabRequestMockProvider.enableMockingOfRequests();
        doReturn(ObjectId.fromString("fffb09455885349da6e19d3ad7fd9c3404c5a0df")).when(gitService).getLastCommitHash(any());

        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduleService.clearAllTasks();
        gitlabRequestMockProvider.reset();
        studentRepository.resetLocalRepo();
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
            verify(versionControlService, timeout(timeoutInMs).times(callCount)).setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getVcsRepositoryUri(),
                    programmingExercise.getProjectKey(), programmingExerciseStudentParticipation.getStudents());
            verify(programmingExerciseParticipationService, timeout(timeoutInMs).times(callCount)).lockStudentParticipation(programmingExerciseStudentParticipation);
        }
    }

    private void mockStudentRepoLocks() throws GitAPIException, GitLabApiException {
        for (final var participation : programmingExercise.getStudentParticipations()) {
            final VcsRepositoryUri repositoryUri = ((ProgrammingExerciseParticipation) participation).getVcsRepositoryUri();
            gitlabRequestMockProvider.setRepositoryPermissionsToReadOnly(repositoryUri, participation.getStudents());
            doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                    .getOrCheckoutRepository((ProgrammingExerciseParticipation) participation);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldExecuteScheduledBuildAndTestAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        // Instructor build should have been triggered.
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Lock student repository must not be called.
        verifyLockStudentRepositoryAndParticipationOperation(false, TIMEOUT_MS);
        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() {
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // Update all scores should not have been triggered.
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
        // Lock student repository must not be called.
        verifyLockStudentRepositoryAndParticipationOperation(false, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledTwiceIfSameExercise() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Lock student repository must be called once per participation.
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() {
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        verifyLockStudentRepositoryAndParticipationOperation(false, 0);
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithManualAssessment() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // but do not build all
        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // Only lock participations
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldUpdateScoresIfHasTestsAfterDueDateAndNoBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        // has AFTER_DUE_DATE tests and no additional build after due date => update the scores to show those test cases in it
        verify(programmingExerciseGradingService, timeout(5000)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // has AFTER_DUE_DATE tests, but also buildAfterDueDate => do not update results, but use the results created on additional build run
        verify(programmingExerciseGradingService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).updateAllResults(programmingExercise);
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasNoTestsAfterDueDate(boolean hasBuildAndTestAfterDueDate) throws Exception {
        mockStudentRepoLocks();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        if (hasBuildAndTestAfterDueDate) {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        }
        else {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        }
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach(testCase -> testCase.setVisibility(Visibility.ALWAYS));
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // no tests marked as AFTER_DUE_DATE => do not update scores on due date
        verify(programmingExerciseGradingService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).updateAllResults(programmingExercise);
        verifyLockStudentRepositoryAndParticipationOperation(true, TIMEOUT_MS);
        if (hasBuildAndTestAfterDueDate) {
            verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        else {
            verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCombineTemplateBeforeRelease() throws Exception {
        ProgrammingExercise programmingExerciseWithTemplate = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        VcsRepositoryUri repositoryUri = programmingExerciseWithTemplate.getVcsTemplateRepositoryUri();
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(repositoryUri);

        programmingExercise.setReleaseDate(nowPlusMillis(DELAY_MS).plusSeconds(Constants.SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(gitService, timeout(TIMEOUT_MS)).combineAllCommitsOfRepositoryIntoOne(repositoryUri);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateNoBuildAndTestDateLock() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var login = TEST_PREFIX + "student3";
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS, login);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        var studentParticipationsRegularDueDate = getParticipationsWithoutIndividualDueDate();
        assertThat(studentParticipationsRegularDueDate).hasSize(2).allMatch(participation -> !participation.getStudent().orElseThrow().getLogin().equals(login));

        var studentParticipationIndividualDueDate = getParticipation(login);
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotNull();
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotEqualTo(programmingExercise.getDueDate());

        // the repo-lock for the participation with a later due date should only have been called after that individual due date has passed
        verifyLockStudentRepositoryAndParticipationOperation(true, studentParticipationsRegularDueDate, DELAY_MS * 2);
        // first the operation should not be called
        verifyLockStudentRepositoryAndParticipationOperation(false, participationIndividualDueDate, 0);
        // after some time the operation should be called as well (verify waits up to 5s until this condition is fulfilled)
        verifyLockStudentRepositoryAndParticipationOperation(true, participationIndividualDueDate, TIMEOUT_MS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateBetweenDueDateAndBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, 2 * SCHEDULER_TASK_TRIGGER_DELAY_MS);
        // individual due date between regular due date and build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(DELAY_MS * 2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        // not yet locked on regular due date
        verify(programmingTriggerService, after(DELAY_MS * 2).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        verifyLockStudentRepositoryAndParticipationOperation(false, participationIndividualDueDate, 0);

        // locked after individual due date
        verifyLockStudentRepositoryAndParticipationOperation(true, participationIndividualDueDate, 2 * DELAY_MS + SCHEDULER_TASK_TRIGGER_DELAY_MS);

        // after build and test date: no individual build and test actions are scheduled
        verify(programmingTriggerService, after(DELAY_MS + SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerBuildForParticipations(List.of(participationIndividualDueDate));
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateAfterBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, DELAY_MS);
        // individual due date after build and test date
        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // special scheduling for both lock and build and test
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateTestsAfterDueDateNoBuildAndTestDate() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        // no build and test date, but after_due_date tests ⇒ score update needed
        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelAllSchedulesOnRemovingExerciseDueDate() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // remove due date and schedule again
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // all schedules are cancelled
        InOrder cancelCalls = inOrder(scheduleService);
        cancelCalls.verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        cancelCalls.verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        for (final var participation : programmingExercise.getStudentParticipations()) {
            cancelCalls.verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.DUE);
            cancelCalls.verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelIndividualSchedulesOnRemovingIndividualDueDate() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // remove individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(null);
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // called twice: first time when removing potential old schedules before scheduling, second time only cancelling
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateIndividualScheduleOnIndividualDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2 * DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        // change individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(nowPlusMillis(DELAY_MS));
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // scheduling called twice, each scheduling cancels potential old schedules
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void keepIndividualScheduleOnExerciseDueDateChange() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, 1000L, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, 2000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));

        // change exercise due date and schedule again
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any());

        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExerciseIfAnyIndividualDueDateInFuture() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -DELAY_MS, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        var participationIndividualDueDate = setupParticipationIndividualDueDate(now, DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any());
        verify(scheduleService, never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCancelAllTasksIfSchedulingNoLongerNeeded() throws Exception {
        mockStudentRepoLocks();
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -DELAY_MS, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).scheduleTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.RELEASE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testStudentExamIndividualWorkingTimeChangeDuringConduction() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExamViaExerciseGroupOrCourseMember();
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam = examRepository.saveAndFlush(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1").getParticipation();
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.saveAndFlush(studentExam);

        instanceMessageReceiveService.processStudentExamIndividualWorkingTimeChangeDuringConduction(studentExam.getId());

        verify(versionControlService, timeout(TIMEOUT_MS)).setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUri(), examExercise.getProjectKey(),
                participation.getStudents());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRescheduleExamDuringConduction() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExamViaExerciseGroupOrCourseMember();
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam = examRepository.saveAndFlush(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1").getParticipation();
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.saveAndFlush(studentExam);

        instanceMessageReceiveService.processRescheduleExamDuringConduction(exam.getId());

        verify(versionControlService, timeout(TIMEOUT_MS)).setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUri(), examExercise.getProjectKey(),
                participation.getStudents());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepositoryAndParticipation(examExercise, participation);
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
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
    }

    private ProgrammingExerciseStudentParticipation setupParticipationIndividualDueDate(final ZonedDateTime reference, Long individualDueDateDelayMillis, String login) {
        var participationIndividualDueDate = getParticipation(login);

        if (individualDueDateDelayMillis != null) {
            participationIndividualDueDate.setIndividualDueDate(plusMillis(reference, individualDueDateDelayMillis));
        }
        else {
            participationIndividualDueDate.setIndividualDueDate(null);
        }

        return participationRepository.saveAndFlush((ProgrammingExerciseStudentParticipation) participationIndividualDueDate);
    }

    private StudentParticipation getParticipation(String login) {
        return programmingExercise.getStudentParticipations().stream().filter(participation -> login.equals(participation.getStudent().orElseThrow().getLogin())).findFirst()
                .orElseThrow();
    }

    private List<StudentParticipation> getParticipationsWithoutIndividualDueDate() {
        return programmingExercise.getStudentParticipations().stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
    }

    private ZonedDateTime plusMillis(final ZonedDateTime reference, long millis) {
        return reference.plus(millis, ChronoUnit.MILLIS);
    }

    private ZonedDateTime nowPlusMillis(long millis) {
        return ZonedDateTime.now().plus(millis, ChronoUnit.MILLIS);
    }
}
