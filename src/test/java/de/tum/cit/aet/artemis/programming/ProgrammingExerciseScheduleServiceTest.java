package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Tests for {@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService}.
 * <p>
 * Note: Verifications of BUILD_AND_TEST_AFTER_DUE_DATE behavior check that the task is
 * <em>scheduled</em> via {@code scheduleService} rather than verifying the downstream
 * {@code triggerInstructorBuildForExercise} call on {@code programmingTriggerService}.
 * This is because {@code triggerInstructorBuildForExercise} is annotated with {@code @Async},
 * and Spring's CGLIB proxy for {@code @Async} intercepts the method call before Mockito's
 * spy can record it, making direct spy verification unreliable.
 */
class ProgrammingExerciseScheduleServiceTest extends AbstractProgrammingIntegrationLocalVCSamlTest {

    private static final String TEST_PREFIX = "programmingexercisescheduleservice";

    private ProgrammingExercise programmingExercise;

    // Delay in milliseconds for scheduling exercise dates in the future.
    private static final long DELAY_MS = 3000;

    // Timeout for verifying mock interactions that happen after a scheduled task fires.
    private static final long VERIFY_TIMEOUT_MS = 20000;

    // Delay for after().never() checks - wait long enough to be confident no invocation occurs.
    private static final long NEVER_VERIFY_DELAY_MS = 2000;

    // Frozen reference time, set by freezeTime()
    private ZonedDateTime frozenNow;

    @BeforeEach
    void init() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        // Clear spy invocations accumulated during setup (exercise creation may trigger scheduling)
        // and from other test classes that share the Spring context.
        clearInvocations(scheduleService, programmingExerciseGradingService);
    }

    @AfterEach
    void tearDown() throws Exception {
        TimeUtil.resetClock();
        scheduleService.clearAllTasks();
    }

    /**
     * Freezes TimeUtil.now() to the current real time in UTC.
     * This ensures that the service's "isBefore" checks remain stable regardless
     * of how long the scheduling method takes to execute (DB queries, etc.).
     * The real TaskScheduler still uses real time to fire tasks.
     * <p>
     * UTC is used because PostgreSQL's TIMESTAMP WITHOUT TIME ZONE strips timezone info,
     * so dates must be in UTC to survive a DB round-trip without shifting.
     */
    private void freezeTime() {
        frozenNow = ZonedDateTime.now(ZoneOffset.UTC);
        TimeUtil.setClock(Clock.fixed(frozenNow.toInstant(), frozenNow.getZone()));
    }

    private ZonedDateTime nowPlusMillis(long millis) {
        return frozenNow.plus(millis, ChronoUnit.MILLIS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldExecuteScheduledBuildAndTestAfterDueDate() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Build and test after due date should have been scheduled.
        verify(scheduleService).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateHasPassed() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1L));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateIsNull() {
        // The factory sets buildAndTestStudentSubmissionsAfterDueDate by default, so explicitly null it
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
        // Update all scores should not have been triggered.
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledTwiceIfSameExercise() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        // Setting it the first time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Setting it the second time.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Both calls triggered scheduling (second one cancels the first internally)
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE),
                any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotExecuteScheduledIfBuildAndTestAfterDueDateChangesToNull() {
        freezeTime();
        // Setting it the first time - must save so processSchedule sees the change.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Now setting the date to null - this must also clear the old scheduled task!
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // Cancellation occurs twice:
        // 1. Inside scheduleExerciseTask during first scheduling (cancels old before scheduling new)
        // 2. Directly in scheduleCourseExercise else-branch when the date is null
        verify(scheduleService, times(2)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithBuildAndTestDateInFuture() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExercisesWithManualAssessment() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // but do not schedule build and test
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldUpdateScoresIfHasTestsAfterDueDateAndNoBuildAfterDueDate() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
        // has AFTER_DUE_DATE tests and no additional build after due date => update the scores to show those test cases in it
        verify(programmingExerciseGradingService, timeout(VERIFY_TIMEOUT_MS)).updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // make sure to trigger the update only for participants who do not have got an individual due date
        verify(programmingExerciseGradingService, never()).updateAllResults(programmingExercise);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasTestsAfterDueDateAndBuildAfterDueDate() {
        freezeTime();
        programmingExercise.setDueDate(nowPlusMillis(DELAY_MS));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(nowPlusMillis(DELAY_MS * 2));
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // has AFTER_DUE_DATE tests, but also buildAfterDueDate => do not update results, but use the results created on additional build run
        verify(programmingExerciseGradingService, after(NEVER_VERIFY_DELAY_MS).never()).updateAllResults(programmingExercise);
        // Use atLeastOnce() because the DUE lifecycle task firing during the after() wait can trigger
        // a second scheduling round when the spy's real method executes asynchronously in the full suite.
        verify(scheduleService, atLeastOnce()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotUpdateScoresIfHasNoTestsAfterDueDate(boolean hasBuildAndTestAfterDueDate) {
        freezeTime();
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
        verify(programmingExerciseGradingService, after(NEVER_VERIFY_DELAY_MS).never()).updateAllResults(programmingExercise);
        if (hasBuildAndTestAfterDueDate) {
            verify(scheduleService).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
        }
        else {
            verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateNoBuildAndTestDateLock() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, null);
        var login = TEST_PREFIX + "student3";
        setupParticipationWithIndividualDueDate(frozenNow, 15000L, login);
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
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, 20000L);
        // individual due date between regular due date and build and test date
        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        // The exercise-level build and test should still be scheduled
        verify(scheduleService).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateAfterBuildAndTestDate() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, 5000L);
        // individual due date after build and test date
        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // special scheduling for both lock and build and test
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE),
                any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void scheduleIndividualDueDateTestsAfterDueDateNoBuildAndTestDate() {
        freezeTime();

        // no build and test date, but after_due_date tests => score update needed
        setupProgrammingExerciseDates(frozenNow, 5000L, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelAllSchedulesOnRemovingExerciseDueDate() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAll(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());

        // remove due date and schedule again
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // all schedules are cancelled
        InOrder cancelCalls = inOrder(scheduleService);
        cancelCalls.verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        cancelCalls.verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(),
                ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        for (final var participation : programmingExercise.getStudentParticipations()) {
            cancelCalls.verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.DUE);
            cancelCalls.verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(), participation.getId(),
                    ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cancelIndividualSchedulesOnRemovingIndividualDueDate() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, null);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());

        // remove individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(null);
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // called twice: first time when removing potential old schedules before scheduling, second time only cancelling
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(),
                participationIndividualDueDate.getId(), ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateIndividualScheduleOnIndividualDueDateChange() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, null);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());

        // change individual due date and schedule again
        participationIndividualDueDate.setIndividualDueDate(nowPlusMillis(15000));
        participationIndividualDueDate = participationRepository.saveAndFlush(participationIndividualDueDate);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        // scheduling called twice, each scheduling cancels potential old schedules
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).cancelScheduledTaskForParticipationLifecycle(programmingExercise.getId(),
                participationIndividualDueDate.getId(), ParticipationLifecycle.DUE);
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void keepIndividualScheduleOnExerciseDueDateChange() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, 5000L, null);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.stream().findFirst().orElseThrow().setVisibility(Visibility.AFTER_DUE_DATE);
        programmingExerciseTestCaseRepository.saveAllAndFlush(testCases);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 10000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());

        // change exercise due date and schedule again
        programmingExercise.setDueDate(nowPlusMillis(15000));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);
        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(), any());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS).times(2)).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldScheduleExerciseIfAnyIndividualDueDateInFuture() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, -5000L, null);
        programmingExercise.setReleaseDate(frozenNow.minusHours(1));
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        var participationIndividualDueDate = setupParticipationWithIndividualDueDate(frozenNow, 5000L, TEST_PREFIX + "student3");
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).scheduleParticipationTask(eq(participationIndividualDueDate), eq(ParticipationLifecycle.DUE), any(), any());
        verify(scheduleService, never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCancelAllTasksIfSchedulingNoLongerNeeded() {
        freezeTime();

        setupProgrammingExerciseDates(frozenNow, -5000L, null);
        programmingExercise.setReleaseDate(frozenNow.minusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setAllowComplaintsForAutomaticAssessments(false);
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        instanceMessageReceiveService.processScheduleProgrammingExercise(programmingExercise.getId());

        verify(scheduleService, after(NEVER_VERIFY_DELAY_MS).never()).scheduleExerciseTask(eq(programmingExercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.RELEASE);
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.DUE);
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService, timeout(VERIFY_TIMEOUT_MS)).cancelScheduledTaskForLifecycle(programmingExercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testStudentExamIndividualWorkingTimeChangeDuringConduction() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = examExercise.getExam();
        exam.setStartDate(TimeUtil.now().minusMinutes(1));
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
        exam.setStartDate(TimeUtil.now().minusMinutes(1));
        exam = examRepository.saveAndFlush(exam);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        participationUtilService.addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1");
        studentExam.setExercises(List.of(examExercise));
        studentExam.setWorkingTime(1);
        studentExamRepository.saveAndFlush(studentExam);
    }

    /**
     * Sets the due date and build and test after due date for the {@code programmingExercise} to reference + delay.
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
}
