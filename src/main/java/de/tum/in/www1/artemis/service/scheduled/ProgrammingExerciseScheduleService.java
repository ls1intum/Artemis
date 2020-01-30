package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE;
import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS;

import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import io.github.jhipster.config.JHipsterConstants;

@Service
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseScheduleService.class);

    private final ScheduleService scheduleService;

    private final Environment env;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final GroupNotificationService groupNotificationService;

    private final Optional<VersionControlService> versionControlService;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseRepository programmingExerciseRepository, Environment env,
            ProgrammingSubmissionService programmingSubmissionService, GroupNotificationService groupNotificationService, Optional<VersionControlService> versionControlService) {
        this.scheduleService = scheduleService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.groupNotificationService = groupNotificationService;
        this.versionControlService = versionControlService;
        this.env = env;
    }

    @PostConstruct
    @Override
    public void scheduleRunningExercisesOnStartup() {
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
                List<ProgrammingExerciseStudentParticipation> failedLockOperations = removeWritePermissionsFromAllStudentRepositories(programmingExerciseId);

                // We sent a notification to the instructor about the success of the repository locking operation.
                long numberOfFailedLockOperations = failedLockOperations.size();
                Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId);
                if (programmingExercise.isEmpty()) {
                    throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
                }
                if (numberOfFailedLockOperations > 0) {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION + failedLockOperations.size());
                }
                else {
                    groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise.get(),
                            Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION);
                }
            }
            catch (EntityNotFoundException ex) {
                log.error("Programming exercise with id " + programmingExerciseId + " is no longer available in database for use in scheduled task.");
            }
        };
    }

    /**
     * Remove the write permissions for all students for their programming exercise repository.
     * They will still be able to read the code, but won't be able to change it.
     *
     * Requests are executed in batches so that the VCS is not overloaded with requests.
     *
     * @param programmingExerciseId     ProgrammingExercise id.
     * @return a list of participations for which the locking operation has failed. If everything went as expected, this should be an empty list.
     * @throws EntityNotFoundException  if the programming exercise can't be found.
     */
    public List<ProgrammingExerciseStudentParticipation> removeWritePermissionsFromAllStudentRepositories(Long programmingExerciseId) throws EntityNotFoundException {
        log.info("Invoking scheduled task 'remove write permissions from all student repositories' for programming exercise with id " + programmingExerciseId + ".");

        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExerciseId);
        if (programmingExercise.isEmpty()) {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
        List<ProgrammingExerciseStudentParticipation> failedLockOperations = new LinkedList<>();

        int index = 0;
        for (StudentParticipation studentParticipation : programmingExercise.get().getStudentParticipations()) {
            // Execute requests in batches instead all at once.
            if (index > 0 && index % EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE == 0) {
                try {
                    log.info("Sleep for {}s during removeWritePermissionsFromAllStudentRepositories", EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS / 1000);
                    Thread.sleep(EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before locking the student repositories for exercise " + programmingExerciseId, ex);
                }
            }

            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            try {
                versionControlService.get().setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getRepositoryUrlAsUrl(),
                        programmingExercise.get().getProjectKey(), programmingExerciseStudentParticipation.getStudent().getLogin());
            }
            catch (Exception e) {
                log.error("Removing write permissions failed for programming exercise with id " + programmingExerciseId + " for student repository with participation id "
                        + studentParticipation.getId() + ": " + e.getMessage());
                failedLockOperations.add(programmingExerciseStudentParticipation);
            }
            index++;
        }
        return failedLockOperations;
    }
}
