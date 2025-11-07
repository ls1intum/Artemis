package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

class ProgrammingExerciseScheduleServiceTest extends AbstractProgrammingIntegrationLocalVCSamlTest {

    private static final String TEST_PREFIX = "programmingexercisescheduleservice";

    private ProgrammingExercise programmingExercise;

    private final LocalRepository studentRepository = new LocalRepository(defaultBranch);

    // When the scheduler is invoked, there is a small delay until the runnable is called.
    // TODO: This could be improved by e.g. manually setting the system time instead of waiting for actual time to pass.
    private static final long SCHEDULER_TASK_TRIGGER_DELAY_MS = 1200;

    private static final long DELAY_MS = 600;

    private static final long TIMEOUT_MS = 10000;

    @BeforeEach
    void init() throws Exception {
        studentRepository.configureRepos(localVCBasePath, "studentLocalRepo", "studentOriginRepo");
        doReturn(ObjectId.fromString("fffb09455885349da6e19d3ad7fd9c3404c5a0df")).when(gitService).getLastCommitHash(any());

        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduleService.clearAllTasks();
        studentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldExecuteScheduledBuildAndTestAfterDueDate() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

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

        verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() {
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // Update all scores should not have been triggered.
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledTwiceIfSameExercise() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

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
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithManualAssessment() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // but do not build all
        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldUpdateScoresIfHasTestsAfterDueDateAndNoBuildAfterDueDate() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(programmingTriggerService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        // has AFTER_DUE_DATE tests and no additional build after due date => update the scores to show those test cases in it
        verify(programmingExerciseGradingService, timeout(5000)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() {
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // has AFTER_DUE_DATE tests, but also buildAfterDueDate => do not update results, but use the results created on additional build run
        verify(programmingExerciseGradingService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).updateAllResults(programmingExercise);
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasNoTestsAfterDueDate(boolean hasBuildAndTestAfterDueDate) {
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
        if (hasBuildAndTestAfterDueDate) {
            verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
        else {
            verify(programmingTriggerService, never()).triggerInstructorBuildForExercise(programmingExercise.getId());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateNoBuildAndTestDateLock() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var login = TEST_PREFIX + "student3";
        setupParticipationWithIndividualDueDate(now, DELAY_MS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS, login);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        var studentParticipationsRegularDueDate = getParticipationsWithoutIndividualDueDate();
        assertThat(studentParticipationsRegularDueDate).hasSize(2).allMatch(participation -> !participation.getStudent().orElseThrow().getLogin().equals(login));

        var studentParticipationIndividualDueDate = getParticipation(login);
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotNull();
        assertThat(studentParticipationIndividualDueDate.getIndividualDueDate()).isNotEqualTo(programmingExercise.getDueDate());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateBetweenDueDateAndBuildAndTestDate() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, 2 * SCHEDULER_TASK_TRIGGER_DELAY_MS);
        // individual due date between regular due date and build and test date
        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, DELAY_MS * 2 + SCHEDULER_TASK_TRIGGER_DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(DELAY_MS * 2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        // not yet locked on regular due date
        verify(programmingTriggerService, after(DELAY_MS * 2).never()).triggerInstructorBuildForExercise(programmingExercise.getId());

        // after build and test date: no individual build and test actions are scheduled
        verify(programmingTriggerService, after(DELAY_MS + SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).triggerBuildForParticipations(List.of(participationIndividualDueDate));
        verify(programmingTriggerService, timeout(TIMEOUT_MS)).triggerInstructorBuildForExercise(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateAfterBuildAndTestDate() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, DELAY_MS);
        // individual due date after build and test date
        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // special scheduling for both lock and build and test
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(),
                any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateTestsAfterDueDateNoBuildAndTestDate() {
        final ZonedDateTime now = ZonedDateTime.now();

        // no build and test date, but after_due_date tests ⇒ score update needed
        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelAllSchedulesOnRemovingExerciseDueDate() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, DELAY_MS * 2, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());

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
    void cancelIndividualSchedulesOnRemovingIndividualDueDate() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, 2 * DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());

        // remove individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(null);
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // called twice: first time when removing potential old schedules before scheduling, second time only cancelling
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateIndividualScheduleOnIndividualDueDateChange() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, DELAY_MS, null);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, 2 * DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());

        // change individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(nowPlusMillis(DELAY_MS));
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // scheduling called twice, each scheduling cancels potential old schedules
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participationIndividualDueDate.getId(),
                ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void keepIndividualScheduleOnExerciseDueDateChange() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, 1000L, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, 2000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());

        // change exercise due date and schedule again
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(TIMEOUT_MS).times(2)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExerciseIfAnyIndividualDueDateInFuture() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -DELAY_MS, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(now, DELAY_MS, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCancelAllTasksIfSchedulingNoLongerNeeded() {
        final ZonedDateTime now = ZonedDateTime.now();

        setupProgrammingExerciseDates(now, -DELAY_MS, null);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, after(SCHEDULER_TASK_TRIGGER_DELAY_MS).never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class),
                any());
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.RELEASE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService, timeout(TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testStudentExamIndividualWorkingTimeChangeDuringConduction() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExam();
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam = examRepository.saveAndFlush(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        participationUtilService.addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1");
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.saveAndFlush(studentExam);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRescheduleExamDuringConduction() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExam();
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam = examRepository.saveAndFlush(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        participationUtilService.addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1");
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.saveAndFlush(studentExam);
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

    private ProgrammingExerciseStudentParticipation setupParticipationWithIndividualDueDate(final ZonedDateTime reference, Long individualDueDateDelayMillis, String login) {
        var participation = getParticipation(login);

        if (individualDueDateDelayMillis != null) {
            participation.setIndividualDueDate(plusMillis(reference, individualDueDateDelayMillis));
        }
        else {
            participation.setIndividualDueDate(null);
        }

        return participationRepository.saveAndFlush((ProgrammingExerciseStudentParticipation) participation);
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
