package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.List;

import javax.annotation.PostConstruct;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;

@Service
public class ProgrammingExerciseScheduleService implements IExerciseScheduleService<ProgrammingExercise> {

    private final Logger log = LoggerFactory.getLogger(TextClusteringScheduleService.class);

    private final ScheduleService scheduleService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionResource programmingSubmissionResource;

    public ProgrammingExerciseScheduleService(ScheduleService scheduleService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionResource programmingSubmissionResource) {
        this.scheduleService = scheduleService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionResource = programmingSubmissionResource;
    }

    @PostConstruct
    public void scheduleRunningExercisesOnStartup() {
        List<ProgrammingExercise> programmingExercisesWithBuildAfterDueDate = programmingExerciseService.findAllWithBuildAndTestAfterDueDateInFuture();
        programmingExercisesWithBuildAfterDueDate.forEach(this::scheduleExercise);
        log.info("Scheduled building the student submissions for " + programmingExercisesWithBuildAfterDueDate.size() + " programming exercises with a buildAndTestAfterDueDate.");
    }

    /**
     * Schedule a clustering task for a text exercise with its due date if automatic assessments are enabled and its due date is in the future.
     * @param exercise exercise to schedule clustering for
     */
    public void scheduleExerciseIfRequired(ProgrammingExercise exercise) {
        if (exercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null || exercise.getBuildAndTestStudentSubmissionsAfterDueDate().isBefore(ZonedDateTime.now())) {
            scheduleService.cancelScheduledTask(exercise);
            return;
        }
        scheduleExercise(exercise);
    }

    public void scheduleExercise(ProgrammingExercise exercise) {
        scheduleService.scheduleTask(exercise, ExerciseLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, buildAndTestRunnableForExercise(exercise));
        log.debug("Scheduled build and test for student submissions after due date for Programming Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for "
                + exercise.getBuildAndTestStudentSubmissionsAfterDueDate() + ".");
    }

    @NotNull
    private Runnable buildAndTestRunnableForExercise(ProgrammingExercise exercise) {
        return () -> {
            // TODO: needed?
            // SecurityUtils.setAuthorizationObject();
            programmingSubmissionResource.triggerInstructorBuildForExercise(exercise.getId());
        };
    }

}
