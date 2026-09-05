package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTaskDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/**
 * REST controller for managing {@link ProgrammingExerciseTask}.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("authoring/tasks")
@RestController
@RequestMapping("api/programming/")
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
     * <p>
     * Excludes inactive test cases from the returned tasks (matching the previous behaviour), but does so by
     * filtering while mapping to the DTO rather than mutating the loaded {@link ProgrammingExerciseTask#getTestCases()}
     * collection in place - that collection backs the {@code programming_exercise_task_test_case} join table, and an
     * in-place {@code removeIf} risks a destructive write to that join table.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200}.
     */
    @GetMapping("programming-exercises/{exerciseId}/tasks")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<ProgrammingExerciseTaskDTO>> getTasks(@PathVariable Long exerciseId) {
        log.debug("REST request to retrieve ProgrammingExerciseTasks for ProgrammingExercise with id : {}", exerciseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithTestCases(exerciseId);
        Set<ProgrammingExerciseTaskDTO> taskDTOs = tasks.stream().map(ProgrammingExerciseTaskDTO::ofWithoutInactiveTestCases).collect(Collectors.toSet());

        return ResponseEntity.ok(taskDTOs);
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
    public ResponseEntity<List<ProgrammingExerciseTaskDTO>> getTasksWithUnassignedTask(@PathVariable Long exerciseId) {
        log.debug("REST request to retrieve ProgrammingExerciseTasks for ProgrammingExercise with id : {}", exerciseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithUnassignedTestCases(exerciseId);
        List<ProgrammingExerciseTaskDTO> taskDTOs = tasks.stream().map(ProgrammingExerciseTaskDTO::of).toList();
        return ResponseEntity.ok(taskDTOs);
    }
}
