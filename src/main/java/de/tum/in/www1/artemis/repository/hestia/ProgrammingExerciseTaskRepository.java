package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
public interface ProgrammingExerciseTaskRepository extends ArtemisJpaRepository<ProgrammingExerciseTask, Long> {

    Set<ProgrammingExerciseTask> findByExerciseId(Long exerciseId);

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
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     *
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
     *
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
     *
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

    @Query("""
            SELECT pt
            FROM ProgrammingExerciseTask  pt
                LEFT JOIN FETCH pt.exerciseHints h
                LEFT JOIN FETCH pt.testCases tc
            WHERE h.id = :codeHintId
            """)
    Optional<ProgrammingExerciseTask> findByCodeHintIdWithTestCases(@Param("codeHintId") Long codeHintId);
}
