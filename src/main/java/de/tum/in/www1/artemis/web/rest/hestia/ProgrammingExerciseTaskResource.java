package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask}.
 */
@RestController
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
     * GET programming-exercises/:exerciseId/tasks
     * Get all tasks with test cases and solution entries for a programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200}.
     */
    @GetMapping("programming-exercises/{exerciseId}/tasks")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<ProgrammingExerciseTask>> getTasks(@PathVariable Long exerciseId) {
        log.debug("REST request to retrieve ProgrammingExerciseTasks for ProgrammingExercise with id : {}", exerciseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exerciseId);
        return ResponseEntity.ok(tasks);
    }
}
