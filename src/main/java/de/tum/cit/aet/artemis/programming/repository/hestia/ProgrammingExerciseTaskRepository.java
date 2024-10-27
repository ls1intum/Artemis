package de.tum.cit.aet.artemis.programming.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseTaskRepository extends ArtemisJpaRepository<ProgrammingExerciseTask, Long> {

    /**
     * Gets all tasks with its test cases and solution entries of the test case for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return All tasks with solution entries and associated test cases
     * @throws EntityNotFoundException If the exercise with exerciseId does not exist
     */
    @NotNull
    default List<ProgrammingExerciseTask> findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(long exerciseId) throws EntityNotFoundException {
        return getArbitraryValueElseThrow(findByExerciseIdWithTestCaseAndSolutionEntries(exerciseId), Long.toString(exerciseId));
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
    Optional<List<ProgrammingExerciseTask>> findByExerciseIdWithTestCaseAndSolutionEntries(@Param("exerciseId") long exerciseId);

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
