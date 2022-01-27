package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link ProgrammingExerciseTask}.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseTaskResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTaskResource.class);

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingExerciseTaskResource(ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService) {
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /programming-exercise-tasks/:taskId/name : Returns the name of the task with the given id
     *
     * @param taskId the id of the task
     * @return the name of the task wrapped in an ResponseEntity or 404 Not Found if no task with that id exists
     */
    @GetMapping(value = "/programming-exercise-tasks/{taskId}/name")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getTaskName(@PathVariable Long taskId) {
        final var title = programmingExerciseTaskRepository.getTaskName(taskId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * {@code GET  /programming-exercise-tasks/:taskId} : get the "id" programmingExerciseTask.
     *
     * @param programmingExerciseTaskId the id of the programmingExerciseTask to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the programmingExerciseTask, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/programming-exercise-tasks/{programmingExerciseTaskId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseTask> getProgrammingExerciseTask(@PathVariable Long programmingExerciseTaskId) {
        log.debug("REST request to get ProgrammingExerciseTask : {}", programmingExerciseTaskId);
        var task = programmingExerciseTaskRepository.findByIdWithTestCaseAndSolutionEntriesAndProgrammingExerciseElseThrow(programmingExerciseTaskId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, task.getExercise(), null);
        return ResponseEntity.ok().body(task);
    }

    /**
     * {@code GET  /programming-exercises/:exerciseId/programming-exercise-tasks} : get the programmingExerciseTasks of a provided programmingExercise.
     *
     * @param exerciseId the exercise id of which to retrieve the tasks.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the tasks, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/programming-exercises/{exerciseId}/programming-exercise-tasks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ProgrammingExerciseTask>> getTasksForProgrammingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ProgrammingExerciseTasks : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(tasks);
    }
}
