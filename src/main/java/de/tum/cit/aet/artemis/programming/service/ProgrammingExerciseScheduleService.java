package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.StartupDelayConfig.PROGRAMMING_EXERCISE_SCHEDULE_DELAY_SEC;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.IExerciseScheduleService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseScheduleService.class);

    private final ScheduleService scheduleService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ResultRepository resultRepository;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseParticipationRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final TaskScheduler scheduler;

    private final ProfileService profileService;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ResultRepository resultRepository, ParticipationRepository participationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseParticipationRepository, ProgrammingTriggerService programmingTriggerService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, @Qualifier("taskScheduler") TaskScheduler scheduler, ProfileService profileService) {
        this.scheduleService = scheduleService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseParticipationRepository = programmingExerciseParticipationRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.scheduler = scheduler;
        this.profileService = profileService;
    }

    /**
     * Schedules programming exercises on startup.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleRunningExercisesOnStartup, Instant.now().plusSeconds(PROGRAMMING_EXERCISE_SCHEDULE_DELAY_SEC));
    }

    @Override
    public void scheduleRunningExercisesOnStartup() {
        try {
            if (profileService.isDevActive()) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();

            List<ProgrammingExercise> exercisesToBeScheduled = programmingExerciseRepository.findAllToBeScheduled(ZonedDateTime.now());
            exercisesToBeScheduled.forEach(this::scheduleExercise);

            List<ProgrammingExercise> programmingExercisesWithTestsAfterDueDateButNoRebuild = programmingExerciseRepository
                    .findAllByDueDateAfterDateWithTestsAfterDueDateWithoutBuildStudentSubmissionsDate(ZonedDateTime.now());
            programmingExercisesWithTestsAfterDueDateButNoRebuild.forEach(this::scheduleExercise);

            List<ProgrammingExercise> programmingExercisesWithExam = programmingExerciseRepository.findAllWithEagerExamByExamEndDateAfterDate(ZonedDateTime.now());
            programmingExercisesWithExam.forEach(this::scheduleExamExercise);

            log.info("Scheduled {} programming exercises.", exercisesToBeScheduled.size());
            log.debug("Scheduled {} programming exercises for a score update after due date.", programmingExercisesWithTestsAfterDueDateButNoRebuild.size());
            log.info("Scheduled {} exam programming exercises.", programmingExercisesWithExam.size());
        }
        catch (Exception e) {
            log.error("Failed to start ProgrammingExerciseScheduleService", e);
        }
    }

    /**
     * Will cancel or reschedule tasks for updated programming exercises
     *
     * @param exercise ProgrammingExercise
     */
    @Override
    public void updateScheduling(ProgrammingExercise exercise) {
        if (needsToBeScheduled(exercise)) {
            scheduleExercise(exercise);
        }
        else {
            // If a programming exercise got changed so that any scheduling becomes unnecessary, we need to cancel all scheduled tasks
            cancelAllScheduledTasks(exercise);
        }
    }

    /**
     * Checks if scheduled tasks have to be started for the given exercise.
     *
     * @param exercise for which the check should be performed.
     * @return true, if the exercise needs to be scheduled.
     */
    private boolean needsToBeScheduled(ProgrammingExercise exercise) {
        // Exam exercises need to be scheduled
        if (exercise.isExamExercise()) {
            return true;
        }
        // Manual assessed programming exercises as well
        if (AssessmentType.AUTOMATIC != exercise.getAssessmentType()) {
            return true;
        }
        // Exercises where students can complain as well
        if (exercise.getAllowComplaintsForAutomaticAssessments()) {
            return true;
        }

        return needsToBeScheduledDueToDates(exercise);
    }

    /**
     * Checks if the exercise needs to be scheduled because any of its relevant
     * dates are in the future.
     *
     * @param exercise for which the check should be performed.
     * @return true, if the exercise needs to be scheduled.
     */
    private boolean needsToBeScheduledDueToDates(ProgrammingExercise exercise) {
        final ZonedDateTime now = ZonedDateTime.now();

        // Exercises with a release date in the future must be scheduled
        if (exercise.getReleaseDate() != null && now.isBefore(exercise.getReleaseDate())) {
            return true;
        }
        // If tests are run after due date and that due date lies in the future, we need to schedule that as well
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && now.isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            return true;
        }
        // Has a regular due date in the future
        if (exercise.getDueDate() != null && now.isBefore(exercise.getDueDate())) {
            return true;
        }

        // Has an individual due date in the future
        return participationRepository.findLatestIndividualDueDate(exercise.getId()).map(now::isBefore).orElse(false);
    }

    private void cancelAllScheduledTasks(ProgrammingExercise exercise) {
        cancelAllScheduledTasks(exercise.getId());
    }

    /**
     * Cancel all scheduled tasks for a programming exercise and its participations.
     * - Release
     * - Due
     * - Build & Test after due date
     * - Assessment due date
     *
     * @param exerciseId the id of the exercise for which the tasks should be cancelled
     */
    public void cancelAllScheduledTasks(Long exerciseId) {
        scheduleService.cancelScheduledTaskForLifecycle(exerciseId, ExerciseLifecycle.RELEASE);
        scheduleService.cancelScheduledTaskForLifecycle(exerciseId, ExerciseLifecycle.DUE);
        scheduleService.cancelScheduledTaskForLifecycle(exerciseId, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        scheduleService.cancelScheduledTaskForLifecycle(exerciseId, ExerciseLifecycle.ASSESSMENT_DUE);
    }

    private void scheduleExercise(ProgrammingExercise exercise) {
        try {
            if (exercise.isExamExercise()) {
                scheduleExamExercise(exercise);
            }
            else {
                scheduleCourseExercise(exercise);
            }
        }
        catch (Exception e) {
            log.error("Failed to schedule exercise {}", exercise.getId(), e);
        }
    }

    private void scheduleCourseExercise(ProgrammingExercise exercise) {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }

        final ZonedDateTime now = ZonedDateTime.now();

        // For any course exercise that needsToBeScheduled (buildAndTestAfterDueDate and/or manual assessment)
        if (exercise.getDueDate() != null && now.isBefore(exercise.getDueDate())) {
            scheduleScoreUpdate(exercise);
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.DUE);
        }

        // For exercises with buildAndTestAfterDueDate
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && now.isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            scheduleBuildAndTestAfterDueDate(exercise);
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }

        scheduleParticipationTasks(exercise, now);
    }

    private void scheduleScoreUpdate(ProgrammingExercise exercise) {
        final boolean updateScores = isScoreUpdateAfterDueDateNeeded(exercise);
        if (updateScores) {
            scheduleService.scheduleExerciseTask(exercise, ExerciseLifecycle.DUE, () -> updateStudentScoresRegularDueDate(exercise).run(), "update student scores");
        }

        log.debug("Scheduled lock student repositories after due date for Programming Exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getDueDate());
    }

    private void scheduleBuildAndTestAfterDueDate(ProgrammingExercise exercise) {
        scheduleService.scheduleExerciseTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise),
                "build and test student submissions");
        log.debug("Scheduled build and test for student submissions after due date for Programming Exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(),
                exercise.getBuildAndTestStudentSubmissionsAfterDueDate());
    }

    /**
     * Schedules all necessary tasks for participations with individual due dates.
     * <p>
     * Also removes schedules for individual participations of their individual due date no longer exists.
     *
     * @param exercise the participations belong to.
     * @param now      the current time.
     */
    private void scheduleParticipationTasks(final ProgrammingExercise exercise, final ZonedDateTime now) {
        final boolean isScoreUpdateNeeded = isScoreUpdateAfterDueDateNeeded(exercise);

        final var participations = programmingExerciseParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(exercise.getId());
        for (final var participation : participations) {
            if (exercise.getDueDate() == null || participation.getIndividualDueDate() == null) {
                scheduleService.cancelAllScheduledParticipationTasks(exercise.getId(), participation.getId());
            }
            else {
                scheduleParticipationWithIndividualDueDate(now, exercise, participation, isScoreUpdateNeeded);
            }
        }
    }

    private void scheduleParticipationWithIndividualDueDate(ZonedDateTime now, ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation,
            boolean isScoreUpdateNeeded) {
        final boolean isBeforeDueDate = now.isBefore(participation.getIndividualDueDate());
        // Update scores on due date
        if (isBeforeDueDate) {
            scheduleAfterDueDateForParticipation(participation, isScoreUpdateNeeded);
        }
        else {
            scheduleService.cancelScheduledTaskForParticipationLifecycle(exercise.getId(), participation.getId(), ParticipationLifecycle.DUE);
        }

        // Build and test after individual due date:
        // only special scheduling if the individual due date is after the build and test date
        if (isBeforeDueDate && exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                && participation.getIndividualDueDate().isAfter(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            scheduleBuildAndTestAfterDueDateForParticipation(participation);
        }
        else {
            scheduleService.cancelScheduledTaskForParticipationLifecycle(exercise.getId(), participation.getId(), ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    private void scheduleAfterDueDateForParticipation(ProgrammingExerciseStudentParticipation participation, boolean isScoreUpdateNeeded) {
        scheduleService.scheduleParticipationTask(participation, ParticipationLifecycle.DUE, () -> {
            if (isScoreUpdateNeeded) {

                scheduleService.scheduleParticipationTask(participation, ParticipationLifecycle.DUE, () -> {
                    final List<Result> updatedResult = programmingExerciseGradingService.updateParticipationResults(participation);
                    resultRepository.saveAll(updatedResult);
                }, "update student scores");
            }
        }, "lock student repository and participation");
        log.debug("Scheduled task to update student scores {} at the individual due date.", participation.getId());
    }

    private void scheduleBuildAndTestAfterDueDateForParticipation(ProgrammingExerciseStudentParticipation participation) {
        scheduleService.scheduleParticipationTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, () -> {
            final ProgrammingExercise exercise = participation.getProgrammingExercise();
            SecurityUtils.setAuthorizationObject();
            try {
                log.info("Invoking scheduled task for participation {} in programming exercise with id {}.", participation.getId(), exercise.getId());
                programmingTriggerService.triggerBuildForParticipations(List.of(participation));
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming participation with id {} in exercise {} is no longer available in database for use in scheduled task.", participation.getId(),
                        exercise.getId());
            }
        }, "build and test student submission");
    }

    private boolean isScoreUpdateAfterDueDateNeeded(ProgrammingExercise exercise) {
        // no rebuild date is set but test cases marked with AFTER_DUE_DATE exist: they have to become visible by recalculation of the scores
        return exercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null && programmingExerciseTestCaseRepository.countAfterDueDateByExerciseId(exercise.getId()) > 0;
    }

    private void scheduleExamExercise(ProgrammingExercise exercise) {
        Exam exam = exercise.getExerciseGroup().getExam();
        ZonedDateTime visibleDate = exam.getVisibleDate();
        ZonedDateTime startDate = exam.getStartDate();
        ZonedDateTime now = ZonedDateTime.now();
        if (visibleDate == null || startDate == null) {
            log.error("Programming exercise {} for exam {} cannot be scheduled properly, visible date is {}, start date is {}", exercise.getId(), exam.getId(), visibleDate,
                    startDate);
            return;
        }

        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && now.isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            scheduleService.scheduleExerciseTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, () -> buildAndTestRunnableForExercise(exercise).run(),
                    "build and test student submissions");
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
        log.debug("Scheduled Exam Programming Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());
    }

    @NonNull
    private Runnable buildAndTestRunnableForExercise(ProgrammingExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                log.info("Invoking scheduled task programming exercise with id {}.", exercise.getId());
                programmingTriggerService.triggerInstructorBuildForExercise(exercise.getId());
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id {} is no longer available in database for use in scheduled task.", exercise.getId());
            }
        };
    }

    /**
     * Returns a runnable, that, once executed, will update all results for the given exercise for students for which no
     * individual due date is set.
     * <p>
     * This might be needed for an exercise that has test cases marked with
     * {@link Visibility#AFTER_DUE_DATE}.
     * <p>
     * Those test cases might already have been run in the continuous integration
     * service and their feedbacks are therefore stored in the database.
     * However, they are not included in the student score before the due date has passed.
     * Updating the student score includes the feedbacks of those test cases into
     * the result without having to trigger a new continuous integration job.
     *
     * @param exercise for which the results should be updated.
     * @return a Runnable that will update all results for the given exercise.
     */
    @NonNull
    public Runnable updateStudentScoresRegularDueDate(final ProgrammingExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            final List<Result> updatedResults = programmingExerciseGradingService.updateResultsOnlyRegularDueDateParticipations(exercise);
            resultRepository.saveAll(updatedResults);
        };
    }
}
