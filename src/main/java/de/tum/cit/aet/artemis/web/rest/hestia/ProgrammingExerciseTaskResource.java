package de.tum.cit.aet.artemis.web.rest.hestia;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * REST controller for managing {@link de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseTask}.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseTaskResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTaskResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingExerciseTaskResource(ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET programming-exercises/:exerciseId/tasks
     * Get all tasks with test cases and solution entries for a programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200}.
     */
    @GetMapping("programming-exercises/{exerciseId}/tasks")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<ProgrammingExerciseTask>> getTasks(@PathVariable Long exerciseId) {
        log.debug("REST request to retrieve ProgrammingExerciseTasks for ProgrammingExercise with id : {}", exerciseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithoutInactiveTestCases(exerciseId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET programming-exercises/:exerciseId/tasks-with-unassigned
     * Get all tasks with test cases and solution entries for a programming exercise
     * including test cases not manually assigned to any tasks in an 'Not assigned to task' task
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200}.
     */
    @GetMapping("programming-exercises/{exerciseId}/tasks-with-unassigned-test-cases")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<ProgrammingExerciseTask>> getTasksWithUnassignedTask(@PathVariable Long exerciseId) {
        log.debug("REST request to retrieve ProgrammingExerciseTasks for ProgrammingExercise with id : {}", exerciseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithUnassignedTestCases(exerciseId);
        return ResponseEntity.ok(tasks);
    }
}
