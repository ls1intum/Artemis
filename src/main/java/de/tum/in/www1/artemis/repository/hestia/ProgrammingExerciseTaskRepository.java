package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
public interface ProgrammingExerciseTaskRepository extends JpaRepository<ProgrammingExerciseTask, Long> {

    Set<ProgrammingExerciseTask> findByExerciseId(Long exerciseId);

    @NotNull
    default ProgrammingExerciseTask findByIdElseThrow(long programmingExerciseTaskId) throws EntityNotFoundException {
        return findById(programmingExerciseTaskId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Task", programmingExerciseTaskId));
    }

    /**
     * Gets a task with its programming exercise, test cases and solution entries of the test cases
     *
     * @param entryId The id of the task
     * @return The task with the given ID if found
     * @throws EntityNotFoundException If no task with the given ID was found
     */
    @NotNull
    default ProgrammingExerciseTask findByIdWithTestCaseAndSolutionEntriesElseThrow(long entryId) throws EntityNotFoundException {
        return findByIdWithTestCaseAndSolutionEntries(entryId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Task", entryId));
    }

    /**
     * Gets a task with its programming exercise, test cases and solution entries of the test cases
     *
     * @param entryId The id of the task
     * @return The task with the given ID
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
            LEFT JOIN FETCH t.testCases tc
            LEFT JOIN FETCH tc.solutionEntries
            WHERE t.id = :entryId
            """)
    Optional<ProgrammingExerciseTask> findByIdWithTestCaseAndSolutionEntries(@Param("entryId") long entryId);

    /**
     * Gets a task by its name and associated programming exercise
     *
     * @param taskName The name of the task
     * @param exerciseId The programming exercise the task should be associated with
     * @return The task with the given name and programming exercise
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
            LEFT JOIN FETCH t.testCases tc
            WHERE t.taskName = :taskName
            AND t.exercise.id = :exerciseId
            """)
    Optional<ProgrammingExerciseTask> findByNameAndExerciseId(@Param("taskName") String taskName, @Param("exerciseId") long exerciseId);

    /**
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     * @throws EntityNotFoundException If the exercise with exerciseId does not exist
     */
    @NotNull
    default Set<ProgrammingExerciseTask> findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(long exerciseId) throws EntityNotFoundException {
        return findByExerciseIdWithTestCaseAndSolutionEntries(exerciseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Task", exerciseId));
    }

    /**
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
            LEFT JOIN FETCH t.testCases tc
            LEFT JOIN FETCH tc.solutionEntries
            WHERE t.exercise.id = :exerciseId
            AND tc.exercise.id = :exerciseId
            """)
    Optional<Set<ProgrammingExerciseTask>> findByExerciseIdWithTestCaseAndSolutionEntries(@Param("exerciseId") long exerciseId);

    /**
     * Gets all tasks with its test cases for a programming exercise
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     */
    @Query("""
            SELECT t
            FROM ProgrammingExerciseTask t
            LEFT JOIN FETCH t.testCases tc
            LEFT JOIN FETCH tc.solutionEntries
            WHERE t.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseTask> findByExerciseIdWithTestCases(@Param("exerciseId") Long exerciseId);

    /**
     * Returns the task name with the given id
     *
     * @param taskId the id of the task
     * @return the name of the task or null if the task does not exist
     */
    @Query("""
            SELECT t.taskName
            FROM ProgrammingExerciseTask t
            WHERE t.id = :taskId
            """)
    String getTaskName(@Param("taskId") Long taskId);

    @Query("""
            SELECT pt FROM ProgrammingExerciseTask  pt
            LEFT JOIN FETCH pt.exerciseHints h
            LEFT JOIN FETCH pt.testCases tc
            WHERE h.id = :codeHintId
            """)
    Optional<ProgrammingExerciseTask> findByCodeHintIdWithTestCases(@Param("codeHintId") Long codeHintId);
}
