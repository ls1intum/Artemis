package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseScheduleService.class);

    private final ScheduleService scheduleService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final GroupNotificationService groupNotificationService;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionService programmingSubmissionService, GroupNotificationService groupNotificationService) {
        this.scheduleService = scheduleService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.groupNotificationService = groupNotificationService;
    }

    @PostConstruct
    @Override
    public void scheduleRunningExercisesOnStartup() {
        SecurityUtils.setAuthorizationObject();
        List<ProgrammingExercise> programmingExercisesWithBuildAfterDueDate = programmingExerciseService.findAllWithBuildAndTestAfterDueDateInFuture();
        programmingExercisesWithBuildAfterDueDate.forEach(this::scheduleExercise);
        log.info("Scheduled building the student submissions for " + programmingExercisesWithBuildAfterDueDate.size() + " programming exercises with a buildAndTestAfterDueDate.");
    }

    /**
     * Will cancel a scheduled task if the buildAndTestAfterDueDate is null or has passed already.
     *
     * @param exercise ProgrammingExercise
     */
    @Override
    public void scheduleExerciseIfRequired(ProgrammingExercise exercise) {
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null || exercise.getBuildAndTestStudentSubmissionsAfterDueDate().isBefore(ZonedDateTime.now())) {
            scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.DUE);
            scheduleService.cancelScheduledTaskForLifecycle(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
            return;
        }
        scheduleExercise(exercise);
    }

    private void scheduleExercise(ProgrammingExercise exercise) {
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, lockStudentRepositories(exercise.getId()));
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
        log.debug("Scheduled build and test for student submissions after due date for Programming Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for "
                + exercise.getBuildAndTestStudentSubmissionsAfterDueDate() + ".");
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

    @NotNull
    private Runnable lockStudentRepositories(long programmingExerciseId) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                List<ProgrammingExerciseStudentParticipation> failedLockOperations = programmingExerciseService
                        .removeWritePermissionsFromAllStudentRepositories(programmingExerciseId);

                // We sent a notification to the instructor about the success of the repository locking operation.
                long numberOfFailedLockOperations = failedLockOperations.size();
                ProgrammingExercise programmingExercise = programmingExerciseService.findById(programmingExerciseId);
                if (numberOfFailedLockOperations > 0) {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise,
                            Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION + failedLockOperations.size());
                }
                else {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise, Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION);
                }
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id " + programmingExerciseId + " is no longer available in database for use in scheduled task.");
            }
        };
    }

}
