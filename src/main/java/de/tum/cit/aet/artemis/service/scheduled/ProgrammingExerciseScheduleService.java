package de.tum.cit.aet.artemis.service.scheduled;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.config.StartupDelayConfig.PROGRAMMING_EXERCISE_SCHEDULE_DELAY_SEC;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.enumeration.AssessmentType;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.cit.aet.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.repository.ExamRepository;
import de.tum.cit.aet.artemis.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.repository.ResultRepository;
import de.tum.cit.aet.artemis.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.exam.ExamDateService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.service.util.Tuple;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile(PROFILE_SCHEDULING)
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseScheduleService.class);

    private final ScheduleService scheduleService;

    private final Environment env;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ResultRepository resultRepository;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseParticipationRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ExerciseDateService exerciseDateService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final GroupNotificationService groupNotificationService;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final ExamDateService examDateService;

    private final GitService gitService;

    private final TaskScheduler scheduler;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ResultRepository resultRepository, ParticipationRepository participationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseParticipationRepository, Environment env, ProgrammingTriggerService programmingTriggerService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, GroupNotificationService groupNotificationService, ExamDateService examDateService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ExerciseDateService exerciseDateService, ExamRepository examRepository,
            StudentExamRepository studentExamRepository, GitService gitService, @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduleService = scheduleService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseParticipationRepository = programmingExerciseParticipationRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseDateService = exerciseDateService;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.examDateService = examDateService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.env = env;
        this.gitService = gitService;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleRunningExercisesOnStartup, Instant.now().plusSeconds(PROGRAMMING_EXERCISE_SCHEDULE_DELAY_SEC));
    }

    @Override
    public void scheduleRunningExercisesOnStartup() {
        try {
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
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

        // For any course exercise with a valid release date
        if (exercise.getReleaseDate() != null && now.isBefore(exercise.getReleaseDate())) {
            scheduleTemplateCommitCombination(exercise);
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.RELEASE);
        }

        // For any course exercise that needsToBeScheduled (buildAndTestAfterDueDate and/or manual assessment)
        if (exercise.getDueDate() != null && now.isBefore(exercise.getDueDate())) {
            scheduleDueDateLockAndScoreUpdate(exercise);
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

    private void scheduleTemplateCommitCombination(ProgrammingExercise exercise) {
        if (exercise.getReleaseDate() != null) {
            var scheduledRunnable = Set.of(new Tuple<>(exercise.getReleaseDate().minusSeconds(Constants.SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS),
                    combineTemplateCommitsForExercise(exercise)));
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, scheduledRunnable);
            log.debug("Scheduled combining template commits before release date for Programming Exercise \"{}\" (#{}) for {}.", exercise.getTitle(), exercise.getId(),
                    exercise.getReleaseDate());
        }
    }

    private void scheduleDueDateLockAndScoreUpdate(ProgrammingExercise exercise) {
        final boolean updateScores = isScoreUpdateAfterDueDateNeeded(exercise);

        scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, () -> {
            lockStudentRepositoriesAndParticipationsRegularDueDate(exercise).run();
            if (updateScores) {
                updateStudentScoresRegularDueDate(exercise).run();
            }
        });

        log.debug("Scheduled lock student repositories after due date for Programming Exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getDueDate());
    }

    private void scheduleBuildAndTestAfterDueDate(ProgrammingExercise exercise) {
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
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

        final List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseParticipationRepository
                .findWithSubmissionsAndTeamStudentsByExerciseId(exercise.getId());
        for (final var participation : participations) {
            if (exercise.getDueDate() == null || participation.getIndividualDueDate() == null) {
                scheduleService.cancelAllScheduledParticipationTasks(exercise.getId(), participation.getId());
            }
            else {
                scheduleParticipationWithIndividualDueDate(now, exercise, participation, isScoreUpdateNeeded);
            }
        }
    }

    private void scheduleParticipationWithIndividualDueDate(final ZonedDateTime now, final ProgrammingExercise exercise,
            final ProgrammingExerciseStudentParticipation participation, boolean isScoreUpdateNeeded) {
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
            lockStudentRepositoryAndParticipation(participation).run();

            if (isScoreUpdateNeeded) {
                final List<Result> updatedResult = programmingExerciseGradingService.updateParticipationResults(participation);
                resultRepository.saveAll(updatedResult);
            }
        });
        log.debug("Scheduled task to lock repository for participation {} at the individual due date.", participation.getId());
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
        });
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

        // The common unlock date of the exam's programming exercises
        ZonedDateTime unlockDate = ExamDateService.getExamProgrammingExerciseUnlockDate(exercise);

        // BEFORE EXAM
        if (now.isBefore(unlockDate)) {
            // Schedule unlocking of student repositories
            // Uses the custom exam unlock date rather than the of the exercise's lifecycle
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, Set.of(new Tuple<>(unlockDate, unlockAllStudentRepositoriesAndParticipations(exercise))));
        }
        // DURING EXAM
        else if (now.isBefore(examDateService.getLatestIndividualExamEndDate(exam))) {
            // This is only a backup (e.g. a crash of this node and restart during the exam)
            // TODO: Christian Femers: this can lead to a weird edge case after the normal exam end date and before the last individual exam end date (in case of working time
            // extensions)
            var scheduledRunnable = Set.of(
                    new Tuple<>(now.plusSeconds(Constants.SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS), unlockAllStudentRepositoriesAndParticipations(exercise)));
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, scheduledRunnable);

            // Re-schedule the locking of student repositories based on the individual working time
            rescheduleProgrammingExerciseDuringExamConduction(exercise);
        }
        // NOTHING TO DO AFTER EXAM

        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && now.isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
        log.debug("Scheduled Exam Programming Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());
    }

    @NotNull
    private Runnable combineTemplateCommitsForExercise(ProgrammingExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                ProgrammingExercise programmingExerciseWithTemplateParticipation = programmingExerciseRepository
                        .findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                gitService.combineAllCommitsOfRepositoryIntoOne(programmingExerciseWithTemplateParticipation.getTemplateParticipation().getVcsRepositoryUri());
                log.debug("Combined template repository commits of programming exercise {}.", programmingExerciseWithTemplateParticipation.getId());
            }
            catch (GitAPIException e) {
                log.error("Failed to communicate with GitAPI for combining template commits of exercise {}", exercise.getId(), e);
            }
        };
    }

    @NotNull
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
     * Returns a runnable that, once executed, will
     * (1) lock all student repositories and
     * (2) lock all student participations and
     * (3) stash all student changes in the online editor for manual assessments.
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise for which the repositories should be locked.
     * @return a Runnable that will lock the repositories once it is executed.
     */
    @NotNull
    public Runnable lockAllStudentRepositoriesAndParticipations(ProgrammingExercise exercise) {
        return lockStudentRepositoriesAndParticipations(exercise, participation -> true);
    }

    /**
     * Returns a runnable that, once executed, will
     * (1) lock all student repositories and
     * (2) stash all student changes in the online editor for manual assessments.
     * <p>
     * NOTE: this will not lock the student participations. See {@link #lockAllStudentRepositoriesAndParticipations(ProgrammingExercise)} for that.
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise for which the repositories should be locked.
     * @return a Runnable that will lock the repositories once it is executed.
     */
    @NotNull
    public Runnable lockAllStudentRepositories(ProgrammingExercise exercise) {
        return lockStudentRepositories(exercise, participation -> true);
    }

    /**
     * Returns a runnable that, once executed, will
     * (1) lock all student repositories and participations that have a due date in the past.
     * (2) stash all student changes in the online editor for manual assessments.
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise for which the repositories should be locked.
     * @return a Runnable that will lock the repositories and participations once it is executed.
     */
    @NotNull
    public Runnable lockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(ProgrammingExercise exercise) {
        return lockStudentRepositoriesAndParticipations(exercise, exerciseDateService::isAfterDueDate);
    }

    /**
     * Returns a runnable that, once executed, will
     * (1) lock all student participations that have a due date in the past.
     * (2) stash all student changes in the online editor for manual assessments.
     * NOTE: this will not lock the student repositories. See {@link #lockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(ProgrammingExercise)} for that.
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise for which the repositories should be locked.
     * @return a Runnable that will lock the participations once it is executed.
     */
    @NotNull
    public Runnable lockAllStudentParticipationsWithEarlierDueDate(ProgrammingExercise exercise) {
        return lockStudentParticipations(exercise, exerciseDateService::isAfterDueDate);
    }

    /**
     * Returns a runnable that, once executed, will
     * (1) lock all student repositories for students for which no individual due date is set and
     * (2) stash all student changes in the online editor for manual assessments.
     * <p>
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise for which the repositories should be locked.
     * @return a Runnable that will lock the repositories once it is executed.
     */
    @NotNull
    public Runnable lockStudentRepositoriesAndParticipationsRegularDueDate(ProgrammingExercise exercise) {
        return lockStudentRepositoriesAndParticipations(exercise, participation -> participation.getIndividualDueDate() == null);
    }

    /**
     * Returns a runnable, that, once executed, will update all results for the given exercise for students for which no
     * individual due date is set.
     * <p>
     * This might be needed for an exercise that has test cases marked with
     * {@link de.tum.cit.aet.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE}.
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
    @NotNull
    public Runnable updateStudentScoresRegularDueDate(final ProgrammingExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            final List<Result> updatedResults = programmingExerciseGradingService.updateResultsOnlyRegularDueDateParticipations(exercise);
            resultRepository.saveAll(updatedResults);
        };
    }

    /**
     * Returns a runnable that, once executed, will (1) lock all student repositories, and (2) lock all student participations, and (3) stash all student changes in the online
     * editor for manual assessments
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise  The exercise for which the repositories should be locked
     * @param condition a condition that determines whether the operation will be executed for a specific participation
     * @return a Runnable that will lock the repositories once it is executed
     */
    @NotNull
    public Runnable lockStudentRepositoriesAndParticipations(ProgrammingExercise exercise, Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                var failedLockOperations = removeWritePermissionsFromAllStudentRepositoriesAndLockParticipations(programmingExerciseId, condition);
                failedLockOperations.thenAccept(failures -> stashStudentChangesAndNotifyInstructor(exercise, failures.size(), condition));
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id {} is no longer available in database for use in scheduled task.", programmingExerciseId);
            }
        };
    }

    /**
     * Returns a runnable that, once executed, will (1) lock all student repositories and (2) stash all student changes in the online editor for manual assessments
     * NOTE: this will not lock the student participations. See {@link #lockStudentRepositoriesAndParticipations(ProgrammingExercise, Predicate)} for that.
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise  The exercise for which the repositories should be locked
     * @param condition a condition that determines whether the operation will be executed for a specific participation
     * @return a Runnable that will lock the repositories once it is executed
     */
    @NotNull
    public Runnable lockStudentRepositories(ProgrammingExercise exercise, Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                var failedLockOperations = removeWritePermissionsFromAllStudentRepositories(programmingExerciseId, condition);
                failedLockOperations.thenAccept(failures -> stashStudentChangesAndNotifyInstructor(exercise, failures.size(), condition));
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id {} is no longer available in database for use in scheduled task.", programmingExerciseId);
            }
        };
    }

    /**
     * Returns a runnable that, once executed, will (1) lock all student participations and (2) stash all student changes in the online editor for manual assessments
     * NOTE: this will not lock the student repositories. See {@link #lockStudentRepositoriesAndParticipations(ProgrammingExercise, Predicate)} for that.
     * NOTE: this will not immediately lock the participations as only a Runnable is returned!
     *
     * @param exercise  The exercise for which the participations should be locked
     * @param condition a condition that determines whether the operation will be executed for a specific participation
     * @return a Runnable that will lock the participations once it is executed
     */
    @NotNull
    public Runnable lockStudentParticipations(ProgrammingExercise exercise, Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                var failedLockOperations = updateParticipationsLockedInDatabase(programmingExerciseId, condition);
                failedLockOperations.thenAccept(failures -> stashStudentChangesAndNotifyInstructor(exercise, failures.size(), condition));
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id {} is no longer available in database for use in scheduled task.", programmingExerciseId);
            }
        };
    }

    /**
     * Stash all student changes in the online editor for manual assessments and notify the instructor about the success of the repository locking and stashing operations.
     *
     * @throws EntityNotFoundException if the programming exercise with template and solution participation was not found
     */
    private void stashStudentChangesAndNotifyInstructor(ProgrammingExercise exercise, long numberOfFailedLockOperations,
            Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();

        String notificationText;
        if (numberOfFailedLockOperations > 0) {
            notificationText = Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION + numberOfFailedLockOperations;
        }
        else {
            notificationText = Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION;
        }
        groupNotificationService.notifyEditorAndInstructorGroupsAboutRepositoryLocks(exercise, notificationText);

        // Stash the not submitted/committed changes for exercises with manual assessment and with online editor enabled
        // This is necessary for students who have used the online editor, to ensure that only submitted/committed changes are displayed during manual assessment
        // in the case they still have saved changes on the Artemis server which have not been committed / pushed
        // NOTE: we always stash, also when manual assessment is not activated, because instructors might change this after the exam
        if (Boolean.TRUE.equals(exercise.isAllowOnlineEditor())) {
            var failedStashOperations = stashChangesInAllStudentRepositories(programmingExerciseId, condition);
            failedStashOperations.thenAccept(failures -> {
                final var stashNotificationText = getNotificationText(failures, Constants.PROGRAMMING_EXERCISE_FAILED_STASH_OPERATIONS_NOTIFICATION,
                        Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_STASH_OPERATION_NOTIFICATION);
                groupNotificationService.notifyEditorAndInstructorGroupsAboutRepositoryLocks(exercise, stashNotificationText);
            });
        }
    }

    /**
     * Creates a runnable that will lock the Git repository of the given participation as well as the participation itself when run.
     *
     * @param participation of which the Git repository will be locked.
     * @return a runnable that will lock the Git repository of the participation when run.
     */
    @NotNull
    private Runnable lockStudentRepositoryAndParticipation(ProgrammingExerciseStudentParticipation participation) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(participation.getProgrammingExercise(), participation);
            }
            catch (EntityNotFoundException ex) {
                log.error("Participation with id {} is no longer available in the database for a scheduled lock repository task.", participation.getId());
            }
        };
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories and participations that fulfill the condition and will schedule all repository lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise        The exercise for which the repositories should be unlocked
     * @param unlockOperation the operation that will be executed for every participation that fulfills the condition
     * @param condition       a condition that determines whether the operation will be executed for a specific participation
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable runUnlockOperation(ProgrammingExercise exercise, Consumer<ProgrammingExerciseStudentParticipation> unlockOperation,
            Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> individualDueDates = new HashSet<>();
                // This operation unlocks the repositories and collects all individual due dates
                BiConsumer<ProgrammingExercise, ProgrammingExerciseStudentParticipation> unlockAndCollectOperation = (programmingExercise, participation) -> {
                    var dueDate = exerciseDateService.getIndividualDueDate(programmingExercise, participation);
                    if (dueDate != null) {
                        individualDueDates.add(new Tuple<>(dueDate, participation));
                    }
                    unlockOperation.accept(participation);
                };
                var failedUnlockOperations = invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, unlockAndCollectOperation, condition,
                        "add write permissions to all student repositories");

                failedUnlockOperations.thenAccept(failures -> {
                    // We send a notification to the instructor about the success of the repository unlocking operation.
                    final var notificationText = getNotificationText(failures, Constants.PROGRAMMING_EXERCISE_FAILED_UNLOCK_OPERATIONS_NOTIFICATION,
                            Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_UNLOCK_OPERATION_NOTIFICATION);
                    groupNotificationService.notifyEditorAndInstructorGroupsAboutRepositoryLocks(exercise, notificationText);

                    // Schedule the lock operations here, this is also done here because the working times might change often before the exam start
                    // Note: this only makes sense before the due date of a course exercise or before the end date of an exam, because for individual dates in the past
                    // the scheduler would execute the lock operation immediately, making to unlock obsolete, therefore we filter out all individual due dates in the past
                    // one use case is to unlock all operation is invoked directly after exam start
                    Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> futureIndividualDueDates = individualDueDates.stream()
                            .filter(tuple -> tuple.x() != null && ZonedDateTime.now().isBefore(tuple.x())).collect(Collectors.toSet());
                    scheduleIndividualRepositoryAndParticipationLockTasks(exercise, futureIndividualDueDates);
                });
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id {} is no longer available in database for use in scheduled task.", programmingExerciseId);
            }
        };
    }

    private static String getNotificationText(List<ProgrammingExerciseStudentParticipation> failures, String programmingExerciseFailedUnlockOperationsNotification,
            String programmingExerciseSuccessfulUnlockOperationNotification) {
        long numberOfFailedUnlockOperations = failures.size();
        String notificationText;
        if (numberOfFailedUnlockOperations > 0) {
            notificationText = programmingExerciseFailedUnlockOperationsNotification + numberOfFailedUnlockOperations;
        }
        else {
            notificationText = programmingExerciseSuccessfulUnlockOperationNotification;
        }
        return notificationText;
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories and will schedule all repository lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories and participations should be unlocked
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentRepositoriesAndParticipations(ProgrammingExercise exercise) {
        return runUnlockOperation(exercise, programmingExerciseParticipationService::unlockStudentRepositoryAndParticipation, participation -> true);
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories and will schedule all repository lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be unlocked
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentRepositories(ProgrammingExercise exercise) {
        return runUnlockOperation(exercise, programmingExerciseParticipationService::unlockStudentRepository, participation -> true);
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories and participations for participations that are inside the working time frame and will schedule
     * all repository lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be unlocked
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(ProgrammingExercise exercise) {
        return runUnlockOperation(exercise, programmingExerciseParticipationService::unlockStudentRepositoryAndParticipation,
                participation -> participation.getProgrammingExercise().isReleased() && exerciseDateService.isBeforeDueDate(participation));
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories that are inside the working time frame and will schedule all repository lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be unlocked
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(ProgrammingExercise exercise) {
        return runUnlockOperation(exercise, programmingExerciseParticipationService::unlockStudentRepository,
                participation -> participation.getProgrammingExercise().isReleased() && exerciseDateService.isBeforeDueDate(participation));
    }

    /**
     * Returns a runnable that, once executed, will unlock all student participations that are inside the working time frame and will schedule all participation lock tasks.
     * Tasks to unlock will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     * NOTE: this will not immediately unlock the participations as only a Runnable is returned!
     *
     * @param exercise The exercise for which the participations should be unlocked
     * @return a Runnable that will unlock the participations once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(ProgrammingExercise exercise) {
        return runUnlockOperation(exercise, programmingExerciseParticipationService::unlockStudentParticipation,
                participation -> participation.getProgrammingExercise().isReleased() && exerciseDateService.isBeforeDueDate(participation));
    }

    /**
     * Schedules individual lock tasks for programming exercises (mostly in the context of exams)
     *
     * @param exercise                             the programming exercise for which the lock is executed
     * @param individualParticipationsWithDueDates the set of student participations with their individual due dates
     */
    private void scheduleIndividualRepositoryAndParticipationLockTasks(ProgrammingExercise exercise,
            Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> individualParticipationsWithDueDates) {
        // 1. Group all participations by due date
        // TODO: use student exams for safety if some participations are not pre-generated
        var participationsGroupedByDueDate = individualParticipationsWithDueDates.stream().filter(tuple -> tuple.x() != null)
                .collect(Collectors.groupingBy(Tuple::x, Collectors.mapping(Tuple::y, Collectors.toSet())));

        // 2. Transform those groups into lock-repository tasks with times
        Set<Tuple<ZonedDateTime, Runnable>> tasks = participationsGroupedByDueDate.entrySet().stream().map(entry -> {
            // Check that this participation is planned to be locked and has still the same due date
            Predicate<ProgrammingExerciseStudentParticipation> lockingCondition = participation -> entry.getValue().contains(participation)
                    && entry.getKey().equals(exerciseDateService.getIndividualDueDate(exercise, participation));

            var task = lockStudentRepositoriesAndParticipations(exercise, lockingCondition);
            return new Tuple<>(entry.getKey(), task);
        }).collect(Collectors.toSet());

        // 3. Schedule all tasks
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, tasks);
    }

    /**
     * Reschedules all programming exercise related tasks in the given exam.
     *
     * @param examId the id of the exam
     */
    public void rescheduleExamDuringConduction(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(examId).orElseThrow(NoSuchElementException::new);

        // get all programming exercises in the exam
        exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream()).filter(exercise -> exercise instanceof ProgrammingExercise)
                .map(exercise -> (ProgrammingExercise) exercise)
                // schedule repository locks for each programming exercise
                .forEach(this::rescheduleProgrammingExerciseDuringExamConduction);
    }

    private void rescheduleProgrammingExerciseDuringExamConduction(ProgrammingExercise programmingExercise) {
        // Note: the programming exercise might not include student participations, so we load it with student participations here
        var programmingExerciseWithStudentParticipations = programmingExerciseRepository.findWithEagerStudentParticipationsByIdElseThrow(programmingExercise.getId());
        // Collect the individual due date of each student participation
        var participationsWithDueDate = programmingExerciseWithStudentParticipations.getStudentParticipations().stream()
                .filter(ProgrammingExerciseStudentParticipation.class::isInstance).map(studentParticipation -> {
                    var dueDate = exerciseDateService.getIndividualDueDate(programmingExercise, studentParticipation);
                    return new Tuple<>(dueDate, (ProgrammingExerciseStudentParticipation) studentParticipation);
                }).collect(Collectors.toSet());

        // Re-schedule the lock operation at the individual end dates
        scheduleIndividualRepositoryAndParticipationLockTasks(programmingExercise, participationsWithDueDate);
    }

    /**
     * Reschedules all programming exercises related tasks in the given student exam.
     *
     * @param studentExamId the id of the student exam
     */
    public void rescheduleStudentExamDuringConduction(Long studentExamId) {
        StudentExam studentExam = studentExamRepository.findWithExercisesParticipationsSubmissionsById(studentExamId, false).orElseThrow(NoSuchElementException::new);

        // iterate over all programming exercises and its student participation in the student's exam
        studentExam.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).map(exercise -> (ProgrammingExercise) exercise).forEach(exercise -> {
            var participations = exercise.getStudentParticipations();
            // if the student does not participate in the programming exercise, skip it
            if (participations.isEmpty()) {
                return;
            }
            StudentParticipation participation = participations.iterator().next();
            // if it's not a programming exercise participation, skip it
            if (!(participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation)) {
                return;
            }
            // get the individual due date of the student's participation in the programming exercise
            ZonedDateTime dueDate = exerciseDateService.getIndividualDueDate(exercise, programmingParticipation);
            // schedule repository locks for each programming exercise
            scheduleIndividualRepositoryAndParticipationLockTasks(exercise, Set.of(new Tuple<>(dueDate, programmingParticipation)));
        });
    }

    private CompletableFuture<List<ProgrammingExerciseStudentParticipation>> removeWritePermissionsFromAllStudentRepositoriesAndLockParticipations(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, programmingExerciseParticipationService::lockStudentRepositoryAndParticipation, condition,
                "remove write permissions from all student repositories and lock participations");
    }

    private CompletableFuture<List<ProgrammingExerciseStudentParticipation>> removeWritePermissionsFromAllStudentRepositories(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, programmingExerciseParticipationService::lockStudentRepository, condition,
                "remove write permissions from all student repositories");
    }

    private CompletableFuture<List<ProgrammingExerciseStudentParticipation>> updateParticipationsLockedInDatabase(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId,
                (programmingExercise, participation) -> programmingExerciseParticipationService.lockStudentParticipation(participation), condition,
                "lock all student participations");
    }

    private CompletableFuture<List<ProgrammingExerciseStudentParticipation>> stashChangesInAllStudentRepositories(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, programmingExerciseParticipationService::stashChangesInStudentRepositoryAfterDueDateHasPassed,
                condition, "stash changes from all student repositories");
    }

    /**
     * Invokes the given <code>operation</code> on all student participations that satisfy the <code>condition</code>-{@link Predicate}.
     * <p>
     *
     * @param programmingExerciseId the programming exercise whose participations should be processed
     * @param operation             the operation to perform
     * @param condition             the condition that tests whether to invoke the operation on a participation
     * @param operationName         the name of the operation, this is only used for logging
     * @return a list containing all participations for which the operation has failed with an exception
     * @throws EntityNotFoundException if the programming exercise can't be found.
     */
    private CompletableFuture<List<ProgrammingExerciseStudentParticipation>> invokeOperationOnAllParticipationsThatSatisfy(Long programmingExerciseId,
            BiConsumer<ProgrammingExercise, ProgrammingExerciseStudentParticipation> operation, Predicate<ProgrammingExerciseStudentParticipation> condition,
            String operationName) {
        log.info("Invoking (scheduled) task '{}' for programming exercise with id {}.", operationName, programmingExerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsByIdElseThrow(programmingExerciseId);
        List<ProgrammingExerciseStudentParticipation> failedOperations = new ArrayList<>();

        // TODO: we should think about executing those operations again in batches to avoid issues on the vcs server

        // Create a threadpool to execute the operation with a fixed amount of threads
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        var participations = programmingExercise.getStudentParticipations();
        List<CompletableFuture<ProgrammingExerciseStudentParticipation>> futures = new ArrayList<>();
        for (StudentParticipation studentParticipation : participations) {
            Supplier<ProgrammingExerciseStudentParticipation> action = () -> {
                // We need to set the authorization object for every thread
                SecurityUtils.setAuthorizationObject();
                var programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;

                if (condition.test(programmingExerciseStudentParticipation)) {
                    operation.accept(programmingExercise, programmingExerciseStudentParticipation);
                }

                return programmingExerciseStudentParticipation;
            };

            CompletableFuture<ProgrammingExerciseStudentParticipation> future = CompletableFuture.supplyAsync(action, threadPool);
            futures.add(future);
        }

        for (var future : futures) {
            future.whenComplete((participation, exception) -> {
                if (exception != null) {
                    log.error(String.format("'%s' failed for programming exercise with id %d for student repository with participation id %d", operationName,
                            programmingExercise.getId(), participation.getId()), exception);
                    failedOperations.add(participation);
                }
            });
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignore -> {
            threadPool.shutdown();
            log.info("Finished executing (scheduled) task '{}' for programming exercise with id {}.", operationName, programmingExercise.getId());
            if (!failedOperations.isEmpty()) {
                var failedIds = failedOperations.stream().map(participation -> participation.getId().toString()).collect(Collectors.joining(","));
                log.warn("The (scheduled) task '{}' for programming exercise {} failed for these {} participations: {}", operation, programmingExercise.getId(),
                        failedOperations.size(), failedIds);
            }
            return failedOperations;
        });
    }
}
