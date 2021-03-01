package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.util.Tuple;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import io.github.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseScheduleService.class);

    private final ScheduleService scheduleService;

    private final Environment env;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final GroupNotificationService groupNotificationService;

    private final StudentExamRepository studentExamRepository;

    private final ExamDateService examDateService;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseRepository programmingExerciseRepository, Environment env,
            ProgrammingSubmissionService programmingSubmissionService, GroupNotificationService groupNotificationService, ExamDateService examDateService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, StudentExamRepository studentExamRepository) {
        this.scheduleService = scheduleService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.groupNotificationService = groupNotificationService;
        this.studentExamRepository = studentExamRepository;
        this.examDateService = examDateService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.env = env;
    }

    @PostConstruct
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

            List<ProgrammingExercise> programmingExercisesWithBuildAfterDueDate = programmingExerciseRepository
                    .findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
            programmingExercisesWithBuildAfterDueDate.forEach(this::scheduleExercise);

            List<ProgrammingExercise> programmingExercisesWithFutureManualAssessment = programmingExerciseRepository
                    .findAllByManualAssessmentAndDueDateAfterDate(ZonedDateTime.now());
            programmingExercisesWithFutureManualAssessment.forEach(this::scheduleExercise);

            List<ProgrammingExercise> programmingExercisesWithExam = programmingExerciseRepository.findAllWithEagerExamAllByExamEndDateAfterDate(ZonedDateTime.now());
            programmingExercisesWithExam.forEach(this::scheduleExamExercise);
            log.info("Scheduled " + programmingExercisesWithBuildAfterDueDate.size() + " programming exercises with a buildAndTestAfterDueDate.");
            log.info("Scheduled " + programmingExercisesWithFutureManualAssessment.size() + " programming exercises with future manual assessment.");
            log.info("Scheduled " + programmingExercisesWithExam.size() + " exam programming exercises.");
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
        if (!needsToBeScheduled(exercise)) {
            // If a programming exercise got changed so that any scheduling becomes unnecessary, we need to cancel all scheduled tasks
            cancelAllScheduledTasks(exercise);
            return;
        }
        scheduleExercise(exercise);
    }

    private static boolean needsToBeScheduled(ProgrammingExercise exercise) {
        // Exam exercises need to be scheduled
        if (isExamExercise(exercise)) {
            return true;
        }
        // Manual assessed programming exercises as well
        if (exercise.getAssessmentType() != AssessmentType.AUTOMATIC) {
            return true;
        }
        // If tests are run after due date and that due date lies in the future, we need to schedule that as well
        return exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && ZonedDateTime.now().isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate());
    }

    private void cancelAllScheduledTasks(ProgrammingExercise exercise) {
        scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.RELEASE);
        scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.DUE);
        scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.ASSESSMENT_DUE);
    }

    private void scheduleExercise(ProgrammingExercise exercise) {
        try {
            if (isExamExercise(exercise)) {
                scheduleExamExercise(exercise);
            }
            else {
                scheduleCourseExercise(exercise);
            }
        }
        catch (Exception e) {
            log.error("Failed to schedule exercise " + exercise.getId(), e);
        }
    }

    private void scheduleCourseExercise(ProgrammingExercise exercise) {
        // For any course exercise that needsToBeScheduled (buildAndTestAfterDueDate and/or manual assessment)
        if (exercise.getDueDate() != null && ZonedDateTime.now().isBefore(exercise.getDueDate())) {
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, lockAllStudentRepositories(exercise));
            log.debug("Scheduled lock student repositories after due date for Programming Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for "
                    + exercise.getDueDate() + ".");
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.DUE);
        }
        // For exercises with buildAndTestAfterDueDate
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && ZonedDateTime.now().isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate())) {
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
            log.debug("Scheduled build and test for student submissions after due date for Programming Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for "
                    + exercise.getBuildAndTestStudentSubmissionsAfterDueDate() + ".");
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
    }

    private void scheduleExamExercise(ProgrammingExercise exercise) {
        var exam = exercise.getExerciseGroup().getExam();
        var visibleDate = exam.getVisibleDate();
        var startDate = exam.getStartDate();
        if (visibleDate == null || startDate == null) {
            log.error("Programming exercise {} for exam {} cannot be scheduled properly, visible date is {}, start date is {}", exercise.getId(), exam.getId(), visibleDate,
                    startDate);
            return;
        }
        var unlockDate = getExamProgrammingExerciseUnlockDate(exercise);
        if (unlockDate.isAfter(ZonedDateTime.now())) {
            // Use the custom date from the exam rather than the of the exercise's lifecycle
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, Set.of(new Tuple<>(unlockDate, unlockAllStudentRepositories(exercise))));
        }
        else if (examDateService.getLatestIndividualExamEndDate(exam).isBefore(ZonedDateTime.now())) {
            // This is only a backup (e.g. a crash of this node and restart during the exam)
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, Set.of(new Tuple<>(ZonedDateTime.now().plusSeconds(5), unlockAllStudentRepositories(exercise))));
        }

        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && exercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(ZonedDateTime.now())) {
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
        }
        else {
            scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
        }
        log.debug("Scheduled Exam Programming Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");
    }

    @NotNull
    private Runnable buildAndTestRunnableForExercise(ProgrammingExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                log.info("Invoking scheduled task programming exercise with id " + exercise.getId() + ".");
                programmingSubmissionService.triggerInstructorBuildForExercise(exercise.getId());
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id " + exercise.getId() + " is no longer available in database for use in scheduled task.");
            }
        };
    }

    /**
     * Returns a runnable that, once executed, will (1) lock all student repositories and (2) stash all student changes in the online editor for manual assessments
     *
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be locked
     * @return a Runnable that will lock the repositories once it is executed
     */
    @NotNull
    public Runnable lockAllStudentRepositories(ProgrammingExercise exercise) {
        return lockStudentRepositories(exercise, participation -> true);
    }

    /**
     * Returns a runnable that, once executed, will (1) lock all student repositories and (2) stash all student changes in the online editor for manual assessments
     *
     * NOTE: this will not immediately lock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be locked
     * @param condition a condition that determines whether the operation will be executed for a specific participation
     * @return a Runnable that will lock the repositories once it is executed
     */
    @NotNull
    private Runnable lockStudentRepositories(ProgrammingExercise exercise, Predicate<ProgrammingExerciseStudentParticipation> condition) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                List<ProgrammingExerciseStudentParticipation> failedLockOperations = removeWritePermissionsFromAllStudentRepositories(programmingExerciseId, condition);
                // We sent a notification to the instructor about the success of the repository locking and stashing operations.
                long numberOfFailedLockOperations = failedLockOperations.size();

                Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository
                        .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId);
                if (programmingExercise.isEmpty()) {
                    throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
                }
                if (numberOfFailedLockOperations > 0) {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION + numberOfFailedLockOperations);
                }
                else {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION);
                }

                // Stash the not submitted/committed changes for exercises with manual assessment and with online editor enabled
                // This is necessary for students who have used the online editor, to ensure that only submitted/committed changes are displayed during manual assessment
                // in the case they still have saved changes on the Artemis server which have not been committed / pushed
                // NOTE: we always stash, also when manual assessment is not activated, because instructors might change this after the exam
                if (Boolean.TRUE.equals(exercise.isAllowOnlineEditor())) {
                    List<ProgrammingExerciseStudentParticipation> failedStashOperations = stashChangesInAllStudentRepositories(programmingExerciseId, condition);
                    long numberOfFailedStashOperations = failedStashOperations.size();
                    if (numberOfFailedStashOperations > 0) {
                        groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                                Constants.PROGRAMMING_EXERCISE_FAILED_STASH_OPERATIONS_NOTIFICATION + numberOfFailedStashOperations);
                    }
                    else {
                        groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                                Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_STASH_OPERATION_NOTIFICATION);
                    }
                }
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id " + programmingExerciseId + " is no longer available in database for use in scheduled task.");
            }
        };
    }

    /**
     * Returns a runnable that, once executed, will unlock all student repositories and will schedule all repository lock tasks.
     * The unlock tasks will be grouped so that for every existing due date (which is the exam start date + the different working times), one task will be scheduled.
     *
     * NOTE: this will not immediately unlock the repositories as only a Runnable is returned!
     *
     * @param exercise The exercise for which the repositories should be unlocked
     * @return a Runnable that will unlock the repositories once it is executed
     */
    @NotNull
    public Runnable unlockAllStudentRepositories(ProgrammingExercise exercise) {
        Long programmingExerciseId = exercise.getId();
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> individualDueDates = new HashSet<>();
                // This operation unlocks the repositories and collects all individual due dates
                BiConsumer<ProgrammingExercise, ProgrammingExerciseStudentParticipation> unlockAndCollectOperation = (programmingExercise, participation) -> {
                    var dueDate = studentExamRepository.getIndividualDueDate(programmingExercise, participation);
                    if (dueDate != null) {
                        individualDueDates.add(new Tuple<>(dueDate, participation));
                    }
                    programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, participation);
                };
                List<ProgrammingExerciseStudentParticipation> failedUnlockOperations = invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId,
                        unlockAndCollectOperation, participation -> true, "add write permissions to all student repositories");

                // We sent a notification to the instructor about the success of the repository unlocking operation.
                long numberOfFailedUnlockOperations = failedUnlockOperations.size();
                Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository
                        .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId);
                if (programmingExercise.isEmpty()) {
                    throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
                }
                if (numberOfFailedUnlockOperations > 0) {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_FAILED_UNLOCK_OPERATIONS_NOTIFICATION + failedUnlockOperations.size());
                }
                else {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_UNLOCK_OPERATION_NOTIFICATION);
                }

                if (exercise.needsLockOperation()) {
                    // Schedule the lock operations here, this is also done here because the working times might change often before the exam start
                    // Note: this only makes sense before the due date of a course exercise or before the end date of an exam, because for individual dates in the past
                    // the scheduler would execute the lock operation immediately, making the unlock obsolete, therefore we filter out all individual due dates in the past
                    // one use case is that the unlock all operation is invoked directly after exam start
                    Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> futureIndividualDueDates = individualDueDates.stream()
                            .filter(tuple -> tuple.x != null && ZonedDateTime.now().isBefore(tuple.x)).collect(Collectors.toSet());
                    scheduleIndividualRepositoryLockTasks(exercise, futureIndividualDueDates);
                }
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id " + programmingExerciseId + " is no longer available in database for use in scheduled task.");
            }
        };
    }

    /**
     * this method schedules individual lock tasks for programming exercises (mostly in the context of exams)
     * @param exercise the programming exercise for which the lock is executed
     * @param individualDueDates these are the individual due dates for students taking individual workingTimes of student exams into account
     */
    private void scheduleIndividualRepositoryLockTasks(ProgrammingExercise exercise, Set<Tuple<ZonedDateTime, ProgrammingExerciseStudentParticipation>> individualDueDates) {
        // 1. Group all participations by due date (TODO use student exams for safety if some participations are not pre-generated)
        var participationsGroupedByDueDate = individualDueDates.stream().filter(tuple -> tuple.getX() != null)
                .collect(Collectors.groupingBy(Tuple::getX, Collectors.mapping(Tuple::getY, Collectors.toSet())));
        // 2. Transform those groups into lock-repository tasks with times
        var tasks = participationsGroupedByDueDate.entrySet().stream().map(entry -> {
            Predicate<ProgrammingExerciseStudentParticipation> lockingCondition = participation -> entry.getValue().contains(participation);
            var groupDueDate = entry.getKey();
            var task = lockStudentRepositories(exercise, lockingCondition);
            return new Tuple<>(groupDueDate, task);
        }).collect(Collectors.toSet());
        // 3. Schedule all tasks
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, tasks);
    }

    private static boolean isExamExercise(ProgrammingExercise exercise) {
        return exercise.isExamExercise();
    }

    private static ZonedDateTime getExamProgrammingExerciseUnlockDate(ProgrammingExercise exercise) {
        // using start date minus 5 minutes here because unlocking will take some time (it is invoked synchronously).
        return exercise.getExerciseGroup().getExam().getStartDate().minusMinutes(5);
    }

    private List<ProgrammingExerciseStudentParticipation> removeWritePermissionsFromAllStudentRepositories(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, programmingExerciseParticipationService::lockStudentRepository, condition,
                "remove write permissions from all student repositories");
    }

    private List<ProgrammingExerciseStudentParticipation> stashChangesInAllStudentRepositories(Long programmingExerciseId,
            Predicate<ProgrammingExerciseStudentParticipation> condition) throws EntityNotFoundException {
        return invokeOperationOnAllParticipationsThatSatisfy(programmingExerciseId, programmingExerciseParticipationService::stashChangesInStudentRepositoryAfterDueDateHasPassed,
                condition, "stash changes from all student repositories");
    }

    /**
     * Invokes the given <code>operation</code> on all student participations that satisfy the <code>condition</code>-{@link Predicate}.
     * <p>
     *
     * @param programmingExerciseId the programming exercise whose participations should be processed
     * @param operation the operation to perform
     * @param condition the condition that tests whether to invoke the operation on a participation
     * @param operationName the name of the operation, this is only used for logging
     * @return a list containing all participations for which the operation has failed with an exception
     * @throws EntityNotFoundException if the programming exercise can't be found.
     */
    private List<ProgrammingExerciseStudentParticipation> invokeOperationOnAllParticipationsThatSatisfy(Long programmingExerciseId,
            BiConsumer<ProgrammingExercise, ProgrammingExerciseStudentParticipation> operation, Predicate<ProgrammingExerciseStudentParticipation> condition,
            String operationName) {
        log.info("Invoking (scheduled) task '" + operationName + "' for programming exercise with id " + programmingExerciseId + ".");

        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExerciseId);
        if (programmingExercise.isEmpty()) {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
        List<ProgrammingExerciseStudentParticipation> failedOperations = new LinkedList<>();

        // TODO: we should think about executing those operations again in batches to avoid issues on the vcs server, however those operations are typically executed
        // synchronously so this might not be an issue

        for (StudentParticipation studentParticipation : programmingExercise.get().getStudentParticipations()) {
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;

            // ignore all participations that don't fulfill the condition
            if (!condition.test(programmingExerciseStudentParticipation)) {
                continue;
            }

            try {
                // this actually invokes the operation
                operation.accept(programmingExercise.get(), programmingExerciseStudentParticipation);
            }
            catch (Exception e) {
                log.error("'" + operationName + "' failed for programming exercise with id " + programmingExerciseId + " for student repository with participation id "
                        + studentParticipation.getId(), e);
                failedOperations.add(programmingExerciseStudentParticipation);
            }
        }
        return failedOperations;
    }
}
