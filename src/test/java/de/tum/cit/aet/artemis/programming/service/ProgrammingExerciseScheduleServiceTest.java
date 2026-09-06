package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for deciding which scheduled tasks a programming exercise needs.
 * <p>
 * Scheduling decides when repositories are locked, when submissions are rebuilt against the final tests and when scores
 * are recomputed, and it is re-evaluated every time an instructor edits an exercise. The failure that matters is the
 * quiet one: an exercise that stops being scheduled keeps its old tasks and fires them at dates that no longer exist,
 * and one that is not scheduled when it should be simply never locks or rebuilds anything. Both look like nothing
 * happening, so each branch is pinned to the tasks it is supposed to schedule or cancel.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseScheduleServiceTest {

    private static final long EXERCISE_ID = 7L;

    @Mock
    private de.tum.cit.aet.artemis.core.service.ScheduleService scheduleService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseParticipationRepository;

    @Mock
    private ProgrammingTriggerService programmingTriggerService;

    @Mock
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private ProfileService profileService;

    private ProgrammingExerciseScheduleService scheduleServiceUnderTest;

    @BeforeEach
    void setUp() {
        scheduleServiceUnderTest = new ProgrammingExerciseScheduleService(scheduleService, programmingExerciseRepository, programmingExerciseTestCaseRepository, resultRepository,
                participationRepository, programmingExerciseParticipationRepository, programmingTriggerService, programmingExerciseGradingService, scheduler, profileService);
        lenient().when(participationRepository.findLatestIndividualDueDate(EXERCISE_ID)).thenReturn(Optional.empty());
        lenient().when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of());
    }

    /**
     * An exercise that needs no scheduling at all: automatically assessed, no complaints, and every date in the past.
     */
    private ProgrammingExercise exerciseThatNeedsNoScheduling() {
        var exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTitle("Sorting Algorithms");
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exercise.setAllowComplaintsForAutomaticAssessments(false);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(10));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        return exercise;
    }

    private void assertEverySchedulingWasCancelled() {
        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.DUE);
        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.ASSESSMENT_DUE);
    }

    @Test
    void anExerciseThatIsOverAndNeedsNothingHasItsSchedulingRemoved() {
        // A task left behind fires at a date the exercise no longer has, which locks or rebuilds repositories unexpectedly.
        scheduleServiceUnderTest.updateScheduling(exerciseThatNeedsNoScheduling());

        assertEverySchedulingWasCancelled();
        verify(scheduleService, never()).scheduleExerciseTask(any(), any(), any(Runnable.class), any());
    }

    @Test
    void anExamExerciseIsAlwaysScheduled() {
        // An exam exercise is driven entirely by the exam's dates, which this service cannot rule out from the exercise alone.
        var exercise = exerciseThatNeedsNoScheduling();
        var exerciseGroup = new ExerciseGroup();
        var exam = new Exam();
        exam.setId(3L);
        exam.setStartDate(ZonedDateTime.now().plusDays(1));
        exam.setEndDate(ZonedDateTime.now().plusDays(2));
        exerciseGroup.setExam(exam);
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
    }

    @Test
    void aManuallyAssessedExerciseIsScheduledEvenWhenEveryDateHasPassed() {
        // The assessment due date still has to fire, so the exercise keeps its scheduling after the due date.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
    }

    @Test
    void anExerciseThatAllowsComplaintsStaysScheduled() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAllowComplaintsForAutomaticAssessments(true);

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
    }

    @Test
    void anExerciseThatHasNotBeenReleasedYetStaysScheduled() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
    }

    @Test
    void anExerciseWithAParticipationWhoseIndividualDueDateHasNotPassedStaysScheduled() {
        // A single student with an extension keeps the whole exercise scheduled, otherwise their due date never fires.
        var exercise = exerciseThatNeedsNoScheduling();
        when(participationRepository.findLatestIndividualDueDate(EXERCISE_ID)).thenReturn(Optional.of(ZonedDateTime.now().plusDays(1)));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.RELEASE);
    }

    @Test
    void anExerciseWithTestsThatOnlyCountAfterTheDueDateGetsAScoreUpdateScheduled() {
        // Those test cases become visible only once the due date passes, and nothing recomputes the scores unless this runs.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseTestCaseRepository.countAfterDueDateByExerciseId(EXERCISE_ID)).thenReturn(2L);

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).scheduleExerciseTask(eq(exercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
    }

    @Test
    void anExerciseWithoutTestsThatCountAfterTheDueDateNeedsNoScoreUpdate() {
        // Every score is already final at the due date, so scheduling a recomputation would only cost a run over every participation.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseTestCaseRepository.countAfterDueDateByExerciseId(EXERCISE_ID)).thenReturn(0L);

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).scheduleExerciseTask(eq(exercise), eq(ExerciseLifecycle.DUE), any(Runnable.class), any());
    }

    @Test
    void anExerciseWhoseDueDateHasPassedHasItsScoreUpdateCancelled() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.DUE);
    }

    @Test
    void anExerciseThatRebuildsAfterTheDueDateGetsThatRunScheduled() {
        // This is the run that grades every submission against the final tests; without it the students keep their old scores.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).scheduleExerciseTask(eq(exercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    void anExerciseWhoseRebuildDateHasPassedHasThatRunCancelled() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
    }

    @Test
    void aParticipationWithoutAnIndividualDueDateHasItsOwnTasksRemoved() {
        // The extension was withdrawn, so the tasks that were scheduled for it must not stay behind.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelAllScheduledParticipationTasks(EXERCISE_ID, 10L);
    }

    /**
     * A participation whose owner was given an extension beyond the exercise's due date.
     */
    private ProgrammingExerciseStudentParticipation participationWithIndividualDueDate(ZonedDateTime individualDueDate) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(10L);
        participation.setIndividualDueDate(individualDueDate);
        return participation;
    }

    @Test
    void aParticipationWithAnExtensionStillToRunGetsItsOwnDueDateTask() {
        // The exercise's own due date has already fired for everyone else, so this participation needs a task of its own.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().plusDays(3));
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).scheduleParticipationTask(eq(participation), eq(de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.DUE), any(Runnable.class), any());
    }

    @Test
    void aParticipationWhoseExtensionHasRunOutHasItsOwnDueDateTaskRemoved() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().minusDays(1));
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelScheduledTaskForParticipationLifecycle(EXERCISE_ID, 10L, de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.DUE);
    }

    @Test
    void aParticipationWhoseExtensionOutlastsTheRebuildDateIsRebuiltSeparately() {
        // The exercise-wide rebuild would grade this submission before the student is finished with it.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(2));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().plusDays(3));
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).scheduleParticipationTask(eq(participation), eq(de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE),
                any(Runnable.class), any());
    }

    @Test
    void aParticipationWhoseExtensionEndsBeforeTheRebuildNeedsNoSeparateRebuild() {
        // The exercise-wide rebuild already covers it, and a second one would build the same submission twice.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(5));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().plusDays(3));
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelScheduledTaskForParticipationLifecycle(EXERCISE_ID, 10L,
                de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
    }

    /**
     * Runs the task that was scheduled for the given exercise lifecycle, which is what the scheduler would do when the
     * date arrives. The tasks are lambdas, so what they actually do is only observable by running them.
     */
    private void runScheduledExerciseTask(ProgrammingExercise exercise, ExerciseLifecycle lifecycle) {
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduleService).scheduleExerciseTask(eq(exercise), eq(lifecycle), task.capture(), any());
        task.getValue().run();
    }

    private void runScheduledParticipationTask(ProgrammingExerciseStudentParticipation participation, de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle lifecycle) {
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduleService).scheduleParticipationTask(eq(participation), eq(lifecycle), task.capture(), any());
        task.getValue().run();
    }

    @Test
    void theRebuildAfterTheDueDateTriggersAnInstructorBuildOfTheWholeExercise() {
        // This is what grades every student against the final tests; it is the task itself that has to do it, not the schedule.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        scheduleServiceUnderTest.updateScheduling(exercise);

        runScheduledExerciseTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);

        verify(programmingTriggerService).triggerInstructorBuildForExercise(EXERCISE_ID);
    }

    @Test
    void theRebuildOfAnExerciseThatWasDeletedMeanwhileDoesNotFail() {
        // The task was scheduled minutes or days earlier, so the exercise can be gone by the time it runs.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        scheduleServiceUnderTest.updateScheduling(exercise);
        org.mockito.Mockito.doThrow(new de.tum.cit.aet.artemis.core.exception.EntityNotFoundException("ProgrammingExercise", EXERCISE_ID)).when(programmingTriggerService)
                .triggerInstructorBuildForExercise(EXERCISE_ID);

        org.assertj.core.api.Assertions.assertThatCode(() -> runScheduledExerciseTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE)).doesNotThrowAnyException();
    }

    @Test
    void theRebuildOfAParticipationWithAnExtensionBuildsThatParticipationOnly() {
        // Only this student's due date has arrived, so only their submission may be graded against the final tests.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(2));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().plusDays(3));
        participation.setProgrammingExercise(exercise);
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));
        scheduleServiceUnderTest.updateScheduling(exercise);

        runScheduledParticipationTask(participation, de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);

        verify(programmingTriggerService).triggerBuildForParticipations(List.of(participation));
    }

    @Test
    void theRebuildOfAParticipationThatWasDeletedMeanwhileDoesNotFail() {
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(2));
        var participation = participationWithIndividualDueDate(ZonedDateTime.now().plusDays(3));
        participation.setProgrammingExercise(exercise);
        when(programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(EXERCISE_ID)).thenReturn(List.of(participation));
        scheduleServiceUnderTest.updateScheduling(exercise);
        org.mockito.Mockito.doThrow(new de.tum.cit.aet.artemis.core.exception.EntityNotFoundException("Participation", 10L)).when(programmingTriggerService)
                .triggerBuildForParticipations(any());

        org.assertj.core.api.Assertions
                .assertThatCode(() -> runScheduledParticipationTask(participation, de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void anExamExerciseThatRebuildsAfterTheExamGetsThatRunScheduled() {
        var exercise = exerciseThatNeedsNoScheduling();
        var exerciseGroup = new ExerciseGroup();
        var exam = new Exam();
        exam.setId(3L);
        exam.setVisibleDate(ZonedDateTime.now().minusDays(1));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exerciseGroup.setExam(exam);
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).scheduleExerciseTask(eq(exercise), eq(ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE), any(Runnable.class), any());
    }

    @Test
    void anExamExerciseWithoutTheDatesItNeedsIsNotScheduled() {
        // An exam whose dates are not set yet cannot be scheduled against, and guessing them would fire tasks at the wrong time.
        var exercise = exerciseThatNeedsNoScheduling();
        var exerciseGroup = new ExerciseGroup();
        var exam = new Exam();
        exam.setId(3L);
        exerciseGroup.setExam(exam);
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService, never()).scheduleExerciseTask(any(), any(), any(Runnable.class), any());
    }

    @Test
    void anExamExerciseWhoseRebuildDateHasPassedHasThatRunCancelled() {
        var exercise = exerciseThatNeedsNoScheduling();
        var exerciseGroup = new ExerciseGroup();
        var exam = new Exam();
        exam.setId(3L);
        exam.setVisibleDate(ZonedDateTime.now().minusDays(2));
        exam.setStartDate(ZonedDateTime.now().minusDays(1));
        exerciseGroup.setExam(exam);
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));

        scheduleServiceUnderTest.updateScheduling(exercise);

        verify(scheduleService).cancelScheduledTaskForLifecycle(EXERCISE_ID, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
    }

    @Test
    void theScoreUpdateOfTheRegularDueDateRecomputesAndStoresEveryResult() {
        // This is what makes the results of test cases that only count after the due date appear, without rebuilding anything.
        var exercise = exerciseThatNeedsNoScheduling();
        var updated = List.of(new de.tum.cit.aet.artemis.assessment.domain.Result());
        when(programmingExerciseGradingService.updateResultsOnlyRegularDueDateParticipations(exercise)).thenReturn(updated);

        scheduleServiceUnderTest.updateStudentScoresRegularDueDate(exercise).run();

        verify(resultRepository).saveAll(updated);
    }

    @Test
    void cancellingTheSchedulingOfAnExerciseRemovesEveryLifecycleTask() {
        scheduleServiceUnderTest.cancelAllScheduledTasks(EXERCISE_ID);

        assertEverySchedulingWasCancelled();
    }

    @Test
    void schedulingOnStartupDoesNothingOnADevelopmentServer() {
        // A developer's machine would otherwise install the schedules of every running exercise of its database copy.
        when(profileService.isDevActive()).thenReturn(true);

        scheduleServiceUnderTest.scheduleRunningExercisesOnStartup();

        verify(programmingExerciseRepository, never()).findAllExerciseIdsToBeScheduledByExerciseDates(any());
    }

    @Test
    void schedulingOnStartupPicksUpTheExercisesThatStillHaveADateToCome() {
        // After a restart nothing is scheduled any more, so they have to be found and scheduled again.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(profileService.isDevActive()).thenReturn(false);
        when(programmingExerciseRepository.findAllExerciseIdsToBeScheduledByExerciseDates(any())).thenReturn(java.util.Set.of(EXERCISE_ID));
        when(programmingExerciseRepository.findAllExerciseIdsWithIndividualDueDatesAfter(any())).thenReturn(java.util.Set.of());
        when(programmingExerciseRepository.findAllByIdIn(any())).thenReturn(List.of(exercise));
        when(programmingExerciseRepository.findAllByDueDateAfterDateWithTestsAfterDueDateWithoutBuildStudentSubmissionsDate(any())).thenReturn(List.of());
        when(programmingExerciseRepository.findAllWithEagerExamByExamEndDateAfterDate(any())).thenReturn(List.of());
        when(programmingExerciseTestCaseRepository.countAfterDueDateByExerciseId(EXERCISE_ID)).thenReturn(1L);

        scheduleServiceUnderTest.scheduleRunningExercisesOnStartup();

        verify(scheduleService, atLeastOnce()).scheduleExerciseTask(eq(exercise), any(), any(Runnable.class), any());
    }

    @Test
    void schedulingOnStartupSurvivesADatabaseThatCannotBeRead() {
        // A failure here would otherwise leave the whole server without any scheduling and without a clear reason.
        when(profileService.isDevActive()).thenReturn(false);
        when(programmingExerciseRepository.findAllExerciseIdsToBeScheduledByExerciseDates(any())).thenThrow(new IllegalStateException("the database is gone"));

        org.assertj.core.api.Assertions.assertThatCode(() -> scheduleServiceUnderTest.scheduleRunningExercisesOnStartup()).doesNotThrowAnyException();
    }

    @Test
    void schedulingAnExerciseThatFailsDoesNotTakeTheOtherExercisesDown() {
        // Startup scheduling walks every running exercise; one broken exercise must not leave the rest unscheduled.
        var exercise = exerciseThatNeedsNoScheduling();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(programmingExerciseTestCaseRepository.countAfterDueDateByExerciseId(EXERCISE_ID)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new IllegalStateException("the scheduler is gone")).when(scheduleService).scheduleExerciseTask(any(), any(), any(Runnable.class), any());

        org.assertj.core.api.Assertions.assertThatCode(() -> scheduleServiceUnderTest.updateScheduling(exercise)).doesNotThrowAnyException();
    }

    @Test
    void cancellingTheSchedulingOfAnExerciseByIdDoesNotNeedTheExerciseItself() {
        // The exercise is cancelled when it is deleted, at which point it can no longer be loaded.
        scheduleServiceUnderTest.cancelAllScheduledTasks(EXERCISE_ID);

        verify(scheduleService).cancelScheduledTaskForLifecycle(anyLong(), eq(ExerciseLifecycle.RELEASE));
    }
}
