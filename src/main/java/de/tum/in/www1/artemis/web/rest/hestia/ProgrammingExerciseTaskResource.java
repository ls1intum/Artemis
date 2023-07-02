package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask}.
 */
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseTaskResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTaskResource.class);

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
