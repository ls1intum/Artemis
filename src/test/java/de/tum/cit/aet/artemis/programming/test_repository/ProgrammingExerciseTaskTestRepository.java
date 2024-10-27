package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
@Repository
@Primary
public interface ProgrammingExerciseTaskTestRepository extends ProgrammingExerciseTaskRepository {

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
        return getValueElseThrow(findByIdWithTestCaseAndSolutionEntries(entryId), entryId);
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
}
