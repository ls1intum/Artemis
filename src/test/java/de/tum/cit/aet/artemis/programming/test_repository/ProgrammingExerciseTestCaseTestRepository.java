package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

@Repository
public interface ProgrammingExerciseTestCaseTestRepository extends ProgrammingExerciseTestCaseRepository {

    Optional<ProgrammingExerciseTestCase> findByExerciseIdAndTestName(long exerciseId, String testName);

    /**
     * Returns all test cases with the associated solution entries for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return all test cases with the associated solution entries
     */
    @Query("""
            SELECT DISTINCT tc
            FROM ProgrammingExerciseTestCase tc
                LEFT JOIN FETCH tc.solutionEntries se
            WHERE tc.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseTestCase> findByExerciseIdWithSolutionEntries(@Param("exerciseId") long exerciseId);
}
