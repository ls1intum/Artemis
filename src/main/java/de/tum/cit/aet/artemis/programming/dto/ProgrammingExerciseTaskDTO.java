package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

/**
 * DTO for a programming exercise task, used both for GET {@code tasks} (active test cases only) and GET
 * {@code tasks-with-unassigned-test-cases} (all test cases, plus a synthetic {@code id == null} "not assigned to
 * task" entry).
 * <p>
 * {@code tasks-with-unassigned-test-cases} returns a {@code List}, as it did before: the synthetic unassigned task
 * carries a {@code null} id, so a {@code Set} would collapse it against any other id-less entry. {@code tasks} keeps
 * the {@code Set} it returned before, which also drops the rows the join fetch repeats per test case.
 *
 * @param id        the task id; {@code null} for the synthetic "not assigned to task" entry
 * @param taskName  the task name as written in the problem statement
 * @param testCases the test cases assigned to the task
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTaskDTO(Long id, String taskName, List<ProgrammingExerciseTestCaseResponseDTO> testCases) {

    /**
     * Converts a task with all of its test cases, active and inactive alike.
     *
     * @param task the task, loaded with its test case collection
     * @return the converted DTO
     */
    public static ProgrammingExerciseTaskDTO of(ProgrammingExerciseTask task) {
        List<ProgrammingExerciseTestCaseResponseDTO> testCaseDTOs = task.getTestCases().stream().map(ProgrammingExerciseTestCaseResponseDTO::of).toList();
        return new ProgrammingExerciseTaskDTO(task.getId(), task.getTaskName(), testCaseDTOs);
    }

    /**
     * Converts a task, mapping only its active test cases, without mutating the task's loaded {@code testCases}
     * collection — that collection backs the {@code programming_exercise_task_test_case} join table, so filtering it
     * in place risks a destructive write. Filtering therefore happens while mapping.
     *
     * @param task the task, loaded with its full (active + inactive) test case collection
     * @return the DTO with only the active test cases included
     */
    public static ProgrammingExerciseTaskDTO ofWithoutInactiveTestCases(ProgrammingExerciseTask task) {
        List<ProgrammingExerciseTestCaseResponseDTO> testCaseDTOs = task.getTestCases().stream().filter(ProgrammingExerciseTestCase::isActive)
                .map(ProgrammingExerciseTestCaseResponseDTO::of).toList();
        return new ProgrammingExerciseTaskDTO(task.getId(), task.getTaskName(), testCaseDTOs);
    }
}
